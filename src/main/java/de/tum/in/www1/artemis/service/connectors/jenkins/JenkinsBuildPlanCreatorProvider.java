package de.tum.in.www1.artemis.service.connectors.jenkins;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@Profile("jenkins")
@Component
public class JenkinsBuildPlanCreatorProvider {

    private final JenkinsBuildPlanCreator jenkinsBuildPlanCreator;

    public JenkinsBuildPlanCreatorProvider(JenkinsBuildPlanCreator jenkinsBuildPlanCreator) {
        this.jenkinsBuildPlanCreator = jenkinsBuildPlanCreator;
    }

    /**
     * Gives a Jenkins plan builder, that is able to build plan configurations for the specified programming language
     *
     * @param programmingLanguage The programming language for which a build plan should get created
     * @return The configuration builder for the specified language
     * @see JavaJenkinsBuildPlanCreator
     */
    public JenkinsXmlConfigBuilder builderFor(ProgrammingLanguage programmingLanguage) {
        return switch (programmingLanguage) {
            case JAVA, KOTLIN -> jenkinsBuildPlanCreator;
            case PYTHON, C, HASKELL -> jenkinsBuildPlanCreator;
            case VHDL -> throw new UnsupportedOperationException("VHDL templates are not available for Jenkins.");
            case ASSEMBLER -> throw new UnsupportedOperationException("Assembler templates are not available for Jenkins.");
            case SWIFT -> throw new UnsupportedOperationException("Swift templates are not available for Jenkins.");
        };
    }
}
