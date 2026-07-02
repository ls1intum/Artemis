package de.tum.cit.aet.artemis.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for the stable-ordering guarantee of {@link UserRepository#searchAllByLoginOrNameOrEmailOrRegistrationNumber(Pageable, String)}.
 *
 * <p>
 * The paginated user search must impose a deterministic order so its LIMIT/OFFSET pages form a stable, non-overlapping partition (issue #13069). This cannot be reproduced through
 * the database in a small-table integration test (a freshly populated table happens to return its rows in id order regardless of whether an {@code ORDER BY} is present), so the
 * guarantee is verified here at the boundary: the default method must hand the query a {@link Pageable} sorted by id ascending. Because the fix lives in the shared repository
 * method,
 * it covers every caller (exam and organization registration) at once.
 */
class UserRepositoryStableSearchSortUnitTest {

    @Test
    void searchAppliesStableIdSortWhenCallerDidNotRequestOne() {
        UserRepository repository = mock(UserRepository.class, CALLS_REAL_METHODS);
        doReturn(Page.empty()).when(repository).findAllByLoginOrNameOrEmailOrRegistrationNumber(any(), anyString());

        repository.searchAllByLoginOrNameOrEmailOrRegistrationNumber(PageRequest.of(0, 10), "student");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByLoginOrNameOrEmailOrRegistrationNumber(pageableCaptor.capture(), anyString());
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    void searchKeepsAnExplicitCallerSort() {
        UserRepository repository = mock(UserRepository.class, CALLS_REAL_METHODS);
        doReturn(Page.empty()).when(repository).findAllByLoginOrNameOrEmailOrRegistrationNumber(any(), anyString());
        Sort explicitSort = Sort.by(Sort.Direction.DESC, "login");

        repository.searchAllByLoginOrNameOrEmailOrRegistrationNumber(PageRequest.of(1, 5, explicitSort), "student");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByLoginOrNameOrEmailOrRegistrationNumber(pageableCaptor.capture(), anyString());
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(explicitSort);
    }
}
