package cn.mapleafgo.jcasbin.db;

import cn.hutool.core.lang.func.VoidFunc1;
import cn.hutool.db.Db;
import cn.hutool.db.transaction.TransactionLevel;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * LeafDb 是一个自定义的数据库操作类，继承自 Hutool 的 Db 类，
 *
 * @author mapleafgo
 */
public class LeafDb extends Db {
    public LeafDb(DataSource ds) {
        super(ds);
    }

    /**
     * 返回一个 LeafDb 实例，保证调用者通过 LeafDb.use(...) 获取到的是自定义子类，
     * 使得重写的 tx/getConnection/closeConnection 能够生效。
     */
    public static LeafDb use(DataSource ds) {
        return new LeafDb(ds);
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            Class<?> dsUtils = Class.forName("org.springframework.jdbc.datasource.DataSourceUtils");
            Method getConnection = dsUtils.getMethod("getConnection", DataSource.class);
            Object conn = getConnection.invoke(null, this.ds);
            if (conn instanceof Connection) {
                return (Connection) conn;
            }
        } catch (ClassNotFoundException e) {
            // Spring 未提供 DataSourceUtils，回退到 dataSource.getConnection()
        } catch (Throwable ignored) {
        }
        // 回退
        return super.getConnection();
    }

    @Override
    public void closeConnection(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            Class<?> dsUtils = Class.forName("org.springframework.jdbc.datasource.DataSourceUtils");
            Method release = dsUtils.getMethod("releaseConnection", Connection.class, DataSource.class);
            release.invoke(null, conn, this.ds);
            return;
        } catch (ClassNotFoundException e) {
            // Spring 不存在，回退到父类行为
        } catch (Throwable ignored) {
            // 反射或调用失败，回退到默认处理
        }
        super.closeConnection(conn);
    }

    @Override
    public LeafDb tx(TransactionLevel level, VoidFunc1<Db> callback) throws SQLException {
        try {
            Class<?> txMgr = Class.forName("org.springframework.transaction.support.TransactionSynchronizationManager");
            java.lang.reflect.Method isActive = txMgr.getMethod("isActualTransactionActive");
            Object res = isActive.invoke(null);
            if (res instanceof Boolean && (Boolean) res) {
                // Spring 事务活跃：直接执行回调，不由 Hutool 管理事务边界
                callback.call(this);
                return this;
            }
        } catch (ClassNotFoundException e) {
            // Spring 不在类路径
        } catch (Throwable ignored) {
        }
        return (LeafDb) super.tx(level, callback);
    }
}
