package de.tum.in.www1.artemis.domain;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A TextCluster.
 */
@Entity
@Table(name = "text_cluster")
public class TextCluster implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(name = "probabilities")
    private byte[] probabilities;

    @Lob
    @Column(name = "distance_matrix")
    private byte[] distanceMatrix;

    @Lob
    @Column(name = "block_order")
    private byte[] blockOrder;

    @OneToMany(mappedBy = "cluster")
    @JsonIgnoreProperties("cluster")
    private List<TextBlock> blocks = new ArrayList<>();

    @ManyToOne
    @JsonIgnore
    private TextExercise exercise;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double[] getProbabilities() {
        return castFromBinary(probabilities);
    }

    public TextCluster probabilities(double[] probabilities) {
        setProbabilities(probabilities);
        return this;
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

    private String[] getBlockOrder() {
        return castFromBinary(blockOrder);
    }

    public void storeBlockOrder() {
        final String[] blockOrder = (String[]) blocks.stream().map(TextBlock::getId).toArray();
        this.blockOrder = castToBinary(blockOrder);
    }

    public int getBlockIndex(TextBlock textBlock) {
        final String[] order = getBlockOrder();
        return IntStream.range(0, order.length).filter(i -> textBlock.getId().equals(order[i])).findFirst().orElse(-1);
    }

    public List<TextBlock> getBlocks() {
        return blocks;
    }

    public TextCluster blocks(List<TextBlock> textBlocks) {
        this.blocks = textBlocks;
        return this;
    }

    public TextCluster addBlocks(TextBlock textBlock) {
        this.blocks.add(textBlock);
        textBlock.setCluster(this);
        return this;
    }

    public TextCluster removeBlocks(TextBlock textBlock) {
        this.blocks.remove(textBlock);
        textBlock.setCluster(null);
        return this;
    }

    public void setBlocks(List<TextBlock> textBlocks) {
        this.blocks = textBlocks;
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

    public double distanceBetweenBlocks(TextBlock first, TextBlock second) {
        int firstIndex = getBlockIndex(first);
        int secondIndex = getBlockIndex(second);

        if (firstIndex == -1 || secondIndex == -1) {
            throw new IllegalArgumentException("Cannot compute distance to Text Block outside cluster.");
        }

        return getDistanceMatrix()[firstIndex][secondIndex];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TextCluster)) {
            return false;
        }
        return id != null && id.equals(((TextCluster) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "TextCluster{" + "id=" + getId() + ", probabilities='" + Arrays.toString(getProbabilities()) + "'" + ", distanceMatrix='" + Arrays.deepToString(getDistanceMatrix())
                + "'" + "}";
    }

    // region Binary Cast
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
    // endregion
}
