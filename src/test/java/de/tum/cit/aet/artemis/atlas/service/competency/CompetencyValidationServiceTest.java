package de.tum.cit.aet.artemis.atlas.service.competency;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
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
}
