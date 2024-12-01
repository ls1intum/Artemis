import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { NgbTooltipMocksModule } from '../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Course } from 'app/entities/course.model';
import { provideRouter } from '@angular/router';
import { ExerciseSaveButtonComponent } from 'app/exam/participate/exercises/exercise-save-button/exercise-save-button.component';
import { TranslateDirective } from '../../../../../../../main/webapp/app/shared/language/translate.directive';
import { By } from '@angular/platform-browser';

describe('QuizExamSubmissionComponent', () => {
    let fixture: ComponentFixture<QuizExamSubmissionComponent>;
    let component: QuizExamSubmissionComponent;

    let quizSubmission: QuizSubmission;
    let multipleChoiceQuestion: MultipleChoiceQuestion;
    let dragAndDropQuestion: DragAndDropQuestion;
    let shortAnswerQuestion: ShortAnswerQuestion;

    let quizService: any;

    beforeEach(() => {
        quizSubmission = new QuizSubmission();
        multipleChoiceQuestion = new MultipleChoiceQuestion();
        multipleChoiceQuestion.id = 1;
        dragAndDropQuestion = new DragAndDropQuestion();
        dragAndDropQuestion.id = 2;
        shortAnswerQuestion = new ShortAnswerQuestion();
        shortAnswerQuestion.id = 3;

        return TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule],
            declarations: [
                QuizExamSubmissionComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(MultipleChoiceQuestionComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockComponent(ShortAnswerQuestionComponent),
                MockComponent(ExerciseSaveButtonComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [provideRouter([]), MockProvider(ArtemisQuizService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizExamSubmissionComponent);
                component = fixture.componentInstance;
                quizService = TestBed.inject(ArtemisQuizService);
            });
    });
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const quizServiceSpy = jest.spyOn(quizService, 'randomizeOrder');

        component.quizConfiguration = { quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion] };
        fixture.detectChanges();

        expect(fixture).toBeDefined();
        expect(quizServiceSpy).toHaveBeenCalledOnce();
        expect(component.selectedAnswerOptions.has(1)).toBeTrue();
        expect(component.selectedAnswerOptions.size).toBe(1);
        expect(component.dragAndDropMappings.has(2)).toBeTrue();
        expect(component.dragAndDropMappings.size).toBe(1);
        expect(component.shortAnswerSubmittedTexts.size).toBe(0);
    });

    it('should update view from submission and fill the dictionary accordingly when submitted answer', () => {
        component.quizConfiguration = { quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion] };

        const multipleChoiceSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
        const multipleChoiceSelectedOptions = new AnswerOption();
        multipleChoiceSelectedOptions.id = 1;
        multipleChoiceSubmittedAnswer.id = 1;
        multipleChoiceSubmittedAnswer.quizQuestion = multipleChoiceQuestion;
        multipleChoiceSubmittedAnswer.selectedOptions = [multipleChoiceSelectedOptions];

        const dragAndDropSubmittedAnswer = new DragAndDropSubmittedAnswer();
        const dragAndDropMapping = new DragAndDropMapping(new DragItem(), new DropLocation());
        dragAndDropMapping.id = 2;
        dragAndDropSubmittedAnswer.id = 2;
        dragAndDropSubmittedAnswer.quizQuestion = dragAndDropQuestion;
        dragAndDropSubmittedAnswer.mappings = [dragAndDropMapping];
        quizSubmission.submittedAnswers = [multipleChoiceSubmittedAnswer, dragAndDropSubmittedAnswer];
        component.studentSubmission = quizSubmission;

        component.updateViewFromSubmission();
        fixture.detectChanges();

        expect(JSON.stringify(component.selectedAnswerOptions.get(1))).toEqual(JSON.stringify([multipleChoiceSelectedOptions]));
        expect(JSON.stringify(component.dragAndDropMappings.get(2))).toEqual(JSON.stringify([dragAndDropMapping]));
        expect(component.shortAnswerSubmittedTexts.size).toBe(0);

        /**
         * Test the return value of the getSubmission and getExercise
         */
        expect(component.getSubmission()).toEqual(quizSubmission);

        /**
         * Change the isSynced value of studentSubmission to false when selection changed
         */
        component.onSelectionChanged();
        expect(component.studentSubmission.isSynced).toBeFalse();
        /**
         * Return the negated value of isSynced when there are unsaved changes
         */
        expect(component.hasUnsavedChanges()).toBeTrue();
    });

    it('should set answerOptions/mappings/submitted texts to empty array when not submitted answer', () => {
        component.quizConfiguration = { quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion] };

        component.updateViewFromSubmission();
        fixture.detectChanges();

        expect(JSON.stringify(component.selectedAnswerOptions.get(1))).toEqual(JSON.stringify([]));
        expect(component.selectedAnswerOptions.has(1)).toBeTrue();
        expect(JSON.stringify(component.dragAndDropMappings.get(2))).toEqual(JSON.stringify([]));
        expect(component.dragAndDropMappings.has(2)).toBeTrue();

        expect(component.shortAnswerSubmittedTexts.size).toBe(1);
        expect(component.shortAnswerSubmittedTexts.has(3)).toBeTrue();
    });

    it('should trigger navigation towards the corrensponding question of the quiz', () => {
        const QUIZ_QUESTION_ID = 'question1';
        const scrollIntoViewSpy = jest.fn();

        const getElementByIdMock = jest.spyOn(document, 'getElementById').mockReturnValue({
            scrollIntoView: scrollIntoViewSpy,
        } as unknown as HTMLElement);

        component.quizConfiguration = {};
        component.navigateToQuestion(1);
        fixture.detectChanges();

        expect(getElementByIdMock).toHaveBeenCalledWith(QUIZ_QUESTION_ID);
        expect(scrollIntoViewSpy).toHaveBeenCalled();
    });

    it('should create multiple choice submission from users selection', () => {
        component.quizConfiguration = { quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion] };
        component.studentSubmission = new QuizSubmission();

        const multipleChoiceSelectedOptions = new AnswerOption();
        multipleChoiceSelectedOptions.id = 1;
        component.selectedAnswerOptions.set(1, [multipleChoiceSelectedOptions]);

        const dragAndDropMapping = new DragAndDropMapping(new DragItem(), new DropLocation());
        dragAndDropMapping.id = 2;
        component.dragAndDropMappings.set(2, [dragAndDropMapping]);

        const shortAnswerSubmittedText = new ShortAnswerSubmittedText();
        shortAnswerSubmittedText.id = 3;
        component.shortAnswerSubmittedTexts.set(3, [shortAnswerSubmittedText]);

        const multipleChoiceSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
        multipleChoiceSubmittedAnswer.quizQuestion = multipleChoiceQuestion;
        multipleChoiceSubmittedAnswer.selectedOptions = [multipleChoiceSelectedOptions];

        const dragAndDropSubmittedAnswer = new DragAndDropSubmittedAnswer();
        dragAndDropSubmittedAnswer.quizQuestion = dragAndDropQuestion;
        dragAndDropSubmittedAnswer.mappings = [dragAndDropMapping];
        dragAndDropQuestion.correctMappings = [dragAndDropMapping];

        const shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
        shortAnswerSubmittedAnswer.quizQuestion = shortAnswerQuestion;
        shortAnswerSubmittedAnswer.submittedTexts = [shortAnswerSubmittedText];

        component.updateSubmissionFromView();
        fixture.detectChanges();

        expect(component.studentSubmission.submittedAnswers?.length).toBe(3);
        expect(JSON.stringify(component.studentSubmission.submittedAnswers)).toEqual(
            JSON.stringify([multipleChoiceSubmittedAnswer, dragAndDropSubmittedAnswer, shortAnswerSubmittedAnswer]),
        );
    });

    it('should parse the answers from the submission version', () => {
        const submissionVersion = {
            content:
                '[ {\r\n  "quizQuestion" : {\r\n    "type" : "drag-and-drop",\r\n    "id" : 2,\r\n    "title" : "dnd image",\r\n    "text" : "Enter your long question if needed",\r\n    "hint" : "Add a hint here (visible during the quiz via ?-Button)",\r\n    "points" : 1,\r\n    "scoringType" : "PROPORTIONAL_WITH_PENALTY",\r\n    "randomizeOrder" : true,\r\n    "invalid" : false,\r\n    "backgroundFilePath" : "/api/files/drag-and-drop/backgrounds/14/DragAndDropBackground_2023-07-08T19-35-26-953_a3265da6.jpg",\r\n    "dropLocations" : [ {\r\n      "id" : 12,\r\n      "posX" : 45.0,\r\n      "posY" : 120.0,\r\n      "width" : 62.0,\r\n      "height" : 52.0,\r\n      "invalid" : false\r\n    } ],\r\n    "dragItems" : [ {\r\n      "id" : 11,\r\n      "pictureFilePath" : "/api/files/drag-and-drop/drag-items/11/DragItem_2023-07-08T19-35-26-956_2ffe94ba.jpg",\r\n      "invalid" : false\r\n    } ]\r\n  },\r\n  "mappings" : [ {\r\n    "invalid" : false,\r\n    "dragItem" : {\r\n      "id" : 11,\r\n      "pictureFilePath" : "/api/files/drag-and-drop/drag-items/11/DragItem_2023-07-08T19-35-26-956_2ffe94ba.jpg",\r\n      "invalid" : false\r\n    },\r\n    "dropLocation" : {\r\n      "id" : 12,\r\n      "posX" : 45.0,\r\n      "posY" : 120.0,\r\n      "width" : 62.0,\r\n      "height" : 52.0,\r\n      "invalid" : false\r\n    }\r\n  } ]\r\n} ]',
        } as unknown as SubmissionVersion;
        component.studentSubmission = new ModelingSubmission();
        component.exercise = new QuizExercise(new Course(), undefined);
        component.exercise.quizQuestions = [dragAndDropQuestion];
        component.quizConfiguration = { quizQuestions: [dragAndDropQuestion] };
        component.setSubmissionVersion(submissionVersion);
        fixture.detectChanges();
        expect(component.submissionVersion).toEqual(submissionVersion);
        expect(component.selectedAnswerOptions.size).toBe(0);
        expect(component.dragAndDropMappings.size).toBe(1);
        expect(component.shortAnswerSubmittedTexts.size).toBe(0);
    });

    it('should call triggerSave if save exercise button is clicked', () => {
        const submissionVersion = {
            content:
                '[ {\r\n  "quizQuestion" : {\r\n    "type" : "drag-and-drop",\r\n    "id" : 2,\r\n    "title" : "dnd image",\r\n    "text" : "Enter your long question if needed",\r\n    "hint" : "Add a hint here (visible during the quiz via ?-Button)",\r\n    "points" : 1,\r\n    "scoringType" : "PROPORTIONAL_WITH_PENALTY",\r\n    "randomizeOrder" : true,\r\n    "invalid" : false,\r\n    "backgroundFilePath" : "/api/files/drag-and-drop/backgrounds/14/DragAndDropBackground_2023-07-08T19-35-26-953_a3265da6.jpg",\r\n    "dropLocations" : [ {\r\n      "id" : 12,\r\n      "posX" : 45.0,\r\n      "posY" : 120.0,\r\n      "width" : 62.0,\r\n      "height" : 52.0,\r\n      "invalid" : false\r\n    } ],\r\n    "dragItems" : [ {\r\n      "id" : 11,\r\n      "pictureFilePath" : "/api/files/drag-and-drop/drag-items/11/DragItem_2023-07-08T19-35-26-956_2ffe94ba.jpg",\r\n      "invalid" : false\r\n    } ]\r\n  },\r\n  "mappings" : [ {\r\n    "invalid" : false,\r\n    "dragItem" : {\r\n      "id" : 11,\r\n      "pictureFilePath" : "/api/files/drag-and-drop/drag-items/11/DragItem_2023-07-08T19-35-26-956_2ffe94ba.jpg",\r\n      "invalid" : false\r\n    },\r\n    "dropLocation" : {\r\n      "id" : 12,\r\n      "posX" : 45.0,\r\n      "posY" : 120.0,\r\n      "width" : 62.0,\r\n      "height" : 52.0,\r\n      "invalid" : false\r\n    }\r\n  } ]\r\n} ]',
        } as unknown as SubmissionVersion;
        component.studentSubmission = new ModelingSubmission();
        component.exercise = new QuizExercise(new Course(), undefined);
        component.exercise.quizQuestions = [dragAndDropQuestion];
        component.quizConfiguration = { quizQuestions: [dragAndDropQuestion] };
        component.setSubmissionVersion(submissionVersion);
        fixture.detectChanges();
        const saveExerciseSpy = jest.spyOn(component, 'notifyTriggerSave');
        const saveButton = fixture.debugElement.query(By.directive(ExerciseSaveButtonComponent));
        saveButton.triggerEventHandler('save', null);
        expect(saveExerciseSpy).toHaveBeenCalledOnce();
    });
});
