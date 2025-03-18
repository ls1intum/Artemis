package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanCheckoutDirectoriesDTO;
import de.tum.cit.aet.artemis.programming.dto.CheckoutDirectoriesDTO;

@Profile(PROFILE_CORE)
@Service
public class RepositoryCheckoutService {

    /**
     * Path a repository should get checked out in a build plan. E.g. the assignment repository should get checked out
     * to a subdirectory called "assignment" for the Python programming language.
     */
    public enum RepositoryCheckoutPath implements CustomizableCheckoutPath {
        ASSIGNMENT {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                return switch (language) {
                    case JAVA, PYTHON, C, HASKELL, KOTLIN, VHDL, ASSEMBLER, SWIFT, OCAML, EMPTY, RUST, JAVASCRIPT, R, C_PLUS_PLUS, TYPESCRIPT, C_SHARP, GO, BASH, MATLAB, RUBY,
                            DART ->
                        "assignment";
                    case SQL, POWERSHELL, ADA, PHP -> throw new UnsupportedOperationException("Unsupported programming language: " + language);
                };
            }
        },
        TEST {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                return switch (language) {
                    case JAVA, PYTHON, HASKELL, KOTLIN, SWIFT, EMPTY, RUST, JAVASCRIPT, R, C_PLUS_PLUS, TYPESCRIPT -> "";
                    case C, VHDL, ASSEMBLER, OCAML, C_SHARP, GO, BASH, MATLAB, RUBY, DART -> "tests";
                    case SQL, POWERSHELL, ADA, PHP -> throw new UnsupportedOperationException("Unsupported programming language: " + language);
                };
            }
        },
        SOLUTION {

            @Override
            public String forProgrammingLanguage(ProgrammingLanguage language) {
                return switch (language) {
                    case HASKELL, OCAML -> "solution";
                    case JAVA, PYTHON, KOTLIN, SWIFT, EMPTY, C, VHDL, ASSEMBLER, JAVASCRIPT, C_SHARP, C_PLUS_PLUS, SQL, R, TYPESCRIPT, RUST, GO, MATLAB, BASH, RUBY, POWERSHELL,
                            ADA, DART, PHP ->
                        throw new IllegalArgumentException("The solution repository is not checked out during the template/submission build plan for " + language);
                };
            }
        }
    }

    interface CustomizableCheckoutPath {

        /**
         * Path of the subdirectory to which a repository should get checked out to depending on the programming language.
         * E.g. for the language {@link ProgrammingLanguage#C} always check the repo out to "tests"
         *
         * @param language The programming language for which there should be a custom checkout path
         * @return The path to the subdirectory as a String to which some repository should get checked out to.
         */
        String forProgrammingLanguage(ProgrammingLanguage language);
    }

    /**
     * Get the checkout directories for the template and submission build plan for a given programming language.
     *
     * @param programmingLanguage for which the checkout directories should be retrieved
     * @param checkoutSolution    whether the checkout solution repository shall be checked out during the template and submission build plan
     * @return the paths of the checkout directories for the default repositories (exercise, solution, tests) for the
     *         template and submission build plan
     */
    public CheckoutDirectoriesDTO getCheckoutDirectories(ProgrammingLanguage programmingLanguage, boolean checkoutSolution) {
        BuildPlanCheckoutDirectoriesDTO submissionBuildPlanCheckoutDirectories = getSubmissionBuildPlanCheckoutDirectories(programmingLanguage, checkoutSolution);
        BuildPlanCheckoutDirectoriesDTO solutionBuildPlanCheckoutDirectories = getSolutionBuildPlanCheckoutDirectories(submissionBuildPlanCheckoutDirectories);

        return new CheckoutDirectoriesDTO(submissionBuildPlanCheckoutDirectories, solutionBuildPlanCheckoutDirectories);
    }

    private BuildPlanCheckoutDirectoriesDTO getSubmissionBuildPlanCheckoutDirectories(ProgrammingLanguage programmingLanguage, boolean checkoutSolution) {
        String exerciseCheckoutDirectory = RepositoryCheckoutService.RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);
        String testCheckoutDirectory = RepositoryCheckoutService.RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage);

        exerciseCheckoutDirectory = startPathWithRootDirectory(exerciseCheckoutDirectory);
        testCheckoutDirectory = startPathWithRootDirectory(testCheckoutDirectory);

        String solutionCheckoutDirectory = null;

        if (checkoutSolution) {
            try {
                String solutionCheckoutDirectoryPath = RepositoryCheckoutService.RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage);
                solutionCheckoutDirectory = startPathWithRootDirectory(solutionCheckoutDirectoryPath);
            }
            catch (IllegalArgumentException exception) {
                // not checked out during template & submission build
            }
        }

        return new BuildPlanCheckoutDirectoriesDTO(exerciseCheckoutDirectory, solutionCheckoutDirectory, testCheckoutDirectory);
    }

    private String startPathWithRootDirectory(String checkoutDirectoryPath) {
        final String ROOT_DIRECTORY = "/";
        if (checkoutDirectoryPath == null || checkoutDirectoryPath.isEmpty()) {
            return ROOT_DIRECTORY;
        }

        return checkoutDirectoryPath.startsWith(ROOT_DIRECTORY) ? checkoutDirectoryPath : ROOT_DIRECTORY + checkoutDirectoryPath;
    }

    private BuildPlanCheckoutDirectoriesDTO getSolutionBuildPlanCheckoutDirectories(BuildPlanCheckoutDirectoriesDTO submissionBuildPlanCheckoutDirectories) {
        String solutionCheckoutDirectory = submissionBuildPlanCheckoutDirectories.exerciseCheckoutDirectory();
        String testCheckoutDirectory = submissionBuildPlanCheckoutDirectories.testCheckoutDirectory();

        return new BuildPlanCheckoutDirectoriesDTO(null, solutionCheckoutDirectory, testCheckoutDirectory);
    }
}
