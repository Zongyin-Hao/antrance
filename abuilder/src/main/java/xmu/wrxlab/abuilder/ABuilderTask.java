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
    private ApplicationVariant debugVariant;

    public void init(ApplicationVariant debugVariant) {
        this.debugVariant = debugVariant;
        setDescription("antrance builder");
        setGroup("android");
    }

    @TaskAction
    public void uploadAPK() throws IOException {
        // 在项目目录下创建/清空 apk
        File apk = new File(ABuilderConfig.v().getProject(), "apk");
        if (!apk.exists()) {
            apk.mkdir();
        } else {
            FileUtils.cleanDirectory(apk);
        }

        // 在apk目录下记录apk配置, 包括applicationId, mainActivity
        BufferedWriter out = new BufferedWriter(new FileWriter(new File(apk, "config.txt")));
        out.write(debugVariant.getApplicationId() + "@" + ABuilderConfig.v().getMainActivity());
        out.flush();
        out.close();

        // 上传apk
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