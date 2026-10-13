package de.tum.cit.aet.artemis.atlas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class CompetencyProvenanceTest {

    private static Competency competencyWithProvenance(boolean generatedByAi) {
        Competency competency = new Competency("Sorting", "Understand sorting.", null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, CompetencyTaxonomy.UNDERSTAND, false);
        competency.setGeneratedByAi(generatedByAi);
        return competency;
    }

    @Test
    void competenciesAndLinksAreManualByDefault() {
        assertThat(new Competency().isGeneratedByAi()).isFalse();
        assertThat(new CompetencyExerciseLink().isGeneratedByAi()).isFalse();
    }

    @Test
    void competencyCopyConstructorsPreserveProvenance() {
        assertThat(new Competency(competencyWithProvenance(true)).isGeneratedByAi()).isTrue();
        assertThat(new Competency(competencyWithProvenance(false)).isGeneratedByAi()).isFalse();
        assertThat(new Prerequisite(competencyWithProvenance(true)).isGeneratedByAi()).isTrue();
        assertThat(new Prerequisite(competencyWithProvenance(false)).isGeneratedByAi()).isFalse();
    }

    @Test
    void entityPayloadsCannotSpoofProvenance() throws Exception {
        var objectMapper = JsonObjectMapper.get();

        Competency competency = objectMapper.readValue("{\"type\":\"competency\",\"generatedByAi\":true}", Competency.class);
        CompetencyExerciseLink link = objectMapper.readValue("{\"generatedByAi\":true}", CompetencyExerciseLink.class);

        assertThat(competency.isGeneratedByAi()).isFalse();
        assertThat(link.isGeneratedByAi()).isFalse();
    }
}
