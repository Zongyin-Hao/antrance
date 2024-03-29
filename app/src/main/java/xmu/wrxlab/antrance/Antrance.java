package xmu.wrxlab.antrance;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class Antrance extends AccessibilityService {

    /** AntranceServer端口 */
    private static final int myPort = 8624;
    /** AntranceIns地址 */
    private static final String address = "127.0.0.1:8625";

    /** notification channel */
    private static final String CHANNEL_NAME = "crash";
    private static final String CHANNEL_DESCRIPTION = "crash";
    private static final String CHANNEL_ID = "8";
    private static final int NOTIFICATION_ID = 8;


    /** 提供stmtlog和uitree服务, 单例 */
    class AntranceServer extends NanoHTTPD {
        /** 当前测试应用的最新log, json格式 */
        private String stmtLog = "";

        public AntranceServer() {
            super(myPort);
        }

        /**
         * 初始化, stmtLog置空, 测试开始时初始化(install,start阶段).
         */
        private synchronized void init() {
            stmtLog = "";
        }

        /**
         * 获取当前测试应用的最新日志.
         * <p> 1. 首先判断stmtLog是否为空, 不为空返回stmtLog, 为空跳转到2 <br>
         *     2. 向antrance ins请求日志, 请求失败返回-1, 请求成功跳转到3 <br>
         *     3. 请求结果不为-1返回请求结果, 为-1跳转到4 <br>
         *     4. 判断是否有恰好有错误日志上传, 即判断stmtLog是否为空, 不为空返回stmtLog, 为空跳转到5 <br>
         *     5. 此时发生未知错误, 返回-2
         * @return json格式日志, -1代表日志请求失败, -2代表未知错误, 一般是使用者操作异常导致的, 比如运行中使用了init
         */
        private synchronized String getStmtLog() {
            if (!stmtLog.equals("")) {
                return stmtLog;
            }
            String ans = "";
            try {
                ans = getJson("http://"+address+"/stmtlog", "");
            } catch (IOException e) {
                e.printStackTrace();
                return "-1";
            }
            if (!ans.equals("-1")) {
                // 这里记得赋值, 防止用户多次调用多次发送getJson请求
                stmtLog = ans;
                return ans;
            }
            if (!stmtLog.equals("")) {
                return stmtLog;
            }
            return "-2";
        }

        private synchronized void setEventId(int id) {
            try {
                getJson("http://"+address+"/seteventid", "id="+id);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * http get json
         * @param url 发送请求的URL
         * @return 响应结果
         */
        private String getJson(String url, String param) throws IOException {
            URLConnection con = new URL(url+"?"+param).openConnection();
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

        /**
         * 获取当前顶层activity的ui tree.
         * <p> 1. 先判断stmtLog是否为空, 若不为空, 则说明当前应用崩溃或者已经调用过getStmtLog完成了一个流程, <br>
         * 返回-1, 表示不可操作,为空正常拉取ui tree返回即可
         * @return xml格式的ui tree, -1代表当前ui tree不可操作
         */
        private synchronized String getUiTree() {
            if (!stmtLog.equals("")) {
                return "-1";
            }
            return UITree.dumpUITree(getWindows());
        }

        /**
         * 接收错误日志
         * @param log 错误日志
         */
        private synchronized void setStmtLog(String log) {
            stmtLog = log;
            // toast有时会导致antrance卡死, 换成notification
//            Handler handlerThree=new Handler(Looper.getMainLooper());
//            handlerThree.post(new Runnable(){
//                public void run(){
//                    Toast.makeText(getApplicationContext() ,"antrance: crash!", Toast.LENGTH_LONG).show();
//                }
//            });
            String contentText = log;
            if (contentText.length() >= 50) {
                contentText = contentText.substring(0, 50);
                contentText += "...";
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(Antrance.this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_android_black_24dp)
                    .setContentTitle("antrance: app crashes!")
                    .setContentText(contentText)
                    .setPriority(NotificationCompat.PRIORITY_MAX);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(Antrance.this);
            // notificationId is a unique int for each notification that you must define
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

        /**
         * 对object, 执行type类型的操作, 操作数为value.
         * <p> 具体介绍参考UITree.perform.
         */
        private synchronized void perform(String jsonData) {
            PerformAction performAction;
            try {
                Gson gson = new Gson();
                performAction = gson.fromJson(jsonData, PerformAction.class);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            UITree.perform(getWindows(), performAction.getType(),
                    performAction.getValue(), performAction.getObject(),
                    performAction.getPrefix());
        }

        /**
         * http server路由.
         * @param session NanoHttpd默认参数
         * @return 相应请求的返回值
         */
        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if (Method.GET.equals(session.getMethod())) {
                if (uri.equals("/init")) {
                    Log.i("antrance", "get /init");
                    init();
                    return NanoHTTPD.newFixedLengthResponse("init");
                } else if (uri.equals("/stmtlog")) {
                    Log.i("antrance", "get /stmtlog");
                    return NanoHTTPD.newFixedLengthResponse(getStmtLog());
                } else if (uri.equals("/uitree")) {
                    Log.i("antrance", "get /uitree");
                    return NanoHTTPD.newFixedLengthResponse(getUiTree());
                }  else if (uri.equals("/seteventid")) {
                    Log.i("antrance", "get /seteventid");
                    Map<String, String> parms = session.getParms();
                    if (parms.containsKey("id")) {
                        String id = parms.get("id");
                        if (id != null) {
                            setEventId(Integer.parseInt(id));
                        }
                    }
                    return NanoHTTPD.newFixedLengthResponse("seteventid");
                }
                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_HTML,
                        "please use /init or /stmtlog or /uitree or seteventid");
            } else if (Method.POST.equals(session.getMethod())) {
                if (uri.equals("/stmtlog")) {
                    Log.i("antrance", "post /stmtlog");
                    Map<String, String> data = new HashMap<String, String>();
                    String log;
                    try {
                        session.parseBody(data);
                        log = data.get("postData");
                    } catch (IOException | ResponseException e) {
                        e.printStackTrace();
                        return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_HTML,
                                "parse body error");
                    }
                    setStmtLog(log);
                    return NanoHTTPD.newFixedLengthResponse("post error log successful");
                } else if (uri.equals("/perform")) {
                    Log.i("antrance", "post /perform");
                    Map<String, String> data = new HashMap<String, String>();
                    try {
                        session.parseBody(data);
                    } catch (IOException | ResponseException e) {
                        e.printStackTrace();
                        return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_HTML,
                                "");
                    }
                    perform(data.get("postData"));
                    return NanoHTTPD.newFixedLengthResponse("perform");
                }
                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_HTML,
                        "please use /stmtlog or /perform");
            } else {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_HTML,
                        "please use get or post");
            }
        }
    }

    public AntranceServer antranceServer = new AntranceServer();

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    /**
     * 由于您必须先创建通知渠道，然后才能在 Android 8.0 及更高版本上发布任何通知，因此应在应用启动时立即执行这段代码.
     * 反复调用这段代码是安全的，因为创建现有通知渠道不会执行任何操作.
     */
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance);
            channel.setDescription(CHANNEL_DESCRIPTION);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            antranceServer.start();
            Log.i("antrance", "antrance start on " + myPort);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("antrance", "antrance start error");
        }
    }

    @Override
    public void onDestroy() {
        antranceServer.stop();
        Log.i("antrance", "antrance stop");
        super.onDestroy();
    }

//    private fun requestMyPermissions() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            //没有授权，编写申请权限代码
//            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
//        } else {
//            Log.d("hzy", "requestMyPermissions: 有写SD权限")
//        }
//        if (ContextCompat.checkSelfPermission(this,
//                        Manifest.permission.READ_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED) {
//            //没有授权，编写申请权限代码
//            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
//        } else {
//            Log.d("hzy", "requestMyPermissions: 有读SD权限")
//        }
//    }

}