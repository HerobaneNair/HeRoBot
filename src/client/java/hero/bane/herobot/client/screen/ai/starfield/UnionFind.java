package hero.bane.herobot.client.screen.ai.starfield;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class UnionFind {
    private final int[] parent;

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

    Map<Integer, List<Integer>> groups() {
        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < parent.length; i++) {
            groups.computeIfAbsent(find(i), k -> new ArrayList<>()).add(i);
        }
        return groups;
    }

    int[] sizes() {
        int[] size = new int[parent.length];
        for (List<Integer> group : groups().values()) {
            for (int idx : group) size[idx] = group.size();
        }
        return size;
    }
}
