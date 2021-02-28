import * as chai from 'chai';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslatePipe } from '@ngx-translate/core';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Course } from 'app/entities/course.model';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';

chai.use(sinonChai);
const expect = chai.expect;

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
            imports: [RouterTestingModule.withRoutes([]), MockModule(ArtemisQuizQuestionTypesModule), MockModule(NgbModule)],
            declarations: [QuizExamSubmissionComponent, MockPipe(TranslatePipe), MockComponent(IncludedInScoreBadgeComponent)],
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
        sinon.restore();
    });

    it('should initialize', () => {
        const quizServiceSpy = sinon.spy(quizService, 'randomizeOrder');

        exercise.quizQuestions = [multipleChoiceQuestion, dragAndDropQuestion];
        component.exercise = exercise;
        fixture.detectChanges();

        expect(fixture).to.be.ok;
        expect(quizServiceSpy.calledOnce);
        expect(component.selectedAnswerOptions.has(1)).to.equal(true);
        expect(component.selectedAnswerOptions.size).to.equal(1);
        expect(component.dragAndDropMappings.has(2)).to.equal(true);
        expect(component.dragAndDropMappings.size).to.equal(1);
        expect(component.shortAnswerSubmittedTexts.size).to.equal(0);
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

        expect(JSON.stringify(component.selectedAnswerOptions.get(1))).to.equal(JSON.stringify([multipleChoiceSelectedOptions]));
        expect(JSON.stringify(component.dragAndDropMappings.get(2))).to.equal(JSON.stringify([dragAndDropMapping]));
        expect(component.shortAnswerSubmittedTexts.size).to.equal(0);

        /**
         * Test the return value of the getSubmission and getExercise
         */
        expect(component.getSubmission()).to.equal(quizSubmission);
        expect(component.getExercise()).to.equal(exercise);

        /**
         * Change the isSynced value of studentSubmission to false when selection changed
         */
        component.onSelectionChanged();
        expect(component.studentSubmission.isSynced).to.equal(false);
        /**
         * Return the negated value of isSynced when there are unsaved changes
         */
        expect(component.hasUnsavedChanges()).to.equal(true);
    });

    it('should set answerOptions/mappings/submitted texts to empty array when not submitted answer', () => {
        exercise.quizQuestions = [multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion];
        component.exercise = exercise;

        component.updateViewFromSubmission();
        fixture.detectChanges();

        expect(JSON.stringify(component.selectedAnswerOptions.get(1))).to.equal(JSON.stringify([]));
        expect(component.selectedAnswerOptions.has(1)).to.equal(true);
        expect(JSON.stringify(component.dragAndDropMappings.get(2))).to.equal(JSON.stringify([]));
        expect(component.dragAndDropMappings.has(2)).to.equal(true);

        expect(component.shortAnswerSubmittedTexts.size).to.equal(1);
        expect(component.shortAnswerSubmittedTexts.has(3)).to.equal(true);
    });

    it('should trigger navigation towards the corrensponding question of the quiz', () => {
        const element = document.createElement('exam-navigation-bar');
        const getNavigationStub = stub(document, 'getElementById').returns(element);

        const yOffsetRect = element.getBoundingClientRect() as DOMRect;
        const yOffsetStub = stub(element, 'getBoundingClientRect').returns(yOffsetRect);

        const windowSpy = sinon.spy(window, 'scrollTo');

        component.navigateToQuestion(1);
        component.exercise = exercise;
        fixture.detectChanges();
        expect(getNavigationStub).to.have.been.called;
        expect(yOffsetStub).to.have.been.called;
        expect(windowSpy).to.have.been.called;
    });

    it('should create multiple choice submission from users selection ', () => {
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

        expect(component.studentSubmission.submittedAnswers?.length).to.equal(3);
        expect(JSON.stringify(component.studentSubmission.submittedAnswers)).to.equal(
            JSON.stringify([multipleChoiceSubmittedAnswer, dragAndDropSubmittedAnswer, shortAnswerSubmittedAnswer]),
        );
    });
});
