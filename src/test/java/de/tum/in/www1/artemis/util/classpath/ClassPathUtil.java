package de.tum.in.www1.artemis.util.classpath;

import java.util.List;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraph.ClasspathElementFilter;
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

    public static List<Class<?>> findAllClassesAsListIn(String packageName) {
        try (ScanResult scanResult = new ClassGraph().enableClassInfo().acceptPackages(packageName).scan()) {
            return scanResult.getAllClasses().loadClasses();
        }
    }
}
