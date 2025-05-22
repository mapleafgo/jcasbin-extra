package cn.mapleafgo.jcasbin.entity;

import cn.hutool.core.util.ArrayUtil;
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
    private String ptype;
    private String v0;
    private String v1;
    private String v2;
    private String v3;
    private String v4;

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
        for (int i = 0; i < rule.size(); i++) {
            CasbinRule.class.getMethod(String.format("setV%d", i), String.class).invoke(this, rule.get(i));
        }
    }

    /**
     * 从Model获取casbinrule
     *
     * @param model casbin model对象
     * @return Rule实体对象列表
     */
    public static List<CasbinRule> transformToCasbinRule(Model model) {
        Set<CasbinRule> casbinRules = new HashSet<>();
        model.model.values().forEach(x -> x.values().forEach(y -> y.policy.forEach(z -> {
            CasbinRule casbinRule = new CasbinRule();
            casbinRule.setPtype(y.key);
            casbinRule.setRule(z);
            casbinRules.add(casbinRule);
        })));
        return new ArrayList<>(casbinRules);
    }

    /**
     * 填充casbinrule
     *
     * @param ptype       the policy type
     * @param fieldIndex  the policy rule's start index to be matched.
     * @param fieldValues the field values to be matched, value ""
     *                    means not to match this field.
     * @return 填充好的casbinrule
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
}
