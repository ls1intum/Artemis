package de.tum.in.www1.artemis.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.web.rest.dto.QuizBatchJoinDTO;

@Service
public class QuizUtilService {

    @Autowired
    private RequestUtilService request;

    /**
     * Create, join and start a batch for student by tutor
     */
    public void prepareBatchForSubmitting(QuizExercise quizExercise, Authentication tutor, Authentication student) throws Exception {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        switch (quizExercise.getQuizMode()) {
            case SYNCHRONIZED -> {
            }
            case BATCHED -> {
                SecurityContextHolder.getContext().setAuthentication(tutor);
                var batch = request.putWithResponseBody("/api/quiz-exercises/" + quizExercise.getId() + "/add-batch", null, QuizBatch.class, HttpStatus.OK);
                request.put("/api/quiz-exercises/" + batch.getId() + "/start-batch", null, HttpStatus.OK);
                SecurityContextHolder.getContext().setAuthentication(student);
                request.postWithoutLocation("/api/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(batch.getPassword()), HttpStatus.OK, null);
            }
            case INDIVIDUAL -> {
                SecurityContextHolder.getContext().setAuthentication(student);
                request.postWithoutLocation("/api/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(null), HttpStatus.OK, null);
            }
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

}
