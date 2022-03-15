package xmu.wrxlab.abuilder;

import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariantOutput;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ABuilderTask extends DefaultTask {
    private File project;
    private String mainActivity = "";
    private boolean ag2 = false;
    private ApplicationVariant debugVariant;
    private String ag2SourcePath = "";
    private String ag2ApplicationId = "";
    private String ag2APKPath = "";

    public void init(File project, String mainActivity, ApplicationVariant debugVariant) {
        this.project = project;
        this.mainActivity = mainActivity;
        this.ag2 = false;
        this.debugVariant = debugVariant;
        setDescription("antrance builder");
        setGroup("android");
    }

    public void ag2Init(File project, String mainActivity, String ag2SourcePath, String ag2ApplicationId, String ag2APKPath) {
        this.project = project;
        this.mainActivity = mainActivity;
        this.ag2 = true;
        this.ag2SourcePath = ag2SourcePath;
        this.ag2ApplicationId = ag2ApplicationId;
        this.ag2APKPath = ag2APKPath;
        setDescription("antrance builder");
        setGroup("android");
    }

    @TaskAction
    public void uploadAPK() throws IOException {
        // 在项目目录下创建/清空 apk
        File apk = new File(project, "apk");
        if (!apk.exists()) {
            apk.mkdir();
        } else {
            FileUtils.cleanDirectory(apk);
        }

        // 在apk目录下记录apk配置, 包括applicationId, mainActivity
        BufferedWriter out = new BufferedWriter(new FileWriter(new File(apk, "config.txt")));
        if (ag2) {
            out.write(ag2ApplicationId + "@" + mainActivity);
        } else {
            out.write(debugVariant.getApplicationId() + "@" + mainActivity);
        }
        out.flush();
        out.close();

        // 上传apk
        if (ag2) {
            File file = new File(ag2APKPath);
            String filePath = file.getAbsolutePath();
            System.out.println("**************************************************");
            System.out.println("upload apk " + filePath);
            System.out.println("**************************************************");
            FileUtils.copyFile(file, new File(apk, "app.apk"));
        } else {
            for (BaseVariantOutput output : debugVariant.getOutputs()) {
                File file = output.getOutputFile();
                String filePath = file.getAbsolutePath();
                System.out.println("**************************************************");
                System.out.println("upload apk " + filePath);
                System.out.println("**************************************************");
                FileUtils.copyFile(file, new File(apk, "app.apk"));
            }
        }
    }
}