package dev.chronolink.compat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;
import cpw.mods.fml.common.versioning.*;

/** Real GT JARs and the real FML parser, retaining the original loader regression. */
public final class LoaderCompatibilityTests {
    private static final StringBuilder report=new StringBuilder();private static int checks;
    private static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;report.append("PASS ").append(name).append('\n');}
    private static Map<String,String> annotation(String jar,String entry)throws Exception{final Map<String,String> map=new LinkedHashMap<String,String>();
        try(ZipFile zip=new ZipFile(jar)){if(zip.getEntry(entry)==null)throw new AssertionError("Missing "+entry);try(InputStream in=zip.getInputStream(zip.getEntry(entry))){new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5){
            @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible){if(!"Lcpw/mods/fml/common/Mod;".equals(desc))return null;return new AnnotationVisitor(Opcodes.ASM5){@Override public void visit(String n,Object v){map.put(n,String.valueOf(v));}};}
        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}}return map;}
    public static void main(String[] args)throws Exception{if(args.length!=4)throw new IllegalArgumentException("devGT runtimeGT reobfMod report");
        Map<String,String> gt=annotation(args[1],"gregtech/GTMod.class"),nh=annotation(args[1],"gregtech/GTNHMod.class"),dev=annotation(args[0],"gregtech/GTNHMod.class"),ours=annotation(args[2],"dev/chronolink/ChronoLink.class");
        check("gregtech".equals(gt.get("modid")),"GT legacy ID");check("MC1710".equals(gt.get("version")),"actual legacy version MC1710");
        check("gregtech_nh".equals(nh.get("modid")),"GTNH version ID");check("5.09.51.482".equals(nh.get("version")),"actual pinned runtime build");check(nh.get("version").equals(dev.get("version")),"dev/runtime match");
        ArtifactVersion installed=new DefaultArtifactVersion("gregtech",gt.get("version"));check(!VersionParser.parseVersionReference("gregtech@[5.09.51.482]").containsVersion(installed),"reproduce old incorrect rejection");
        check("chronolink".equals(ours.get("modid")),"shipped mod ID");check(System.getProperty("chronolink.expectedVersion").equals(ours.get("version")),"shipped version matches actual build");
        String deps=ours.get("dependencies");check("required-after:gregtech;required-after:gregtech_nh@[5.09.51.482];required-after:CoFHCore;required-after:appliedenergistics2".equals(deps),"ordered exact dependencies plus AE");
        Map<String,ArtifactVersion> req=new LinkedHashMap<String,ArtifactVersion>();for(String d:deps.split(";")){check(d.startsWith("required-after:"),"ordered "+d);ArtifactVersion r=VersionParser.parseVersionReference(d.substring(15));req.put(r.getLabel(),r);}
        check(req.get("gregtech").containsVersion(installed),"real legacy GT accepted");check(req.get("gregtech_nh").containsVersion(new DefaultArtifactVersion("gregtech_nh",nh.get("version"))),"real GTNH build accepted");
        check(!req.get("gregtech_nh").containsVersion(new DefaultArtifactVersion("gregtech_nh","5.09.51.483")),"later build rejected");check(!req.get("gregtech_nh").containsVersion(new DefaultArtifactVersion("gregtech_nh","5.09.51.481")),"earlier build rejected");check(!req.get("gregtech_nh").containsVersion(installed),"no legacy/versioned ID substitution");
        check(req.containsKey("CoFHCore")&&req.containsKey("appliedenergistics2"),"real RF and AE required");ArtifactVersion mc=VersionParser.parseVersionReference("Minecraft@"+ours.get("acceptedMinecraftVersions"));check(mc.containsVersion(new DefaultArtifactVersion("Minecraft","1.7.10")),"target Minecraft accepted");check(!mc.containsVersion(new DefaultArtifactVersion("Minecraft","1.12.2")),"wrong Minecraft rejected");
        report.append("RESULT: ").append(checks).append(" loader-contract checks passed.\nFull GTNH world loading NOT tested.\n");Path out=Paths.get(args[3]);Files.createDirectories(out.toAbsolutePath().getParent());Files.write(out,report.toString().getBytes(StandardCharsets.UTF_8));System.out.print(report);
    }
}
