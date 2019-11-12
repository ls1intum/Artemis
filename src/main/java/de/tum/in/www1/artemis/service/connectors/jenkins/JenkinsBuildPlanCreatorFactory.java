package de.tum.in.www1.artemis.service.connectors.jenkins;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

@Component
public class JenkinsBuildPlanCreatorFactory {

    private final JavaJenkinsBuildPlanCreator javaJenkinsBuildPlanCreator;

    public JenkinsBuildPlanCreatorFactory(JavaJenkinsBuildPlanCreator javaJenkinsBuildPlanCreator) {
        this.javaJenkinsBuildPlanCreator = javaJenkinsBuildPlanCreator;
    }

    public JenkinsXmlConfigBuilder builderFor(ProgrammingLanguage programmingLanguage) {
        switch (programmingLanguage) {
        case JAVA:
            return javaJenkinsBuildPlanCreator;
        default:
            throw new IllegalArgumentException("Unsupported programming language for new Jenkins job!");
        }
    }
}
