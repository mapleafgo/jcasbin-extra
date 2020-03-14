package cn.jcasbin.watcher;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.watch.WatchEvent;
import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.persist.Watcher;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * casbin etcd 观察者
 *
 * @author 慕枫
 */
@Slf4j
public class EtcdWatcher implements Watcher {
    private Client client;
    private String keyName;

    private Runnable callback;

    public EtcdWatcher(Client client, String keyName) {
        this.client = client;
        this.keyName = keyName;
    }

    public ByteSequence getKeyName() {
        return ByteSequence.from(keyName, Charset.defaultCharset());
    }

    @Override
    public void setUpdateCallback(Runnable runnable) {
        this.callback = runnable;
    }

    @Override
    public void update() {
        CompletableFuture<GetResponse> completableFuture = client.getKVClient().get(getKeyName());
        try {
            int v = 0;
            GetResponse resp = completableFuture.get();
            if (resp.getCount() != 0) {
                v = Integer.parseInt(resp.getKvs().get(0).getValue().toString(Charset.defaultCharset()));
                log.info("casbin watcher Get: {}", v);
                v += 1;
            }
            client.getKVClient().put(getKeyName(), ByteSequence.from(String.valueOf(v), Charset.defaultCharset()));
        } catch (InterruptedException | ExecutionException e) {
            log.error("casbin watcher error", e);
        }
    }

    public void startWatch() {
        client.getWatchClient().watch(getKeyName(), watchResponse -> {
            List<WatchEvent> eventList = watchResponse.getEvents();
            if (callback != null) eventList.forEach(e -> callback.run());
        });
    }

    public void closeWatch() {
        client.getWatchClient().close();
    }
}
