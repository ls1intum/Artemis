package de.tum.cit.aet.artemis.programming.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.programming.dto.BuildConfigAdvancedSettingsDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseBuildConfigDTO;

/**
 * The branching settings and the build time window share the {@code advanced_settings} JSON column. The accessors that existed before the column, such as
 * {@link ProgrammingExerciseBuildConfig#isAllowBranching()}, must behave as they always did, and a default must be stored as NULL.
 */
class ProgrammingExerciseBuildConfigAdvancedSettingsTest {

    private static final ZonedDateTime START = ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, ZoneOffset.UTC);

    private static final BuildTimeWindow WINDOW = new BuildTimeWindow(START, START.plusDays(7));

    @Test
    void aNewBuildConfigHasTheDefaultsAndStoresNothing() {
        var buildConfig = new ProgrammingExerciseBuildConfig();

        assertThat(buildConfig.isAllowBranching()).isFalse();
        assertThat(buildConfig.getBranchRegex()).isEqualTo(".*");
        assertThat(buildConfig.getBuildTimeWindow()).isNull();
        assertThat(buildConfig.getAdvancedSettings()).isNull();
    }

    @Test
    void branchingIsReadAndWrittenThroughTheJsonSettings() {
        var buildConfig = new ProgrammingExerciseBuildConfig();

        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex("^feature-.*$");

        assertThat(buildConfig.isAllowBranching()).isTrue();
        assertThat(buildConfig.getBranchRegex()).isEqualTo("^feature-.*$");
        assertThat(buildConfig.getAdvancedSettings().branching()).isEqualTo(new BranchingSettings(true, "^feature-.*$"));
    }

    @Test
    void aNullRegexFallsBackToTheDefaultRegex() {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);

        buildConfig.setBranchRegex(null);

        assertThat(buildConfig.getBranchRegex()).isEqualTo(".*");
    }

    @Test
    void settingBranchingBackToTheDefaultStoresNullAgain() {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex("^x$");

        buildConfig.setAllowBranching(false);
        buildConfig.setBranchRegex(".*");

        assertThat(buildConfig.getAdvancedSettings()).isNull();
    }

    @Test
    void aRegexIsKeptWhileBranchingIsNotAllowed() {
        // the instructor may type the regex first and tick the checkbox later, the previous column mapping kept both independently as well
        var buildConfig = new ProgrammingExerciseBuildConfig();

        buildConfig.setBranchRegex("^x$");

        assertThat(buildConfig.isAllowBranching()).isFalse();
        assertThat(buildConfig.getBranchRegex()).isEqualTo("^x$");
    }

    @Test
    void theWindowAndBranchingDoNotOverwriteEachOther() {
        var buildConfig = new ProgrammingExerciseBuildConfig();

        buildConfig.setAllowBranching(true);
        buildConfig.setBuildTimeWindow(WINDOW);
        assertThat(buildConfig.isAllowBranching()).isTrue();
        assertThat(buildConfig.getBuildTimeWindow()).isEqualTo(WINDOW);

        buildConfig.setBuildTimeWindow(null);
        assertThat(buildConfig.isAllowBranching()).isTrue();
        assertThat(buildConfig.getBuildTimeWindow()).isNull();

        buildConfig.setBuildTimeWindow(WINDOW);
        buildConfig.setAllowBranching(false);
        assertThat(buildConfig.getBuildTimeWindow()).isEqualTo(WINDOW);
        assertThat(buildConfig.getAdvancedSettings().branching()).isNull();
    }

    @Test
    void aWindowWithoutBoundsIsNotStored() {
        var buildConfig = new ProgrammingExerciseBuildConfig();

        buildConfig.setBuildTimeWindow(new BuildTimeWindow(null, null));

        assertThat(buildConfig.getAdvancedSettings()).isNull();
    }

    @Test
    void theCopyConstructorCopiesTheSettings() {
        var original = new ProgrammingExerciseBuildConfig();
        original.setAllowBranching(true);
        original.setBranchRegex("^x$");
        original.setBuildTimeWindow(WINDOW);

        var copy = new ProgrammingExerciseBuildConfig(original);

        assertThat(copy.isAllowBranching()).isTrue();
        assertThat(copy.getBranchRegex()).isEqualTo("^x$");
        assertThat(copy.getBuildTimeWindow()).isEqualTo(WINDOW);
    }

    @Test
    void theDtoKeepsBranchingFlatAndCarriesTheWindowNested() {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setAllowBranching(true);
        buildConfig.setBranchRegex("^x$");
        buildConfig.setBuildTimeWindow(WINDOW);

        var dto = UpdateProgrammingExerciseBuildConfigDTO.of(buildConfig);

        assertThat(dto.allowBranching()).isTrue();
        assertThat(dto.branchRegex()).isEqualTo("^x$");
        assertThat(dto.advancedSettings()).isEqualTo(new BuildConfigAdvancedSettingsDTO(WINDOW));

        var roundTripped = dto.toEntity();
        assertThat(roundTripped.isAllowBranching()).isTrue();
        assertThat(roundTripped.getBranchRegex()).isEqualTo("^x$");
        assertThat(roundTripped.getBuildTimeWindow()).isEqualTo(WINDOW);
        assertThat(dto.withoutId().advancedSettings()).isEqualTo(dto.advancedSettings());
    }

    @Test
    void theDtoOmitsTheNestedSettingsWithoutAWindow() {
        var dto = UpdateProgrammingExerciseBuildConfigDTO.of(new ProgrammingExerciseBuildConfig());

        assertThat(dto.advancedSettings()).isNull();
        assertThat(dto.toEntity().getAdvancedSettings()).isNull();
    }

    @Test
    void anExportWithoutTheNestedSettingsStillImports() {
        // an exercise exported by a version before the build time window has no 'advancedSettings'
        String json = """
                {"timeoutSeconds":120,"allowBranching":true,"branchRegex":"^x$","checkoutSolutionRepository":false}
                """;

        var dto = JsonMapper.builder().build().readValue(json, UpdateProgrammingExerciseBuildConfigDTO.class);

        assertThat(dto.advancedSettings()).isNull();
        var buildConfig = dto.toEntity();
        assertThat(buildConfig.isAllowBranching()).isTrue();
        assertThat(buildConfig.getBranchRegex()).isEqualTo("^x$");
        assertThat(buildConfig.getBuildTimeWindow()).isNull();
    }

    @Test
    void theSettingsSurviveAJsonRoundTripAndIgnoreUnknownProperties() {
        var mapper = JsonMapper.builder().build();
        var settings = new BuildConfigAdvancedSettings(new BranchingSettings(true, "^x$"), WINDOW);

        String json = mapper.writeValueAsString(settings);
        assertThat(json).contains("\"allowed\":true").contains("2026-11-02T12:00:00Z");

        assertThat(mapper.readValue(json, BuildConfigAdvancedSettings.class)).isEqualTo(settings);
        // a row written by a newer version with an option this version does not know must stay readable
        String fromTheFuture = "{\"branching\":{\"allowed\":true,\"regex\":\"^x$\",\"future\":1},\"someNewOption\":{\"a\":1}}";
        assertThat(mapper.readValue(fromTheFuture, BuildConfigAdvancedSettings.class)).isEqualTo(new BuildConfigAdvancedSettings(new BranchingSettings(true, "^x$"), null));
    }
}
