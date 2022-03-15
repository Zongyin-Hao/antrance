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

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                // 必须重新设置一下app, 不然配置中的sourcesets加载不进来(比如kotlin路径), 可能是深拷贝了
                transform.setApp(app);
            } // end of execute
        }); // end of afterEvaluate
    }
}

