package de.tum.in.www1.artemis.domain;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A TextCluster.
 */
@Entity
@Table(name = "text_cluster")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
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

    @OneToMany(mappedBy = "cluster")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<TextBlock> blocks = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double[] getProbabilities() {
        final ByteArrayInputStream bais = new ByteArrayInputStream(probabilities);
        try (final ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (double[]) ois.readObject();
        }
        catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public TextCluster probabilities(double[] probabilities) {
        setProbabilities(probabilities);
        return this;
    }

    public void setProbabilities(double[] probabilities) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(probabilities);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        this.probabilities = baos.toByteArray();
    }

    public double[][] getDistanceMatrix() {
        final ByteArrayInputStream bais = new ByteArrayInputStream(distanceMatrix);
        try (final ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (double[][]) ois.readObject();
        }
        catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public TextCluster distanceMatrix(double[][] distanceMatrix) {
        setDistanceMatrix(distanceMatrix);
        return this;
    }

    public void setDistanceMatrix(double[][] distanceMatrix) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(distanceMatrix);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        this.distanceMatrix = baos.toByteArray();
    }

    public Set<TextBlock> getBlocks() {
        return blocks;
    }

    public TextCluster blocks(Set<TextBlock> textBlocks) {
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

    public void setBlocks(Set<TextBlock> textBlocks) {
        this.blocks = textBlocks;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public int size() {
        return blocks.size();
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
        return 31;
    }

    @Override
    public String toString() {
        return "TextCluster{" + "id=" + getId() + ", probabilities='" + getProbabilities() + "'" + ", distanceMatrix='" + getDistanceMatrix() + "'" + "}";
    }
}
