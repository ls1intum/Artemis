package de.tum.cit.aet.artemis.globalsearch.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchableEntityCensusServiceTest {

    @Mock
    private CourseIndexCensusService courseIndexCensusService;

    @Test
    void runsTheCensusWhenEnabled() {
        when(courseIndexCensusService.censusAllCourses()).thenReturn(List.of());

        new SearchableEntityCensusService(true, courseIndexCensusService).runCensus();

        verify(courseIndexCensusService).censusAllCourses();
    }

    @Test
    void skipsTheCensusWhenDisabled() {
        new SearchableEntityCensusService(false, courseIndexCensusService).runCensus();

        verify(courseIndexCensusService, never()).censusAllCourses();
    }
}
