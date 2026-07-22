package hero.bane.herobot.ai;

import java.util.ArrayList;
import java.util.List;

public record FuncDecl(String name, List<VarType> params, String folder) {
    public FuncDecl {
        params = List.copyOf(params);
        if (folder == null) folder = "";
    }

    public FuncDecl(String name) {
        this(name, List.of(), "");
    }

    public String qualifiedName() {
        return folder.isEmpty() ? name : folder + "/" + name;
    }

    public int numParams() {
        return params.size();
    }

    public VarType paramType(int index) {
        return index >= 0 && index < params.size() ? params.get(index) : null;
    }

    public FuncDecl withName(String name) {
        return new FuncDecl(name, params, folder);
    }

    public FuncDecl withFolder(String folder) {
        return new FuncDecl(name, params, folder);
    }

    public FuncDecl withParamAdded(VarType type) {
        List<VarType> next = new ArrayList<>(params);
        next.add(type);
        return new FuncDecl(name, next, folder);
    }

    public FuncDecl withParamRemoved(int index) {
        if (index < 0 || index >= params.size()) return this;
        List<VarType> next = new ArrayList<>(params);
        next.remove(index);
        return new FuncDecl(name, next, folder);
    }

    public FuncDecl withParamType(int index, VarType type) {
        if (index < 0 || index >= params.size()) return this;
        List<VarType> next = new ArrayList<>(params);
        next.set(index, type);
        return new FuncDecl(name, next, folder);
    }
}
