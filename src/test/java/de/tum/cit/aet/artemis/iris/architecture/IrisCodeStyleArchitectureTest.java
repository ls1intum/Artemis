package de.tum.cit.aet.artemis.iris.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class IrisCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".iris";
    }

    @Override
    protected int dtoNameEndingThreshold() {
        // Non-DTO-named classes that legitimately live in iris dto packages: a handful of nested enums (including the
        // Course Memory source enum PyrisCourseMemorySource) plus the RawJsonDeserializer helpers co-located with the
        // @JsonRawValue DTOs they complement (IrisMessageContentResponseDTO and PyrisJsonMessageContentDTO). These
        // follow an accepted pattern; the count is tracked here rather than 0.
        return 9;
    }
}
