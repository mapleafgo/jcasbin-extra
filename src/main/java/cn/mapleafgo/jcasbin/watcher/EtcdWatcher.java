package cn.mapleafgo.jcasbin.watcher;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.watch.WatchEvent;
import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.persist.Watcher;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * casbin etcd 观察者
 *
 * @author 慕枫
 */
@Slf4j
public class EtcdWatcher implements Watcher, AutoCloseable {
    private final Client client;
    private final String keyName;
    private final Executor callbackExecutor;

    private volatile Runnable callback;
    private volatile Consumer<String> callbackConsumer;
    private AutoCloseable watcher;
    private static final int IO_TIMEOUT_SECONDS = 5;
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public EtcdWatcher(Client client, String keyName) {
        this(client, keyName, ForkJoinPool.commonPool());
    }

    public EtcdWatcher(Client client, String keyName, Executor callbackExecutor) {
        this.client = client;
        this.keyName = keyName;
        this.callbackExecutor = callbackExecutor == null ? ForkJoinPool.commonPool() : callbackExecutor;
    }

    public ByteSequence getKeyName() {
        return ByteSequence.from(keyName, CHARSET);
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
        try {
            int v = 0;
            GetResponse resp = client.getKVClient().get(getKeyName()).get(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (resp.getCount() != 0) {
                String valueStr = resp.getKvs().get(0).getValue().toString(CHARSET);
                v = parseIntOrZero(valueStr);
                log.info("casbin watcher Get: {}", v);
                v += 1;
            }
            // ensure put completes with timeout so errors are observed and we don't block indefinitely
            client.getKVClient().put(getKeyName(), ByteSequence.from(String.valueOf(v), CHARSET)).get(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("casbin watcher interrupted", ie);
        } catch (TimeoutException te) {
            log.error("casbin watcher timeout", te);
        } catch (Exception e) {
            log.error("casbin watcher execution error", e.getCause() != null ? e.getCause() : e);
        }
    }

    public void startWatch() {
        // close existing watcher if present to avoid resource leak
        closeWatch();
        // store watcher so we can close it later
        AutoCloseable w = client.getWatchClient().watch(getKeyName(), watchResponse -> {
            List<WatchEvent> eventList = watchResponse.getEvents();
            Runnable cb = this.callback; // capture volatile to local
            Consumer<String> cbConsumer = this.callbackConsumer;
            if (cb != null) {
                callbackExecutor.execute(() -> safeExecute(() -> eventList.forEach(e -> cb.run())));
            }
            if (cbConsumer != null) {
                callbackExecutor.execute(() -> safeExecute(() -> eventList.forEach(e -> cbConsumer.accept(formatEvent(e)))));
            }
        });
        if (w != null) {
            watcher = w;
        }
    }

    public synchronized void closeWatch() {
        try {
            if (watcher != null) {
                watcher.close();
                watcher = null;
            }
        } catch (Exception e) {
            log.warn("error closing etcd watcher", e);
        }
    }

    @Override
    public void close() {
        closeWatch();
    }

    private int parseIntOrZero(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            log.warn("casbin watcher value is not an integer: {}", s);
            return 0;
        }
    }

    private String formatEvent(WatchEvent e) {
        return String.format("casbin watcher Get: %s", e.getKeyValue().getValue().toString(CHARSET));
    }

    private void safeExecute(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            log.error("error running watcher callback", t);
        }
    }

}
