package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Entity
@Table(name = "slide")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Slide extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "attachment_unit_id")
    private AttachmentUnit attachmentUnit;

    @Size(max = 150)
    @Column(name = "slide_image_path", length = 150)
    private String slideImagePath;

    @Column(name = "slide_number")
    private int slideNumber;

    @Column(name = "hidden")
    private ZonedDateTime hidden;

    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    public AttachmentUnit getAttachmentUnit() {
        return attachmentUnit;
    }

    public void setAttachmentUnit(AttachmentUnit attachmentUnit) {
        this.attachmentUnit = attachmentUnit;
    }

    public String getSlideImagePath() {
        return slideImagePath;
    }

    public void setSlideImagePath(String slideImagePath) {
        this.slideImagePath = slideImagePath;
    }

    public int getSlideNumber() {
        return slideNumber;
    }

    public void setSlideNumber(int slideNumber) {
        this.slideNumber = slideNumber;
    }

    public ZonedDateTime getHidden() {
        return hidden;
    }

    public void setHidden(ZonedDateTime hidden) {
        this.hidden = hidden;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }
}
