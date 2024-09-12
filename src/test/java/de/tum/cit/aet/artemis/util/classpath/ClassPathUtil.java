package de.tum.cit.aet.artemis.util.classpath;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraph.ClasspathElementFilter;
import io.github.classgraph.PackageInfo;
import io.github.classgraph.ScanResult;

/**
 * Utility methods for scanning the class path using {@link ClassGraph}
 */
public final class ClassPathUtil {

    private ClassPathUtil() {

    }

    public static ClassPathNode findAllClassesIn(String packageName) {
        return findAllClassesIn(packageName, classPathElement -> true);
    }

    public static ClassPathNode findAllClassesIn(String packageName, ClasspathElementFilter filter) {
        try (ScanResult scanResult = new ClassGraph().enableAllInfo().acceptPackages(packageName).filterClasspathElements(filter).scan()) {
            return new RootNode(packageName).addAll(scanResult.getAllClasses());
        }
    }

    public static Set<String> findAllPackagesIn(String basePackage, ClasspathElementFilter filter) {
        try (ScanResult scanResult = new ClassGraph().acceptPackages(basePackage).filterClasspathElements(filter).scan()) {
            return scanResult.getPackageInfo().stream().map(PackageInfo::getName).filter(pkg -> pkg.startsWith(basePackage) && pkg.indexOf('.', basePackage.length() + 1) == -1)
                    .filter(pkg -> !pkg.equals(basePackage)).map(pkg -> pkg.substring(basePackage.length() + 1)).collect(Collectors.toSet());
        }
    }

    public static List<Class<?>> findAllClassesAsListIn(String packageName) {
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().acceptPackages(packageName).scan()) {
            return scanResult.getAllClasses().loadClasses();
        }
    }
}
