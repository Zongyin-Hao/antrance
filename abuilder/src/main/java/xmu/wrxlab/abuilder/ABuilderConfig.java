package xmu.wrxlab.abuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * gradle插件解析出的配置
 */
public class ABuilderConfig {
    public static ABuilderConfig ABuilderConfig = new ABuilderConfig();

    public static ABuilderConfig v() {
        return ABuilderConfig;
    }

    /** soot server地址 */
    private String address = "127.0.0.1:8082";
    /** 数据库路径 */
    private String database = "";
    /**
     * 自定义项目名
     */
    private String projectId = "";
    /**
     * 主类, 用于apk启动, 也可以直接去对应项目apk目录下配置config.txt
     */
    private String mainActivity = "";
    /**
     * 若项目要对依赖的module插桩, 则在这里配置要插桩的module路径, 目前只支持一个, 空字符串表示不开启这项功能
     */
    private List<String> exModules = new ArrayList<>();
    /**
     * exModule下的源码路径
     */
    private List<String> exSources = new ArrayList<>();

    /**
     * 是否需要开启兼容模式, 开启兼容模式后无法使用debugVariant和sourceSet, 需要自己配置apk路径, application id以及源文件路径
     * 1.0.0以上不支持兼容模式
     */
    private boolean ag2 = false;
    /**
     * 兼容模式下需要手动配置源码路径
     */
    private String ag2SourcePath = "";

    /**
     * 兼容模式下需要手动配置applicationId
     */
    private String ag2ApplicationId = "";
    /**
     * 兼容模式下需要手动配置apk路径, 不清楚的话先编译一下, 看看apk在哪输出
     */
    private String ag2APKPath = "";

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * 获取相应项目的路径, database/projectId
     */
    public File getProject() {
        return new File(database, projectId);
    }

    public String getMainActivity() {
        return mainActivity;
    }

    public void setMainActivity(String mainActivity) {
        this.mainActivity = mainActivity;
    }

    public List<String> getExSources() {
        return exSources;
    }

    public void setExSources(List<String> exSources) {
        this.exSources = exSources;
    }

    public List<String> getExModules() {
        return exModules;
    }

    public void setExModules(List<String> exModules) {
        this.exModules = exModules;
    }

    public boolean isAg2() {
        return ag2;
    }

    public void setAg2(boolean ag2) {
        this.ag2 = ag2;
    }

    public String getAg2SourcePath() {
        return ag2SourcePath;
    }

    public void setAg2SourcePath(String ag2SourcePath) {
        this.ag2SourcePath = ag2SourcePath;
    }

    public String getAg2ApplicationId() {
        return ag2ApplicationId;
    }

    public void setAg2ApplicationId(String ag2ApplicationId) {
        this.ag2ApplicationId = ag2ApplicationId;
    }

    public String getAg2APKPath() {
        return ag2APKPath;
    }

    public void setAg2APKPath(String ag2APKPath) {
        this.ag2APKPath = ag2APKPath;
    }

    public void output() {
        System.out.println("[MyConfig] soot server address = " + address);
        System.out.println("[MyConfig] database = " + database);
        System.out.println("[MyConfig] projectId = " + projectId);
        System.out.println("[MyConfig] mainActivity = " + mainActivity);
        System.out.println("[MyConfig] exModule = " + exModules.size());
        System.out.println("[MyConfig] exSource = " + exSources.size());
        System.out.println("[MyConfig] ag2 = " + ag2);
        System.out.println("[MyConfig] ag2SourcePath = " + ag2SourcePath);
        System.out.println("[MyConfig] ag2ApplicationId = " + ag2ApplicationId);
        System.out.println("[MyConfig] ag2APKPath = " + ag2APKPath);
        if (!ag2 && (!ag2SourcePath.equals("") || !ag2ApplicationId.equals("") || !ag2APKPath.equals(""))) {
            throw new RuntimeException("!ag2 && (!ag2SourcePath.equals(\"\") || !ag2ApplicationId.equals(\"\") || !ag2APKPath.equals(\"\"))");
        }
        if (ag2 && (ag2SourcePath.equals("") || ag2ApplicationId.equals("") || ag2APKPath.equals(""))) {
            throw new RuntimeException("ag2 && (ag2SourcePath.equals(\"\") || ag2ApplicationId.equals(\"\") || ag2APKPath.equals(\"\"))");
        }
    }

}
