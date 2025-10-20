package de.tum.cit.aet.artemis.quiz.service.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuestionSubtype;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.GeneratedMcQuestionDTO;

@Service
public class AiQuizImportService {

    private final QuizQuestionRepository quizQuestionRepo;

    private final QuizExerciseRepository quizExerciseRepo;

    private final AiAuditService audit;

    public AiQuizImportService(QuizQuestionRepository quizQuestionRepo, QuizExerciseRepository quizExerciseRepo, AiAuditService audit) {
        this.quizQuestionRepo = quizQuestionRepo;
        this.quizExerciseRepo = quizExerciseRepo;
        this.audit = audit;
    }

    public List<Long> importQuestions(QuizExercise exercise, List<GeneratedMcQuestionDTO> dtos, String username) {
        List<Long> createdIds = new ArrayList<>();
        for (var q : dtos) {
            ValidationUtils.enforceSubtypeConstraints(q); // <- our class in same package
            var mc = mapToEntity(q);
            mc.setExercise(exercise);                // <- correct setter
            quizQuestionRepo.save(mc);
            createdIds.add(mc.getId());
        }
        audit.logImport(username, exercise.getId(), dtos.size());
        return createdIds;
    }

    private MultipleChoiceQuestion mapToEntity(GeneratedMcQuestionDTO dto) {
        var mc = new MultipleChoiceQuestion();
        mc.setTitle(dto.title());
        mc.setText(dto.text());
        mc.setExplanation(dto.explanation());
        mc.setHint(dto.hint());

        List<AnswerOption> options = dto.options().stream().map(o -> {
            var ao = new AnswerOption();
            ao.setText(o.text());
            ao.setIsCorrect(o.correct());
            ao.setExplanation(o.feedback());
            ao.setHint("");
            return ao;
        }).toList();

        mc.setAnswerOptions(new ArrayList<>(options));
        mc.setRandomizeOrder(true);
        mc.setSingleChoice(dto.subtype() == AiQuestionSubtype.SINGLE_CORRECT || dto.subtype() == AiQuestionSubtype.TRUE_FALSE);
        return mc;
    }
}
