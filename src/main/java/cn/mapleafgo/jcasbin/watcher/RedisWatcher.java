package cn.mapleafgo.jcasbin.watcher;

import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.persist.Watcher;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * 使用 JedisPool 和 Executor 进行并发的现代 Redis 观察器。
 *
 * @author 慕枫
 */
@Slf4j
public class RedisWatcher implements Watcher, AutoCloseable {
    private final JedisPool pool;
    private final String keyName;
    private final String channelName;
    private final Executor callbackExecutor;
    private final ExecutorService subscriberExecutor;
    private final boolean shutdownSubscriberExecutorOnClose;
    private volatile Runnable callback;
    private volatile Consumer<String> callbackConsumer;
    private volatile JedisPubSub pubSub;

    /**
     *  创建一个 RedisWatcher。
     * <p>
     * 注意：此构造函数要求调用者提供 {@code subscriberExecutor}，该
     * 运行阻止 Jedis 订阅调用。观察者只会关闭订阅者
     * executor if {@code shutdownSubscriberExecutorOnClose} 为 true（参见私有构造函数）。
     * <p>
     * 如果 {@code callbackExecutor} 为 null，则使用 {@link java.util.concurrent.ForkJoinPool#commonPool（）}。
     */
    public RedisWatcher(JedisPool pool, String keyName, String channelName, Executor callbackExecutor, ExecutorService subscriberExecutor) {
        this(pool, keyName, channelName, callbackExecutor, subscriberExecutor, false);
    }

    private RedisWatcher(JedisPool pool, String keyName, String channelName, Executor callbackExecutor, ExecutorService subscriberExecutor, boolean shutdownSubscriberExecutorOnClose) {
        this.pool = Objects.requireNonNull(pool);
        this.keyName = Objects.requireNonNull(keyName);
        this.channelName = Objects.requireNonNull(channelName);
        // allow null callbackExecutor and default to commonPool
        this.callbackExecutor = callbackExecutor == null ? ForkJoinPool.commonPool() : callbackExecutor;
        this.subscriberExecutor = Objects.requireNonNull(subscriberExecutor);
        this.shutdownSubscriberExecutorOnClose = shutdownSubscriberExecutorOnClose;
    }

    @Override
    public void setUpdateCallback(Runnable runnable) {
        this.callback = runnable;
    }

    @Override
    public void setUpdateCallback(Consumer<String> consumer) {
        this.callbackConsumer = consumer;
    }

    @Override
    public void update() {
        try (Jedis j = pool.getResource()) {
            // Jedis supports INCR which returns the new value
            Long val = j.incr(keyName);
            log.info("redis watcher INCR {} -> {}", keyName, val);
        } catch (Exception e) {
            log.error("redis watcher update failed", e);
        }
    }

    public synchronized void startWatch() {
        if (pubSub != null) {
            // already running
            return;
        }

        JedisPubSub ps = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                Runnable cb = callback;
                Consumer<String> cbConsumer = callbackConsumer;
                if (cb != null) {
                    callbackExecutor.execute(() -> {
                        try {
                            cb.run();
                        } catch (Throwable t) {
                            log.error("error running watcher callback", t);
                        }
                    });
                }
                if (cbConsumer != null) {
                    String formatted = formatEvent(message);
                    callbackExecutor.execute(() -> {
                        try {
                            cbConsumer.accept(formatted);
                        } catch (Throwable t) {
                            log.error("error running watcher consumer", t);
                        }
                    });
                }
            }
        };

        this.pubSub = ps;

        subscriberExecutor.execute(() -> {
            try (Jedis j = pool.getResource()) {
                // subscribe blocks until unsubscribed
                j.subscribe(ps, channelName);
            } catch (Exception e) {
                log.error("redis watcher subscribe error", e);
            }
        });
    }

    public synchronized void closeWatch() {
        JedisPubSub current = this.pubSub;
        if (current != null) {
            try {
                current.unsubscribe();
            } catch (Exception e) {
                log.warn("error unsubscribing", e);
            } finally {
                this.pubSub = null;
            }
        }
    }

    @Override
    public void close() {
        closeWatch();
        if (shutdownSubscriberExecutorOnClose) {
            try {
                subscriberExecutor.shutdownNow();
            } catch (Exception e) {
                log.warn("failed to shutdown subscriber executor", e);
            }
        }
    }

    private String formatEvent(String message) {
        return String.format("casbin watcher Get: %s", message == null ? "" : message);
    }
}
