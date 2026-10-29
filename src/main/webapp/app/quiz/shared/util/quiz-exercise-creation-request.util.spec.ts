import { describe, expect, it } from 'vitest';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { toQuizExerciseCreate } from 'app/quiz/shared/util/quiz-exercise-creation-request.util';

describe('toQuizExerciseCreate', () => {
    it('should send no ids and keep every reference pointing at the part that carries it', () => {
        const dragItem = new DragItem();
        dragItem.tempID = 2200;
        const savedDropLocation = new DropLocation();
        savedDropLocation.id = 31;
        savedDropLocation.tempID = 3100;
        const mapping = new DragAndDropMapping(dragItem, savedDropLocation);
        mapping.id = 41;

        const question = new DragAndDropQuestion();
        question.id = 11;
        question.dragItems = [dragItem];
        question.dropLocations = [savedDropLocation];
        question.correctMappings = [mapping];
        const exercise = new QuizExercise(undefined, undefined);
        exercise.quizQuestions = [question];

        const sent = JSON.parse(JSON.stringify(toQuizExerciseCreate(exercise)));
        const sentQuestion = sent.quizQuestions[0];

        expect(sentQuestion).not.toHaveProperty('id');
        expect(sentQuestion.dragItems[0]).toEqual({ tempID: 2200 });
        expect(sentQuestion.dropLocations[0]).toEqual({ tempID: 31, posX: 0, posY: 0, width: 0, height: 0 });
        expect(sentQuestion.correctMappings[0]).toEqual({ dragItemTempId: 2200, dropLocationTempId: 31 });
    });

    it('should fall back to the editor defaults for a quiz that lacks them', () => {
        const exercise = new QuizExercise(undefined, undefined);
        exercise.quizMode = undefined;
        exercise.quizQuestions = [new DragAndDropQuestion()];

        const request = toQuizExerciseCreate(exercise);

        expect(request.quizMode).toBe('SYNCHRONIZED');
        expect(request.quizQuestions[0].scoringType).toBe('PROPORTIONAL_WITH_PENALTY');
    });
});
