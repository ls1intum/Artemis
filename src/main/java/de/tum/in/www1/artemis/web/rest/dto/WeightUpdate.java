package de.tum.in.www1.artemis.web.rest.dto;

/**
 * This is a dto for updating the weight of an entity. Currently only used for updating programming exercise test case weights.
 */
public class WeightUpdate {

    private Long id;

    private Integer weight;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }
}
