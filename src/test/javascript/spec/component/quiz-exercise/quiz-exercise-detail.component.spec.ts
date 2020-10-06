import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import * as moment from 'moment';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { stub } from 'sinon';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('QuizExercise Management Detail Component', () => {
    let comp: QuizExerciseDetailComponent;
    let courseManagementService: CourseManagementService;
    let quizExerciseService: QuizExerciseService;
    let fileUploaderService: FileUploaderService;
    let fixture: ComponentFixture<QuizExerciseDetailComponent>;

    const course: Course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    const mcQuestion = new MultipleChoiceQuestion();

    quizExercise.id = 456;
    quizExercise.title = 'test';
    quizExercise.duration = 600;
    mcQuestion.title = 'test';
    mcQuestion.answerOptions = [new AnswerOption()];
    quizExercise.quizQuestions = [mcQuestion];

    const route = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id }) } } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizExerciseDetailComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(QuizExerciseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(QuizExerciseDetailComponent);
        comp = fixture.componentInstance;
        courseManagementService = fixture.debugElement.injector.get(CourseManagementService);
        quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
        fileUploaderService = TestBed.inject(FileUploaderService);
    });

    describe('OnInit', () => {
        it('Should call courseExerciseService.find and quizExerciseService.find', () => {
            // GIVEN
            const quizExerciseServiceStub = stub(quizExerciseService, 'find');
            const courseManagementServiceStub = stub(courseManagementService, 'find');

            quizExerciseServiceStub.returns(
                of(
                    new HttpResponse<QuizExercise>({ body: quizExercise }),
                ),
            );
            courseManagementServiceStub.returns(
                of(
                    new HttpResponse<Course>({ body: course }),
                ),
            );

            // WHEN
            comp.course = course;
            comp.ngOnInit();

            // THEN
            expect(quizExerciseServiceStub).to.have.been.called;
            expect(courseManagementServiceStub).to.have.been.called;
        });
    });

    describe('onDurationChange', () => {
        // setup
        beforeEach(() => {
            comp.quizExercise = quizExercise;
        });

        it('Should update duration and quizExercise.duration with same values', () => {
            comp.duration = { minutes: 15, seconds: 30 };
            comp.onDurationChange();

            // compare duration with quizExercise.duration
            const durationAsSeconds = moment.duration(comp.duration).asSeconds();
            expect(durationAsSeconds).to.equal(comp.quizExercise.duration);
        });

        it('Should increase minutes when reaching 60 seconds', () => {
            comp.duration = { minutes: 0, seconds: 60 };
            comp.onDurationChange();

            expect(comp.duration.minutes).to.equal(1);
            expect(comp.duration.seconds).to.equal(0);
        });

        it('Should decrease minutes when reaching -1 seconds', () => {
            comp.duration = { minutes: 1, seconds: -1 };
            comp.onDurationChange();

            expect(comp.duration.minutes).to.equal(0);
            expect(comp.duration.seconds).to.equal(59);
        });
    });

    describe('add empty questions', () => {
        // setup
        beforeEach(() => {
            comp.quizExercise = quizExercise;
        });

        it('should add empty MC question', () => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.addMultipleChoiceQuestion();

            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
            expect(comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1].type).to.equal(QuizQuestionType.MULTIPLE_CHOICE);
        });

        it('should add empty DnD question', () => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.addDragAndDropQuestion();

            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
            expect(comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1].type).to.equal(QuizQuestionType.DRAG_AND_DROP);
        });

        it('should add empty SA question', () => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.addShortAnswerQuestion();

            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
            expect(comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1].type).to.equal(QuizQuestionType.SHORT_ANSWER);
        });
    });

    describe('import questions', () => {
        // setup
        beforeEach(() => {
            comp.quizExercise = quizExercise;
        });

        it('should import MC question ', () => {
            const question = new MultipleChoiceQuestion();
            const answerOption1 = new AnswerOption();
            answerOption1.text = 'wrong answer';
            answerOption1.explanation = 'wrong explanation';
            answerOption1.hint = 'wrong hint';
            answerOption1.isCorrect = false;
            const answerOption2 = new AnswerOption();
            answerOption1.text = 'right answer';
            answerOption1.explanation = 'right explanation';
            answerOption1.isCorrect = true;
            question.answerOptions = [answerOption1, answerOption2];

            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;

            comp.verifyAndImportQuestions([question]);

            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
            const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as MultipleChoiceQuestion;
            expect(lastAddedQuestion.type).to.equal(QuizQuestionType.MULTIPLE_CHOICE);
            expect(lastAddedQuestion.answerOptions).to.have.lengthOf(2);
            expect(lastAddedQuestion.answerOptions![0]).to.deep.equal(answerOption1);
            expect(lastAddedQuestion.answerOptions![1]).to.deep.equal(answerOption2);
        });

        it('should import DnD question', fakeAsync(() => {
            const question = new DragAndDropQuestion();
            const dragItem1 = new DragItem();
            dragItem1.text = 'dragItem 1';
            const dragItem2 = new DragItem();
            dragItem2.text = 'dragItem 1';
            question.dragItems = [dragItem1, dragItem2];
            const dropLocation1 = new DropLocation();
            dropLocation1.posX = 50;
            dropLocation1.posY = 60;
            dropLocation1.width = 70;
            dropLocation1.height = 80;
            question.dropLocations = [dropLocation1];
            const dragAndDropMapping = new DragAndDropMapping(dragItem1, dropLocation1);
            question.correctMappings = [dragAndDropMapping];

            // mock fileUploaderService
            spyOn(fileUploaderService, 'duplicateFile').and.returnValue(Promise.resolve({ path: 'test' }));

            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.verifyAndImportQuestions([question]);
            tick();

            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
            const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as DragAndDropQuestion;
            expect(lastAddedQuestion.type).to.equal(QuizQuestionType.DRAG_AND_DROP);
            expect(lastAddedQuestion.correctMappings).to.have.lengthOf(1);
            expect(lastAddedQuestion.dragItems![0]).to.deep.equal(dragItem1);
            expect(lastAddedQuestion.dragItems![1]).to.deep.equal(dragItem2);
            expect(lastAddedQuestion.dropLocations![0]).to.deep.equal(dropLocation1);
        }));

        it('should import SA question', () => {
            const question = new ShortAnswerQuestion();
            const shortAnswerSolution1 = new ShortAnswerSolution();
            shortAnswerSolution1.text = 'solution 1';
            const shortAnswerSolution2 = new ShortAnswerSolution();
            shortAnswerSolution2.text = 'solution 2';
            question.solutions = [shortAnswerSolution1, shortAnswerSolution2];
            const spot1 = new ShortAnswerSpot();
            spot1.question = question;
            spot1.spotNr = 1;
            spot1.width = 50;
            const spot2 = new ShortAnswerSpot();
            spot2.question = question;
            spot2.spotNr = 2;
            spot2.width = 70;
            question.spots = [spot1, spot2];
            const shortAnswerMapping1 = new ShortAnswerMapping(spot1, shortAnswerSolution1);
            const shortAnswerMapping2 = new ShortAnswerMapping(spot2, shortAnswerSolution2);
            question.correctMappings = [shortAnswerMapping1, shortAnswerMapping2];

            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.verifyAndImportQuestions([question]);

            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
            const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as ShortAnswerQuestion;
            expect(lastAddedQuestion.type).to.equal(QuizQuestionType.SHORT_ANSWER);
            expect(lastAddedQuestion.correctMappings).to.have.lengthOf(2);
            expect(lastAddedQuestion.correctMappings![0]).to.deep.equal(shortAnswerMapping1);
            expect(lastAddedQuestion.correctMappings![1]).to.deep.equal(shortAnswerMapping2);
            expect(lastAddedQuestion.spots).to.have.lengthOf(2);
            expect(lastAddedQuestion.spots![0]).to.equal(spot1);
            expect(lastAddedQuestion.spots![1]).to.equal(spot2);
            expect(lastAddedQuestion.solutions).to.have.lengthOf(2);
            expect(lastAddedQuestion.solutions![0]).to.equal(shortAnswerSolution1);
            expect(lastAddedQuestion.solutions![1]).to.equal(shortAnswerSolution2);
        });
    });
});
