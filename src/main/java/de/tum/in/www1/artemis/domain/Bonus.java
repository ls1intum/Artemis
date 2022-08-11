package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.repository.GradingScaleRepository;

/**
 * A bonus source for an exam that maps bonus from another course or exam to the target exam
 */
@Entity
@Table(name = "bonus")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Bonus extends DomainObject {

    /**
     * Can be either +1 or -1 to add or subtract bonus.
     */
    @Column(name = "weight")
    private Double weight;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_grading_scale_id", referencedColumnName = "id")
    private GradingScale source;

    @ManyToOne(optional = false)
    @JoinColumn(name = "bonus_to_grading_scale_id", referencedColumnName = "id")
    @JsonIgnore
    private GradingScale bonusToGradingScale;

    /**
     * This field is persisted at {@see bonusToGradingScale}, it is defined here for transferring the value from client.
     */
    @Transient
    @JsonProperty
    private BonusStrategy bonusStrategy;

    public GradingScale getSource() {
        return source;
    }

    public void setSource(GradingScale sourceGradingScale) {
        this.source = sourceGradingScale;
    }

    public GradingScale getBonusToGradingScale() {
        return bonusToGradingScale;
    }

    public void setBonusToGradingScale(GradingScale gradingScale) {
        this.bonusToGradingScale = gradingScale;
    }

    public Integer getWeight() {
        return (int) Math.signum(weight);
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String calculateGradeWithBonus(GradingScaleRepository gradingScaleRepository, BonusStrategy bonusStrategy, Double achievedPointsForBonus,
            Double achievedPointsForTarget) {
        return bonusStrategy.calculateGradeWithBonus(gradingScaleRepository, gradingScaleRepository.findWithEagerBonusFromByBonusFromId(getId()).orElse(null),
                achievedPointsForTarget, getSource(), achievedPointsForBonus, getWeight());
    }

    public BonusStrategy getBonusStrategy() {
        return bonusStrategy;
    }

    public void setBonusStrategy(BonusStrategy bonusStrategy) {
        this.bonusStrategy = bonusStrategy;
    }
}
