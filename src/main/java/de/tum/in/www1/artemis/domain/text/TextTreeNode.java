package de.tum.in.www1.artemis.domain.text;


import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.NaturalId;

import javax.persistence.*;
import java.io.Serializable;

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
    private double lambda_val;

    @Column(name = "child_size", nullable = false)
    private long child_size;

    @ManyToOne
    @JsonIgnore
    private TextExercise exercise;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public long getParent() { return parent; }

    public void setParent(long parent) { this.parent = parent; }

    public long getChild() { return child; }

    public void setChild(long child) { this.child = child; }

    public double getLambda_val() {
        return lambda_val == -1 ? Double.POSITIVE_INFINITY : lambda_val;
    }

    public void setLambda_val(double lambda_val) { this.lambda_val = lambda_val == Double.POSITIVE_INFINITY ? -1 : lambda_val; }

    public long getChild_size() { return child_size; }

    public void setChild_size(long child_size) { this.child_size = child_size; }

    public TextExercise getExercise() { return exercise; }

    public void setExercise(TextExercise exercise) { this.exercise = exercise; }

    public TextTreeNode exercise(TextExercise exercise) {
        setExercise(exercise);
        return this;
    }

    public boolean isBlockNode() {
        return this.child_size == 1;
    }
}
