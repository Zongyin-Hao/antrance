package xmu.wrxlab.abuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/** 每个SrcNode可以看作一个包, children为其子包/子文件, 边权代表包名/文件名 */
class SrcNode {

    private final Map<String, SrcNode> children;

    /** 每层记录自己下面可能存在的类及其对应的源文件.
     * 添加时若一个key已经在map中出现过则将其value标记为@, 表示我们分析不出来他的真正归属, 只能舍弃.
     * 不要担心public类被舍弃, 这个是children负责判断的 */
    private final Map<String, String> unsureClasses;

    public SrcNode() {
        children = new HashMap<>();
        unsureClasses = new HashMap<>();
    }

    public void addChild(File srcFile, String name, SrcNode node, boolean isPkg) throws IOException {
        children.put(name, node);
        if (!isPkg) {
            // 更新unsureClasses原则:读每行, 读纯英文小写, 若不是class读空白后再读一个英文小写, 还不是的话不更新.
            // 是class的话读空白, 读标识符, 标识符更新到unsureClasses中
            // BufferedReader是可以按行读取文件
            FileInputStream inputStream = new FileInputStream(srcFile);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                int[] idx = new int[1];
                idx[0] = 0;
                String str = readLowerCases(line, idx);
                if (str.equals("")) {
                    continue;
                }
                if (!str.equals("class")) {
                    str = readWhites(line, idx);
                    if (str.equals("")) {
                        continue;
                    }
                    str = readLowerCases(line, idx);
                    if (str.equals("")) {
                        continue;
                    }
                }
                if (!str.equals("class")) {
                    continue;
                }
                str = readWhites(line, idx);
                if (str.equals("")) {
                    continue;
                }
                str = readIdentifier(line, idx);
                if (str.equals("")) {
                    continue;
                }
                if (unsureClasses.containsKey(str)) {
                    unsureClasses.put(str, "@");
                    System.out.println("[unsure] " + srcFile.getAbsolutePath() + ": class " + str);
                } else {
                    unsureClasses.put(str, name);
                }
            }
            //close
            inputStream.close();
            bufferedReader.close();
        }
    }

    private String readLowerCases(String line, int[] idx) {
        StringBuilder ans = new StringBuilder();
        while (idx[0] < line.length() && Character.isLowerCase(line.charAt(idx[0]))) {
            ans.append(line.charAt(idx[0]));
            idx[0]++;
        }
        return ans.toString();
    }

    private String readWhites(String line, int[] idx) {
        StringBuilder ans = new StringBuilder();
        while (idx[0] < line.length() && Character.isWhitespace(line.charAt(idx[0]))) {
            ans.append(line.charAt(idx[0]));
            idx[0]++;
        }
        return ans.toString();
    }

    private String readIdentifier(String line, int[] idx) {
        // [a-z, A-Z][a-z, A-Z, 0-9, _]
        if (!Character.isLowerCase(line.charAt(idx[0])) && !Character.isUpperCase(line.charAt(idx[0]))) {
            return "";
        }
        StringBuilder ans = new StringBuilder();
        while (idx[0] < line.length() && (Character.isLowerCase(line.charAt(idx[0])) ||
                Character.isUpperCase(line.charAt(idx[0])) ||
                Character.isDigit(line.charAt(idx[0]))  ||
                line.charAt(idx[0]) == '_')) {
            ans.append(line.charAt(idx[0]));
            idx[0]++;
        }
        return ans.toString();
    }

    public SrcNode getChild(String name) {
        return children.getOrDefault(name, null);
    }

    public Map<String, SrcNode> getChildren() {
        return children;
    }

    /**
     * unsureClasses.containsKey(cls) && !unsureClasses.get(cls).equals("@")
     * @param cls only the last class name of a dot class path
     */
    public boolean mayHaveClass(String cls) {
        return unsureClasses.containsKey(cls) && !unsureClasses.get(cls).equals("@");
    }

    public Map<String, String> getUnsureClasses() {
        return unsureClasses;
    }
}


public class SrcTree {
    private final SrcNode root;

    /** only for unsureClasses */
    private final Map<String, String> dotClassSource;

    public SrcTree() {
        root = new SrcNode();
        dotClassSource = new HashMap<>();
    }

    public Map<String, String> getDotClassSource() {
        return dotClassSource;
    }

    public void debug() {
        debugDFS(0, "DEBUG", root);
    }

    private void debugDFS(int depth, String name, SrcNode cur) {
        for (int i = 0; i < depth; i++) {
            System.out.print("--");
        }
        System.out.println(name);
        for (Map.Entry<String, SrcNode> entry : cur.getChildren().entrySet()) {
            debugDFS(depth+1, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 根据传入的path创建相应的节点
     * @param path 格式: com/example/test/Test.xxx(java,kt)
     */
    public void addSrc(File srcFile, String path) throws IOException {
        // 格式化为[com, example, test, Test]
        String[] sp = path.split("/");
        int length = sp.length;
        if (!sp[length-1].contains(".")) {
            return;
        }
        String[] temp = sp[length-1].split("\\.");
        if (temp.length != 2 || (!temp[1].equals("java") && !temp[1].equals("kt"))) {
            return;
        }
        sp[length-1] = temp[0];

        SrcNode father = root;
        for (int i = 0; i < length; i++) {
            SrcNode node = father.getChild(sp[i]);
            if (node == null) {
                node = new SrcNode();
                father.addChild(srcFile, sp[i], node, i != length-1);
            }
            father = node;
        }
    }

    /**
     * 按照srcTree判断class是否存在
     * @param path 格式: com/example/test/Test$1.class
     * @return class是否存在
     */
    public boolean hasPath(String path) {
        // 格式化为[com, example, test, Test]
        String[] sp = path.split("/");
        int length = sp.length;
        String temp = sp[length-1].split("\\.")[0];
        sp[length-1] = temp.split("\\$")[0];

        // hasPath时, 若最后一步没找到, 则去掉Kt找,
        // 若去掉Kt找不到, 则(此时不要去Kt)去unsureClasses找,
        // 若有key且value不为@, 则视为找到.
        // unsureClasses找到时做好dotclasspath(不带$, .class)与sourcename(不带.java, .kt)的对应
        SrcNode father = root;
        for (int i = 0; i < length; i++) {
            SrcNode node = father.getChild(sp[i]);
            if (node == null) {
                if (i == length - 1) {
                    if (sp[i].endsWith("Kt") &&
                            father.getChild(sp[i].substring(0, sp[i].length()-2)) != null) {
                        return true;
                    }
                    if (father.mayHaveClass(sp[i])) {
                        StringBuilder dotPrefix = new StringBuilder(sp[0]);
                        for (int j = 1; j < length-1; j++) {
                            dotPrefix.append(".").append(sp[j]);
                        }
                        String dotClass = dotPrefix.toString()+"."+sp[i];
                        String sourceName = father.getUnsureClasses().get(sp[i]);
                        dotClassSource.put(dotClass, sourceName);
                        return true;
                    }
                } else if (Character.isLowerCase(sp[i].charAt(0))) {
                    // 包名首字母大写在类文件中会自动转为小写, 特殊处理这种情况
                    String upFirst = Character.toUpperCase(sp[i].charAt(0))+sp[i].substring(1);
                    node = father.getChild(upFirst);
                    if (node != null) {
                        father = node;
                        continue;
                    }
                }
                return false;
            }
            father = node;
        }

        return true;
    }
}
