package de.tum.cit.aet.artemis.iris.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class IrisChatSessionUtilService {

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    public IrisProgrammingExerciseChatSession createAndSaveProgrammingExerciseChatSessionForUser(ProgrammingExercise exercise, User user) {
        return irisSessionRepository.save(IrisChatSessionFactory.createProgrammingExerciseChatSessionForUser(exercise, user));
    }

}
