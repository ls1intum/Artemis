package de.tum.cit.aet.artemis.admin.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class AdminCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".admin";
    }

    // TODO: Reduce this to 0 by converting AuditingEntityDTO and CourseManagementOverviewExerciseStatisticsDTO to records.
    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 2;
    }

    // TODO: Reduce this to 0 by renaming CourseStatisticsAverageScore, LegalDocument and StatisticsEntry to end with "DTO".
    @Override
    protected int dtoNameEndingThreshold() {
        return 3;
    }
}
