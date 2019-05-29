package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;

@Service
public class ProgrammingExerciseTestCaseService {

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    public ProgrammingExerciseTestCaseService(ProgrammingExerciseTestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    // TODO: Workaround for known bug, should be removed once fixed: https://jira.spring.io/browse/DATAJPA-1357
    private Authentication getAuthDummy() {
        return new Authentication() {

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
    }

    public Set<ProgrammingExerciseTestCase> findByExerciseId(Long id) {
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(getAuthDummy());
        return this.testCaseRepository.findByExerciseId(id);
    }

    public Set<ProgrammingExerciseTestCase> findActiveByExerciseId(Long id) {
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(getAuthDummy());
        return this.testCaseRepository.findActiveByExerciseId(id);
    }

    public void generateFromFeedbacks(List<Feedback> feedbacks, ProgrammingExercise exercise) {
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(getAuthDummy());

        Set<ProgrammingExerciseTestCase> existingTestCases = testCaseRepository.findByExerciseId(exercise.getId());
        Set<ProgrammingExerciseTestCase> testCasesFromFeedbacks = feedbacks.stream()
                .map(feedback -> new ProgrammingExerciseTestCase().testName(feedback.getText()).weight(1).exercise(exercise).active(true)).collect(Collectors.toSet());
        // Get test cases that are not already in database - those will be added as new entries.
        Set<ProgrammingExerciseTestCase> newTestCases = testCasesFromFeedbacks.stream()
                .filter(testCase -> existingTestCases.stream().noneMatch(existingTestCase -> testCase.equals(existingTestCase))).collect(Collectors.toSet());
        // Get test cases that were removed from test files, set them to inactive.
        Set<ProgrammingExerciseTestCase> removedTestCases = existingTestCases.stream()
                .filter(testCase -> testCase.isActive() && testCasesFromFeedbacks.stream().noneMatch(existingTestCase -> testCase.equals(existingTestCase)))
                .map(testCase -> testCase.active(false)).collect(Collectors.toSet());

        Set<ProgrammingExerciseTestCase> toSave = new HashSet<>();
        toSave.addAll(newTestCases);
        toSave.addAll(removedTestCases);
        if (toSave.size() > 0) {
            testCaseRepository.saveAll(toSave);
        }
    }
}
