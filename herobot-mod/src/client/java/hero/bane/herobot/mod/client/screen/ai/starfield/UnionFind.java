package hero.bane.herobot.mod.client.screen.ai.starfield;

import java.util.Arrays;

final class UnionFind {
    private final int[] parent;
    private int[] items;
    private int[] start;
    private int[] elemGroup;
    private int groupCount;

    UnionFind(int n) {
        parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
    }

    int find(int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    void union(int a, int b) {
        parent[find(a)] = find(b);
    }

    void buildGroups() {
        int n = parent.length;
        int[] gid = new int[n];
        Arrays.fill(gid, -1);
        elemGroup = new int[n];
        int gc = 0;
        for (int i = 0; i < n; i++) {
            int root = find(i);
            int g = gid[root];
            if (g < 0) {
                g = gc++;
                gid[root] = g;
            }
            elemGroup[i] = g;
        }
        start = new int[gc + 1];
        for (int i = 0; i < n; i++) start[elemGroup[i] + 1]++;
        for (int g = 0; g < gc; g++) start[g + 1] += start[g];
        int[] cursor = new int[gc];
        System.arraycopy(start, 0, cursor, 0, gc);
        items = new int[n];
        for (int i = 0; i < n; i++) items[cursor[elemGroup[i]]++] = i;
        groupCount = gc;
    }

    int groupCount() {
        return groupCount;
    }

    int groupStart(int g) {
        return start[g];
    }

    int groupEnd(int g) {
        return start[g + 1];
    }

    int[] items() {
        return items;
    }

    int sizeOf(int i) {
        int g = elemGroup[i];
        return start[g + 1] - start[g];
    }
}
