package xmu.wrxlab.antrance;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.RequiresApi;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class UITree {

    private static String safeCharSeqToString(CharSequence cs) {
        if (cs != null) return cs.toString();
        else return "";
    }

    public static String dumpUITree(List<AccessibilityWindowInfo> windows) {
        Document document = DocumentHelper.createDocument();
        Element rootElement = document.addElement("rt");

        // For convenience the returned windows are ordered in a descending layer order,
        // which is the windows that are on top are reported first.
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo windowInfo = windows.get(i);
            AccessibilityNodeInfo rootNode = windowInfo.getRoot();
            if (rootNode == null) continue;
            dumpUITreeDFS(rootElement, rootNode, 0, i);
        }

        return document.asXML();
    }

    private static void dumpUITreeDFS(Element fatherElement, AccessibilityNodeInfo curNode,
                                      int depth, int index) {
        if (!curNode.isVisibleToUser()) {
            return;
        }
        Rect bds = new Rect();
        curNode.getBoundsInScreen(bds);
        if (bds.right - bds.left <= 5 || bds.bottom - bds.top <= 5) {
            return;
        }
        curNode.refresh();
        String pkg = safeCharSeqToString(curNode.getPackageName());
        String cls = safeCharSeqToString(curNode.getClassName());
        String res = safeCharSeqToString(curNode.getViewIdResourceName());
        String dsc = safeCharSeqToString(curNode.getContentDescription());
        String txt = safeCharSeqToString(curNode.getText());
        /*
         * 计算操作码.
         * https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo
         * 支持|运算, 从低位到高位:
         * 1位: clickable
         * 2位: longClickable
         * 3位: editable
         * 4位: scrollable
         * 5位: checkable
         * */
        int op = 0;
        if (curNode.isClickable()) {
            op |= 1;
        }
        if (curNode.isLongClickable()) {
            op |= 2;
        }
        if (curNode.isEditable()) {
            op |= 4;
        }
        if (curNode.isScrollable()) {
            op |= 8;
        }
        if (curNode.isCheckable()) {
            op |= 16;
        }
        int sta = 0;
        if (curNode.isChecked()) {
            sta |= 1;
        }

        Element curElement = fatherElement.addElement("nd")
                .addAttribute("dp", ""+depth)
                .addAttribute("idx", ""+index)
                .addAttribute("bds", bds.left+"@"+bds.top+"@"+bds.right+"@"+bds.bottom)
                .addAttribute("pkg", ""+pkg)
                .addAttribute("cls", ""+cls)
                .addAttribute("res", ""+res)
                .addAttribute("dsc", ""+dsc)
                .addAttribute("txt", ""+txt)
                .addAttribute("op", ""+op)
                .addAttribute("sta", ""+sta);

        int count = curNode.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo childNode = curNode.getChild(i);
            if (childNode == null) continue;
            dumpUITreeDFS(curElement, childNode, depth+1, i);
        }

        curNode.recycle();
    }

    /**
     * 根据object(pkg@cls@res@op, dp, idx)定位AccessibilityNodeInfo, 执行type类型的操作, 操作数为value.
     * <p> 若操作对象存在返回其边界(left@top@right@bottom), 不存在返回空 <br>
     *     我们仅使用accessibility service进行edit(x)操作, 其余操作默认不执行 <br>
     *     前缀定位规则: pkg@cls@res@op匹配, 复数结果选择idx prefix差值最小的节点.
     */
    public static void perform(List<AccessibilityWindowInfo> windows, String type, String value,
                                 String object, List<Integer> prefix) {
        AccessibilityNodeInfo[] ans = new AccessibilityNodeInfo[1];
        ans[0] = null;
        int[] ansSim = new int[1];
        ansSim[0] = -1;

        // For convenience the returned windows are ordered in a descending layer order,
        // which is the windows that are on top are reported first.
        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < windows.size(); i++) {
            AccessibilityWindowInfo windowInfo = windows.get(i);
            AccessibilityNodeInfo rootNode = windowInfo.getRoot();
            if (rootNode == null) continue;
            queue.offer(i);
            performDfs(rootNode, object, prefix, queue, i, 0, ans, ansSim);
            queue.poll();
        }

        if (ans[0] != null) {
            Rect bds = new Rect();
            ans[0].getBoundsInScreen(bds);

            // 我们这里只执行edit操作, 其余操作交给adb执行
            if (type.equals("edit") || type.equals("editx")) {
                Log.i("antrance", "edit value="+value+"; node="+ans[0].toString());
                Bundle arguments = new Bundle();
                arguments.putString(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
                try {
                    ans[0].performAction(AccessibilityNodeInfoCompat.ACTION_SET_TEXT, arguments);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 记得recycle
            ans[0].recycle();
        }
    }

    private static int calSim(List<Integer> prefix1, Queue<Integer> queue) {
        List<Integer> prefix2 = new ArrayList<>(queue);
        if (prefix1.size() != prefix2.size()) {
            return -1;
        } else {
            int sum = 0;
            for (int i = 0; i < prefix1.size(); i++) {
                if (prefix1.get(i).equals(prefix2.get(i))) {
                    sum += 1;
                }
            }
            return sum;
        }
    }

    private static void performDfs(AccessibilityNodeInfo curNode, String object,
                                   List<Integer> prefix, Queue<Integer> queue, int idx, int depth,
                                   AccessibilityNodeInfo[] ans, int[] ansSim) {
        if (!curNode.isVisibleToUser()) {
            return;
        }
        Rect bds = new Rect();
        curNode.getBoundsInScreen(bds);
        if (bds.right - bds.left <= 5 || bds.bottom - bds.top <= 5) {
            return;
        }
        curNode.refresh();
        String pkg = safeCharSeqToString(curNode.getPackageName());
        String cls = safeCharSeqToString(curNode.getClassName());
        String res = safeCharSeqToString(curNode.getViewIdResourceName());
        int op = 0;
        if (curNode.isClickable()) {
            op |= 1;
        }
        if (curNode.isLongClickable()) {
            op |= 2;
        }
        if (curNode.isEditable()) {
            op |= 4;
        }
        if (curNode.isScrollable()) {
            op |= 8;
        }
        if (curNode.isCheckable()) {
            op |= 16;
        }
        String pkgClsResOpDp = pkg+"@"+cls+"@"+res+"@"+op+"@"+depth;

        boolean update = false;
        if (pkgClsResOpDp.equals(object)) {
            int nodeSim = calSim(prefix, queue);
            if (ans[0] == null || ansSim[0] < nodeSim) {
                update = true;
                ans[0] = curNode;
                ansSim[0] = nodeSim;
            }
        }

        int count = curNode.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo childNode = curNode.getChild(i);
            if (childNode == null) continue;
            queue.offer(i);
            performDfs(childNode,object, prefix, queue, i, depth+1, ans, ansSim);
            queue.poll();
        }

        if (!update) {
            curNode.recycle();
        }
    }

}
