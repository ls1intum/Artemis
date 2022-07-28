package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.repository.GradingScaleRepository;

/**
 * A bonus source for an exam that maps bonus from another course or exam to the target exam
 */
@Entity
@Table(name = "bonus")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Bonus extends DomainObject {

    private static final int CALCULATION_MINUS = -1;

    private static final int CALCULATION_PLUS = 1;

    /**
     * Can be either +1 or -1 to add or subtract bonus.
     */
    @Column(name = "weight")
    private Double weight;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_grading_scale_id", referencedColumnName = "id")
    private GradingScale source;

    // @ManyToOne(optional = false)
    // @JoinColumn(name = "target_grading_scale_id", referencedColumnName = "id")
    // private GradingScale target;

    public GradingScale getSource() {
        return source;
    }

    public void setSource(GradingScale sourceGradingScale) {
        this.source = sourceGradingScale;
    }

    public GradingScale getTarget() {
        // return target;
        return null;
    }

    public void setTarget(GradingScale targetGradingScale) {
        // this.target = targetGradingScale;
    }

    public Integer getWeight() {
        return (int) Math.signum(1.0);
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, BonusStrategy bonusStrategy, Double achievedPointsForBonus,
            Double achievedPointsForTarget) {
        return bonusStrategy.calculateGradeWithBonus(gradingScaleRepository, gradingScaleRepository.findByBonusFromId(getId()).orElse(null), achievedPointsForTarget, getSource(),
                achievedPointsForBonus, getWeight());
    }
}
