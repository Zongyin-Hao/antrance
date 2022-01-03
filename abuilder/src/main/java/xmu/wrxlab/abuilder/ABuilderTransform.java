package xmu.wrxlab.abuilder;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.pipeline.TransformManager;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 自定义Transform, 获取src, 过滤出classes, 连接abuilder server, 调用soot为app插入antrance ins.
 * @author hzy
 * @version 1.0
 */
public class ABuilderTransform extends Transform {
    private final static String address = "127.0.0.1:8081";
    private final AppExtension app;

    ABuilderTransform(AppExtension app) {
        this.app = app;
    }

    /**
     * Transform name.
     * @return abuildertransform
     */
    @Override
    public String getName() {
        return "abuildertransform";
    }

    /**
     * Transform处理的数据类型.
     * <p> 1. TransformManager.CONTENT_CLASS <br>
     * 2. TransformManager.CONTENT_RESOURCES <br>
     * @return TransformManager.CONTENT_CLASS
     */
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    /**
     * Transform操作的内容的范围.
     * <p> 如果要处理所有的class字节码, 返回TransformManager.SCOPE_FULL_PROJECT
     * @return TransformManager.SCOPE_FULL_PROJECT
     */
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /**
     * 是否增量编译.
     * @return true
     */
    @Override
    public boolean isIncremental() {
        return false;
    }

    /**
     * 实现自定义Transform.
     */
    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);

        ABuilderConfig myConfig = ABuilderConfig.v();
        System.out.println("==================================================");
        System.out.println("[AntranceBuilder] Start");
        myConfig.output();
        System.out.println("==================================================");

        // 在数据库中创建项目目录
        File myProject = new File(myConfig.getDatabase(), myConfig.getProjectId());
        if (!myProject.exists()) {
            myProject.mkdir();
        }
        // 在项目目录下创建/清空src, 存储源码
        // 同时创建SrcTree
        File mySrc = new File(myProject.getAbsolutePath(), "src");
        if (!mySrc.exists()) {
            mySrc.mkdir();
        } else {
            FileUtils.cleanDirectory(mySrc);
        }
        SrcTree srcTree = new SrcTree();
        for (AndroidSourceSet sourceSet : app.getSourceSets()) {
            if (sourceSet.getCompileOnlyConfigurationName().startsWith("compile")) {
                for (File src : sourceSet.getJava().getSrcDirs()) {
                    System.out.println("[src] " + src.getAbsolutePath());
                    // 拷贝源码
                    FileUtils.copyDirectory(src, mySrc);
                    // 创建SrcTree
                    URI srcURI = src.toURI();
                    listFilesForFolder(src, (file) -> {
                        String rPath = srcURI.relativize(file.toURI()).getPath();
                        srcTree.addSrc(rPath);
                    });
                }
            }
        }


        // 在项目目录下创建/清空classes, 根据src过滤出相关的类, 作为soot的输入
        File myClasses = new File(myProject.getAbsolutePath(), "classes");
        if (!myClasses.exists()) {
            myClasses.mkdir();
        } else {
            FileUtils.cleanDirectory(myClasses);
        }
        // classPath可能有多个, 对应myClasses中递增的classPathId
        // 另外, classPathId>1时要通知soot删除重复的桩
        int classPathId = 0;
        for (TransformInput transformInput : transformInvocation.getInputs()) {
            // 1. jar, 不处理
            for (JarInput jarInput : transformInput.getJarInputs()) {
                File src = jarInput.getFile();
                String jarName = jarInput.getName();
                // 用md5 hex重新获取一个名字, 我们需要重新命名jar包, 防止重名
                String hexName = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length()-4); // 去掉.jar
                }
                // Transform的输出路径由outputProvider.getContentLocation(name,type,scope,format)决定:
                // 首先是build/transform/task名/编译类型(debug/release)
                // 然后是format(jars: Format.JAR/folders: Format.DIRECTORY)
                // 接着是type, 要根据实际定义决定其数值, 一般来说CLASSES为1,、RESOURCES为2...
                // type后面是scope, 十六进制表示, 同样得根据实际定义来
                // 最后就是正常的路径了
                File dst = transformInvocation.getOutputProvider().getContentLocation(jarName+"_"+hexName,
                        jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);

                FileUtils.copyFile(src, dst);
            }
            // 2. class
            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                File src = directoryInput.getFile();
                File dst = transformInvocation.getOutputProvider().getContentLocation(directoryInput.getName(),
                        directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY);
                // 先复制一遍保证可用
                FileUtils.copyDirectory(src, dst);

                classPathId += 1;
                // 根据classPathId在myClasses下创建相应的myClassesEntry
                File myClassesEntry = new File(myClasses.getAbsolutePath(), ""+classPathId);
                myClassesEntry.mkdir();

                // 根据SrcTree过滤, 过滤后的类拷贝到myClassesEntry下
                URI srcURI = src.toURI();
                listFilesForFolder(src, (file) -> {
                    String rPath = srcURI.relativize(file.toURI()).getPath();
                    if (srcTree.hasPath(rPath)) {
                        FileUtils.copyFile(file, new File(myClassesEntry.getAbsolutePath(), rPath));
                    }
                });

                // 向soot发送任务, 进行插桩
                String ans = getSoot("http://"+address+"/soot", myConfig.getDatabase(), myConfig.getProjectId(),
                        myClassesEntry.getAbsolutePath(), dst.getAbsolutePath(), classPathId-1);
                System.out.println(ans);
            }
        }

        System.out.println("==================================================");
        System.out.println("[AntranceBuilder] End");
        System.out.println("==================================================");
    }

    interface MyInterface {
        void lamda(File file) throws IOException;
    }

    public void listFilesForFolder(File folder, MyInterface func) throws IOException {
        for (File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                listFilesForFolder(fileEntry, func);
            } else {
                func.lamda(fileEntry);
            }
        }
    }

    /**
     * http get soot
     * @param url 发送请求的URL
     * @return 响应结果
     */
    private String getSoot(String url, String database, String projectId,
                           String inputPath, String outputPath, int rmIns) throws IOException {
        String query = String.format("database=%s&projectId=%s&inputPath=%s&outputPath=%s&rmIns=%d",
                URLEncoder.encode(database, "UTF-8"),
                URLEncoder.encode(projectId, "UTF-8"),
                URLEncoder.encode(inputPath, "UTF-8"),
                URLEncoder.encode(outputPath, "UTF-8"),
                rmIns);

        URLConnection con = new URL(url + "?" + query).openConnection();
        con.setRequestProperty("Accept-Charset", "UTF-8");
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }
}
