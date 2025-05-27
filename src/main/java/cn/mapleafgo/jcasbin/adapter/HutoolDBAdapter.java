package cn.mapleafgo.jcasbin.adapter;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.db.Session;
import cn.mapleafgo.jcasbin.entity.CasbinRule;
import lombok.SneakyThrows;
import org.casbin.jcasbin.exception.CasbinAdapterException;
import org.casbin.jcasbin.model.Assertion;
import org.casbin.jcasbin.model.Model;
import org.casbin.jcasbin.persist.Adapter;
import org.casbin.jcasbin.persist.BatchAdapter;
import org.casbin.jcasbin.persist.UpdatableAdapter;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Casbin HutoolDB适配器
 *
 * @author 慕枫
 */
public class HutoolDBAdapter implements Adapter, BatchAdapter, UpdatableAdapter {
    protected final Session session;
    protected final String tableName;

    public HutoolDBAdapter(DataSource dataSource, String tableName) throws SQLException {
        if (StrUtil.isBlank(tableName)) {
            throw new CasbinAdapterException("表名不能为空");
        }
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
        List<CasbinRule> rules = session.findAll(Entity.create(tableName), CasbinRule.class);

        for (CasbinRule rule : rules) {
            List<String> policy = rule.getRule();
            if (policy.isEmpty()) {
                continue;
            }
            loadPolicyLine(policy, model);
        }
    }

    protected void loadPolicyLine(List<String> rules, Model model) {
        String key = rules.get(0);
        String sec = key.substring(0, 1);
        Map<String, Assertion> astMap = model.model.get(sec);
        if (astMap == null) {
            return;
        }
        Assertion ast = astMap.get(key);
        if (ast == null) {
            return;
        }
        List<String> policy = ListUtil.sub(rules, 1, rules.size() - 1);
        ast.policy.add(policy);
        ast.policyIndex.put(policy.toString(), ast.policy.size() - 1);
    }

    @Override
    public void savePolicy(Model model) {
        List<CasbinRule> casbinRules = CasbinRule.transformToCasbinRule(model);
        if (casbinRules.isEmpty()) {
            return;
        }

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
            if (session.count(entity) <= 0) {
                session.insert(entity);
            }
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

    @Override
    public void addPolicies(String sec, String ptype, List<List<String>> rules) {
        if (rules.isEmpty()) {
            return;
        }
        List<Entity> entities = rules.stream()
            .map(rule -> {
                CasbinRule casbinRule = new CasbinRule();
                casbinRule.setPtype(ptype);
                casbinRule.setRule(rule);
                return casbinRule;
            })
            .distinct()
            .map(Entity::parse)
            .peek(entity -> entity.setTableName(tableName))
            .toList();

        try {
            session.tx(s -> {
                for (Entity entity : entities) {
                    s.del(entity);
                }
                s.insert(entities);
            });
        } catch (SQLException e) {
            session.quietRollback();
            throw new CasbinAdapterException("casbin policy 批量新增失败", e);
        }
    }

    @Override
    public void removePolicies(String sec, String ptype, List<List<String>> rules) {
        if (rules.isEmpty()) {
            return;
        }
        List<Entity> entities = rules.stream().map(rule -> {
                CasbinRule casbinRule = new CasbinRule();
                casbinRule.setPtype(ptype);
                casbinRule.setRule(rule);
                return casbinRule;
            })
            .distinct()
            .map(Entity::parse)
            .peek(entity -> entity.setTableName(tableName))
            .toList();

        try {
            session.tx(s -> {
                for (Entity entity : entities) {
                    s.del(entity);
                }
            });
        } catch (SQLException e) {
            session.quietRollback();
            throw new CasbinAdapterException("casbin policy 批量移除失败", e);
        }
    }

    @Override
    public void updatePolicy(String sec, String ptype, List<String> oldRule, List<String> newPolicy) {
        CasbinRule casbinRule = new CasbinRule();
        casbinRule.setPtype(ptype);

        casbinRule.setRule(oldRule);

        Entity oleEntity = Entity.parse(casbinRule);
        oleEntity.setTableName(tableName);

        casbinRule.setRule(newPolicy);

        Entity newEntity = Entity.parse(casbinRule);
        newEntity.setTableName(tableName);
        try {
            session.tx(s -> {
                if (session.count(newEntity) <= 0) {
                    s.del(oleEntity);
                    session.insert(newEntity);
                }
            });
        } catch (SQLException e) {
            session.quietRollback();
            throw new CasbinAdapterException("casbin policy 变更失败", e);
        }
    }
}
