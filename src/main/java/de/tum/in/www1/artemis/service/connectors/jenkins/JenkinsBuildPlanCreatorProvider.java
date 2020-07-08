package de.tum.in.www1.artemis.service.connectors.jenkins;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@Profile("jenkins")
@Component
public class JenkinsBuildPlanCreatorProvider {

    private final JavaJenkinsBuildPlanCreator javaJenkinsBuildPlanCreator;

    private final PythonJenkinsBuildPlanCreator pythonJenkinsBuildPlanCreator;

    private final CJenkinsBuildPlanCreator cJenkinsBuildPlanCreator;

    private final HaskellJenkinsBuildPlanCreator haskellJenkinsBuildPlanCreator;

    public JenkinsBuildPlanCreatorProvider(JavaJenkinsBuildPlanCreator javaJenkinsBuildPlanCreator, PythonJenkinsBuildPlanCreator pythonJenkinsBuildPlanCreator,
            CJenkinsBuildPlanCreator cJenkinsBuildPlanCreator, HaskellJenkinsBuildPlanCreator haskellJenkinsBuildPlanCreator) {
        this.javaJenkinsBuildPlanCreator = javaJenkinsBuildPlanCreator;
        this.pythonJenkinsBuildPlanCreator = pythonJenkinsBuildPlanCreator;
        this.cJenkinsBuildPlanCreator = cJenkinsBuildPlanCreator;
        this.haskellJenkinsBuildPlanCreator = haskellJenkinsBuildPlanCreator;
    }

    /**
     * Gives a Jenkins plan builder, that is able to build plan configurations for the specified programming language
     *
     * @param programmingLanguage The programming language for which a build plan should get created
     * @return The configuration builder for the specified language
     * @see JavaJenkinsBuildPlanCreator
     */
    public JenkinsXmlConfigBuilder builderFor(ProgrammingLanguage programmingLanguage) {
        switch (programmingLanguage) {
            case JAVA:
                return javaJenkinsBuildPlanCreator;
            case PYTHON:
                return pythonJenkinsBuildPlanCreator;
            case C:
                return cJenkinsBuildPlanCreator;
            case HASKELL:
                return haskellJenkinsBuildPlanCreator;
            default:
                throw new IllegalArgumentException("Unsupported programming language for new Jenkins job!");
        }
    }
}
