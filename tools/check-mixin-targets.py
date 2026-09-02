import io, os, re, subprocess
ROOT = r"C:/Users/srira/Programming/mc/11/HeroBot"
CP = ";".join([
 ROOT+"/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-043a8b3edf/26.2/minecraft-common-043a8b3edf-26.2.jar",
 ROOT+"/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-043a8b3edf/26.2/minecraft-clientOnly-043a8b3edf-26.2.jar",
])
cache={}
def parse(fqn):
    """-> {name: {desc: [invoked 'owner.name:desc' strings]}} or None"""
    if fqn in cache: return cache[fqn]
    r=subprocess.run(["javap","-c","-s","-p","-classpath",CP,fqn],capture_output=True,text=True)
    if "class not found" in (r.stdout+r.stderr) or not r.stdout.strip():
        cache[fqn]=None; return None
    out={}; name=None; desc=None; body=None
    for line in r.stdout.split("\n"):
        m=re.match(r"^  (?!Code:|descriptor:|\s).*?([A-Za-z_$<>][A-Za-z0-9_$.<>]*)\([^)]*\)(?:\s+throws[^;]*)?;\s*$", line)
        if m:
            if name is not None and desc is not None:
                out.setdefault(name,{}).setdefault(desc,[]).extend(body)
            name=m.group(1).split(".")[-1]; desc=None; body=[]
            continue
        d=re.match(r"^\s*descriptor:\s*(\S+)\s*$", line)
        if d and name is not None:
            desc=d.group(1); continue
        if name is not None and body is not None:
            mm=re.search(r"// (?:Interface)?Method (\S+)", line)
            if mm: body.append("M:"+mm.group(1))
            mf=re.search(r"// Field (\S+)", line)
            if mf: body.append("F:"+mf.group(1))
    if name is not None and desc is not None:
        out.setdefault(name,{}).setdefault(desc,[]).extend(body)
    cache[fqn]=out; return out

def ctor_names(fqn, simple, table):
    # javap prints constructors as the simple class name
    if simple in table:
        table["<init>"]=table[simple]
    return table

DIRS=[("herobot-mod/src/main/java/hero/bane/herobot/mod/common/mixin","main"),
      ("herobot-mod/src/client/java/hero/bane/herobot/mod/client/mixin","client")]
problems=[]
STATS={"mixins":0,"sel":0,"at":0}
for rel,tag in DIRS:
    d=os.path.join(ROOT,rel)
    for f in sorted(os.listdir(d)):
        if not f.endswith(".java"): continue
        src=io.open(os.path.join(d,f),encoding="utf-8").read()
        m=re.search(r"@Mixin\(\s*(?:value\s*=\s*)?([A-Za-z0-9_.]+)\.class",src)
        t=re.search(r'@Mixin\(\s*targets\s*=\s*"([^"]+)"',src)
        if m:
            simple=m.group(1)
            im=re.search(r"^import ([a-z][A-Za-z0-9_.]*\."+re.escape(simple)+r");",src,re.M)
            fqn=im.group(1) if im else None
        elif t:
            fqn=t.group(1); simple=re.split(r"[.$]",fqn)[-1]
        else: continue
        if not fqn: problems.append((tag,f,"?","unresolved @Mixin import")); continue
        tab=parse(fqn)
        if tab is None: problems.append((tag,f,fqn,"TARGET CLASS NOT FOUND")); continue
        tab=ctor_names(fqn,simple,tab); STATS["mixins"]+=1
        sels=re.findall(r'method\s*=\s*"([^"]+)"',src)
        chosen=[]
        for sel in sels:
            STATS["sel"]+=1
            n=sel.split("(")[0]
            if n not in tab:
                problems.append((tag,f,fqn,f"selector '{sel}': no method named '{n}'")); continue
            if "(" in sel:
                want=sel[sel.index("("):]
                if want not in tab[n]:
                    problems.append((tag,f,fqn,f"selector '{sel}': descriptor not found. available: {list(tab[n])}"))
                    continue
                chosen.append((n,want))
            else:
                for dd in tab[n]: chosen.append((n,dd))
        bodies=[]
        for n,dd in chosen: bodies.extend(tab[n][dd])
        for at in re.findall(r'target\s*=\s*"(L[^"]+)"',src):
            STATS["at"]+=1
            owner,rest=at[1:].split(";",1)
            if ":" in rest and "(" not in rest.split(":")[0]:
                member=rest.split(":")[0]; want=owner+"."+member+":"+rest.split(":",1)[1]; kind="F:"
            else:
                member=rest.split("(")[0]; want=owner+"."+member+":"+rest[rest.index("("):]; kind="M:"
            short=want.split(".",0)
            ok=any(b==kind+want or b==kind+member+":"+want.split(":",1)[1] for b in bodies)
            if bodies and not ok:
                problems.append((tag,f,fqn,f"@At '{at}'\n      NOT invoked in {[n+d for n,d in chosen]}"))
print("STATS:",STATS)
print(f"{len(problems)} problem(s)\n")
for tag,f,fqn,msg in problems: print(f"[{tag}] {f}  ({fqn})\n    {msg}\n")
