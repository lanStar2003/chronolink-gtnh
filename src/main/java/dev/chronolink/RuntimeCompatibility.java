package dev.chronolink;

/** Separates the published GT artifact version from its historical FML version (MC1710). */
public final class RuntimeCompatibility {
    public static final String EXPECTED_GT_BUILD = "5.09.51.482";

    private RuntimeCompatibility() {}

    public static boolean supportsBuild(String build) {
        return EXPECTED_GT_BUILD.equals(build);
    }

    public static String requireSupportedGregTech(ClassLoader loader) {
        final String build;
        try {
            // A direct reference to GT_Version.VERSION would be inlined by javac and
            // would check OUR compile-time value, not the user's installed artifact.
            Class<?> versionClass = Class.forName("gregtech.GT_Version", false, loader);
            Object value = versionClass.getField("VERSION").get(null);
            if (!(value instanceof String)) {
                throw new IllegalStateException("GregTech build marker is not a String");
            }
            build = (String) value;
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                "ChronoLink cannot read the installed GregTech artifact version. "
                + "This build targets GTNH 2.8.4 / GT5-Unofficial " + EXPECTED_GT_BUILD
                + ". See the nested cause in fml-client-latest.log.", failure);
        }
        if (!supportsBuild(build)) {
            throw new IllegalStateException(
                "ChronoLink targets GTNH 2.8.4 / GT5-Unofficial " + EXPECTED_GT_BUILD
                + "; the installed GregTech artifact reports " + build
                + ". FML's MC1710 label is not the artifact version. "
                + "Use the matching ChronoLink build; do not replace GT in an existing pack.");
        }
        return build;
    }
}
