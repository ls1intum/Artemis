package de.tum.cit.aet.artemis.communication.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.FaqDTO;
import de.tum.cit.aet.artemis.communication.dto.ImportFaqsDTO;
import de.tum.cit.aet.artemis.communication.service.FaqImportService;
import de.tum.cit.aet.artemis.communication.service.FaqService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.FaqSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

class FaqImportResourceTest {

    @Test
    void importSelected_returnsCreatedCopiesWhenGlobalSearchIndexingFails() {
        var importService = mock(FaqImportService.class);
        var userRepository = mock(UserRepository.class);
        var faqService = mock(FaqService.class);
        var searchIndex = mock(SearchableEntityWeaviateService.class);
        var resource = new FaqImportResource(importService, userRepository, faqService, Optional.of(searchIndex));
        var caller = new User();
        var target = new Course();
        target.setId(42L);
        var copy = new Faq();
        copy.setId(31L);
        copy.setCourse(target);
        copy.setQuestionTitle("Copied question");
        copy.setQuestionAnswer("Copied answer");
        copy.setFaqState(FaqState.ACCEPTED);
        when(userRepository.getUserWithAuthorities()).thenReturn(caller);
        when(importService.importSelectedFaqs(42L, Set.of(10L), caller)).thenReturn(List.of(copy));
        doThrow(new IllegalStateException("Index unavailable")).when(searchIndex).upsertFaqAsync(any(FaqSearchableEntityDTO.class));

        var response = resource.importSelected(42L, new ImportFaqsDTO(Set.of(10L)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/communication/courses/42/faqs"));
        assertThat(response.getBody()).extracting(FaqDTO::id).containsExactly(31L);
        verify(faqService).autoIngestFaqIntoPyris(31L);
        verify(searchIndex).upsertFaqAsync(new FaqSearchableEntityDTO(31L, 42L, "Copied question", "Copied answer", "ACCEPTED"));
    }
}
