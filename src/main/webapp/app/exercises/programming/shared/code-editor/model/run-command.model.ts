import { ProgrammingLanguage, ProjectType } from 'app/entities/programming-exercise.model';

/**
 * Returns the respective command for running/compiling the programming exercise;
 * @param programmingLanguage of the programming exercise
 * @param projectType of the programming exercise
 * @param args The arguments which will be included in the command
 */
export function getRunCommand(programmingLanguage: ProgrammingLanguage, projectType?: ProjectType, args?: string) {
    switch (programmingLanguage) {
        case ProgrammingLanguage.JAVA:
            if (projectType && [ProjectType.PLAIN_GRADLE, ProjectType.GRADLE_GRADLE].includes(projectType)) {
                return `./gradlew --no-daemon -Dorg.gradle.jvmargs="-Xmx100m" clean test ${args}`;
            } else {
                return `mvn clean test ${args}`;
            }
        case ProgrammingLanguage.PYTHON:
            return 'python3 -m compileall . -q;pytest';
        case ProgrammingLanguage.C:
            return args ? `make run ARGS="${args}"` : 'make run';
    }
}
