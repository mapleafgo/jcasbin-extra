package cn.mapleafgo.jcasbin.entity;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;
import lombok.Data;
import lombok.SneakyThrows;
import org.casbin.jcasbin.model.Model;

import java.util.*;

/**
 * CasbinRule entity
 *
 * @author 慕枫
 */
@Data
public class CasbinRule {
    private Long id;
    private String ptype;
    private String v0;
    private String v1;
    private String v2;
    private String v3;
    private String v4;

    /**
     * 从 Model 获取 casbinrule
     *
     * @param model casbin model 对象
     * @return Rule实体对象列表
     */
    public static List<CasbinRule> transformToCasbinRule(Model model) {
        Set<CasbinRule> casbinRules = new HashSet<>();
        model.model.values().forEach(x -> x.values().forEach(y -> y.policy.forEach(z -> {
            CasbinRule casbinRule = new CasbinRule();
            casbinRule.setId(IdUtil.getSnowflakeNextId());
            casbinRule.setPtype(y.key);
            casbinRule.setRule(z);
            casbinRules.add(casbinRule);
        })));
        return new ArrayList<>(casbinRules);
    }

    /**
     * 填充 casbinrule
     *
     * @param ptype       the policy type
     * @param fieldIndex  the policy rule's start index to be matched.
     * @param fieldValues the field values to be matched, value ""
     *                    means not to match this field.
     * @return 填充好的 casbinrule
     */
    public static Map<String, Object> toRuleMap(String ptype, int fieldIndex, String... fieldValues) {
        if (ArrayUtil.isEmpty(fieldValues)) {
            return null;
        }
        HashMap<String, Object> ruleMap = new HashMap<>();
        ruleMap.put("ptype", ptype);
        for (int i = 0; i < fieldValues.length; i++) {
            ruleMap.put(String.format("v%d", fieldIndex + i), fieldValues[i]);
        }
        return ruleMap;
    }

    /**
     * 获取列表形式规则
     *
     * @return 返回列表形式规则
     */
    @SneakyThrows
    public List<String> getRule() {
        List<String> rule = new ArrayList<>();
        rule.add(ptype);
        for (int i = 0; i < 5; i++) {
            String value = (String) CasbinRule.class.getMethod(String.format("getV%d", i)).invoke(this);
            if (Objects.isNull(value)) {
                break;
            }
            rule.add(value);
        }
        return rule;
    }

    /**
     * 填充rule
     *
     * @param rule 权限关键字
     */
    @SneakyThrows
    public void setRule(List<String> rule) {
        if (rule.isEmpty()) {
            return;
        }
        for (int i = 0; i < Math.min(5, rule.size()); i++) {
            CasbinRule.class.getMethod(String.format("setV%d", i), String.class).invoke(this, rule.get(i));
        }
    }
}
