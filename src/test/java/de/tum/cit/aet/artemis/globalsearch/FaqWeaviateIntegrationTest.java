package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertFaqExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertFaqNotInWeaviate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.communication.FaqFactory;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for FAQ Weaviate indexing, including cleanup during course deletion.
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class FaqWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "faqweaviateint";

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @MockitoSpyBean
    private PyrisFaqApi pyrisFaqApi;

    private Course course;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        // Pyris is not running in integration tests — stub the FAQ deletion to prevent PyrisConnectorException
        doNothing().when(pyrisFaqApi).deleteFaq(any());
    }

    @Nested
    class CourseDeletionTests {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void testDeleteCourse_removesFaqsFromWeaviate() throws Exception {
            Faq faq1 = FaqFactory.generateFaq(course, FaqState.ACCEPTED, "FAQ Title 1", "FAQ Answer 1");
            faq1 = faqRepository.save(faq1);
            searchableEntityWeaviateService.upsertFaqAsync(faq1);

            Faq faq2 = FaqFactory.generateFaq(course, FaqState.ACCEPTED, "FAQ Title 2", "FAQ Answer 2");
            faq2 = faqRepository.save(faq2);
            searchableEntityWeaviateService.upsertFaqAsync(faq2);

            assertFaqExistsInWeaviate(weaviateService, faq1.getId());
            assertFaqExistsInWeaviate(weaviateService, faq2.getId());

            long faq1Id = faq1.getId();
            long faq2Id = faq2.getId();

            request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

            assertFaqNotInWeaviate(weaviateService, faq1Id);
            assertFaqNotInWeaviate(weaviateService, faq2Id);
        }
    }
}
