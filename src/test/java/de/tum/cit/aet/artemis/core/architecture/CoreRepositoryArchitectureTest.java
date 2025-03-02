package de.tum.cit.aet.artemis.core.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class CoreRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".core";
    }

    @Override
    protected Set<String> testTransactionalExclusions() {
        // @formatter:off
        return Set.of(
            "de.tum.cit.aet.artemis.core.config.migration.entries.MigrationEntry20250228_202600.execute()",
            "de.tum.cit.aet.artemis.core.config.migration.entries.MigrationEntry20250228_202600.updateAttachmentLinks(int)",
            "de.tum.cit.aet.artemis.core.config.migration.entries.MigrationEntry20250228_202600.updateQuizQuestionBackgroundImagePaths(int)",
            "de.tum.cit.aet.artemis.core.config.migration.entries.MigrationEntry20250228_202600.updateDragItemFilePaths(int)",
            "de.tum.cit.aet.artemis.core.config.migration.entries.MigrationEntry20250228_202600.updateCourseIconPaths(int)",
            "de.tum.cit.aet.artemis.core.config.migration.entries.MigrationEntry20250228_202600.updateUserImageUrls(int)",
            "de.tum.cit.aet.artemis.core.config.migration.entries.MigrationEntry20250228_202600.updateStudentExamAssetPaths(int)",
            "de.tum.cit.aet.artemis.core.config.migration.entries.MigrationEntry20250228_202600.updateFileUploadSubmissionPaths(int)"
        );
        // @formatter:on
    }
}
