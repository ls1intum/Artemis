package de.tum.in.www1.artemis.domain;

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
}
