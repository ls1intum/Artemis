import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
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

describe('QuizExamSubmissionComponent', () => {
    let fixture: ComponentFixture<QuizExamSubmissionComponent>;
    let component: QuizExamSubmissionComponent;

    let quizSubmission: QuizSubmission;
    let exercise: QuizExercise;
    let multipleChoiceQuestion: MultipleChoiceQuestion;
    let dragAndDropQuestion: DragAndDropQuestion;
    let shortAnswerQuestion: ShortAnswerQuestion;

    let quizService: any;

    beforeEach(() => {
        quizSubmission = new QuizSubmission();
        exercise = new QuizExercise(new Course(), new ExerciseGroup());
        multipleChoiceQuestion = new MultipleChoiceQuestion();
        multipleChoiceQuestion.id = 1;
        dragAndDropQuestion = new DragAndDropQuestion();
        dragAndDropQuestion.id = 2;
        shortAnswerQuestion = new ShortAnswerQuestion();
        shortAnswerQuestion.id = 3;

        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [
                QuizExamSubmissionComponent,
                MockDirective(NgbTooltip),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(MultipleChoiceQuestionComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockComponent(ShortAnswerQuestionComponent),
            ],
            providers: [MockProvider(ArtemisQuizService)],
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

        exercise.quizQuestions = [multipleChoiceQuestion, dragAndDropQuestion];
        component.exercise = exercise;
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
        exercise.quizQuestions = [multipleChoiceQuestion, dragAndDropQuestion];
        component.exercise = exercise;

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
        expect(component.getExercise()).toEqual(exercise);

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
        exercise.quizQuestions = [multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion];
        component.exercise = exercise;

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
        const element = document.createElement('exam-navigation-bar');
        const getNavigationStub = jest.spyOn(document, 'getElementById').mockReturnValue(element);

        const yOffsetRect = element.getBoundingClientRect() as DOMRect;
        const yOffsetStub = jest.spyOn(element, 'getBoundingClientRect').mockReturnValue(yOffsetRect);

        const windowSpy = jest.spyOn(window, 'scrollTo');

        component.navigateToQuestion(1);
        component.exercise = exercise;
        fixture.detectChanges();
        expect(getNavigationStub).toHaveBeenCalled();
        expect(yOffsetStub).toHaveBeenCalled();
        expect(windowSpy).toHaveBeenCalled();
    });

    it('should create multiple choice submission from users selection', () => {
        exercise.quizQuestions = [multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion];
        component.studentSubmission = new QuizSubmission();
        component.exercise = exercise;

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
});
