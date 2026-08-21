package de.tum.cit.aet.artemis.exercise.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class ExerciseCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".exercise";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 0;
    }

    @Override
    protected int dtoNameEndingThreshold() {
        // 9 legacy non-DTO-named classes in exercise dto packages, plus ParticipationScoreSearchDTOTest and
        // ParticipationSearchDTOTest: *DTOTest unit tests co-located with the DTOs they test, the same accepted
        // pattern used by e.g. IrisAssessmentDTOTest (see IrisCodeStyleArchitectureTest).
        return 11;
    }
}
