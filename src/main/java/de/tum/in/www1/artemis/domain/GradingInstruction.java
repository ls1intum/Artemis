package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "grading_instruction")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class GradingInstruction implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Exercise exercise;

    @Column(name = "credits")
    private Double credits;

    @Column(name = "level")
    private String level;

    @Enumerated(EnumType.STRING)
    @Column(name = "instruction_description")
    @Lob
    private String instructionDescription;

    @Column(name = "feedback")
    @Lob
    private String feedback;

    @Column(name = "usage_count")
    private int usageCount;

    public String getInstructionDescription() {
        return instructionDescription;
    }

    public GradingInstruction instructionDescription(String instructionDescription) {
        this.instructionDescription = instructionDescription;
        return this;
    }

    public void setInstructionDescription(String instructionDescription) {
        this.instructionDescription = instructionDescription;
    }

}
