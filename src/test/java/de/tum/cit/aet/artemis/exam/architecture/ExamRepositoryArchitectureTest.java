package de.tum.cit.aet.artemis.exam.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

public class ExamRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".exam";
    }

    // TODO: This method should be removed once all repositories are tested
    @Override
    protected Set<String> testTransactionalExclusions() {
        return Set.of("de.tum.cit.aet.artemis.exam.service.StudentExamService.generateMissingStudentExams(de.tum.cit.aet.artemis.exam.domain.Exam)",
                "de.tum.cit.aet.artemis.exam.service.StudentExamService.generateStudentExams(de.tum.cit.aet.artemis.exam.domain.Exam)");
    }
}
