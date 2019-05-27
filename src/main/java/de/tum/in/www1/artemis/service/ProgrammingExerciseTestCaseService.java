package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.ListUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.TestCaseType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;

@Service
public class ProgrammingExerciseTestCaseService {

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    public ProgrammingExerciseTestCaseService(ProgrammingExerciseTestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    public void generateFromFeedbacks(List<Feedback> feedbacks, ProgrammingExercise exercise) {
        // Known bug: https://jira.spring.io/browse/DATAJPA-1357
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = new Authentication() {

            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return null;
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getDetails() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {

            }

            @Override
            public String getName() {
                return null;
            }
        };
        context.setAuthentication(authentication);

        List<ProgrammingExerciseTestCase> existingTestCases = testCaseRepository.getByExerciseId(exercise.getId());
        List<ProgrammingExerciseTestCase> testCasesFromFeedbacks = feedbacks.stream().map(feedback ->
            new ProgrammingExerciseTestCase()
                .testName(feedback.getText())
                .weight(1)
                .exercise(exercise)
                .active(true)
        ).collect(Collectors.toList());
        List<ProgrammingExerciseTestCase> newTestCases = testCasesFromFeedbacks.stream()
            .filter(testCase -> existingTestCases.stream().noneMatch(existingTestCase -> testCase.equals(existingTestCase)))
            .collect(Collectors.toList());
        List<ProgrammingExerciseTestCase> removedTestCases = existingTestCases.stream()
            .filter(testCase -> testCasesFromFeedbacks.stream().noneMatch(existingTestCase -> testCase.equals(existingTestCase)))
            .map(testCase -> testCase.active(false))
            .collect(Collectors.toList());

        List<ProgrammingExerciseTestCase> toSave = new ArrayList<>();
        toSave.addAll(newTestCases);
        toSave.addAll(removedTestCases);
/*        testCaseRepository.saveAll(ListUtils.union(newTestCases, removedTestCases));*/
        if (toSave.size() > 0) {
            testCaseRepository.saveAll(toSave);
        }
    }
}
