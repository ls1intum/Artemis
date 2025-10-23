import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { QuizExamSubmissionComponent } from 'app/exam/overview/exercises/quiz/quiz-exam-submission.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockInstance, MockPipe, MockProvider } from 'ng-mocks';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { provideRouter } from '@angular/router';
import { ExerciseSaveButtonComponent } from 'app/exam/overview/exercises/exercise-save-button/exercise-save-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { By } from '@angular/platform-browser';
import { QuizConfiguration } from 'app/quiz/shared/entities/quiz-configuration.model';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { ImageComponent } from 'app/shared/image/image.component';
import { captureException } from '@sentry/angular';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import * as QuizStepWizardUtil from 'app/quiz/shared/questions/quiz-stepwizard.util';

jest.mock('@sentry/angular', () => ({
    captureException: jest.fn(),
}));

describe('QuizExamSubmissionComponent', () => {
    MockInstance(DragAndDropQuestionComponent, 'secureImageComponent', signal({} as ImageComponent));

    let fixture: ComponentFixture<QuizExamSubmissionComponent>;
    let component: QuizExamSubmissionComponent;

    let quizSubmission: QuizSubmission;
    let multipleChoiceQuestion: MultipleChoiceQuestion;
    let dragAndDropQuestion: DragAndDropQuestion;
    let shortAnswerQuestion: ShortAnswerQuestion;

    let quizService: any;

    beforeEach(async () => {
        quizSubmission = new QuizSubmission();
        multipleChoiceQuestion = new MultipleChoiceQuestion();
        multipleChoiceQuestion.id = 1;
        multipleChoiceQuestion.answerOptions = [];
        dragAndDropQuestion = new DragAndDropQuestion();
        dragAndDropQuestion.id = 2;
        shortAnswerQuestion = new ShortAnswerQuestion();
        shortAnswerQuestion.id = 3;
        shortAnswerQuestion.text = 'Short answer question text';

        await TestBed.configureTestingModule({
            declarations: [
                QuizExamSubmissionComponent,
                MockComponent(DragAndDropQuestionComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(MultipleChoiceQuestionComponent),
                MockComponent(ShortAnswerQuestionComponent),
                MockComponent(ExerciseSaveButtonComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [provideRouter([]), MockProvider(ArtemisQuizService)],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizExamSubmissionComponent);
        component = fixture.componentInstance;
        quizService = TestBed.inject(ArtemisQuizService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        const quizServiceSpy = jest.spyOn(quizService, 'randomizeOrder');

        const quizConfiguration: QuizConfiguration = { quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion] };
        fixture.componentRef.setInput('studentSubmission', quizSubmission);
        fixture.componentRef.setInput('quizConfiguration', quizConfiguration);
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
        const quizConfiguration: QuizConfiguration = { quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion] };
        fixture.componentRef.setInput('quizConfiguration', quizConfiguration);

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
        fixture.componentRef.setInput('studentSubmission', quizSubmission);

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
        expect(component.studentSubmission().isSynced).toBeFalse();
        /**
         * Return the negated value of isSynced when there are unsaved changes
         */
        expect(component.hasUnsavedChanges()).toBeTrue();
    });

    it('should set answerOptions/mappings/submitted texts to empty array when not submitted answer', () => {
        const quizConfiguration: QuizConfiguration = { quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion] };
        fixture.componentRef.setInput('quizConfiguration', quizConfiguration);
        fixture.componentRef.setInput('studentSubmission', quizSubmission);

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

        fixture.componentRef.setInput('quizConfiguration', {});
        fixture.componentRef.setInput('studentSubmission', quizSubmission);

        component.navigateToQuestion(1);
        fixture.detectChanges();

        expect(getElementByIdMock).toHaveBeenCalledWith(QUIZ_QUESTION_ID);
        expect(scrollIntoViewSpy).toHaveBeenCalled();
    });

    it('should capture exception when element is not found', () => {
        const questionId = 1;
        jest.spyOn(document, 'getElementById').mockReturnValue(null);

        component.navigateToQuestion(questionId);

        expect(captureException).toHaveBeenCalledWith('navigateToQuestion: element not found for questionId ' + questionId);
    });

    it('should highlight the correct quiz question', () => {
        const addTemporaryHighlightToQuestionSpy = jest.spyOn(QuizStepWizardUtil, 'addTemporaryHighlightToQuestion');
        const mockQuestion: QuizQuestion = {
            id: 1,
            type: QuizQuestionType.MULTIPLE_CHOICE,
            points: 1,
            randomizeOrder: false,
            invalid: false,
            exportQuiz: false,
        };
        const quizConfiguration: QuizConfiguration = { quizQuestions: [mockQuestion] };
        fixture.componentRef.setInput('quizConfiguration', quizConfiguration);

        component['highlightQuizQuestion'](1);

        expect(addTemporaryHighlightToQuestionSpy).toHaveBeenCalledWith(mockQuestion);
    });

    it('should not highlight if question is not found', () => {
        const addTemporaryHighlightToQuestionSpy = jest.spyOn(QuizStepWizardUtil, 'addTemporaryHighlightToQuestion');
        const quizConfiguration: QuizConfiguration = { quizQuestions: [] };
        fixture.componentRef.setInput('quizConfiguration', quizConfiguration);

        component['highlightQuizQuestion'](1);

        expect(addTemporaryHighlightToQuestionSpy).not.toHaveBeenCalled();
    });

    it('should not highlight if quizQuestions is undefined', () => {
        const addTemporaryHighlightToQuestionSpy = jest.spyOn(QuizStepWizardUtil, 'addTemporaryHighlightToQuestion');
        const quizConfiguration: QuizConfiguration = { quizQuestions: undefined };
        fixture.componentRef.setInput('quizConfiguration', quizConfiguration);

        component['highlightQuizQuestion'](1);

        expect(addTemporaryHighlightToQuestionSpy).not.toHaveBeenCalled();
    });

    it('should create multiple choice submission from users selection', () => {
        const quizConfiguration: QuizConfiguration = { quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion] };
        fixture.componentRef.setInput('quizConfiguration', quizConfiguration);
        fixture.componentRef.setInput('studentSubmission', new QuizSubmission());

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

        expect(component.studentSubmission().submittedAnswers?.length).toBe(3);
        expect(JSON.stringify(component.studentSubmission().submittedAnswers)).toEqual(
            JSON.stringify([multipleChoiceSubmittedAnswer, dragAndDropSubmittedAnswer, shortAnswerSubmittedAnswer]),
        );
    });

    it('should parse the answers from the submission version', () => {
        const submissionVersion = {
            content:
                '[ {\r\n  "quizQuestion" : {\r\n    "type" : "drag-and-drop",\r\n    "id" : 2,\r\n    "title" : "dnd image",\r\n    "text" : "Enter your long question if needed",\r\n    "hint" : "Add a hint here (visible during the quiz via ?-Button)",\r\n    "points" : 1,\r\n    "scoringType" : "PROPORTIONAL_WITH_PENALTY",\r\n    "randomizeOrder" : true,\r\n    "invalid" : false,\r\n    "backgroundFilePath" : "/api/core/files/drag-and-drop/backgrounds/14/DragAndDropBackground_2023-07-08T19-35-26-953_a3265da6.jpg",\r\n    "dropLocations" : [ {\r\n      "id" : 12,\r\n      "posX" : 45.0,\r\n      "posY" : 120.0,\r\n      "width" : 62.0,\r\n      "height" : 52.0,\r\n      "invalid" : false\r\n    } ],\r\n    "dragItems" : [ {\r\n      "id" : 11,\r\n      "pictureFilePath" : "/api/files/drag-and-drop/drag-items/11/DragItem_2023-07-08T19-35-26-956_2ffe94ba.jpg",\r\n      "invalid" : false\r\n    } ]\r\n  },\r\n  "mappings" : [ {\r\n    "invalid" : false,\r\n    "dragItem" : {\r\n      "id" : 11,\r\n      "pictureFilePath" : "/api/files/drag-and-drop/drag-items/11/DragItem_2023-07-08T19-35-26-956_2ffe94ba.jpg",\r\n      "invalid" : false\r\n    },\r\n    "dropLocation" : {\r\n      "id" : 12,\r\n      "posX" : 45.0,\r\n      "posY" : 120.0,\r\n      "width" : 62.0,\r\n      "height" : 52.0,\r\n      "invalid" : false\r\n    }\r\n  } ]\r\n} ]',
        } as unknown as SubmissionVersion;
        const quizExercise = new QuizExercise(new Course(), undefined);
        quizExercise.quizQuestions = [dragAndDropQuestion];
        fixture.componentRef.setInput('studentSubmission', new ModelingSubmission());
        fixture.componentRef.setInput('exercise', quizExercise);
        fixture.componentRef.setInput('quizConfiguration', { quizQuestions: [dragAndDropQuestion] });
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
                '[ {\r\n  "quizQuestion" : {\r\n    "type" : "drag-and-drop",\r\n    "id" : 2,\r\n    "title" : "dnd image",\r\n    "text" : "Enter your long question if needed",\r\n    "hint" : "Add a hint here (visible during the quiz via ?-Button)",\r\n    "points" : 1,\r\n    "scoringType" : "PROPORTIONAL_WITH_PENALTY",\r\n    "randomizeOrder" : true,\r\n    "invalid" : false,\r\n    "backgroundFilePath" : "/api/core/files/drag-and-drop/backgrounds/14/DragAndDropBackground_2023-07-08T19-35-26-953_a3265da6.jpg",\r\n    "dropLocations" : [ {\r\n      "id" : 12,\r\n      "posX" : 45.0,\r\n      "posY" : 120.0,\r\n      "width" : 62.0,\r\n      "height" : 52.0,\r\n      "invalid" : false\r\n    } ],\r\n    "dragItems" : [ {\r\n      "id" : 11,\r\n      "pictureFilePath" : "/api/files/drag-and-drop/drag-items/11/DragItem_2023-07-08T19-35-26-956_2ffe94ba.jpg",\r\n      "invalid" : false\r\n    } ]\r\n  },\r\n  "mappings" : [ {\r\n    "invalid" : false,\r\n    "dragItem" : {\r\n      "id" : 11,\r\n      "pictureFilePath" : "/api/files/drag-and-drop/drag-items/11/DragItem_2023-07-08T19-35-26-956_2ffe94ba.jpg",\r\n      "invalid" : false\r\n    },\r\n    "dropLocation" : {\r\n      "id" : 12,\r\n      "posX" : 45.0,\r\n      "posY" : 120.0,\r\n      "width" : 62.0,\r\n      "height" : 52.0,\r\n      "invalid" : false\r\n    }\r\n  } ]\r\n} ]',
        } as unknown as SubmissionVersion;
        const quizExercise = new QuizExercise(new Course(), undefined);
        quizExercise.quizQuestions = [dragAndDropQuestion];
        fixture.componentRef.setInput('studentSubmission', new ModelingSubmission());
        fixture.componentRef.setInput('exercise', quizExercise);
        fixture.componentRef.setInput('quizConfiguration', { quizQuestions: [dragAndDropQuestion] });
        component.setSubmissionVersion(submissionVersion);
        fixture.detectChanges();
        const saveExerciseSpy = jest.spyOn(component, 'notifyTriggerSave');
        const saveButton = fixture.debugElement.query(By.directive(ExerciseSaveButtonComponent));
        saveButton.triggerEventHandler('save', null);
        expect(saveExerciseSpy).toHaveBeenCalledOnce();
    });
});
