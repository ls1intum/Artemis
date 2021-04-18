package de.tum.in.www1.artemis.util.classpath;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.assertj.core.util.TriFunction;

import io.github.classgraph.ClassInfo;

/**
 * Represents a Java class with additional information, preserving the package tree structure.
 */
public class ClassNode extends ClassPathNode {

    private final Class<?> containedClass;

    private final ClassInfo classInfo;

    /**
     * Creates a new class node.
     *
     * @param parent         the parent node of this class node, must not be <code>null</code>.
     * @param containedClass the {@link Class} object, must not be <code>null</code>.
     * @param classInfo      the {@link ClassInfo} describing this class node's class, may be <code>null</code>.
     */
    public ClassNode(PackageNode parent, Class<?> containedClass, ClassInfo classInfo) {
        super(Objects.requireNonNull(parent), getClassNameWithoutPackage(containedClass));
        this.containedClass = Objects.requireNonNull(containedClass);
        this.classInfo = classInfo;
    }

    /**
     * Returns the {@link Class} object of this class node.
     *
     * @return the {@link Class} object, never <code>null</code>
     */
    public Class<?> getContainedClass() {
        return containedClass;
    }

    /**
     * Returns the {@link ClassInfo} of this class node.
     *
     * @return the {@link ClassInfo}, allowed to be <code>null</code>.
     */
    public ClassInfo getClassInfo() {
        return classInfo;
    }

    @Override
    public Stream<ClassNode> allClassNodes() {
        return Stream.of(this);
    }

    @Override
    public <N, C extends N, P extends N> C mapTreeAdvanced(Function<ClassNode, C> classMapper, TriFunction<PackageNode, Stream<P>, Stream<C>, P> packageMapper) {
        return classMapper.apply(this);
    }
}
