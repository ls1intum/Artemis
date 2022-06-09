package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A bonus source for an exam that maps bonus from another course or exam to the target exam
 */
@Entity
@Table(name = "bonus_source")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BonusSource extends DomainObject {

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_strategy")
    private BonusStrategy bonusStrategy = BonusStrategy.GRADE_STEPS; // default

    @Column(name = "value")
    private double value;

    @OneToOne
    @JoinColumn(name = "source_grading_scale_id")
    private GradingScale sourceGradingScale;

    public BonusStrategy getBonusStrategy() {
        return bonusStrategy;
    }

    public void setBonusStrategy(BonusStrategy bonusStrategy) {
        this.bonusStrategy = bonusStrategy;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public GradingScale getSourceGradingScale() {
        return sourceGradingScale;
    }

    public void setSourceGradingScale(GradingScale sourceGradingScale) {
        this.sourceGradingScale = sourceGradingScale;
    }
}
