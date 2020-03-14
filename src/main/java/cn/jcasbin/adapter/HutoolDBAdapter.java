package cn.jcasbin.adapter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.Session;
import cn.jcasbin.entity.CasbinRule;
import lombok.SneakyThrows;
import org.casbin.jcasbin.exception.CasbinAdapterException;
import org.casbin.jcasbin.model.Model;
import org.casbin.jcasbin.persist.Adapter;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Casbin HutoolDB适配器
 *
 * @author 慕枫
 */
public class HutoolDBAdapter implements Adapter {
    private final Session session;
    private final String tableName;

    public HutoolDBAdapter(DataSource dataSource, String tableName) throws SQLException {
        if (StrUtil.isBlank(tableName)) throw new CasbinAdapterException("表名不能为空");
        this.session = Session.create(dataSource);
        this.tableName = tableName;

        String initTable = "CREATE TABLE IF NOT EXISTS %s (" +
            "    ptype varchar(10) NOT NULL," +
            "    v0    varchar(100) DEFAULT NULL," +
            "    v1    varchar(100) DEFAULT NULL," +
            "    v2    varchar(100) DEFAULT NULL," +
            "    v3    varchar(100) DEFAULT NULL," +
            "    v4    varchar(100) DEFAULT NULL" +
            ")";
        Db.use(dataSource).execute(String.format(initTable, tableName));
    }

    @Override
    @SneakyThrows(SQLException.class)
    public void loadPolicy(Model model) {
        List<Entity> entityList = session.findAll(tableName);
        // 按ptype对策略进行分组,并合并重复数据
        Map<String, List<List<String>>> policies = entityList.parallelStream().distinct()
            .collect(Collectors.toMap(entity -> entity.getStr("ptype"), entity -> {
                entity.remove("ptype");
                ArrayList<List<String>> lists = new ArrayList<>();
                lists.add(Arrays.asList(entity.values().toArray(new String[0])));
                return lists;
            }, (value, newValue) -> {
                value.addAll(newValue);
                return value;
            }));
        // 对分组的策略进行加载
        policies.keySet().forEach(k -> model.model.get(k.substring(0, 1)).get(k).policy.addAll(policies.get(k)));
    }

    @Override
    public void savePolicy(Model model) {
        List<CasbinRule> casbinRules = CasbinRule.transformToCasbinRule(model);
        if (casbinRules.isEmpty()) return;

        try {
            session.tx(s -> {
                s.execute("DELETE FROM ?", tableName);
                s.insert(casbinRules.stream().map(Entity::parse).peek(entity -> entity.setTableName(tableName)).collect(Collectors.toList()));
            });
        } catch (SQLException e) {
            session.quietRollback();
            throw new CasbinAdapterException("casbin policy 保存失败", e);
        }
    }

    @Override
    public void addPolicy(String sec, String ptype, List<String> rule) {
        CasbinRule casbinRule = new CasbinRule();
        casbinRule.setPtype(ptype);
        casbinRule.setRule(rule);

        Entity entity = Entity.parse(casbinRule);
        entity.setTableName(tableName);
        try {
            if (session.count(entity) <= 0)
                session.insert(entity);
        } catch (SQLException e) {
            session.quietRollback();
            throw new CasbinAdapterException("casbin policy 新增失败", e);
        }
    }

    @Override
    public void removePolicy(String sec, String ptype, List<String> rule) {
        CasbinRule casbinRule = new CasbinRule();
        casbinRule.setPtype(ptype);
        casbinRule.setRule(rule);

        Entity entity = Entity.parse(casbinRule);
        entity.setTableName(tableName);
        try {
            session.tx(s -> s.del(entity));
        } catch (SQLException e) {
            session.quietRollback();
            throw new CasbinAdapterException("casbin policy 移除失败", e);
        }
    }

    @Override
    public void removeFilteredPolicy(String sec, String ptype, int fieldIndex, String... fieldValues) {
        Entity entity = Entity.create(tableName);
        entity.putAll(Objects.requireNonNull(CasbinRule.toRuleMap(ptype, fieldIndex, fieldValues)));

        try {
            session.tx(s -> s.del(entity));
        } catch (SQLException e) {
            session.quietRollback();
            throw new CasbinAdapterException("casbin policy 按条件移除失败", e);
        }
    }
}
