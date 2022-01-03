package xmu.wrxlab.antrance;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * 解析json字符串
 */
public class PerformAction {
    private String type;
    private String value;
    private String object;
    private List<Integer> prefix;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<Integer> getPrefix() {
        return prefix;
    }

    public void setPrefix(List<Integer> prefix) {
        this.prefix = prefix;
    }
}
