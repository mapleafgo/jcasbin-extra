package cn.mapleafgo.jcasbin.adapter;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.IdUtil;
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

/**
 * Casbin HutoolDB 适配器
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

        String initTable = """
            CREATE TABLE IF NOT EXISTS %s (
                id    bigint NOT NULL PRIMARY KEY,
                ptype varchar(10) NOT NULL,
                v0    varchar(100) DEFAULT NULL,
                v1    varchar(100) DEFAULT NULL,
                v2    varchar(100) DEFAULT NULL,
                v3    varchar(100) DEFAULT NULL,
                v4    varchar(100) DEFAULT NULL
            )
            """;
        Db.use(dataSource).execute(String.format(initTable, tableName));
    }

    public static void loadPolicyLine(List<String> rule, Model model) {
        String key = rule.get(0);
        String sec = key.substring(0, 1);
        Map<String, Assertion> astMap = model.model.get(sec);
        if (astMap == null) {
            return;
        }
        Assertion ast = astMap.get(key);
        if (ast == null) {
            return;
        }
        List<String> policy = ListUtil.sub(rule, 1, rule.size());
        ast.policy.add(policy);
        ast.policyIndex.put(policy.toString(), ast.policy.size() - 1);
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
            HutoolDBAdapter.loadPolicyLine(policy, model);
        }
    }

    @Override
    public void savePolicy(Model model) {
        List<CasbinRule> casbinRules = CasbinRule.transformToCasbinRule(model);
        if (casbinRules.isEmpty()) {
            return;
        }

        List<Entity> list = casbinRules.stream()
            .peek(r -> r.setId(IdUtil.getSnowflakeNextId()))
            .map(r -> Entity.create(tableName).parseBean(r))
            .toList();
        try {
            session.tx(s -> {
                s.execute("TRUNCATE ?", tableName);
                s.insert(list);
            });
        } catch (SQLException e) {
            throw new CasbinAdapterException("casbin policy 保存失败", e);
        }
    }

    @Override
    public void addPolicy(String sec, String ptype, List<String> rule) {
        try {
            session.tx(s -> addPolicy(s, ptype, rule));
        } catch (SQLException e) {
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
            throw new CasbinAdapterException("casbin policy 按条件移除失败", e);
        }
    }

    @Override
    public void addPolicies(String sec, String ptype, List<List<String>> rules) {
        if (rules.isEmpty()) {
            return;
        }
        try {
            session.tx(s -> {
                for (List<String> rule : rules) {
                    addPolicy(s, ptype, rule);
                }
            });
        } catch (SQLException e) {
            throw new CasbinAdapterException("casbin policy 批量新增失败", e);
        }
    }

    @Override
    public void removePolicies(String sec, String ptype, List<List<String>> rules) {
        if (rules.isEmpty()) {
            return;
        }
        try {
            session.tx(s -> {
                for (List<String> rule : rules) {
                    CasbinRule casbinRule = new CasbinRule();
                    casbinRule.setPtype(ptype);
                    casbinRule.setRule(rule);

                    Entity entity = Entity.parse(casbinRule);
                    entity.setTableName(tableName);
                    s.del(entity);
                }
            });
        } catch (SQLException e) {
            throw new CasbinAdapterException("casbin policy 批量移除失败", e);
        }
    }

    @Override
    public void updatePolicy(String sec, String ptype, List<String> oldRule, List<String> newPolicy) {
        CasbinRule rule = new CasbinRule();
        rule.setPtype(ptype);
        rule.setRule(oldRule);

        Entity delEntity = Entity.parse(rule);
        delEntity.setTableName(tableName);
        try {
            session.tx(s -> {
                s.del(delEntity);
                addPolicy(s, ptype, newPolicy);
            });
        } catch (SQLException e) {
            throw new CasbinAdapterException("casbin policy 变更失败", e);
        }
    }

    /**
     * 添加策略到数据库
     *
     * @param s     数据库会话
     * @param ptype 策略类型
     * @param rule  策略规则
     * @throws SQLException 数据库操作异常
     */
    private void addPolicy(Session s, String ptype, List<String> rule) throws SQLException {
        if (rule.isEmpty()) {
            return;
        }

        Entity entity = Entity.create(tableName);

        CasbinRule cRule = new CasbinRule();
        cRule.setPtype(ptype);
        cRule.setRule(rule);

        entity = entity.parseBean(cRule);
        s.del(entity);

        cRule.setId(IdUtil.getSnowflakeNextId());
        entity = entity.parseBean(cRule);

        s.insert(entity);
    }
}
