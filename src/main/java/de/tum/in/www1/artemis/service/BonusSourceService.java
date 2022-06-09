package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.BonusSource;
import de.tum.in.www1.artemis.repository.BonusSourceRepository;

@Service
public class BonusSourceService {

    private final BonusSourceRepository bonusSourceRepository;

    public BonusSourceService(BonusSourceRepository bonusSourceRepository) {
        this.bonusSourceRepository = bonusSourceRepository;
    }

    /**
     * Saves a bonus source to the database if it is valid
     *
     * @param bonusSource the bonus source to be saved
     * @return the saved bonus source
     */
    public BonusSource saveBonusSource(BonusSource bonusSource) {
        // if (bonusSource.getCourse() != null && bonusSource.getExam() != null) {
        // throw new BadRequestAlertException("Bonus sources can't belong both to a course and an exam", "bonusSource", "bonusSourceBelongsToCourseAndExam");
        // }
        // Set<GradeStep> gradeSteps = bonusSource.getGradeSteps();
        // checkGradeStepValidity(gradeSteps);
        // for (GradeStep gradeStep : gradeSteps) {
        // gradeStep.setBonusSource(bonusSource);
        // }
        // bonusSource.setGradeSteps(gradeSteps);
        return bonusSourceRepository.save(bonusSource);
    }

}
