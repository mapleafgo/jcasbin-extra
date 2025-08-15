package cn.mapleafgo.jcasbin.adapter;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.db.Entity;
import cn.mapleafgo.jcasbin.db.LeafDb;
import cn.mapleafgo.jcasbin.entity.CasbinRule;
import lombok.SneakyThrows;
import org.casbin.jcasbin.exception.CasbinAdapterException;
import org.casbin.jcasbin.model.Model;
import org.casbin.jcasbin.persist.FilteredAdapter;
import org.casbin.jcasbin.persist.Helper;
import org.casbin.jcasbin.persist.file_adapter.FilteredAdapter.Filter;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Casbin HutoolDB 适配器，支持 Filtered
 *
 * @author mapleafgo
 */
public class HutoolDBFilteredAdapter extends HutoolDBAdapter implements FilteredAdapter {
    private boolean isFiltered = false;

    public HutoolDBFilteredAdapter(DataSource dataSource, String tableName) throws SQLException {
        super(dataSource, tableName);
    }

    @Override
    public void loadFilteredPolicy(Model model, Object filter) throws CasbinAdapterException {
        if (filter == null) {
            loadPolicy(model);
            isFiltered = false;
            return;
        }
        if (!(filter instanceof Filter)) {
            isFiltered = false;
            throw new CasbinAdapterException("Invalid filter type.");
        }
        loadFilteredPolicyFile(model, (Filter) filter, HutoolDBAdapter::loadPolicyLine);
        isFiltered = true;
    }

    @Override
    public boolean isFiltered() {
        return isFiltered;
    }

    @SneakyThrows(SQLException.class)
    private void loadFilteredPolicyFile(Model model, Filter filter, Helper.loadPolicyLineHandler<List<String>, Model> handler) throws CasbinAdapterException {
        List<CasbinRule> rules = LeafDb.use(dataSource).findAll(Entity.create(tableName), CasbinRule.class);
        for (CasbinRule rule : rules) {
            if (filterLine(rule, filter)) {
                continue;
            }
            handler.accept(rule.getRule(), model);
        }
    }

    private boolean filterLine(CasbinRule line, Filter filter) {
        if (filter == null) {
            return false;
        }
        String[] rule = ArrayUtil.toArray(line.getRule(), String.class);
        if (Objects.equals("p", line.getPtype())) {
            return filterWords(rule, filter.p);
        } else if (Objects.equals("g", line.getPtype())) {
            return filterWords(rule, filter.g);
        }
        return true;
    }

    private boolean filterWords(String[] rule, String[] filter) {
        if (rule.length < filter.length + 1) {
            return true;
        }
        return !Arrays.equals(Arrays.copyOfRange(rule, 1, filter.length + 1), filter);
    }
}
