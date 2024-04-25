package de.tum.in.www1.artemis.util.classpath;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

import org.assertj.core.util.TriFunction;

/**
 * Abstract node of the java class path with additional information, preserving the package tree structure.
 * <p>
 * Each node has a name and parent node.
 * <p>
 * All nodes are stored in lexicographic order, with possible sub-packages separated and always coming before the classes. This means a structure like
 *
 * <pre>
 * package de.tum
 *  +- package de.tum.in
 *  |   +- class A
 *  |   +- class D
 *  |
 *  +- package de.tum.zz
 *  |   +- class C
 *  |
 *  +- class B
 *  +- class F
 * </pre>
 *
 * is traversed in the order <code>A -> D -> C -> B -> F</code>.
 * <p>
 * {@link Comparable} as well as the methods {@link Object#equals(Object)} and {@link Object#hashCode()} are purely based on the complete name of the class path element.
 */
public abstract class ClassPathNode implements Comparable<ClassPathNode>, Iterable<ClassNode> {

    private final PackageNode parent;

    private final String segmentName;

    private final String name;

    /**
     * Creates a new {@link ClassPathNode} with the given parent and segment name.
     *
     * @param parent      the parent package node, may be <code>null</code>
     * @param segmentName the segment name, must not be <code>null</code>
     */
    ClassPathNode(PackageNode parent, String segmentName) {
        this.parent = parent;
        this.segmentName = Objects.requireNonNull(segmentName);
        name = parent != null ? parent.getName() + "." + segmentName : segmentName;
    }

    /**
     * Returns the {@link PackageNode} that is the parent of this class path element.
     * <p>
     * The parent of a class is the package that it is in, the parent of a package is the package above, e.g. the parent of <code>de.tum.in</code> would be <code>de.tum</code>
     * (unless <code>de.tum.in</code> is already the root node). In case of the {@link RootNode}, the parent is <code>null</code>.
     *
     * @return the {@link PackageNode} that is the parent of this class path node, or null
     */
    public PackageNode getParent() {
        return parent;
    }

    /**
     * Returns the full name of this node.
     * <p>
     * The full name of a node is the same as the one returned {@link Package#getName()} or {@link Class#getName()} respectively. For an unrestricted {@link RootNode}, the name is
     * the empty string (similar to <code>/</code> as top level path on Unix).
     * <p>
     * Examples: <code>de.tum.in</code>, <code>de.tum.in.SomeClass$NestedClass</code>
     *
     * @return the name of this node, never <code>null</code> but potentially empty.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the name of the segment represented by this node. This is the section of the name after the last '<code>.</code>'.
     * <p>
     * For a package node <code>de.tum.in</code>, this would be <code>in</code>, for a class <code>de.tum.in.SomeClass$NestedClass</code>, this would be
     * <code>SomeClass$NestedClass</code>. For an unrestricted {@link RootNode}, the segment name is the empty string (similar to <code>/</code> as top level path on Unix).
     *
     * @return the segment name of the node, never <code>null</code> but potentially empty.
     */
    public String getSegmentName() {
        return segmentName;
    }

    /**
     * Returns a stream of all class nodes in the tree structure in the order as described in {@linkplain ClassPathNode this classes JavaDoc}.
     *
     * @return a {@link Stream} of all {@link ClassNode}s in the class path tree structure.
     */
    public abstract Stream<ClassNode> allClassNodes();

    @NotNull
    @Override
    public Iterator<ClassNode> iterator() {
        return allClassNodes().iterator();
    }

    /**
     * Maps the class path tree structure to another tree structure from the bottom up.
     *
     * @param <N>           The common superclass of both leaf nodes and inner nodes
     * @param <C>           The new type for the <b>C</b>lass nodes
     * @param <P>           The new type for the <b>P</b>ackage nodes
     * @param classMapper   A function that maps the {@link ClassNode}s to the new leaf node type <code>C</code>
     * @param packageMapper A function that maps a {@link PackageNode} to the new inner node type <code>P</code>. It gets passed the already converted children {@link ClassNode}s
     *                          and {@link PackageNode}s as two {@link Stream}s of type <code>P</code> and <code>C</code>.
     * @return the new tree structure, a subclass of <code>N</code>
     */
    public abstract <N, C extends N, P extends N> N mapTreeAdvanced(Function<ClassNode, C> classMapper, TriFunction<PackageNode, Stream<P>, Stream<C>, P> packageMapper);

    /**
     * Maps the class path tree structure to another tree structure from the bottom up.
     * <p>
     * A simplified version of {@link #mapTreeAdvanced(Function, TriFunction)}.
     *
     * @param <T>           the (common super-) type of the new tree nodes
     * @param classMapper   A function that maps the {@link ClassNode}s to the new node type <code>T</code>
     * @param packageMapper A function that maps a {@link PackageNode} to the new node type <code>T</code>. It gets passed the already converted children {@link ClassNode}s and
     *                          {@link PackageNode}s as a single {@link Stream} of <code>T</code> nodes, converted package nodes first.
     * @return the new tree structure
     * @see #mapTreeAdvanced(Function, TriFunction)
     */
    public <T> T mapTree(Function<ClassNode, T> classMapper, BiFunction<ClassPathNode, Stream<T>, T> packageMapper) {
        return mapTreeAdvanced(classMapper, (packageNode, packages, classes) -> packageMapper.apply(packageNode, Stream.concat(packages, classes)));
    }

    /**
     * Class path nodes are only compared lexicographically by their full name.
     *
     * @return {@link String#compareTo(String)} of the elements name.
     */
    @Override
    public int compareTo(ClassPathNode o) {
        return name.compareTo(o.name);
    }

    /**
     * {@link Object#hashCode()} based on this class path node's {@linkplain #getName() name}.
     *
     * @return the hashCode for this class path node
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * {@link Object#equals(Object)} based on this class path node's {@linkplain #getName() name}.
     *
     * @param obj the reference object with which to compare
     * @return true if <code>obj</code> is a {@link ClassPathNode} with equal {@linkplain #getName() name}.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ClassPathNode)) {
            return false;
        }
        ClassPathNode other = (ClassPathNode) obj;
        return name.equals(other.name);
    }

    static String getClassNameWithoutPackage(Class<?> clazz) {
        String name = clazz.getName();
        return name.substring(name.lastIndexOf('.') + 1); // + 1 for excluding the '.' and compensating -1 / not found
    }
}
