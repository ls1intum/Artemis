package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.IrisGPT3_5RequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.iris.IrisTemplateRepository;
import de.tum.in.www1.artemis.service.connectors.iris.IrisModel;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;

@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "bamboo", "bitbucket", "jira", "ldap", "scheduling", "athene", "apollon", "iris-gpt3_5" })
public class AbstractIrisIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    protected CourseRepository courseRepository;

    @Autowired
    protected IrisSettingsService irisSettingsService;

    @Autowired
    protected IrisTemplateRepository irisTemplateRepository;

    @Autowired
    @Qualifier("irisGPT3_5RequestMockProvider")
    protected IrisGPT3_5RequestMockProvider gpt35RequestMockProvider;

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

    @BeforeEach
    void setup() {
        gpt35RequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        gpt35RequestMockProvider.reset();
    }

    protected void activateIrisGlobally() {
        var globalSettings = irisSettingsService.getGlobalSettings();
        globalSettings.getIrisChatSettings().setEnabled(true);
        globalSettings.getIrisChatSettings().setPreferredModel(IrisModel.GPT35);
        globalSettings.getIrisHestiaSettings().setEnabled(true);
        globalSettings.getIrisHestiaSettings().setPreferredModel(IrisModel.GPT35);
        irisSettingsService.saveGlobalIrisSettings(globalSettings);
    }

    protected void activateIrisFor(Course course) {
        var courseWithSettings = irisSettingsService.addDefaultIrisSettingsTo(course);
        courseWithSettings.getIrisSettings().getIrisChatSettings().setEnabled(true);
        courseWithSettings.getIrisSettings().getIrisChatSettings().setTemplate(createDummyTemplate());
        courseWithSettings.getIrisSettings().getIrisChatSettings().setPreferredModel(IrisModel.GPT35);
        courseWithSettings.getIrisSettings().getIrisHestiaSettings().setEnabled(true);
        courseWithSettings.getIrisSettings().getIrisHestiaSettings().setTemplate(createDummyTemplate());
        courseWithSettings.getIrisSettings().getIrisHestiaSettings().setPreferredModel(IrisModel.GPT35);
        courseRepository.save(courseWithSettings);
    }

    protected void activateIrisFor(ProgrammingExercise exercise) {
        var exerciseWithSettings = irisSettingsService.addDefaultIrisSettingsTo(exercise);
        exerciseWithSettings.getIrisSettings().getIrisChatSettings().setEnabled(true);
        exerciseWithSettings.getIrisSettings().getIrisChatSettings().setTemplate(createDummyTemplate());
        exerciseWithSettings.getIrisSettings().getIrisChatSettings().setPreferredModel(IrisModel.GPT35);
        programmingExerciseRepository.save(exerciseWithSettings);
    }

    protected IrisTemplate createDummyTemplate() {
        var template = new IrisTemplate();
        template.setContent("Hello World");
        return template;
    }

    protected void verifyMessageWasSentOverWebsocket(String user, Long sessionId, String message) throws InterruptedException {
        Thread.sleep(1000);
        verify(websocketMessagingService, times(1)).sendMessageToUser(eq(user), eq("/topic/iris/sessions/" + sessionId), ArgumentMatchers.assertArg(object -> {
            if (object instanceof IrisMessage irisMessage) {
                var contents = irisMessage.getContent();
                assertThat(contents).hasSize(1);
                var irisMessageContent = contents.get(0);
                assertThat(irisMessageContent.getTextContent()).isEqualTo(message);
            }
        }));
    }
}
