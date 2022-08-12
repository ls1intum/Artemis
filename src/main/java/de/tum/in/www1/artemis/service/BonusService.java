package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Bonus;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.IBonusStrategy;
import de.tum.in.www1.artemis.repository.BonusRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;

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
        // if (bonusSource.getCourse() != null && bonusSource.getExam() != null) {
        // throw new BadRequestAlertException("Bonus sources can't belong both to a course and an exam", "bonusSource", "bonusSourceBelongsToCourseAndExam");
        // }
        // Set<GradeStep> gradeSteps = bonusSource.getGradeSteps();
        // checkGradeStepValidity(gradeSteps);
        // for (GradeStep gradeStep : gradeSteps) {
        // gradeStep.setBonusSource(bonusSource);
        // }
        // bonusSource.setGradeSteps(gradeSteps);
        return bonusRepository.save(bonus);
    }

    public String calculateGradeWithBonus(IBonusStrategy bonusStrategy, GradingScale bonusToGradingScale, Double basePoints, GradingScale sourceGradingScale, Double sourcePoints,
            double calculationSign) {
        return bonusStrategy.calculateGradeWithBonus(gradingScaleRepository, bonusToGradingScale, basePoints, sourceGradingScale, sourcePoints, calculationSign);
    }

}
