package de.tum.in.www1.artemis.util.classpath;

import java.util.Collection;
import java.util.List;

import io.github.classgraph.ClassInfo;

/**
 * Represents the top level Java package, preserving the package tree structure.
 * <p>
 * This is just a {@link PackageNode} with no parent that allows adding classes into the tree.
 * <p>
 * The root node can be either unrestricted, in which case its {@linkplain ClassPathNode#getSegmentName() segment name} is the empty string and all class paths can be added, or
 * restricted to a root package for all its nodes like e.g. <code>de.tum.in</code>, in which case only class path elements {@linkplain ClassPathNode#getName() whose name} starts
 * with that string can be added in the tree. It follows that classes in the default package can only be added to an unrestricted root node.
 */
public class RootNode extends PackageNode {

    /**
     * Creates a new <b>unrestricted</b> root node.
     */
    public RootNode() {
        this("");
    }

    /**
     * Creates a new <b>restricted</b> root node.
     *
     * @param name the {@linkplain ClassPathNode#getSegmentName() segment name} of this root node and the start of the names of all its children in the class path tree.
     */
    public RootNode(String name) {
        super(name);
    }

    /**
     * Adds the given {@link ClassInfo} to this root nodes class path tree.
     *
     * @param classInfo the {@link ClassInfo} describing the class that should be added
     * @return true if the class was not previously contained in the class path tree
     * @throws IllegalArgumentException if the class name of the given {@link ClassInfo} does not start with this root nodes name
     */
    public boolean add(ClassInfo classInfo) {
        Class<?> clazz = classInfo.loadClass();
        var remainingSegments = extractRemainingClassPathSegments(clazz);
        return add(classInfo, clazz, remainingSegments);
    }

    /**
     * Adds the given {@link Class} to this root nodes class path tree, <b>without {@link ClassInfo}</b>.
     *
     * @param clazz the {@link Class} object
     * @return true if the class was not previously contained in the class path tree
     * @throws IllegalArgumentException if the class name of the given {@link Class} does not start with this root nodes name
     */
    public boolean addClassWithoutInfo(Class<?> clazz) {
        var remainingSegments = extractRemainingClassPathSegments(clazz);
        return add(null, clazz, remainingSegments);
    }

    /**
     * Adds all given {@link ClassInfo}s to this root nodes class path tree.
     *
     * @param classes the {@link Class} objects
     * @return this, for easier usage.
     * @throws IllegalArgumentException if the class name of the given {@link ClassInfo} does not start with this root nodes name
     * @see #add(ClassInfo)
     */
    public RootNode addAll(Collection<ClassInfo> classes) {
        classes.forEach(this::add);
        return this;
    }

    /**
     * Adds all given {@link Class}es to this root nodes class path tree, <b>without {@link ClassInfo}</b>.
     *
     * @param classes the {@link ClassInfo}s describing the classes that should be added
     * @return this, for easier usage.
     * @throws IllegalArgumentException if the class name of the given {@link Class} does not start with this root nodes name
     * @see #addClassWithoutInfo(Class)
     */
    public RootNode addAllClassesWithoutInfo(Collection<Class<?>> classes) {
        classes.forEach(this::addClassWithoutInfo);
        return this;
    }

    private List<String> extractRemainingClassPathSegments(Class<?> clazz) {
        String name = clazz.getName();
        if (!name.startsWith(getName())) {
            throw new IllegalArgumentException(clazz + " class path does not start with the root class path name");
        }
        name = name.substring(getName().length() + 1); // + 1 for the dot
        var remainingSegments = List.of(name.split("\\."));
        if (remainingSegments.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(clazz + " has a blank class path segment name");
        }
        return remainingSegments;
    }
}
