package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A Rating.
 */
@Entity
@Table(name = "result_rating")
@JsonInclude(Include.NON_EMPTY)
public class Rating extends DomainObject {

    @Column(name = "rating")
    private Integer rating;

    @OneToOne
    @JoinColumn(name = "result_id")
    private Result result;

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "Rating{" + "id=" + getId() + ", rating='" + getRating() + "'}";
    }
}
