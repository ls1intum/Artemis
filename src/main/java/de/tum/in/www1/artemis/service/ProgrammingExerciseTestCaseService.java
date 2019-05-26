package de.tum.in.www1.artemis.service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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

        testCaseRepository.deleteByExerciseId(exercise.getId());
        List<ProgrammingExerciseTestCase> testCases = feedbacks.stream().map(feedback -> {
            TestCaseType type = feedback.getText().contains("Structural") ? TestCaseType.STRUCTURAL
                    : feedback.getText().contains("Behavior") ? TestCaseType.BEHAVIOR : TestCaseType.OTHER;
            ProgrammingExerciseTestCase testCase = new ProgrammingExerciseTestCase();
            testCase.setTestName(feedback.getText());
            testCase.setType(type);
            testCase.setWeight(1);
            testCase.setExercise(exercise);
            return testCase;
        }).collect(Collectors.toList());
        testCaseRepository.saveAll(testCases);
    }
}
