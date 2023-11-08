import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Exam } from 'app/entities/exam.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { QuizExamSummaryComponent } from 'app/exam/participate/summary/exercises/quiz-exam-summary/quiz-exam-summary.component';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';

const multipleChoiceQuestion = { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE } as MultipleChoiceQuestion;
const wrongAnswerOption = { id: 1, isCorrect: false, question: multipleChoiceQuestion } as AnswerOption;
const correctAnswerOption = { id: 2, isCorrect: true, question: multipleChoiceQuestion } as AnswerOption;
multipleChoiceQuestion.answerOptions = [wrongAnswerOption, correctAnswerOption];

const dragAndDropQuestion = { id: 2, type: QuizQuestionType.DRAG_AND_DROP } as DragAndDropQuestion;
const dragItem = { id: 1, question: dragAndDropQuestion, text: 'dragItem' } as DragItem;
const dropLocation = { id: 1, question: dragAndDropQuestion, posX: 1, posY: 1, width: 1, height: 1 } as DropLocation;
const correctDragAndDropMapping = { id: 1, dragItemIndex: 1, dropLocationIndex: 1, dragItem, dropLocation, question: dragAndDropQuestion } as DragAndDropMapping;
dragAndDropQuestion.correctMappings = [correctDragAndDropMapping];
dragAndDropQuestion.dragItems = [dragItem];
dragAndDropQuestion.dropLocations = [dropLocation];

const shortAnswerQuestion = { id: 3, type: QuizQuestionType.SHORT_ANSWER } as ShortAnswerQuestion;
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

const multipleChoiceSubmittedAnswer = { id: 1, selectedOptions: [correctAnswerOption], quizQuestion: multipleChoiceQuestion, scoreInPoints: 1 } as MultipleChoiceSubmittedAnswer;
const dragAndDropSubmittedAnswer = { id: 1, mappings: [correctDragAndDropMapping], quizQuestion: dragAndDropQuestion, scoreInPoints: 1 } as DragAndDropSubmittedAnswer;
const shortAnswerSubmittedAnswer = { id: 1, mappings: [correctDragAndDropMapping], quizQuestion: shortAnswerQuestion, scoreInPoints: 1 } as ShortAnswerSubmittedAnswer;
const shortAnswerSubmittedText = { id: 1, spot: shortAnswerSpot, text: 'solution', submittedAnswer: shortAnswerSubmittedAnswer } as ShortAnswerSubmittedText;
shortAnswerSubmittedAnswer.submittedTexts = [shortAnswerSubmittedText];

const submissionWithAnswers = {
    id: 1,
    submittedAnswers: [multipleChoiceSubmittedAnswer, dragAndDropSubmittedAnswer, shortAnswerSubmittedAnswer],
    submitted: true,
} as QuizSubmission;
const exercise = { id: 1, studentParticipations: [studentParticipation], quizQuestions: [multipleChoiceQuestion, dragAndDropQuestion, shortAnswerQuestion] } as QuizExercise;

describe('QuizExamSummaryComponent', () => {
    let fixture: ComponentFixture<QuizExamSummaryComponent>;
    let component: QuizExamSummaryComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(ArtemisQuizQuestionTypesModule)],
            declarations: [QuizExamSummaryComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TranslateService), MockProvider(QuizExerciseService), MockProvider(ArtemisServerDateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizExamSummaryComponent);
                component = fixture.componentInstance;
                component.quizParticipation = {
                    quizQuestions: exercise.quizQuestions!,
                    studentParticipations: exercise.studentParticipations,
                };
                component.submission = { id: 2, submittedAnswers: [] };
                component.resultsPublished = true;
                component.exam = { id: 1 } as Exam;
            });
    });

    it('should initialize', () => {
        component.exam = { id: 1, publishResultsDate: dayjs().subtract(1, 'hours') } as Exam;
        component.ngOnChanges();
        expect(component).not.toBeNull();
        expect(component.exam).not.toBeNull();
        expect(component.showMissingResultsNotice).toBeTrue();
    });

    it('should initialize the solution dictionaries correctly', () => {
        component.submission = submissionWithAnswers;
        component.ngOnChanges();
        expect(component.selectedAnswerOptions.get(1)![0]).toEqual(correctAnswerOption);
        expect(component.getScoreForQuizQuestion(1)).toBe(1);
        expect(component.dragAndDropMappings.get(2)![0]).toEqual(correctDragAndDropMapping);
        expect(component.getScoreForQuizQuestion(2)).toBe(1);
        expect(component.shortAnswerSubmittedTexts.get(3)![0]).toEqual(shortAnswerSubmittedText);
        expect(component.getScoreForQuizQuestion(3)).toBe(1);
    });
});
