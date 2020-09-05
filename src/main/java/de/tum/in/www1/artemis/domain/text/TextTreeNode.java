package de.tum.in.www1.artemis.domain.text;

import java.io.Serializable;

import javax.persistence.*;

import org.hibernate.annotations.NaturalId;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *  An node in the cluster tree. The nodes here actually have the properties of edges.
 *  But as each edge can be mapped to exactly one node (its child in our case), they can be respresented
 *  interchangeably. An artificial edge is created to represent the root node.
 */
@Entity
@Table(name = "text_tree_node")
public class TextTreeNode implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column(name = "parent", nullable = false)
    private long parent;

    @NaturalId
    @Column(name = "child", nullable = false, unique = true)
    private long child;

    @Column(name = "lambda_val", nullable = false)
    private double lambdaVal;

    @Column(name = "child_size", nullable = false)
    private long childSize;

    @ManyToOne
    @JsonIgnore
    private TextExercise exercise;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getParent() {
        return parent;
    }

    public void setParent(long parent) {
        this.parent = parent;
    }

    public long getChild() {
        return child;
    }

    public void setChild(long child) {
        this.child = child;
    }

    public double getLambdaVal() {
        return lambdaVal == -1 ? Double.POSITIVE_INFINITY : lambdaVal;
    }

    public void setLambdaVal(double lambdaVal) {
        this.lambdaVal = lambdaVal == Double.POSITIVE_INFINITY ? -1 : lambdaVal;
    }

    public long getChildSize() {
        return childSize;
    }

    public void setChildSize(long childSize) {
        this.childSize = childSize;
    }

    public TextExercise getExercise() {
        return exercise;
    }

    public void setExercise(TextExercise exercise) {
        this.exercise = exercise;
    }

    public TextTreeNode exercise(TextExercise exercise) {
        setExercise(exercise);
        return this;
    }

    public boolean isBlockNode() {
        return this.childSize == 1;
    }
}
