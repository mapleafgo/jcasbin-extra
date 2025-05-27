package cn.mapleafgo.jcasbin.adapter;

import cn.hutool.db.Entity;
import cn.mapleafgo.jcasbin.entity.CasbinRule;
import lombok.SneakyThrows;
import org.casbin.jcasbin.exception.CasbinAdapterException;
import org.casbin.jcasbin.model.Model;
import org.casbin.jcasbin.persist.FilteredAdapter;
import org.casbin.jcasbin.persist.Helper;
import org.casbin.jcasbin.persist.file_adapter.FilteredAdapter.Filter;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

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
        loadFilteredPolicyFile(model, (Filter) filter, Helper::loadPolicyLine);
        isFiltered = true;
    }

    @Override
    public boolean isFiltered() {
        return isFiltered;
    }

    @SneakyThrows(SQLException.class)
    private void loadFilteredPolicyFile(Model model, Filter filter, Helper.loadPolicyLineHandler<String, Model> handler) throws CasbinAdapterException {
        List<CasbinRule> rules = session.findAll(Entity.create(tableName), CasbinRule.class);
        for (CasbinRule rule : rules) {
            if (filterLine(rule, filter)) {
                continue;
            }
            loadPolicyLine(rule.getRule(), model);
        }
    }

    private boolean filterLine(CasbinRule line, Filter filter) {
        if (filter == null) {
            return false;
        }
        String[] filterSlice = null;
        if ("p".equals(line.getPtype())) {
            filterSlice = filter.p;
        } else if ("g".equals(line.getPtype())) {
            filterSlice = filter.g;
        }
        if (filterSlice == null) {
            filterSlice = new String[]{};
        }
        return filterWords(line.getRule(), filterSlice);
    }

    private boolean filterWords(List<String> line, String[] filter) {
        boolean skipLine = false;
        int i = 0;
        for (String s : filter) {
            i++;
            if (!s.isEmpty() && !s.trim().equals(line.get(i).trim())) {
                skipLine = true;
                break;
            }
        }
        return skipLine;
    }
}
