package de.tum.cit.aet.artemis.iris.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class IrisRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".iris";
    }

    @Override
    protected java.util.Set<String> testTransactionalExclusions() {
        return java.util.Set.of("de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.getOrCreateCourseSettings(long)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.getSettingsForCourse(de.tum.cit.aet.artemis.core.domain.Course)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.getSettingsForCourse(long)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.getCourseSettingsDTO(long)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.updateCourseSettings(long,de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsDTO)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.deleteSettingsFor(de.tum.cit.aet.artemis.core.domain.Course)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.deleteSettingsFor(long)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.isEnabledForCourse(long)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.isEnabledForCourse(de.tum.cit.aet.artemis.core.domain.Course)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.ensureEnabledForCourseOrElseThrow(de.tum.cit.aet.artemis.core.domain.Course)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.getSettingsForCourseOrThrow(long)",
                "de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService.getApplicationRateLimitDefaults()");
    }
}
