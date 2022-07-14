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

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_strategy")
    private BonusStrategy bonusStrategy = BonusStrategy.GRADES_DISCRETE; // default

    /**
     * Can be either +1 or -1 to add or subtract bonus.
     */
    @Column(name = "calculationSign")
    private Integer calculationSign;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_grading_scale_id", referencedColumnName = "id")
    private GradingScale source;

    // @ManyToOne(optional = false)
    // @JoinColumn(name = "target_grading_scale_id", referencedColumnName = "id")
    // private GradingScale target;

    public BonusStrategy getBonusStrategy() {
        return bonusStrategy;
    }

    public void setBonusStrategy(BonusStrategy bonusStrategy) {
        this.bonusStrategy = bonusStrategy;
    }

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

    public Integer getCalculationSign() {
        return (int) Math.signum(1.0);
    }

    public void setCalculationSign(Integer calculationSign) {
        this.calculationSign = calculationSign;
    }

    public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, Double achievedPointsForBonus, Double achievedPointsForTarget) {
        return getBonusStrategy().calculateGradeWithBonus(gradingScaleRepository, gradingScaleRepository.findByBonusFromId(getId()).orElse(null), achievedPointsForTarget,
                getSource(), achievedPointsForBonus, getCalculationSign());
    }
}
