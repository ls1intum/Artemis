package de.tum.in.www1.artemis.domain;

import java.util.Objects;

public class TextEmbedding {

    private String id;

    private float[] vector;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public float[] getVector() {
        return vector;
    }

    public void setVector(float[] vector) {
        this.vector = vector;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TextEmbedding textEmbedding = (TextEmbedding) o;
        if (textEmbedding.id == null || id == null) {
            return false;
        }
        return Objects.equals(id, textEmbedding.id);
    }
}
