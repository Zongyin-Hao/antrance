package xmu.wrxlab.abuilder;

import java.io.File;

/** gradle插件解析出的配置 */
public class ABuilderConfig {
    public static ABuilderConfig ABuilderConfig = new ABuilderConfig();
    public static ABuilderConfig v() {
        return ABuilderConfig;
    }

    private String database = "/home/hzy/softwares/database";
    private String projectId = "com.example.debugapp";
    private String mainActivity = "com.example.debugapp.MainActivity";

    private String sourcePath = "";

    private String extProject = "";

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

    public File getProject() {
        return new File(database, projectId);
    }

    public String getMainActivity() {
        return mainActivity;
    }

    public void setMainActivity(String mainActivity) {
        this.mainActivity = mainActivity;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getExtProject() {
        return extProject;
    }

    public void setExtProject(String extProject) {
        this.extProject = extProject;
    }

    public void output() {
        System.out.println("[MyConfig] database = " + database);
        System.out.println("[MyConfig] projectId = " + projectId);
        System.out.println("[MyConfig] mainActivity = " + mainActivity);
        System.out.println("[MyConfig] sourcePath = " + sourcePath);
        System.out.println("[MyConfig] extProject = " + extProject);
    }

}
