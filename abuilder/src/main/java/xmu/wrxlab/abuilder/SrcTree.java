package xmu.wrxlab.abuilder;

import java.util.HashMap;
import java.util.Map;

/** 每个SrcNode可以看作一个包, children为其子包/子文件, 边权代表包名/文件名 */
class SrcNode {
    private Map<String, SrcNode> children;

    public SrcNode() {
        children = new HashMap<>();
    }

    public void addChild(String name, SrcNode node) {
        children.put(name, node);
    }

    public SrcNode getChild(String name) {
        return children.getOrDefault(name, null);
    }
}


public class SrcTree {
    private SrcNode root;

    public SrcTree() {
        root = new SrcNode();
    }

    /**
     * 根据传入的path创建相应的节点
     * @param path 格式: com/example/test/Test.xxx(java,kt)
     */
    public void addSrc(String path) {
        // 格式化为[com, example, test, Test]
        String[] sp = path.split("/");
        int length = sp.length;
        sp[length-1] = sp[length-1].split("\\.")[0];

        SrcNode father = root;
        for (int i = 0; i < length; i++) {
            SrcNode node = father.getChild(sp[i]);
            if (node == null) {
                node = new SrcNode();
                father.addChild(sp[i], node);
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

        SrcNode father = root;
        for (int i = 0; i < length; i++) {
            SrcNode node = father.getChild(sp[i]);
            if (node == null) {
                return false;
            }
            father = node;
        }

        return true;
    }
}
