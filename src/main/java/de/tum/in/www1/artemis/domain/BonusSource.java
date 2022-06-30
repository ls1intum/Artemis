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
@Table(name = "bonus_source")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BonusSource extends DomainObject {

    private static final int CALCULATION_MINUS = -1;

    private static final int CALCULATION_PLUS = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_strategy")
    private BonusStrategy bonusStrategy = BonusStrategy.GRADES_DISCRETE; // default

    // @Column(name = "value")
    // private double value;

    /**
     * Can be either +1 or -1 to add or subtract bonus.
     */
    @Column(name = "calculation")
    private Integer calculationSign;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_grading_scale_id", referencedColumnName = "id")
    private GradingScale sourceGradingScale;

    @ManyToOne(optional = false)
    @JoinColumn(name = "target_grading_scale_id", referencedColumnName = "id")
    private GradingScale targetGradingScale;

    public BonusStrategy getBonusStrategy() {
        return bonusStrategy;
    }

    public void setBonusStrategy(BonusStrategy bonusStrategy) {
        this.bonusStrategy = bonusStrategy;
    }

    public GradingScale getSourceGradingScale() {
        return sourceGradingScale;
    }

    public void setSourceGradingScale(GradingScale sourceGradingScale) {
        this.sourceGradingScale = sourceGradingScale;
    }

    public GradingScale getTargetGradingScale() {
        return targetGradingScale;
    }

    public void setTargetGradingScale(GradingScale targetGradingScale) {
        this.targetGradingScale = targetGradingScale;
    }

    public Integer getCalculationSign() {
        return calculationSign;
    }

    public void setCalculationSign(Integer calculationSign) {
        this.calculationSign = calculationSign;
    }

    public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, Double achievedPointsForBonus, Double achievedPointsForTarget) {
        return getBonusStrategy().calculateGradeWithBonus(gradingScaleRepository, getTargetGradingScale(), achievedPointsForTarget, getSourceGradingScale(), achievedPointsForBonus,
                getCalculationSign());
    }
}
