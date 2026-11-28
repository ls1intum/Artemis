package de.tum.cit.aet.artemis.shared.architecture.util;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

/**
 * Utility methods for scanning the class path using {@link ClassGraph}
 */
public final class ClassPathUtil {

    private ClassPathUtil() {

    }

    /**
     * Scans with everything ClassGraph 4 enabled by {@code enableAllInfo()}: class, field, method and annotation information, constant initializers and non-public members.
     */
    private static ClassGraph classGraphWithAllInfo() {
        return new ClassGraph().enableClasspath().enableClassInfo().enableFieldInfo().enableMethodInfo().enableAnnotationInfo().enableStaticFinalFieldConstantInitializerValues()
                .ignoreClassVisibility().ignoreFieldVisibility().ignoreMethodVisibility();
    }

    public static ClassPathNode findAllClassesIn(String packageName, Predicate<String> filter) {
        try (ScanResult scanResult = classGraphWithAllInfo().acceptPackages(packageName).filterClasspathElements(filter).scan()) {
            return new RootNode(packageName).addAll(scanResult.getAllClasses());
        }
    }

    /**
     * Finds the names of the direct sub-packages of the base package that contain classes, at any depth.
     * ClassGraph 6 only reports packages that contain classes themselves, so the names are derived from the scanned classes.
     */
    public static Set<String> findAllPackagesIn(String basePackage, Predicate<String> filter) {
        String prefix = basePackage + ".";
        try (ScanResult scanResult = new ClassGraph().enableClasspath().enableClassInfo().acceptPackages(basePackage).filterClasspathElements(filter).scan()) {
            return scanResult.getAllClasses().stream().map(ClassInfo::getPackageName).filter(pkg -> pkg.startsWith(prefix)).map(pkg -> pkg.substring(prefix.length()))
                    .map(subPackage -> subPackage.contains(".") ? subPackage.substring(0, subPackage.indexOf('.')) : subPackage).collect(Collectors.toSet());
        }
    }
}
