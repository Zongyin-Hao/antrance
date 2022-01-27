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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义Transform, 获取src, 过滤出classes, 连接abuilder server, 调用soot为app插入antrance ins.
 *
 * @author hzy
 * @version 1.0
 */
public class ABuilderTransform extends Transform {

    /**
     * 桩类名, 做antrance ins删除时用
     */
    private final String[] antranceInses = {
            "AntranceIns", "UnCaughtExceptionHandler", "UnCaughtExceptionHandler$1"
    };

    private AppExtension app;

    public ABuilderTransform(AppExtension app) {
        this.app = app;
    }

    public void setApp(AppExtension app) {
        this.app = app;
    }

    /**
     * Transform name.
     *
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
     *
     * @return TransformManager.CONTENT_CLASS
     */
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    /**
     * Transform操作的内容的范围.
     * <p> 如果要处理所有的class字节码, 返回TransformManager.SCOPE_FULL_PROJECT
     *
     * @return TransformManager.SCOPE_FULL_PROJECT
     */
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    /**
     * 是否增量编译.
     *
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

        // 1.创建数据库
        // ================================================================================
        // 在数据库中创建项目目录
        File myProject = new File(myConfig.getDatabase(), myConfig.getProjectId());
        if (!myProject.exists()) {
            myProject.mkdir();
        }

        // 2.copy源码, 创建源码树(解析源码与class的对应关系, 后面会根据srcTree过滤要分析的class)
        // ================================================================================
        // 在项目目录下创建/清空src, 存储源码
        // 同时创建SrcTree
        File mySrc = new File(myProject.getAbsolutePath(), "src");
        if (!mySrc.exists()) {
            mySrc.mkdir();
        } else {
            FileUtils.cleanDirectory(mySrc);
        }
        // 2.1 app source
        SrcTree srcTree = new SrcTree();
        for (AndroidSourceSet sourceSet : app.getSourceSets()) {
            if (sourceSet.getCompileOnlyConfigurationName().startsWith("compile")) {
                for (File src : sourceSet.getJava().getSrcDirs()) {
                    if (!src.exists()) {
                        continue;
                    }
                    System.out.println("[src] " + src.getAbsolutePath());
                    // 拷贝源码
                    FileUtils.copyDirectory(src, mySrc);
                    // 创建SrcTree
                    URI srcURI = src.toURI();
                    listFilesForFolder(src, (file) -> {
                        String rPath = srcURI.relativize(file.toURI()).getPath();
                        srcTree.addSrc(file, rPath);
                    });
                }
            }
        }
        // 2.2 ex source
        if (myConfig.getExSources() != null && myConfig.getExSources().size() != 0) {
            List<String> exSources = myConfig.getExSources();
            for (String exSource : exSources) {
                File src = new File(exSource);
                if (!src.exists()) {
                    throw new RuntimeException("exSource does not exist! " + src.getAbsolutePath());
                }
                System.out.println("[exsrc] " + src.getAbsolutePath());
                // 拷贝源码
                FileUtils.copyDirectory(src, mySrc);
                // 创建SrcTree
                URI srcURI = src.toURI();
                listFilesForFolder(src, (file) -> {
                    String rPath = srcURI.relativize(file.toURI()).getPath();
                    srcTree.addSrc(file, rPath);
                });
            }
        }

        // 3.遍历要分析的class, 使用srcTree进行过滤, 过滤过程中srcTree会记录每个class对应的src, 输出到目标路径下
        // 注意存在exModule时要对相应jar包做解包, 插桩, 打包
        // 为了方彼操作先把要分析的jar/class的src/dst路径保存下来, 并将src文件直接复制到dst, 后面再对src做分析,
        // 用修改后的文件去覆盖dst
        // * 注意维护好jar包逻辑, antrance ins删除逻辑, sootId逻辑
        // ================================================================================
        // 在项目目录下创建/清空jars/classes, 根据src过滤出相关的类, 作为soot的输入
        File myJars = new File(myProject.getAbsolutePath(), "jars");
        if (!myJars.exists()) {
            myJars.mkdir();
        } else {
            FileUtils.cleanDirectory(myJars);
        }
        File myClasses = new File(myProject.getAbsolutePath(), "classes");
        if (!myClasses.exists()) {
            myClasses.mkdir();
        } else {
            FileUtils.cleanDirectory(myClasses);
        }
        // 3.1 如上面介绍的, 先将要分析到的jar/class的src/dst路径保存下来
        // todo 实现jar逻辑
        List<File> srcJars = new ArrayList<>();
        List<File> dstJars = new ArrayList<>();
        List<File> srcClasses = new ArrayList<>();
        List<File> dstClasses = new ArrayList<>();
        for (TransformInput transformInput : transformInvocation.getInputs()) {
            // jar
            for (JarInput jarInput : transformInput.getJarInputs()) {
                File src = jarInput.getFile();
                String jarName = jarInput.getName();
                // 用md5 hex重新获取一个名字, 我们需要重新命名jar包, 防止重名
                String hexName = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length() - 4); // 去掉.jar
                }
                // Transform的输出路径由outputProvider.getContentLocation(name,type,scope,format)决定:
                // 首先是build/transform/task名/编译类型(debug/release)
                // 然后是format(jars: Format.JAR/folders: Format.DIRECTORY)
                // 接着是type, 要根据实际定义决定其数值, 一般来说CLASSES为1,、RESOURCES为2...
                // type后面是scope, 十六进制表示, 同样得根据实际定义来
                // 最后就是正常的路径了
                File dst = transformInvocation.getOutputProvider().getContentLocation(jarName + "_" + hexName,
                        jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
                FileUtils.copyFile(src, dst); // 在这里实现src->dst的拷贝

                List<String> exModules = myConfig.getExModules();
                if (exModules != null && exModules.size() != 0) {
                    boolean ok = false;
                    for (String exModule : exModules) {
                        if (src.getAbsolutePath().startsWith(new File(exModule, "build").getAbsolutePath())) {
                            ok = true;
                        }
                    }
                    if (ok) {
                        srcJars.add(src);  // 在这里实现要分析的src jar的添加
                        dstJars.add(dst);  // 在这里实现要分析的dst jar的添加
                    }
                }

            }
            // class
            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                File src = directoryInput.getFile();
                File dst = transformInvocation.getOutputProvider().getContentLocation(directoryInput.getName(),
                        directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY);
                FileUtils.copyDirectory(src, dst); // 在这里实现src->dst的拷贝, 先复制一遍保证可用
                srcClasses.add(src); // 在这里实现src class的添加
                dstClasses.add(dst); // 在这里实现dst class的添加
            }
        }

        // 3.2 现在src/dst jar/class都已经保存好, 接下来开始过滤/分析
        // * 注意综合考虑jar, class, 维护好antrance ins删除逻辑以及sootId逻辑
        // * 过滤后为空的话删除路径, 不发送/soot请求

        // 我们需要在第一次分析时告诉soot这是新一轮分析的开始(id为0), 从而使soot能正确维护stmtTable
        int sootId = 0;
        // todo 实现jar逻辑
        for (int i = 0; i < srcJars.size(); i++) {
            File src = srcJars.get(i);
            File dst = dstJars.get(i);
            File myJarsEntry = new File(myJars.getAbsolutePath(), "" + i);
            myJarsEntry.mkdir();
            File jarOutput = new File(myJarsEntry.getAbsolutePath(), "output");
            jarOutput.mkdir();

            File jarInput = new File(myJarsEntry.getAbsolutePath(), "input");
            jarInput.mkdir();

            UzipTojar.uZip(src.getAbsolutePath(), jarOutput.getAbsolutePath());

            // 根据SrcTree过滤, 过滤后的类拷贝到myClassesEntry下
            boolean[] analyze = new boolean[1]; // 判断过滤后的classEntry是否为空, 为空的话不调用soot做分析
            analyze[0] = false;
            URI srcURI = jarOutput.toURI();
            System.out.println("[srcURI] " + srcURI);

            listFilesForFolder(jarOutput, (file) -> {
                String rPath = srcURI.relativize(file.toURI()).getPath();
                if (srcTree.hasPath(rPath)) {
                    analyze[0] = true;
                    FileUtils.copyFile(file, new File(jarInput.getAbsolutePath(), rPath));
                }
            });

            if (analyze[0]) {
                // 此时需要调用soot进行分析, 发送GET /soot请求, 更新preInsPath和sootId
                // 向soot发送任务, 进行插桩
                String ans = getSoot("http://" + myConfig.getAddress() + "/soot",
                        myConfig.getDatabase(),
                        myConfig.getProjectId(),
                        jarInput.getAbsolutePath(),
                        jarOutput.getAbsolutePath(), sootId);
                System.out.println("[soot] " + ans);

                System.out.println("[abuilder] clean antrance ins in " + jarOutput);
                for (String antranceIns : antranceInses) {
                    File file = new File(jarOutput, antranceIns + ".class");
                    if (file.exists()) {
                        file.delete();
                    }
                }

                UzipTojar.toJar(myConfig.getDatabase() + "/command.sh"
                        , jarOutput.getAbsolutePath(), dst.getAbsolutePath());

                // 更新sootId
                sootId++;
            }

        }

        // 每做一次soot分析都要把上一个dst路径下的antrance ins删除, 原因有三点:
        // 1. antrance ins只能有一份, 不然新一点的gradle版本会报类重复
        // 2. 只有最新的antrance ins中的stmtTableSize才是正确的
        // 3. 不太好判断哪次分析是最后一次, 但是很好判断哪次分析是第一次, 因此从第一次执行后每次删除上一次比较方便
        String preInsPath = "";
        for (int i = 0; i < srcClasses.size(); i++) {
            File src = srcClasses.get(i);
            File dst = dstClasses.get(i);
            // 在myClasses下创建相应的myClassesEntry, class路径我们之前已经清理过了, 是一个干净的路径
            File myClassesEntry = new File(myClasses.getAbsolutePath(), "" + i);
            myClassesEntry.mkdir();

            // 根据SrcTree过滤, 过滤后的类拷贝到myClassesEntry下
            boolean[] analyze = new boolean[1]; // 判断过滤后的classEntry是否为空, 为空的话不调用soot做分析
            analyze[0] = false;
            URI srcURI = src.toURI();
            System.out.println("[srcURI] " + srcURI);
            listFilesForFolder(src, (file) -> {
                String rPath = srcURI.relativize(file.toURI()).getPath();
                if (srcTree.hasPath(rPath)) {
                    analyze[0] = true;
                    FileUtils.copyFile(file, new File(myClassesEntry.getAbsolutePath(), rPath));
                }
            });

            if (analyze[0]) {
                // 此时需要调用soot进行分析, 发送GET /soot请求, 更新preInsPath和sootId
                // 向soot发送任务, 进行插桩
                String ans = getSoot("http://" + myConfig.getAddress() + "/soot",
                        myConfig.getDatabase(),
                        myConfig.getProjectId(),
                        myClassesEntry.getAbsolutePath(),
                        dst.getAbsolutePath(), sootId);
                System.out.println("[soot] " + ans);
                // 根据preInsPath是为空判断是否删除上次目标路径下的antrance ins
                if (!preInsPath.equals("")) {
                    System.out.println("[abuilder] clean antrance ins in " + preInsPath);
                    for (String antranceIns : antranceInses) {
                        File file = new File(preInsPath, antranceIns + ".class");
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                }
                // 更新preInsPath和sootId
                preInsPath = dst.getAbsolutePath();
                sootId++;
            }
        }

        // 3.3 在数据库下写入源码和类的对应关系(只针对unsureClasses)
        File myClsSrcMap = new File(myProject.getAbsolutePath(), "clssrcmap");
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(myClsSrcMap));
            for (Map.Entry<String, String> entry : srcTree.getDotClassSource().entrySet()) {
                out.write(entry.getKey() + "@" + entry.getValue() + "\n");
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("write cls src map error");
        }

        System.out.println("==================================================");
        System.out.println("[AntranceBuilder] End");
        System.out.println("==================================================");
    }

    interface MyInterface {
        void lamda(File file) throws IOException;
    }

    /**
     * 递归遍历folder下的所有路径/文件, 对每个遍历到的文件执行func函数
     *
     * @param folder 递归遍历的路径/文件
     * @param func   对遍历到的每个文件f执行func(f)
     * @throws IOException
     */
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
     * 请求soot server进行分析(要分析的文件在database下, transform的前几步已经把这些文件准备好了)
     *
     * @param url        address/soot
     * @param database   数据库, 一个本地路径, 不打算支持网络传输
     * @param projectId  用户配置的项目名
     * @param inputPath  soot输入路径
     * @param outputPath soot输出路径
     * @param sootId     重要, sootId为0表示一轮分析的开始, 指导soot做正确的初始化, 主要是stmtTable的维护以及一些文件的初始化
     * @return 响应结果
     * @throws IOException
     */
    private String getSoot(String url, String database, String projectId,
                           String inputPath, String outputPath, int sootId) throws IOException {
        String query = String.format("database=%s&projectId=%s&inputPath=%s&outputPath=%s&sootId=%d",
                URLEncoder.encode(database, "UTF-8"),
                URLEncoder.encode(projectId, "UTF-8"),
                URLEncoder.encode(inputPath, "UTF-8"),
                URLEncoder.encode(outputPath, "UTF-8"),
                sootId);

        URLConnection con = new URL(url + "?" + query).openConnection();
        con.setRequestProperty("Accept-Charset", "UTF-8");
        try (BufferedReader br = new BufferedReader(
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
