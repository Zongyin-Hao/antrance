import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import fi.iki.elonen.NanoHTTPD;
import xmu.wrxlab.antrance.Antrance;

/**
 * 负责收集程序运行过程中的语句日志
 * <p> tip: <br>
 *     1. 正常情况下由antrance调用/stmtlog获取日志, 程序崩溃时由UnCaughtExceptionHandler自动上传日志 <br>
 *     2. web server由static代码块启动, 当stmtTable被访问时static代码块会被执行, <br>
 *     由于我们对每个函数入口处做了插桩, web server一定会启动
 */
public class AntranceIns extends NanoHTTPD  {
    /** AntranceIns端口 */
    private static final int myPort = 8625;

    /** 当前应用的projectId(用户定义), 通过soot做字符串修改 */
    private static String projectId = "hao.zong.yin";

    /** stmt表大小, 由soot在编译时计算, 并做int面值修改 */
    private static int stmtTableSize = 9999;
    /** 根据stmtTableSize在static代码块中初始化. <br>
     * <p> 数组的每一项都对应一条检测语句, 当检测语句出现时会将对应项置1 <br>
     *     java对32位以下变量的读写是原子的, 利用这一点这里做了无锁优化(之前使用ConcurrentHashMap效率太低)
     *     关于线程间的可见性问题, 一个变量不加volatile是有可见性问题的, 但我测试发现数组没什么问题,
     *     尽管大家推荐使用Unsafe, 不过既然我测试着没什么问题, 又不需要强可见性, 那就这样用这吧 */
    public static int[] stmtTable = null;
    // update: thread visibility for stmtTable
    public static AtomicLongArray stmtTable2 = null;

    // current event id, stmtTable2.set(i, origin|1L<<eventId.get())
    // 1<<0表示init覆盖, 因此最大能表示1~62共62个动作
    public static AtomicInteger eventId = new AtomicInteger(0);

    public static void setStmtTable2(int i) {
//        if (stmtTable2.get(i) == 0) {
//            Log.i("hzy", Thread.currentThread().getId() + "--------------------set " + i);
//        }
        int x = eventId.get();
        long y = stmtTable2.get(i);
        if (0 <= x && x <= 62) {
            stmtTable2.compareAndSet(i, y, y | (1L<<x));
        }
    }

    /** 保证应用被kill前只能上传一次log */
    private static final AtomicBoolean oneLog = new AtomicBoolean(false);

    /**
     * 获取json格式的日志, 注意应用被kill前只能获取一次日志.
     * @param status true: 正常 false: 崩溃
     * @param error 程序崩溃时记录的栈信息
     * @return json格式的日志, -1说明之前已经调用过一次getLogJson
     */
    public static String getLogJson(boolean status, Throwable error) {
        if (oneLog.get()) return "-1";
        oneLog.set(true);
        // stmtLog序列化成json
        // json格式:
        // { "projectId"(当前程序的项目id, 用户指定):"com.example.debugapp",
        //   "status"(程序正常/崩溃):"true/false",
        //   "stmts"(程序运行过程中执行的语句id):[0, 3, 8001, 10234],
        //   "eventids"(语句关联的eventids, 1<<0表示init覆盖):["9","3","1","2"],
        //   "stackTrace"(status为false时表示出现了uncaught exception, 需记录栈调用信息, status true时为空): [
        //     "类@语句在文件中的源码行"
        //   ],
        //   "stackTraceOrigin": "原始栈信息"
        // }
        // 由于AntranceIns是插桩插到app中的, 尽量不要使用外部依赖
        JSONObject stmtLog = new JSONObject();
        try {
            stmtLog.put("projectId", projectId);
            stmtLog.put("status", String.valueOf(status));
            JSONArray stmts = new JSONArray();
            for (int i = 0; i < stmtTableSize; i++) {
                if (stmtTable2.get(i) != 0) {
                    stmts.put(i);
                }
            }
            stmtLog.put("stmts", stmts);
            JSONArray eventids = new JSONArray();
            for (int i = 0; i < stmtTableSize; i++) {
                if (stmtTable2.get(i) != 0) {
                    eventids.put(String.valueOf(stmtTable2.get(i)));
                }
            }
            stmtLog.put("eventids", eventids);
            JSONArray stackTrace = new JSONArray();
            if (error != null) {
                for (StackTraceElement stackTraceElement : error.getStackTrace()) {
                    stackTrace.put(stackTraceElement.getClassName()+"@"+stackTraceElement.getLineNumber());
                }
                Throwable cause = error.getCause();
                if (cause != null) {
                    for (StackTraceElement stackTraceElement : cause.getStackTrace()) {
                        stackTrace.put(stackTraceElement.getClassName()+"@"+stackTraceElement.getLineNumber());
                    }
                }
            }
            stmtLog.put("stackTrace", stackTrace);
            String stackTraceOrigin = "";
            if (error != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                error.printStackTrace(pw);
                stackTraceOrigin = sw.toString();
            }
            stmtLog.put("stackTraceOrigin", stackTraceOrigin);
        } catch (JSONException ex) {
            // 键为null或使用json不支持的数字格式(NaN, infinities)
            throw new RuntimeException(ex);
        }
        return stmtLog.toString();
    }

    // for MyCrash
    public static String myCrashJson(List<String> crashStack, String stackTraceOrigin) {
        if (oneLog.get()) return "-1";
        oneLog.set(true);
        // stmtLog序列化成json
        // json格式:
        // { "projectId"(当前程序的项目id, 用户指定):"com.example.debugapp",
        //   "status"(程序正常/崩溃):true/false,
        //   "stmts"(程序运行过程中执行的语句id):[0, 3, 8001, 10234],
        //   "eventids"(语句关联的eventids, 1<<0表示init覆盖):["9","3","1","2"],
        //   "stackTrace"(status为false时表示出现了uncaught exception, 需记录栈调用信息, status true时为空): [
        //     "类@语句在文件中的源码行"
        //   ]
        //   "stackTraceOrigin": "原始栈信息"
        // }
        // 由于AntranceIns是插桩插到app中的, 尽量不要使用外部依赖
        JSONObject stmtLog = new JSONObject();
        try {
            stmtLog.put("projectId", projectId);
            stmtLog.put("status", "false");
            JSONArray stmts = new JSONArray();
            for (int i = 0; i < stmtTableSize; i++) {
                if (stmtTable2.get(i) != 0) {
                    stmts.put(i);
                }
            }
            stmtLog.put("stmts", stmts);
            JSONArray eventids = new JSONArray();
            for (int i = 0; i < stmtTableSize; i++) {
                if (stmtTable2.get(i) != 0) {
                    eventids.put(String.valueOf(stmtTable2.get(i)));
                }
            }
            stmtLog.put("eventids", eventids);
            JSONArray stackTrace = new JSONArray();
            for (String crashMsg : crashStack) {
                stackTrace.put(crashMsg);
            }
            stmtLog.put("stackTrace", stackTrace);
            stmtLog.put("stackTraceOrigin", stackTraceOrigin);
        } catch (JSONException ex) {
            // 键为null或使用json不支持的数字格式(NaN, infinities)
            throw new RuntimeException(ex);
        }
        return stmtLog.toString();
    }

    public static AntranceIns antranceIns = null;

    public AntranceIns(){
        super(myPort);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (Method.GET.equals(session.getMethod())) {
            if (uri.equals("/stmtlog")) {
                Log.i("antrance", "get /stmtlog");
                return NanoHTTPD.newFixedLengthResponse(getLogJson(true, null));
            } else if (uri.equals("/seteventid")) {
                Map<String, String> parms = session.getParms();
                if (parms.containsKey("id")) {
                    String id = parms.get("id");
                    if (id != null) {
                        eventId.set(Integer.parseInt(id));
                    }
                }
                return NanoHTTPD.newFixedLengthResponse("seteventid");
            }
        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_HTML,
                "please use GET /stmtlog or /eventid");
    }

    static {
        // 为了保证soot修改整形/字符串成员变量的正确执行, 这里需要抵抗编译优化
        int x = 12306;
        String y = "777";
        if (x - Integer.parseInt(y) == 624) {
            projectId = "x.x.x";
            stmtTableSize = 15;
            myCrashJson(null, null);
        }

        stmtTable = new int[stmtTableSize];
        stmtTable2 = new AtomicLongArray(stmtTableSize);

        antranceIns = new AntranceIns();
        try {
            antranceIns.start();
            Log.i("antrance", "antrance ins start on " + myPort);
        } catch (IOException e) {
            Log.e("antrance", "antrance ins start error");
            e.printStackTrace();
        }

        // 注册崩溃回调
        Thread.setDefaultUncaughtExceptionHandler(new UnCaughtExceptionHandler());
        // Runtime.getRuntime().addShutdownHook在这里不管用, 可能是因为art不支持
    }
}