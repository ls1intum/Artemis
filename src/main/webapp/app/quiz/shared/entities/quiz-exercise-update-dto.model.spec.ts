import { describe, expect, it } from 'vitest';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { toQuizExerciseUpdateDTO } from './quiz-exercise-update-dto.model';

describe('QuizExerciseUpdateDTO', () => {
    it('should normalize persisted drag-and-drop ids for updates', () => {
        const dragItem = new DragItem();
        dragItem.id = 21;
        dragItem.tempID = 2100;
        dragItem.pictureFilePath = 'element-21.png';

        const dropLocation = new DropLocation();
        dropLocation.id = 31;
        dropLocation.tempID = 3100;
        dropLocation.posX = 10;
        dropLocation.posY = 20;
        dropLocation.width = 30;
        dropLocation.height = 40;

        const mapping = new DragAndDropMapping(dragItem, dropLocation);
        mapping.id = 41;

        const question = new DragAndDropQuestion();
        question.id = 11;
        question.title = 'DnD';
        question.points = 1;
        question.dragItems = [dragItem];
        question.dropLocations = [dropLocation];
        question.correctMappings = [mapping];

        const exercise = new QuizExercise(undefined, undefined);
        exercise.quizQuestions = [question];

        const dto = toQuizExerciseUpdateDTO(exercise);
        const dndQuestion = dto.quizQuestions?.[0];

        expect(dndQuestion).toEqual(
            expect.objectContaining({
                id: 11,
                type: 'drag-and-drop',
            }),
        );
        expect((dndQuestion as any).dragItems[0]).toEqual(expect.objectContaining({ id: 21, tempID: 21, pictureFilePath: 'element-21.png' }));
        expect((dndQuestion as any).dropLocations[0]).toEqual(expect.objectContaining({ id: 31, tempID: 31, posX: 10, posY: 20, width: 30, height: 40 }));
        expect((dndQuestion as any).correctMappings[0]).toEqual(expect.objectContaining({ id: 41, dragItemTempId: 21, dropLocationTempId: 31 }));
    });

    it('should preserve temp ids for new drag-and-drop elements', () => {
        const dragItem = new DragItem();
        dragItem.tempID = 2200;

        const dropLocation = new DropLocation();
        dropLocation.tempID = 3200;
        dropLocation.posX = 15;
        dropLocation.posY = 25;
        dropLocation.width = 35;
        dropLocation.height = 45;

        const mapping = new DragAndDropMapping(dragItem, dropLocation);

        const question = new DragAndDropQuestion();
        question.title = 'DnD';
        question.points = 1;
        question.dragItems = [dragItem];
        question.dropLocations = [dropLocation];
        question.correctMappings = [mapping];

        const exercise = new QuizExercise(undefined, undefined);
        exercise.quizQuestions = [question];

        const dto = toQuizExerciseUpdateDTO(exercise);
        const dndQuestion = dto.quizQuestions?.[0] as any;

        expect(dndQuestion.dragItems[0]).toEqual(expect.objectContaining({ id: undefined, tempID: 2200 }));
        expect(dndQuestion.dropLocations[0]).toEqual(expect.objectContaining({ id: undefined, tempID: 3200 }));
        expect(dndQuestion.correctMappings[0]).toEqual(expect.objectContaining({ dragItemTempId: 2200, dropLocationTempId: 3200 }));
    });

    it('should preserve multiple-choice option ids on update', () => {
        const answerOption = new AnswerOption();
        answerOption.id = 6;
        answerOption.text = 'A';
        answerOption.isCorrect = true;

        const question = new MultipleChoiceQuestion();
        question.id = 5;
        question.title = 'MC';
        question.points = 1;
        question.answerOptions = [answerOption];

        const exercise = new QuizExercise(undefined, undefined);
        exercise.quizQuestions = [question];

        const dto = toQuizExerciseUpdateDTO(exercise);
        const mcQuestion = dto.quizQuestions?.[0] as any;

        expect(mcQuestion).toEqual(expect.objectContaining({ id: 5, type: 'multiple-choice' }));
        expect(mcQuestion.answerOptions[0]).toEqual(expect.objectContaining({ id: 6, text: 'A', isCorrect: true }));
    });
});
