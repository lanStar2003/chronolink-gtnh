package dev.chronolink.compat;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
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
import dev.chronolink.RuntimeCompatibility;

/** Headless dependency-contract regression, NOT a full Minecraft startup test. */
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

    private static String realBuild(Path upstream, Path shippedMod) throws Exception {
        // Load just the shipped version guard and GT's generated version class.
        // No GT machines, Minecraft world, class initializer or API stubs are emulated.
        URL[] urls = {shippedMod.toUri().toURL(), upstream.toUri().toURL()};
        try (URLClassLoader isolated = new URLClassLoader(urls, null)) {
            Class<?> guard = Class.forName("dev.chronolink.RuntimeCompatibility", true, isolated);
            return (String) guard.getMethod("requireSupportedGregTech", ClassLoader.class)
                .invoke(null, isolated);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("devGT runtimeGT reobfMod reportPath");
        Path dev = Paths.get(args[0]), runtime = Paths.get(args[1]), mod = Paths.get(args[2]);
        Map<String, String> gt = annotation(runtime, "gregtech/GTMod.class");
        Map<String, String> ours = annotation(mod, "dev/chronolink/ChronoLink.class");
        String fmlVersion = gt.get("version");
        report.append("Upstream artifact: GT5-Unofficial 5.09.51.482\n")
            .append("Actual upstream @Mod version: ").append(fmlVersion).append('\n')
            .append("Actual shipped dependencies: ").append(ours.get("dependencies")).append('\n');
        check("gregtech".equals(gt.get("modid")), "upstream-mod-id");
        check("MC1710".equals(fmlVersion), "real-GT-runtime-uses-MC1710-FML-label");
        check(RuntimeCompatibility.EXPECTED_GT_BUILD.equals(realBuild(dev, mod)), "shipped-guard-accepts-real-dev-artifact");
        check(RuntimeCompatibility.EXPECTED_GT_BUILD.equals(realBuild(runtime, mod)), "shipped-guard-accepts-real-runtime-artifact");
        ArtifactVersion installed = new DefaultArtifactVersion("gregtech", fmlVersion);
        check(!VersionParser.parseVersionReference("gregtech@[5.09.51.482]").containsVersion(installed),
            "reproduce-alpha1-rejection-with-real-FML-parser");
        check("chronolink".equals(ours.get("modid")), "shipped-mod-id");
        check("0.1.0-alpha.2".equals(ours.get("version")), "shipped-hotfix-version");
        String dependencies = ours.get("dependencies");
        check("required-after:gregtech;required-after:CoFHCore".equals(dependencies), "both-required-dependencies-retained");
        boolean found = false;
        for (String dependency : dependencies.split(";")) {
            if (dependency.startsWith("required-after:gregtech")) {
                found = true;
                ArtifactVersion requirement = VersionParser.parseVersionReference(dependency.substring("required-after:".length()));
                check(requirement.containsVersion(installed), "shipped-JAR-passes-real-FML-GT-version-check");
                check(!requirement.containsVersion(new DefaultArtifactVersion("not-gregtech", "MC1710")), "different-mod-id-is-not-accepted");
            }
        }
        check(found, "GT-is-still-required-before-ChronoLink");
        ArtifactVersion mc = VersionParser.parseVersionReference("Minecraft@" + ours.get("acceptedMinecraftVersions"));
        check(mc.containsVersion(new DefaultArtifactVersion("Minecraft", "1.7.10")), "Minecraft-1.7.10-accepted");
        check(!mc.containsVersion(new DefaultArtifactVersion("Minecraft", "1.12.2")), "other-Minecraft-version-rejected");
        check(RuntimeCompatibility.supportsBuild("5.09.51.482"), "exact-GT-artifact-accepted");
        check(!RuntimeCompatibility.supportsBuild("5.09.51.483"), "later-GT-artifact-rejected");
        check(!RuntimeCompatibility.supportsBuild("5.09.51.481"), "earlier-GT-artifact-rejected");
        check(!RuntimeCompatibility.supportsBuild("MC1710"), "FML-label-not-mistaken-for-build-marker");
        check(!RuntimeCompatibility.supportsBuild(null), "missing-build-marker-rejected");
        report.append("RESULT: ").append(checks).append(" loader-contract checks passed.\n")
            .append("Full GTNH startup, world loading and device transfers NOT tested.\n");
        Path output = Paths.get(args[3]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(report.toString());
    }
}
