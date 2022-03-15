package xmu.wrxlab.abuilder;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.build.gradle.api.BaseVariantOutput;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ABuilder implements Plugin<Project> {
    @Override
    public void apply(Project project) {

        // 读取用户配置
        ABuilderConfig.ABuilderConfig = project.getExtensions().create("abuilder", ABuilderConfig.class);
        // 通过android插件注册自定义transform任务
        AppExtension app = project.getExtensions().getByType(AppExtension.class);
        ABuilderTransform transform = new ABuilderTransform(app);
        app.registerTransform(transform);

        // 处理完gradle配置后回调, 在android extension中注册abuilder任务,
        // 负责clean->ABuilderTransform->上传apk
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                // 必须重新设置一下app, 不然配置中的sourcesets加载不进来(比如kotlin路径), 可能是深拷贝了
                transform.setApp(app);

                // 只考虑buildType是debug的包
                ApplicationVariant debugVariant = null;
                for (ApplicationVariant variant : app.getApplicationVariants()) {
                    if (variant.getBuildType().getName().equalsIgnoreCase("debug")) {
                        debugVariant = variant;
                        break;
                    } // end of if
                } // end of for
                // abuilder任务

                ABuilderTask abuilderTask = project.getTasks().create("abuilder", ABuilderTask.class);
                if (ABuilderConfig.v().isAg2()) {
                    // 兼容模式
                    abuilderTask.ag2Init(ABuilderConfig.v().getProject(), ABuilderConfig.v().getMainActivity(),
                            ABuilderConfig.v().getAg2SourcePath(), ABuilderConfig.v().getAg2ApplicationId(),
                            ABuilderConfig.v().getAg2APKPath());
                    abuilderTask.dependsOn(project.getTasks().findByName("assemble"));
                } else {
                    if (debugVariant != null) {
                        abuilderTask.init(ABuilderConfig.v().getProject(), ABuilderConfig.v().getMainActivity(),
                                debugVariant);
                        // clean->ABuilderTransform->上传apk
                        debugVariant.getAssemble().dependsOn(project.getTasks().findByName("clean"));
                        abuilderTask.dependsOn(debugVariant.getAssemble());
                    } else {
                        throw new RuntimeException("!ag2 && debugVariant == null!");
                    }
                }

            } // end of execute
        }); // end of afterEvaluate
    }
}

