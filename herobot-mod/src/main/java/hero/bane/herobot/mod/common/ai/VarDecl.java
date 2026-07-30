package hero.bane.herobot.mod.common.ai;

public record VarDecl(String name, VarType type, Object defaultValue, String folder) {
    public VarDecl {
        if (folder == null) folder = "";
    }

    public VarDecl(String name, VarType type, Object defaultValue) {
        this(name, type, defaultValue, "");
    }

    public VarDecl withName(String name) {
        return new VarDecl(name, type, defaultValue, folder);
    }

    public VarDecl withType(VarType type, Object defaultValue) {
        return new VarDecl(name, type, defaultValue, folder);
    }

    public VarDecl withFolder(String folder) {
        return new VarDecl(name, type, defaultValue, folder);
    }

    public String qualifiedName() {
        return folder.isEmpty() ? name : folder + "/" + name;
    }
}
