package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

@ExtendWith(MockitoExtension.class)
class AtlasAgentToolsServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private AtlasAgentService atlasAgentService;

    private AtlasAgentToolsService toolsService;

    @BeforeEach
    void setUp() {
        toolsService = new AtlasAgentToolsService(new ObjectMapper(), courseRepository, exerciseRepository, atlasAgentService);
    }

    @AfterEach
    void tearDown() {
        AtlasAgentToolsService.clearCurrentCourseId();
        AtlasAgentToolsService.clearCurrentSessionId();
    }

    @Nested
    class NullThreadLocalGuards {

        @Test
        void shouldReturnErrorWhenThreadLocalsNullForCompetencyExpert() {
            String result = toolsService.delegateToCompetencyExpert("topic", "requirements", "constraints", "context");

            assertThat(result).contains("\"error\"");
            assertThat(result).contains("missing request context");
            verify(atlasAgentService, never()).delegateToAgent(any(), anyString(), anyLong(), anyString(), anyBoolean());
        }

        @Test
        void shouldReturnErrorWhenThreadLocalsNullForCompetencyMapper() {
            String result = toolsService.delegateToCompetencyMapper("topic", "requirements", "constraints", "context");

            assertThat(result).contains("\"error\"");
            assertThat(result).contains("missing request context");
            verify(atlasAgentService, never()).delegateToAgent(any(), anyString(), anyLong(), anyString(), anyBoolean());
        }

        @Test
        void shouldReturnErrorWhenThreadLocalsNullForExerciseMapper() {
            String result = toolsService.delegateToExerciseMapper(1L, "title", "requirements", "context");

            assertThat(result).contains("\"error\"");
            assertThat(result).contains("missing request context");
            verify(atlasAgentService, never()).delegateToAgent(any(), anyString(), anyLong(), anyString(), anyBoolean());
        }

        @Test
        void shouldReturnErrorWhenOnlyCourseIdSet() {
            AtlasAgentToolsService.setCurrentCourseId(1L);

            String result = toolsService.delegateToCompetencyExpert("topic", "req", "con", "ctx");

            assertThat(result).contains("\"error\"");
            verify(atlasAgentService, never()).delegateToAgent(any(), anyString(), anyLong(), anyString(), anyBoolean());
        }

        @Test
        void shouldReturnErrorWhenOnlySessionIdSet() {
            AtlasAgentToolsService.setCurrentSessionId("session");

            String result = toolsService.delegateToCompetencyExpert("topic", "req", "con", "ctx");

            assertThat(result).contains("\"error\"");
            verify(atlasAgentService, never()).delegateToAgent(any(), anyString(), anyLong(), anyString(), anyBoolean());
        }
    }

    @Nested
    class DelegationBriefFormatting {

        @BeforeEach
        void setThreadLocals() {
            AtlasAgentToolsService.setCurrentCourseId(42L);
            AtlasAgentToolsService.setCurrentSessionId("test-session");
        }

        @Test
        void shouldFormatCompetencyExpertBriefCorrectly() {
            when(atlasAgentService.delegateToAgent(any(), anyString(), anyLong(), anyString(), anyBoolean())).thenReturn("ok");

            toolsService.delegateToCompetencyExpert("Recursion", "Create competency", "None", "Algorithms course");

            verify(atlasAgentService).delegateToAgent(eq(AtlasAgentService.AgentType.COMPETENCY_EXPERT),
                    eq("TOPIC: Recursion\nREQUIREMENTS: Create competency\nCONSTRAINTS: None\nCONTEXT: Algorithms course"), eq(42L), eq("test-session"), eq(false));
        }

        @Test
        void shouldFormatExerciseMapperBriefCorrectly() {
            when(atlasAgentService.delegateToAgent(any(), anyString(), anyLong(), anyString(), anyBoolean())).thenReturn("ok");

            toolsService.delegateToExerciseMapper(5L, "Bubble Sort", "Map to competencies", "Student selected");

            verify(atlasAgentService).delegateToAgent(eq(AtlasAgentService.AgentType.EXERCISE_MAPPER),
                    eq("EXERCISE_ID: 5\nEXERCISE_TITLE: Bubble Sort\nREQUIREMENTS: Map to competencies\nCONTEXT: Student selected"), eq(42L), eq("test-session"), eq(false));
        }
    }

    @Nested
    class ReturnMarkerStripping {

        @BeforeEach
        void setThreadLocals() {
            AtlasAgentToolsService.setCurrentCourseId(1L);
            AtlasAgentToolsService.setCurrentSessionId("session");
        }

        @Test
        void shouldStripReturnToMainAgentMarker() {
            when(atlasAgentService.delegateToAgent(any(), anyString(), anyLong(), anyString(), anyBoolean())).thenReturn("Response text %%ARTEMIS_RETURN_TO_MAIN_AGENT%%");

            String result = toolsService.delegateToCompetencyExpert("t", "r", "c", "x");

            assertThat(result).isEqualTo("Response text");
        }

        @Test
        void shouldReturnUnchangedWhenNoMarkerPresent() {
            when(atlasAgentService.delegateToAgent(any(), anyString(), anyLong(), anyString(), anyBoolean())).thenReturn("Clean response");

            String result = toolsService.delegateToCompetencyMapper("t", "r", "c", "x");

            assertThat(result).isEqualTo("Clean response");
        }
    }
}
