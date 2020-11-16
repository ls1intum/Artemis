import { Location } from '@angular/common';
import { HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { expect as jestExpect } from '@jest/globals';
import { TranslateService } from '@ngx-translate/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import * as chai from 'chai';
import * as moment from 'moment';
import { JhiAlertService } from 'ng-jhipster';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('QuizExercise Management Detail Component', () => {
    let comp: QuizExerciseDetailComponent;
    let courseManagementService: CourseManagementService;
    let quizExerciseService: QuizExerciseService;
    let fileUploaderService: FileUploaderService;
    let fixture: ComponentFixture<QuizExerciseDetailComponent>;
    let router: Router;
    let location: Location;
    let alertService: JhiAlertService;
    let dragAndDropQuestionUtil: DragAndDropQuestionUtil;
    let shortAnswerQuestionUtil: ShortAnswerQuestionUtil;
    let changeDetector: ChangeDetectorRef;

    const course: Course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    const mcQuestion = new MultipleChoiceQuestion();
    const answerOption = new AnswerOption();

    const resetQuizExercise = () => {
        quizExercise.id = 456;
        quizExercise.title = 'test';
        quizExercise.duration = 600;
        answerOption.isCorrect = true;
        mcQuestion.title = 'test';
        mcQuestion.answerOptions = [answerOption];
        quizExercise.quizQuestions = [mcQuestion];
        quizExercise.isPlannedToStart = false;
        quizExercise.releaseDate = undefined;
    };

    resetQuizExercise();

    const route = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id }) } } as any) as ActivatedRoute;

    const createValidMCQuestion = () => {
        const question = new MultipleChoiceQuestion();
        question.title = 'test';
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
        question.score = 10;
        return { question, answerOption1, answerOption2 };
    };

    const createValidDnDQuestion = () => {
        const question = new DragAndDropQuestion();
        question.title = 'test';
        const dragItem1 = new DragItem();
        dragItem1.text = 'dragItem 1';
        dragItem1.pictureFilePath = 'test';
        const dragItem2 = new DragItem();
        dragItem2.text = 'dragItem 1';
        question.dragItems = [dragItem1, dragItem2];
        const dropLocation = new DropLocation();
        dropLocation.posX = 50;
        dropLocation.posY = 60;
        dropLocation.width = 70;
        dropLocation.height = 80;
        question.dropLocations = [dropLocation];
        const correctDragAndDropMapping = new DragAndDropMapping(dragItem1, dropLocation);
        question.correctMappings = [correctDragAndDropMapping];
        return { question, dragItem1, dragItem2, dropLocation, correctDragAndDropMapping };
    };

    const createValidSAQuestion = () => {
        const question = new ShortAnswerQuestion();
        question.title = 'test';
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
        return { question, shortAnswerMapping1, shortAnswerMapping2, spot1, spot2, shortAnswerSolution1, shortAnswerSolution2 };
    };

    beforeEach(
        waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [QuizExerciseDetailComponent],
                providers: [
                    ChangeDetectorRef,
                    { provide: ActivatedRoute, useValue: route },
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: Router, useClass: MockRouter },
                ],
            })
                .overrideTemplate(QuizExerciseDetailComponent, '')
                .compileComponents();
        }),
    );

    beforeEach(() => {
        fixture = TestBed.createComponent(QuizExerciseDetailComponent);
        comp = fixture.componentInstance;
        courseManagementService = fixture.debugElement.injector.get(CourseManagementService);
        quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
        router = fixture.debugElement.injector.get(Router);
        location = fixture.debugElement.injector.get(Location);
        fileUploaderService = TestBed.inject(FileUploaderService);
        alertService = fixture.debugElement.injector.get(JhiAlertService);
        dragAndDropQuestionUtil = fixture.debugElement.injector.get(DragAndDropQuestionUtil);
        shortAnswerQuestionUtil = fixture.debugElement.injector.get(ShortAnswerQuestionUtil);
        changeDetector = fixture.debugElement.injector.get(ChangeDetectorRef);
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
            resetQuizExercise();
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

        it('Should set duration to due date release date difference', () => {
            comp.isExamMode = true;
            comp.quizExercise.releaseDate = moment();
            comp.quizExercise.dueDate = moment().add(1530, 's');
            comp.onDurationChange();
            expect(comp.quizExercise.duration).to.equal(1530);
            comp.isExamMode = false;
        });
    });

    describe('add questions', () => {
        // setup
        beforeEach(() => {
            resetQuizExercise();
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

        afterAll(() => {
            quizExercise.quizQuestions = [mcQuestion];
        });
    });

    describe('add existing questions', () => {
        it('should add questions with export quiz option and call import', () => {
            comp.showExistingQuestions = true;
            const { question: exportedQuestion } = createValidMCQuestion();
            exportedQuestion.title = 'exported';
            exportedQuestion.exportQuiz = true;
            const { question: notExportedQuestion } = createValidMCQuestion();
            notExportedQuestion.title = 'notExported';
            notExportedQuestion.exportQuiz = false;
            comp.existingQuestions = [exportedQuestion, notExportedQuestion];
            const verifyAndImportQuestionsStub = stub(comp, 'verifyAndImportQuestions');
            const cacheValidationStub = stub(comp, 'cacheValidation');
            // console.log(verifyAndImportQuestionsStub.lastCall.firstArg);
            comp.addExistingQuestions();
            expect(verifyAndImportQuestionsStub).to.have.been.calledWithExactly([exportedQuestion]);
            expect(cacheValidationStub).to.have.been.called;
            expect(comp.showExistingQuestions).to.equal(false);
            expect(comp.showExistingQuestionsFromCourse).to.equal(true);
            expect(comp.selectedCourseId).to.equal(undefined);
            expect(comp.allExistingQuestions).to.deep.equal([]);
            expect(comp.existingQuestions).to.deep.equal([]);
            verifyAndImportQuestionsStub.restore();
            cacheValidationStub.restore();
        });
        describe('applyFilter', () => {
            const { question: multiChoiceQuestion } = createValidMCQuestion();
            const { question: dndQuestion } = createValidDnDQuestion();
            const { question: shortQuestion } = createValidSAQuestion();
            beforeEach(() => {
                comp.allExistingQuestions = [multiChoiceQuestion, dndQuestion, shortQuestion];
                comp.mcqFilterEnabled = false;
                comp.dndFilterEnabled = false;
                comp.shortAnswerFilterEnabled = false;
                comp.searchQueryText = '';
            });
            it('should put mc question when mc filter selected', () => {
                comp.mcqFilterEnabled = true;
                comp.applyFilter();
                expect(comp.existingQuestions).to.deep.equal([multiChoiceQuestion]);
            });
            it('should put mc question when dnd filter selected', () => {
                comp.dndFilterEnabled = true;
                comp.applyFilter();
                expect(comp.existingQuestions).to.deep.equal([dndQuestion]);
            });

            it('should put mc question when sa filter selected', () => {
                comp.shortAnswerFilterEnabled = true;
                comp.applyFilter();
                expect(comp.existingQuestions).to.deep.equal([shortQuestion]);
            });

            it('should put all if all selected', () => {
                comp.mcqFilterEnabled = true;
                comp.dndFilterEnabled = true;
                comp.shortAnswerFilterEnabled = true;
                comp.applyFilter();
                expect(comp.existingQuestions).to.deep.equal(comp.allExistingQuestions);
            });
        });
        describe('select course', () => {
            let quizExerciseServiceFindForCourseStub: SinonStub;
            let quizExerciseServiceFindStub: SinonStub;
            beforeEach(() => {
                comp.allExistingQuestions = [];
                comp.courses = [course];
                comp.selectedCourseId = course.id;
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                quizExerciseServiceFindForCourseStub = stub(quizExerciseService, 'findForCourse');
                quizExerciseServiceFindForCourseStub.returns(
                    of(
                        new HttpResponse<QuizExercise[]>({ body: [quizExercise] }),
                    ),
                );
                quizExerciseServiceFindStub = stub(quizExerciseService, 'find');
                quizExerciseServiceFindStub.returns(
                    of(
                        new HttpResponse<QuizExercise>({ body: quizExercise }),
                    ),
                );
            });

            afterEach(() => {
                quizExerciseServiceFindForCourseStub.reset();
                quizExerciseServiceFindStub.reset();
            });
            it('should call find course with selected id', () => {
                comp.onCourseSelect();
                expect(quizExerciseServiceFindForCourseStub).to.have.been.calledWithExactly(comp.selectedCourseId);
                expect(quizExerciseServiceFindStub).to.have.been.calledWithExactly(quizExercise.id);
                expect(comp.allExistingQuestions).to.deep.equal(quizExercise.quizQuestions);
            });
            it('should not call find course without selected id', () => {
                comp.selectedCourseId = undefined;
                comp.onCourseSelect();
                expect(quizExerciseServiceFindForCourseStub).to.not.have.been.called;
                expect(quizExerciseServiceFindStub).to.not.have.been.called;
            });
            it('should call alert service if fails', () => {
                quizExerciseServiceFindForCourseStub.returns(throwError({ status: 404 }));
                console.error = jest.fn();
                let alertServiceStub: SinonStub;
                alertServiceStub = stub(alertService, 'error');
                comp.onCourseSelect();
                expect(alertServiceStub).to.have.been.called;
            });

            afterAll(() => {
                quizExerciseServiceFindForCourseStub.restore();
                quizExerciseServiceFindStub.restore();
            });
        });
    });
    describe('delete questions', () => {
        const deleteQuestionAndExpect = () => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            const questionToDelete = comp.quizExercise.quizQuestions![amountQuizQuestions - 1];
            comp.deleteQuestion(questionToDelete);
            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions - 1);
            expect(comp.quizExercise.quizQuestions?.filter((question) => question === questionToDelete));
        };
        // setup
        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
        });

        it('should delete MC question', () => {
            comp.addMultipleChoiceQuestion();
            deleteQuestionAndExpect();
        });

        it('should delete DnD question', () => {
            comp.addDragAndDropQuestion();
            deleteQuestionAndExpect();
        });

        it('should delete SA question', () => {
            comp.addShortAnswerQuestion();
            deleteQuestionAndExpect();
        });
    });

    describe('updating question', () => {
        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
        });
        it('should replace quiz questions with copy of it', () => {
            const cacheValidationStub = stub(comp, 'cacheValidation');
            comp.onQuestionUpdated();
            expect(cacheValidationStub).to.have.been.called;
            expect(comp.quizExercise.quizQuestions).to.deep.equal(Array.from(comp.quizExercise.quizQuestions!));
            cacheValidationStub.restore();
        });
    });

    describe('import questions', () => {
        const importQuestionAndExpectOneMoreQuestionInQuestions = (question: QuizQuestion, withTick: boolean) => {
            const amountQuizQuestions = comp.quizExercise.quizQuestions?.length || 0;
            comp.verifyAndImportQuestions([question]);
            if (withTick) {
                tick();
            }
            expect(comp.quizExercise.quizQuestions).to.have.lengthOf(amountQuizQuestions + 1);
        };
        // setup
        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
        });

        it('should set import file correctly', () => {
            const file = new File(['content'], 'testFileName', { type: 'text/plain' });
            const ev = { target: { files: [file] } };
            const changeDetectorDetectChangesStub = stub(changeDetector.constructor.prototype, 'detectChanges');
            comp.setImportFile(ev);
            expect(comp.importFile).to.deep.equal(file);
            expect(comp.importFileName).to.equal('testFileName');
            expect(changeDetectorDetectChangesStub).to.have.been.called;
            changeDetectorDetectChangesStub.restore();
        });

        it('should import MC question ', () => {
            const { question, answerOption1, answerOption2 } = createValidMCQuestion();
            importQuestionAndExpectOneMoreQuestionInQuestions(question, false);
            const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as MultipleChoiceQuestion;
            expect(lastAddedQuestion.type).to.equal(QuizQuestionType.MULTIPLE_CHOICE);
            expect(lastAddedQuestion.answerOptions).to.have.lengthOf(2);
            expect(lastAddedQuestion.answerOptions![0]).to.deep.equal(answerOption1);
            expect(lastAddedQuestion.answerOptions![1]).to.deep.equal(answerOption2);
        });

        it('should import DnD question', fakeAsync(() => {
            const { question, dragItem1, dragItem2, dropLocation } = createValidDnDQuestion();

            // mock fileUploaderService
            spyOn(fileUploaderService, 'duplicateFile').and.returnValue(Promise.resolve({ path: 'test' }));
            importQuestionAndExpectOneMoreQuestionInQuestions(question, true);
            const lastAddedQuestion = comp.quizExercise.quizQuestions![comp.quizExercise.quizQuestions!.length - 1] as DragAndDropQuestion;
            expect(lastAddedQuestion.type).to.equal(QuizQuestionType.DRAG_AND_DROP);
            expect(lastAddedQuestion.correctMappings).to.have.lengthOf(1);
            expect(lastAddedQuestion.dragItems![0]).to.deep.equal(dragItem1);
            expect(lastAddedQuestion.dragItems![1]).to.deep.equal(dragItem2);
            expect(lastAddedQuestion.dropLocations![0]).to.deep.equal(dropLocation);
            expect(lastAddedQuestion.dragItems![0].pictureFilePath).to.equal('test');
            expect(lastAddedQuestion.dragItems![1].pictureFilePath).to.equal(undefined);
        }));

        it('should import SA question', () => {
            const { question, shortAnswerMapping1, shortAnswerMapping2, spot1, spot2, shortAnswerSolution1, shortAnswerSolution2 } = createValidSAQuestion();
            importQuestionAndExpectOneMoreQuestionInQuestions(question, false);
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

    describe('quiz validity', () => {
        // setup

        const removeQuestionTitleAndExpectInvalidQuiz = (question: QuizQuestion) => {
            question.title = '';
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(false);
        };

        const removeCorrectMappingsAndExpectInvalidQuiz = (question: DragAndDropQuestion | ShortAnswerQuestion) => {
            question.correctMappings = [];
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(false);
        };

        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
        });

        it('should be valid with default test setting', () => {
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(true);
        });

        it('should not be valid without a quiz title', () => {
            quizExercise.title = '';
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(false);
        });

        describe('unknown question type', () => {
            let question: MultipleChoiceQuestion;
            beforeEach(() => {
                const multiChoiceQuestion = createValidMCQuestion();
                question = multiChoiceQuestion.question;
                question.type = undefined;
                comp.quizExercise.quizQuestions = [question];
            });

            it('should be valid if a question has unknown type and a title', () => {
                question.title = 'test';
                comp.cacheValidation();
                expect(comp.quizIsValid).to.equal(true);
            });
            it('should not be valid if a question has unknown type and no title', () => {
                question.title = '';
                comp.cacheValidation();
                expect(comp.quizIsValid).to.equal(false);
            });
        });

        it('should not be valid if a question has negative score', () => {
            const { question } = createValidMCQuestion();
            question.score = -1;
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(false);
        });

        it('should be valid with valid MC question', () => {
            const { question } = createValidMCQuestion();
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(true);
        });

        it('should not be valid if MC question has no title', () => {
            const { question } = createValidMCQuestion();
            removeQuestionTitleAndExpectInvalidQuiz(question);
        });

        it('should not be valid if MC question has no correct answer', () => {
            const { question } = createValidMCQuestion();
            question.answerOptions = [];
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(false);
        });

        it('should be valid with valid DnD question', () => {
            const { question } = createValidDnDQuestion();
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(true);
        });

        it('should not be valid if DnD question has no title', () => {
            const { question } = createValidDnDQuestion();
            removeQuestionTitleAndExpectInvalidQuiz(question);
        });

        it('should not be valid if DnD question has no correct mapping', () => {
            const { question } = createValidDnDQuestion();
            removeCorrectMappingsAndExpectInvalidQuiz(question);
        });

        it('should be valid with valid SA question', () => {
            const { question } = createValidSAQuestion();
            comp.quizExercise.quizQuestions = [question];
            comp.cacheValidation();
            expect(comp.quizIsValid).to.equal(true);
        });

        it('should not be valid if SA question has no title', () => {
            const { question } = createValidSAQuestion();
            removeQuestionTitleAndExpectInvalidQuiz(question);
        });

        it('should not be valid if SA question has no correct mapping', () => {
            const { question } = createValidSAQuestion();
            removeCorrectMappingsAndExpectInvalidQuiz(question);
        });
    });

    describe('saving', () => {
        let quizExerciseServiceCreateStub: SinonStub;
        let quizExerciseServiceUpdateStub: SinonStub;

        const saveQuizWithPendingChangesCache = () => {
            comp.cacheValidation();
            comp.pendingChangesCache = true;
            comp.save();
        };

        const saveAndExpectAlertService = () => {
            console.error = jest.fn();
            let alertServiceStub: SinonStub;
            alertServiceStub = stub(alertService, 'error');
            saveQuizWithPendingChangesCache();
            expect(alertServiceStub).to.have.been.called;
            expect(comp.isSaving).to.equal(false);
            jestExpect(console.error).toHaveBeenCalled();
        };

        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
            quizExerciseServiceCreateStub = stub(quizExerciseService, 'create');
            quizExerciseServiceCreateStub.returns(
                of(
                    new HttpResponse<QuizExercise>({ body: quizExercise }),
                ),
            );
            quizExerciseServiceUpdateStub = stub(quizExerciseService, 'update');
            quizExerciseServiceUpdateStub.returns(
                of(
                    new HttpResponse<QuizExercise>({ body: quizExercise }),
                ),
            );
        });

        it('should call create if valid and quiz exercise no id', () => {
            comp.quizExercise.id = undefined;
            saveQuizWithPendingChangesCache();
            expect(quizExerciseServiceCreateStub).to.have.been.called;
            expect(quizExerciseServiceUpdateStub).to.not.have.been.called;
        });

        it('should update if valid and quiz exercise has id', () => {
            saveQuizWithPendingChangesCache();
            expect(quizExerciseServiceCreateStub).to.not.have.been.called;
            expect(quizExerciseServiceUpdateStub).to.have.been.called;
            expect(quizExerciseServiceUpdateStub).to.have.been.calledWith(comp.quizExercise, {});
        });

        it('should not save if not valid', () => {
            saveQuizWithPendingChangesCache();
            expect(quizExerciseServiceCreateStub).to.not.have.been.called;
            expect(quizExerciseServiceUpdateStub).to.have.been.called;
        });

        it('should call update with notification text if there is one', () => {
            comp.notificationText = 'test';
            saveQuizWithPendingChangesCache();
            expect(quizExerciseServiceUpdateStub).to.have.been.calledWith(comp.quizExercise, { notificationText: 'test' });
        });

        it('should call alert service if response has no body on update', () => {
            quizExerciseServiceUpdateStub.returns(of(new HttpResponse<QuizExercise>({})));
            saveAndExpectAlertService();
        });

        it('should call alert service if response has no body on create', () => {
            comp.quizExercise.id = undefined;
            quizExerciseServiceCreateStub.returns(of(new HttpResponse<QuizExercise>({})));
            saveAndExpectAlertService();
        });

        it('should call alert service if update fails', () => {
            quizExerciseServiceUpdateStub.returns(throwError({ status: 404 }));
            saveAndExpectAlertService();
        });

        it('should call alert service if response has no body on create', () => {
            comp.quizExercise.id = undefined;
            quizExerciseServiceCreateStub.returns(throwError({ status: 404 }));
            saveAndExpectAlertService();
        });
    });

    describe('routing', () => {
        let routerSpy: SinonSpy;
        let locationSpy: SinonSpy;

        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
            routerSpy = spy(router, 'navigate');
            locationSpy = spy(location, 'back');
        });

        afterEach(() => {
            routerSpy.resetHistory();
        });

        it('should go back to quiz exercise page on cancel', () => {
            comp.cancel();
            expect(routerSpy).to.have.been.calledOnceWithExactly(['/course-management', comp.quizExercise.course!.id, 'quiz-exercise']);
        });

        it('should go back to quiz exercise page on cancel', () => {
            comp.isExamMode = true;
            comp.cancel();
            expect(routerSpy).to.have.been.calledOnceWithExactly(['/course-management', comp.courseId, 'exams', comp.examId, 'exercise-groups']);
        });

        it('should call back on location on back', () => {
            comp.back();
            expect(locationSpy).to.have.been.called;
        });
    });

    describe('prepare entity', () => {
        beforeEach(() => {
            resetQuizExercise();
            comp.quizExercise = quizExercise;
        });

        it('should set duration to 10 if not given', () => {
            comp.quizExercise.duration = undefined;
            comp.prepareEntity(comp.quizExercise);
            expect(comp.quizExercise.duration).to.equal(10);
        });

        it('should set release date to moment release date if exam mode', () => {
            comp.isExamMode = true;
            const now = moment();
            comp.quizExercise.releaseDate = now;
            comp.prepareEntity(comp.quizExercise);
            expect(comp.quizExercise.releaseDate).to.deep.equal(moment(now));
        });
    });

    describe('show existing questions', () => {
        it('should set showExistingQuestionsFromCourse to given value', () => {
            comp.setExistingQuestionSourceToCourse(true);
            expect(comp.showExistingQuestionsFromCourse).to.equal(true);
            comp.setExistingQuestionSourceToCourse(false);
            expect(comp.showExistingQuestionsFromCourse).to.equal(false);
        });
    });

    describe('importing quiz', () => {
        let generateFileReaderStub: SinonStub;
        let verifyStub: SinonStub;
        let readAsText: jest.Mock;
        let reader: FileReader;
        const jsonContent = `[{
            "type": "multiple-choice",
            "id": 1,
            "title": "vav",
            "text": "Enter your long question if needed",
            "hint": "Add a hint here (visible during the quiz via ?-Button)",
            "score": 1,
            "scoringType": "ALL_OR_NOTHING",
            "randomizeOrder": true,
            "invalid": false,
            "answerOptions": [
              {
                "id": 1,
                "text": "Enter a correct answer option here",
                "hint": "Add a hint here (visible during the quiz via ?-Button)",
                "explanation": "Add an explanation here (only visible in feedback after quiz has ended)",
                "isCorrect": true,
                "invalid": false
              },
              {
                "id": 2,
                "text": "Enter a wrong answer option here",
                "isCorrect": false,
                "invalid": false
              }
            ]
          }]`;
        const fakeFile = new File([jsonContent], 'file.txt', { type: 'text/plain' });
        const questions = JSON.parse(jsonContent) as QuizQuestion[];
        beforeEach(() => {
            comp.importFile = fakeFile;
            verifyStub = stub(comp, 'verifyAndImportQuestions');
            readAsText = jest.fn();
            reader = new FileReader();
            reader = { ...reader, result: jsonContent };
            generateFileReaderStub = stub(comp, 'generateFileReader').returns({ ...reader, onload: null, readAsText });
        });
        it('should call verify and import questions with right json', async () => {
            await comp.importQuiz();
            jestExpect(readAsText).toHaveBeenCalledWith(fakeFile);
            expect(generateFileReaderStub).to.have.been.called;
            comp.onFileLoadImport(reader);
            expect(verifyStub).to.have.been.calledWithExactly(questions);
            expect(comp.importFile).to.equal(undefined);
        });

        it('should not call any functions without import file', async () => {
            comp.importFile = undefined;
            await comp.importQuiz();
            jestExpect(readAsText).toHaveBeenCalledTimes(0);
            expect(generateFileReaderStub).to.not.have.been.called;
            expect(comp.importFile).to.equal(undefined);
        });

        it('should alert user when onload throws error', async () => {
            const alert = window.alert;
            const alertFunction = jest.fn();
            window.alert = alertFunction;
            verifyStub.throws('');
            await comp.importQuiz();
            comp.onFileLoadImport(reader);
            jestExpect(alertFunction).toHaveBeenCalled();
            window.alert = alert;
            verifyStub.reset();
        });
    });

    describe('generating file reader', () => {
        it('should return file reader when called', () => {
            expect(comp.generateFileReader()).to.deep.equal(new FileReader());
        });
    });

    describe('invalid reasons', () => {
        const filterReasonAndExpectMoreThanOneInArray = (translateKey: string) => {
            const invalidReasons = comp.computeInvalidReasons().filter((reason) => reason.translateKey === translateKey);
            expect(invalidReasons.length).to.be.greaterThan(0);
        };

        const checkForInvalidFlaggedQuestionAndReason = () => {
            comp.checkForInvalidFlaggedQuestions(comp.quizExercise.quizQuestions);
            filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionHasInvalidFlaggedElements');
        };
        it('should concatenate invalid reasons', () => {
            const computeInvalidReasonsStub = stub(comp, 'computeInvalidReasons').returns([
                { translateKey: 'testKey1', translateValues: 'testValue' },
                { translateKey: 'testKey2', translateValues: 'testValue' },
            ]);
            expect(comp.invalidReasonsHTML()).to.equal('testKey1   -   testKey2  ');
            computeInvalidReasonsStub.restore();
        });

        describe('should include right reasons in reasons array for quiz', () => {
            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
            });
            it('should put reason for no title', () => {
                quizExercise.title = '';
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.quizTitle');
            });
            it('should put reason for too long title', () => {
                quizExercise.title = new Array(251).join('a');
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.quizTitleLength');
            });
            it('should put reason for no duration', () => {
                quizExercise.duration = 0;
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.quizDuration');
            });
            it('should put reason for no questions', () => {
                quizExercise.quizQuestions = [];
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.noQuestion');
            });

            it('should put reason for invalid release time', () => {
                quizExercise.isPlannedToStart = true;
                quizExercise.releaseDate = undefined;
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.invalidStartTime');
            });

            it('should put reason if release time is before now', () => {
                quizExercise.isPlannedToStart = true;
                quizExercise.releaseDate = moment().subtract(1500, 's');
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.startTimeInPast');
            });
        });

        describe('should include right reasons in reasons array for MC and general', () => {
            let question: MultipleChoiceQuestion;
            let answerOption1: AnswerOption;
            let answerOption2: AnswerOption;

            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                const multiChoiceQuestion = createValidMCQuestion();
                question = multiChoiceQuestion.question;
                answerOption1 = multiChoiceQuestion.answerOption1;
                answerOption2 = multiChoiceQuestion.answerOption2;
                comp.quizExercise.quizQuestions = [question];
            });
            it('should put reason for negative score ', () => {
                question.score = -1;
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionScore');
            });
            it('should put reason for no title', () => {
                question.title = '';
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionTitle');
            });

            it('should put reason for no correct answer for MC', () => {
                answerOption1.isCorrect = false;
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionCorrectAnswerOption');
            });
            it('should put reason for no correct explanation for MC', () => {
                answerOption1.explanation = '';
                answerOption2.explanation = '';
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.explanationIsMissing');
            });

            it('should put reason for too long title', () => {
                question.title = new Array(251).join('a');
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionTitleLength');
            });

            it('should put reason if question title is included in invalid flagged question', () => {
                answerOption1.invalid = true;
                checkForInvalidFlaggedQuestionAndReason();
            });
        });

        describe('should include right reasons in reasons array for DnD', () => {
            let question: DragAndDropQuestion;
            let dragItem1: DragItem;
            let dropLocation: DropLocation;
            let correctDragAndDropMapping: DragAndDropMapping;

            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                const dndQuestion = createValidDnDQuestion();
                question = dndQuestion.question;
                dragItem1 = dndQuestion.dragItem1;
                correctDragAndDropMapping = dndQuestion.correctDragAndDropMapping;
                dropLocation = dndQuestion.dropLocation;
                comp.quizExercise.quizQuestions = [question];
            });
            it('should put reason for no correct mappings ', () => {
                question.correctMappings = [];
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionCorrectMapping');
            });

            it('should put reason for unsolvable ', () => {
                const dragAndDropUtilSolveStub = stub(dragAndDropQuestionUtil, 'solve').returns([]);
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionUnsolvable');
                dragAndDropUtilSolveStub.restore();
            });

            it('should put reason for misleading correct mappings ', () => {
                const dragAndDropUtilMisleadingStub = stub(dragAndDropQuestionUtil, 'validateNoMisleadingCorrectMapping').returns(false);
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping');
                dragAndDropUtilMisleadingStub.restore();
            });

            it('should put reason and flag as invalid if a drag item is invalid', () => {
                dragItem1.invalid = true;
                checkForInvalidFlaggedQuestionAndReason();
            });

            it('should put reason and flag as invalid if a correct mapping is invalid', () => {
                correctDragAndDropMapping.invalid = true;
                checkForInvalidFlaggedQuestionAndReason();
            });

            it('should put reason and flag as invalid if a drop location is invalid', () => {
                dropLocation.invalid = true;
                checkForInvalidFlaggedQuestionAndReason();
            });
        });

        describe('should include right reasons in reasons array for SA', () => {
            let question: ShortAnswerQuestion;
            let shortAnswerSolution1: ShortAnswerSolution;
            let shortAnswerMapping1: ShortAnswerMapping;
            let spot1: ShortAnswerSpot;
            beforeEach(() => {
                resetQuizExercise();
                comp.quizExercise = quizExercise;
                const saQuestion = createValidSAQuestion();
                question = saQuestion.question;
                shortAnswerSolution1 = saQuestion.shortAnswerSolution1;
                shortAnswerMapping1 = saQuestion.shortAnswerMapping1;
                spot1 = saQuestion.spot1;
                comp.quizExercise.quizQuestions = [question];
            });
            it('should put reason for no correct mappings ', () => {
                question.correctMappings = [];
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.questionCorrectMapping');
            });

            it('should put reason for misleading correct mappings ', () => {
                const shortAnswerUtilMisleadingStub = stub(shortAnswerQuestionUtil, 'validateNoMisleadingCorrectShortAnswerMapping').returns(false);
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping');
                shortAnswerUtilMisleadingStub.restore();
            });

            it('should put reason when every spot has a solution ', () => {
                const shortAnswerUtilMisleadingStub = stub(shortAnswerQuestionUtil, 'everySpotHasASolution').returns(false);
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEverySpotHasASolution');
                shortAnswerUtilMisleadingStub.restore();
            });

            it('should put reason when every mapped solution has a spot ', () => {
                const shortAnswerUtilMisleadingStub = stub(shortAnswerQuestionUtil, 'everyMappedSolutionHasASpot').returns(false);
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEveryMappedSolutionHasASpot');
                shortAnswerUtilMisleadingStub.restore();
            });

            it('should put reason when there is an empty solution ', () => {
                shortAnswerSolution1.text = '';
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionSolutionHasNoValue');
            });

            it('should put reason when duplicate mappings', () => {
                const shortAnswerUtilMisleadingStub = stub(shortAnswerQuestionUtil, 'hasMappingDuplicateValues').returns(true);
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionDuplicateMapping');
                shortAnswerUtilMisleadingStub.restore();
            });
            it('should put reason for not many solutions as spots', () => {
                const shortAnswerUtilMisleadingStub = stub(shortAnswerQuestionUtil, 'atLeastAsManySolutionsAsSpots').returns(false);
                filterReasonAndExpectMoreThanOneInArray('artemisApp.quizExercise.invalidReasons.shortAnswerQuestionUnsolvable');
                shortAnswerUtilMisleadingStub.restore();
            });

            it('should put reason and flag as invalid if a solution is invalid', () => {
                shortAnswerSolution1.invalid = true;
                checkForInvalidFlaggedQuestionAndReason();
            });

            it('should put reason and flag as invalid if a spot is invalid', () => {
                shortAnswerMapping1.invalid = true;
                checkForInvalidFlaggedQuestionAndReason();
            });
            it('should put reason and flag as invalid if a spot is invalid', () => {
                spot1.invalid = true;
                checkForInvalidFlaggedQuestionAndReason();
            });
        });
    });
});
