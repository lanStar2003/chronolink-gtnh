package dev.chronolink.compat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;

/** Reads real published JARs and uses the real Forge parser; not a game startup test. */
public final class LoaderCompatibilityTests {
    private static final StringBuilder report = new StringBuilder();
    private static int checks;

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
        report.append("PASS ").append(name).append('\n');
    }

    private static Map<String, String> annotation(Path jar, String entry) throws Exception {
        final Map<String, String> values = new LinkedHashMap<String, String>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            if (zip.getEntry(entry) == null) throw new AssertionError("Missing " + entry);
            try (InputStream in = zip.getInputStream(zip.getEntry(entry))) {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (!"Lcpw/mods/fml/common/Mod;".equals(desc)) return null;
                        return new AnnotationVisitor(Opcodes.ASM5) {
                            @Override public void visit(String name, Object value) {
                                values.put(name, String.valueOf(value));
                            }
                        };
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return values;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("devGT runtimeGT reobfMod reportPath");
        Path dev = Paths.get(args[0]), runtime = Paths.get(args[1]), mod = Paths.get(args[2]);
        Map<String, String> gt = annotation(runtime, "gregtech/GTMod.class");
        Map<String, String> nh = annotation(runtime, "gregtech/GTNHMod.class");
        Map<String, String> nhDev = annotation(dev, "gregtech/GTNHMod.class");
        Map<String, String> ours = annotation(mod, "dev/chronolink/ChronoLink.class");
        report.append("Upstream artifact: GT5-Unofficial 5.09.51.482\n")
            .append("Actual gregtech @Mod version: ").append(gt.get("version")).append('\n')
            .append("Actual gregtech_nh @Mod version: ").append(nh.get("version")).append('\n')
            .append("Actual shipped dependencies: ").append(ours.get("dependencies")).append('\n');
        check("gregtech".equals(gt.get("modid")), "upstream-legacy-mod-id");
        check("MC1710".equals(gt.get("version")), "real-runtime-legacy-FML-version-is-MC1710");
        check("gregtech_nh".equals(nh.get("modid")), "upstream-versioned-GTNH-mod-id");
        check("5.09.51.482".equals(nh.get("version")), "real-runtime-GTNH-build-version");
        check(nh.get("version").equals(nhDev.get("version")), "real-dev-and-runtime-versions-match");
        ArtifactVersion installed = new DefaultArtifactVersion("gregtech", gt.get("version"));
        check(!VersionParser.parseVersionReference("gregtech@[5.09.51.482]").containsVersion(installed),
            "reproduce-alpha1-rejection-with-real-FML-parser");
        check("chronolink".equals(ours.get("modid")), "shipped-mod-id");
        check("0.1.0-alpha.2".equals(ours.get("version")), "shipped-hotfix-version");
        String dependencies = ours.get("dependencies");
        check("required-after:gregtech;required-after:gregtech_nh@[5.09.51.482];required-after:CoFHCore".equals(dependencies),
            "all-required-dependencies-retained-and-version-bound-to-correct-id");
        Map<String, ArtifactVersion> requirements = new LinkedHashMap<String, ArtifactVersion>();
        for (String dependency : dependencies.split(";")) {
            check(dependency.startsWith("required-after:"), "dependency-is-required-and-ordered-" + dependency);
            ArtifactVersion requirement = VersionParser.parseVersionReference(dependency.substring("required-after:".length()));
            requirements.put(requirement.getLabel(), requirement);
        }
        check(requirements.get("gregtech").containsVersion(installed), "shipped-JAR-accepts-real-legacy-GT-version");
        ArtifactVersion nhRequirement = requirements.get("gregtech_nh");
        check(nhRequirement.containsVersion(new DefaultArtifactVersion("gregtech_nh", nh.get("version"))),
            "shipped-JAR-accepts-real-GTNH-runtime-build");
        check(!nhRequirement.containsVersion(new DefaultArtifactVersion("gregtech_nh", "5.09.51.483")), "later-GT-build-rejected");
        check(!nhRequirement.containsVersion(new DefaultArtifactVersion("gregtech_nh", "5.09.51.481")), "earlier-GT-build-rejected");
        check(!nhRequirement.containsVersion(installed), "legacy-id-cannot-substitute-for-GTNH-id");
        check(requirements.containsKey("CoFHCore"), "CoFHCore-still-required");
        ArtifactVersion mc = VersionParser.parseVersionReference("Minecraft@" + ours.get("acceptedMinecraftVersions"));
        check(mc.containsVersion(new DefaultArtifactVersion("Minecraft", "1.7.10")), "Minecraft-1.7.10-accepted");
        check(!mc.containsVersion(new DefaultArtifactVersion("Minecraft", "1.12.2")), "other-Minecraft-version-rejected");
        report.append("RESULT: ").append(checks).append(" loader-contract checks passed.\n")
            .append("Full GTNH startup, world loading and device transfers NOT tested.\n");
        Path output = Paths.get(args[3]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(report.toString());
    }
}
