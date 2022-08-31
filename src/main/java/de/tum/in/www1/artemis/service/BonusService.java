package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Bonus;
import de.tum.in.www1.artemis.domain.GradeType;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.IBonusStrategy;
import de.tum.in.www1.artemis.repository.BonusRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.dto.BonusExampleDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class BonusService {

    private final BonusRepository bonusRepository;

    private final GradingScaleRepository gradingScaleRepository;

    public BonusService(BonusRepository bonusRepository, GradingScaleRepository gradingScaleRepository) {
        this.bonusRepository = bonusRepository;
        this.gradingScaleRepository = gradingScaleRepository;
    }

    /**
     * Saves a bonus source to the database if it is valid
     *
     * @param bonus the bonus source to be saved
     * @return the saved bonus source
     */
    public Bonus saveBonus(Bonus bonus) {
        if (!bonus.getSourceGradingScale().getGradeType().equals(GradeType.BONUS)) {
            throw new BadRequestAlertException("Source grade scale should have bonus type.", "bonus", "invalidSourceGradingScale");
        }
        if (!bonus.getBonusToGradingScale().getGradeType().equals(GradeType.GRADE)) {
            throw new BadRequestAlertException("BonusTo grade scale should have grade type.", "bonus", "invalidBonusToGradingScale");
        }
        return bonusRepository.save(bonus);
    }

    public BonusExampleDTO calculateGradeWithBonus(IBonusStrategy bonusStrategy, GradingScale bonusToGradingScale, Double achievedPointsOfBonusTo, GradingScale sourceGradingScale,
            Double achievedPointsOfSource, double calculationSign) {
        return bonusStrategy.calculateGradeWithBonus(gradingScaleRepository, bonusToGradingScale, achievedPointsOfBonusTo, sourceGradingScale, achievedPointsOfSource,
                calculationSign);
    }

}
