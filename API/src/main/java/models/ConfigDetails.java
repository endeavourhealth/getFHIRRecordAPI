package models;

import java.util.List;

public class ConfigDetails {
    public String getConfigName() {
        return configName;
    }

    public List<String> getValidOrgs() {
        return validOrgs;
    }

    public void setValidOrgs(List<String> validOrgs) {
        this.validOrgs = validOrgs;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    private String configName;
    private List<String> validOrgs;

    public String getDbtype() {
        return dbtype;
    }

    public void setDbtype(String dbtype) {
        this.dbtype = dbtype;
    }

    private String dbtype;


}
