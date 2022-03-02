package xmu.wrxlab.abuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class UzipTojar {
    public static void uZip(String jar,String path) throws IOException {
        //创建ProcessBuilder对象
        ProcessBuilder processBuilder = new ProcessBuilder();
        //设置执行的第三方程序(命令)
        processBuilder.command("unzip",jar,"-d",path);
        //processBuilder.command("java","-jar","f:/xc-service-manage-course.jar");
        //jar cvf xxx.jar *.*
//        processBuilder.command("ls");
        // 将标准输入流和错误输入流合并
        processBuilder.redirectErrorStream(true);

        //启动一个进程
        Process process = processBuilder.start();

        //读取输入流
        InputStream inputStream = process.getInputStream();
        //将字节流转成字符流
        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        //字符缓冲区
        char[] chars = new char[1024];
        int len = -1;
        while ((len = reader.read(chars)) != -1) {
            String string = new String(chars, 0, len);
//            System.out.println(string);
        }
        inputStream.close();
        reader.close();
    }
    public static void toJar(String shFile,String output,String dest) throws IOException {
        System.out.println(shFile + " " + output + " " + dest);
        //创建ProcessBuilder对象
        ProcessBuilder processBuilder = new ProcessBuilder();
        //设置执行的第三方程序(命令) 第一个参数代表执行sh命令 第二个执行文件 第三个要打包的文件 第四个jar包位置
        processBuilder.command("sh", shFile, output, dest);

        // 将标准输入流和错误输入流合并
        processBuilder.redirectErrorStream(true);

        //启动一个进程
        Process process = processBuilder.start();

        //读取输入流
        InputStream inputStream = process.getInputStream();
        //将字节流转成字符流
        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        //字符缓冲区
        char[] chars = new char[1024];
        int len = -1;
        while ((len = reader.read(chars)) != -1) {
            String string = new String(chars, 0, len);
            System.out.println("[toJar] "+string);
        }

        inputStream.close();
        reader.close();
    }
}
