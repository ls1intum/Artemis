package de.tum.in.www1.artemis.util;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import java.util.*;

import javax.jms.JMSException;
import javax.jms.Message;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.MessageBrokerConstants;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.ExerciseUnitRepository;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;

@SpringBootTest
@Component
public class JmsMessageMockProvider {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Mock
    private Message message;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private LearningGoalRepository learningGoalRepository;

    public JmsMessageMockProvider() {
        this.message = Mockito.mock(Message.class);
    }

    public void mockRemoveExerciseUnits() throws JMSException {
        doAnswer(invocation -> {
            long exerciseId = invocation.getArgument(1);
            List<ExerciseUnit> exerciseUnits = this.exerciseUnitRepository.findByIdWithLearningGoalsBidirectional(exerciseId);
            for (ExerciseUnit exerciseUnit : exerciseUnits) {
                Set<LearningGoal> associatedLearningGoals = new HashSet<>(exerciseUnit.getLearningGoals());
                for (LearningGoal learningGoal : associatedLearningGoals) {
                    Optional<LearningGoal> learningGoalFromDbOptional = learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoal.getId());
                    if (learningGoalFromDbOptional.isPresent()) {
                        LearningGoal learningGoalFromDb = learningGoalFromDbOptional.get();
                        learningGoalFromDb.removeLectureUnit(exerciseUnit);
                        learningGoalRepository.save(learningGoalFromDb);
                    }
                }
                Lecture lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsElseThrow(exerciseUnit.getLecture().getId());
                List<LectureUnit> lectureUnitsUpdated = new ArrayList<>();
                for (LectureUnit unit : lecture.getLectureUnits()) {
                    if (Objects.nonNull(unit) && !unit.getId().equals(exerciseUnit.getId())) {
                        lectureUnitsUpdated.add(unit);
                    }
                }
                lecture.getLectureUnits().clear();
                lecture.getLectureUnits().addAll(lectureUnitsUpdated);
                lectureRepository.save(lecture);
            }
            return message;
        }).when(jmsTemplate).convertAndSend(eq(MessageBrokerConstants.LECTURE_QUEUE_REMOVE_EXERCISE_UNITS), anyLong(), any());

        doReturn(message).when(jmsTemplate).receiveSelected(anyString(), anyString());
        doReturn(true).when(message).getBody(Boolean.class);
    }

    public void mockDeleteLectures() throws JMSException {
        doAnswer(invocation -> {
            Set<Lecture> lectures = invocation.getArgument(1);
            for (Lecture lecture : lectures) {
                Optional<Lecture> lectureToDeleteOptional = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lecture.getId());
                Lecture lectureToDelete = lectureToDeleteOptional.get();
                List<LectureUnit> lectureUnits = lectureToDelete.getLectureUnits().stream().filter(Objects::nonNull).toList();
                for (LectureUnit lectureUnit : lectureUnits) {
                    Optional<LectureUnit> lectureUnitFromDbOptional = lectureUnitRepository.findByIdWithLearningGoalsBidirectional(lectureUnit.getId());
                    if (lectureUnitFromDbOptional.isPresent()) {
                        Set<LearningGoal> learningGoals = new HashSet<>(lectureUnit.getLearningGoals());
                        for (LearningGoal learningGoal : learningGoals) {
                            Optional<LearningGoal> learningGoalFromDbOptional = learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoal.getId());
                            if (learningGoalFromDbOptional.isPresent()) {
                                LearningGoal learningGoalFromDb = learningGoalFromDbOptional.get();
                                learningGoalFromDb.removeLectureUnit(lectureUnit);
                                learningGoalRepository.save(learningGoalFromDb);
                            }
                        }
                    }
                }
                lectureRepository.deleteById(lecture.getId());
            }
            return message;
        }).when(jmsTemplate).convertAndSend(eq(MessageBrokerConstants.LECTURE_QUEUE_DELETE_LECTURES), any(), any());

        doReturn(message).when(jmsTemplate).receiveSelected(anyString(), anyString());
        doReturn(true).when(message).getBody(Boolean.class);
    }

    public void mockFilterLectures(Set<Lecture> lectures) throws JMSException {
        doReturn(message).when(jmsTemplate).receiveSelected(anyString(), anyString());
        doReturn(lectures).when(message).getBody(Set.class);
    }
}
