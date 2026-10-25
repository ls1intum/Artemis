package de.tum.cit.aet.artemis.atlas.service.competency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

class CompetencyValidationServiceTest {

    private final CompetencyValidationService service = new CompetencyValidationService();

    private CourseCompetency valid() {
        Competency c = new Competency();
        c.setTitle("Title");
        c.setDescription("desc");
        c.setMasteryThreshold(50);
        return c;
    }

    @Test
    void checkForCreationRejectsExistingId() {
        CourseCompetency c = valid();
        c.setId(1L);
        assertThatThrownBy(() -> service.checkForCreation(c)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void checkForUpdateRequiresId() {
        assertThatThrownBy(() -> service.checkForUpdate(valid())).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsBlankTitle() {
        CourseCompetency c = valid();
        c.setTitle("   ");
        assertThatThrownBy(() -> service.checkAttributes(c)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void rejectsMasteryThresholdOutOfRange() {
        CourseCompetency c = valid();
        c.setMasteryThreshold(0);
        assertThatThrownBy(() -> service.checkAttributes(c)).isInstanceOf(BadRequestAlertException.class);
        c.setMasteryThreshold(101);
        assertThatThrownBy(() -> service.checkAttributes(c)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void acceptsValidCompetency() {
        assertThatCode(() -> service.checkAttributes(valid())).doesNotThrowAnyException();
    }

    @Test
    void importRequiresADifferentSourceCourseBeforeLoadingData() {
        var absent = new CompetencyImportOptionsDTO(Set.of(), Optional.empty(), false, false, false, Optional.empty(), false);
        assertThatThrownBy(() -> CompetencyValidationService.sourceCourseIdForImport(absent, 1L, "competency")).isInstanceOf(BadRequestAlertException.class)
                .hasMessage("No source course specified");
        var sameCourse = new CompetencyImportOptionsDTO(Set.of(), Optional.of(1L), false, false, false, Optional.empty(), false);
        assertThatThrownBy(() -> CompetencyValidationService.sourceCourseIdForImport(sameCourse, 1L, "competency")).isInstanceOf(BadRequestAlertException.class)
                .hasMessage("Cannot import from a course into itself");
        assertThat(CompetencyValidationService.sourceCourseIdForImport(sameCourse, 2L, "competency")).isEqualTo(1L);
    }
}
