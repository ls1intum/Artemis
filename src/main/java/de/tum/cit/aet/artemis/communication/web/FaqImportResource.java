package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.dto.FaqDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqImportSearchResultDTO;
import de.tum.cit.aet.artemis.communication.dto.ImportFaqsDTO;
import de.tum.cit.aet.artemis.communication.service.FaqImportService;
import de.tum.cit.aet.artemis.communication.service.FaqService;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.FaqSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

/** REST endpoints for finding and copying individual FAQs into a course. */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.FAQ)
@RestController
@RequestMapping("api/communication/")
public class FaqImportResource {

    private static final Logger log = LoggerFactory.getLogger(FaqImportResource.class);

    private final FaqImportService faqImportService;

    private final UserRepository userRepository;

    private final FaqService faqService;

    private final Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService;

    public FaqImportResource(FaqImportService faqImportService, UserRepository userRepository, FaqService faqService,
            Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService) {
        this.faqImportService = faqImportService;
        this.userRepository = userRepository;
        this.faqService = faqService;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
    }

    /**
     * Searches FAQs the caller can read in other courses.
     *
     * @param courseId   the target course, where instructor access is required
     * @param searchTerm a literal substring of the question, answer, or course name
     * @param page       the zero-based index of a page of ten results
     * @return the matching page, with source courses and the exact total
     */
    @GetMapping("courses/{courseId}/faqs/for-import")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<FaqImportSearchResultDTO> searchForImport(@PathVariable long courseId, @RequestParam(defaultValue = "") @Size(max = 200) String searchTerm,
            @RequestParam(defaultValue = "0") @Min(0) int page) {
        return ResponseEntity.ok(faqImportService.searchForImport(courseId, searchTerm, page, userRepository.getUserWithAuthorities()));
    }

    /**
     * Copies selected FAQs after rechecking every source FAQ's visibility.
     *
     * @param courseId the target course, where instructor access is required
     * @param options  the selected source IDs
     * @return the created copies; source FAQs are unchanged
     */
    @PostMapping("courses/{courseId}/faqs/import")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<FaqDTO>> importSelected(@PathVariable long courseId, @Valid @RequestBody ImportFaqsDTO options) {
        var imported = faqImportService.importSelectedFaqs(courseId, options.faqIds(), userRepository.getUserWithAuthorities());
        imported.forEach(this::indexImportedFaq);
        return ResponseEntity.created(URI.create("/api/communication/courses/" + courseId + "/faqs")).body(imported.stream().map(FaqDTO::new).toList());
    }

    private void indexImportedFaq(Faq faq) {
        // The copies are already committed. An unavailable index must not turn a successful import into a retry that creates duplicates.
        try {
            faqService.autoIngestFaqIntoPyris(faq.getId());
        }
        catch (RuntimeException e) {
            log.warn("Could not ingest imported FAQ {} into Iris", faq.getId(), e);
        }
        try {
            searchableEntityWeaviateService.ifPresent(service -> service.upsertFaqAsync(FaqSearchableEntityDTO.fromFaq(faq)));
        }
        catch (RuntimeException e) {
            log.warn("Could not index imported FAQ {} for global search", faq.getId(), e);
        }
    }

}
