package de.tum.in.www1.artemis.domain.lecture_module;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Lecture;

@Entity
@Table(name = "lecture_module")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "L")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of lecture-content when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = AttachmentModule.class, name = "attachment"), @JsonSubTypes.Type(value = ExerciseModule.class, name = "exercise"),
        @JsonSubTypes.Type(value = HTMLModule.class, name = "html"), @JsonSubTypes.Type(value = VideoModule.class, name = "video"), })
public abstract class LectureModule extends DomainObject {

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    @Lob
    private String description;

    @ManyToOne
    @JoinColumn(name = "lecture_id")
    @JsonIgnoreProperties("lectureModules")
    private Lecture lecture;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }
}
