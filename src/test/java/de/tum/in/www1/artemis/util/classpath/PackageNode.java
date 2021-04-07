package de.tum.in.www1.artemis.util.classpath;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import org.assertj.core.util.TriFunction;

import io.github.classgraph.ClassInfo;

/**
 * Represents a Java Package with additional information, preserving the package tree structure.
 * <p>
 * A package consists of its sub-packages and own classes (the ones that are directly in that package).
 */
public class PackageNode extends ClassPathNode {

    /**
     * Using a map here to find sub-packages by their segment name quickly.
     */
    private final Map<String, PackageNode> subPackages = new HashMap<>();

    private final Set<ClassNode> ownClasses = new HashSet<>();

    /**
     * Creates a new package node with <code>null</code> parent, only for {@link RootNode}.
     *
     * @param segmentName this nodes segment name, must not be <code>null</code>.
     */
    PackageNode(String segmentName) {
        super(null, segmentName);
    }

    /**
     * Creates a new package node with the given {@linkplain ClassPathNode#getParent() parent} and {@linkplain ClassPathNode#getSegmentName() segment name}.
     *
     * @param parent      this nodes parent package, must not be <code>null</code>
     * @param segmentName this nodes segment name. Must not be <code>null</code>, contain '<code>.</code>' or be blank.
     */
    public PackageNode(PackageNode parent, String segmentName) {
        super(Objects.requireNonNull(parent), segmentName);
        if (segmentName.contains(".")) {
            throw new IllegalArgumentException("PackageNode segment name must not contain '.': " + segmentName);
        }
        if (segmentName.isBlank()) {
            throw new IllegalArgumentException("PackageNode segment name must not be blank");
        }
    }

    /**
     * Returns the direct sub-package nodes of this {@link PackageNode}.
     *
     * @return an unmodifiable collection of direct sub-package nodes in lexicographical order, never <code>null</code>.
     */
    public Collection<PackageNode> getSubPackages() {
        return Collections.unmodifiableCollection(subPackages.values());
    }

    /**
     * Returns the classes directly contained in / owned by this {@link PackageNode}.
     *
     * @return an unmodifiable collection of classes in this {@link PackageNode} in lexicographical order, never <code>null</code>.
     */
    public Collection<ClassNode> getOwnClasses() {
        return Collections.unmodifiableCollection(ownClasses);
    }

    @Override
    public Stream<ClassNode> allClassNodes() {
        return Stream.concat(subPackages.values().stream().flatMap(PackageNode::allClassNodes), ownClasses.stream());
    }

    /**
     * This recursively adds a given class to the package structure, used by {@link RootNode#add(ClassInfo)}.
     *
     * @param classInfo         the classes {@link ClassInfo}, may be <code>null</code>
     * @param clazz             the {@link Class} object, must not be <code>null</code>
     * @param remainingSegments the list of segments left to process
     * @return true if the class was not previously contained in the class path tree structure
     */
    boolean add(ClassInfo classInfo, Class<?> clazz, List<String> remainingSegments) {
        int remainingSegementCount = remainingSegments.size();
        // Only the class name remains, we have to add the class to this package
        if (remainingSegementCount == 1) {
            return ownClasses.add(new ClassNode(this, clazz, classInfo));
        }
        // Otherwise, more package segments left, so the class needs to be added to a sub-package
        String subPackageName = remainingSegments.get(0);
        PackageNode subPackage = subPackages.computeIfAbsent(subPackageName, newSubPackageName -> new PackageNode(this, newSubPackageName));
        return subPackage.add(classInfo, clazz, remainingSegments.subList(1, remainingSegementCount));
    }

    @Override
    public <N, C extends N, P extends N> P mapTreeAdvanced(Function<ClassNode, C> classMapper, TriFunction<PackageNode, Stream<P>, Stream<C>, P> packageMapper) {
        var mappedSubPackages = subPackages.values().stream().map(node -> node.mapTreeAdvanced(classMapper, packageMapper));
        var mappedOwnClasses = ownClasses.stream().map(node -> node.mapTreeAdvanced(classMapper, packageMapper));
        return packageMapper.apply(this, mappedSubPackages, mappedOwnClasses);
    }
}
