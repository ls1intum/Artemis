package de.tum.in.www1.artemis.domain;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A TextCluster.
 */
@Entity
@Table(name = "text_cluster")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextCluster extends DomainObject {

    // @Lob
    @Column(name = "probabilities")
    private byte[] probabilities;

    // @Lob
    @Column(name = "distance_matrix")
    private byte[] distanceMatrix;

    @OneToMany(mappedBy = "cluster")
    @OrderBy("position_in_cluster")
    @JsonIgnoreProperties("cluster")
    private List<TextBlock> blocks = new ArrayList<>();

    @ManyToOne
    @JsonIgnore
    private TextExercise exercise;

    @Column(name = "disabled")
    @JsonIgnore
    private boolean disabled;

    public double[] getProbabilities() {
        return castFromBinary(probabilities);
    }

    public void setProbabilities(double[] probabilities) {
        this.probabilities = castToBinary(probabilities);
    }

    public double[][] getDistanceMatrix() {
        return castFromBinary(distanceMatrix);
    }

    public TextCluster distanceMatrix(double[][] distanceMatrix) {
        setDistanceMatrix(distanceMatrix);
        return this;
    }

    public void setDistanceMatrix(double[][] distanceMatrix) {
        this.distanceMatrix = castToBinary(distanceMatrix);
    }

    private int getBlockIndex(TextBlock textBlock) {
        return blocks.indexOf(textBlock);
    }

    public List<TextBlock> getBlocks() {
        return blocks;
    }

    public TextCluster blocks(List<TextBlock> textBlocks) {
        this.blocks = textBlocks;
        updatePositions();
        return this;
    }

    /**
     * Adds a TextBlock to the Cluster
     * @param textBlock the TextBlock which should be added
     */
    public void addBlocks(TextBlock textBlock) {
        int newPosition = this.blocks.size();
        this.blocks.add(textBlock);
        textBlock.setCluster(this);
        textBlock.setPositionInCluster(newPosition);
    }

    public void removeBlocks(TextBlock textBlock) {
        this.blocks.remove(textBlock);
        textBlock.setCluster(null);
        textBlock.setPositionInCluster(null);
    }

    public void setBlocks(List<TextBlock> textBlocks) {
        this.blocks = textBlocks;
        updatePositions();
    }

    public TextExercise getExercise() {
        return exercise;
    }

    public TextCluster exercise(TextExercise exercise) {
        setExercise(exercise);
        return this;
    }

    public void setExercise(TextExercise exercise) {
        this.exercise = exercise;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public int size() {
        return blocks.size();
    }

    /**
     * Calculates the distance between two textblocks if they are in the same cluster
     * @param first the first TextBlock
     * @param second the second Textblock
     * @return the distance between the two parameters
     */
    public double distanceBetweenBlocks(TextBlock first, TextBlock second) {
        int firstIndex = getBlockIndex(first);
        int secondIndex = getBlockIndex(second);

        if (firstIndex == -1 || secondIndex == -1) {
            throw new IllegalArgumentException("Cannot compute distance to Text Block outside cluster.");
        }

        return getDistanceMatrix()[firstIndex][secondIndex];
    }

    private void updatePositions() {
        for (int i = 0; i < size(); i++) {
            blocks.get(i).setPositionInCluster(i);
        }
    }

    @Override
    public String toString() {
        return "TextCluster{" + "id=" + getId() + (exercise != null ? ", exercise='" + exercise.getId() + "'" : "") + ", size='" + size() + "'" + "}";
    }

    // region Binary Cast
    @SuppressWarnings("unchecked")
    private <T> T castFromBinary(byte[] data) {
        final ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (final ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
        catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private <T> byte[] castToBinary(T data) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(data);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }

    public int openTextBlockCount() {
        return (int) blocks.stream().filter(textBlock -> !textBlock.isAssessable()).count();
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
    // endregion
}
