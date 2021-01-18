import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { QuizExamSummaryComponent } from 'app/exam/participate/summary/exercises/quiz-exam-summary/quiz-exam-summary.component';
import { MockPipe } from 'ng-mocks/dist/lib/mock-pipe/mock-pipe';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { MockModule, MockProvider } from 'ng-mocks';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { Exam } from 'app/entities/exam.model';

chai.use(sinonChai);
const expect = chai.expect;

let multipleChoiceQuestion = { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE } as MultipleChoiceQuestion;
const wrongAnswerOption = { id: 1, isCorrect: false, question: multipleChoiceQuestion } as AnswerOption;
const correctAnswerOption = { id: 2, isCorrect: true, question: multipleChoiceQuestion } as AnswerOption;
multipleChoiceQuestion.answerOptions = [wrongAnswerOption, correctAnswerOption];

let dragAndDropQuestion = { id: 1, type: QuizQuestionType.DRAG_AND_DROP } as DragAndDropQuestion;
const dragItem = { id: 1, question: dragAndDropQuestion, text: 'dragItem' } as DragItem;
const dropLocation = { id: 1, question: dragAndDropQuestion, posX: 1, posY: 1, width: 1, height: 1 } as DropLocation;
const correctDragAndDropMapping = { id: 1, dragItemIndex: 1, dropLocationIndex: 1, dragItem, dropLocation, question: dragAndDropQuestion } as DragAndDropMapping;
dragAndDropQuestion.correctMappings = [correctDragAndDropMapping];
dragAndDropQuestion.dragItems = [dragItem];
dragAndDropQuestion.dropLocations = [dropLocation];

let shortAnswerQuestion = { id: 1, type: QuizQuestionType.SHORT_ANSWER } as ShortAnswerQuestion;
const shortAnswerSpot = { id: 1, width: 1, spotNr: 1, question: shortAnswerQuestion, posX: 1, posY: 1, tempID: 1 } as ShortAnswerSpot;
const shortAnswerSolution = { id: 1, text: 'solution', question: shortAnswerQuestion, posX: 1, posY: 1, tempID: 1 } as ShortAnswerSolution;
const correctShortAnswerMapping = {
    id: 1,
    shortAnswerSolutionIndex: 1,
    shortAnswerSpotIndex: 1,
    solution: shortAnswerSolution,
    spot: shortAnswerSpot,
    question: shortAnswerQuestion,
} as ShortAnswerMapping;
shortAnswerQuestion.correctMappings = [correctShortAnswerMapping];
shortAnswerQuestion.solutions = [shortAnswerSolution];
shortAnswerQuestion.spots = [shortAnswerSpot];

const studentParticipation = { id: 1 } as StudentParticipation;

const multipleChoiceSubmittedAnswer = { id: 1, selectedOptions: [correctAnswerOption], quizQuestion: multipleChoiceQuestion } as MultipleChoiceSubmittedAnswer;
const dragAndDropSubmittedAnswer = { id: 1, mappings: [correctDragAndDropMapping], quizQuestion: dragAndDropQuestion } as DragAndDropSubmittedAnswer;
let shortAnswerSubmittedAnswer = { id: 1, mappings: [correctDragAndDropMapping], quizQuestion: shortAnswerQuestion } as ShortAnswerSubmittedAnswer;
const shortAnswerSubmittedText = { id: 1, spot: shortAnswerSpot, text: 'solution', submittedAnswer: shortAnswerSubmittedAnswer } as ShortAnswerSubmittedText;
shortAnswerSubmittedAnswer.submittedTexts = [shortAnswerSubmittedText];

const submission = { id: 1, submittedAnswers: [multipleChoiceSubmittedAnswer, dragAndDropSubmittedAnswer, shortAnswerSubmittedAnswer], submitted: true } as QuizSubmission;
const exercise = { id: 1, studentParticipations: [studentParticipation], quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion] } as QuizExercise;

describe('QuizExamSummaryComponent', () => {
    let fixture: ComponentFixture<QuizExamSummaryComponent>;
    let component: QuizExamSummaryComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(ArtemisQuizQuestionTypesModule)],
            declarations: [QuizExamSummaryComponent, MockPipe(TranslatePipe)],
            providers: [MockProvider(TranslateService), MockProvider(QuizExerciseService), MockProvider(ArtemisServerDateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizExamSummaryComponent);
                component = fixture.componentInstance;
                component.exercise = exercise;
                component.submission = submission;
                component.resultsPublished = true;
                component.exam = { id: 1 } as Exam;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });
});
