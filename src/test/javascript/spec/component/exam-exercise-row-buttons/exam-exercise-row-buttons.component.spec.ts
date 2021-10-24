import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { CookieService } from 'ngx-cookie-service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { DeviceDetectorService } from 'ngx-device-detector';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ExamExerciseRowButtonsComponent } from 'app/exercises/shared/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { SinonSpy, SinonStub, stub } from 'sinon';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import * as sinon from 'sinon';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExamExerciseRowButtonsComponent', () => {
    const course = { id: 3 } as Course;
    const exam = { id: 4 } as Exam;

    let modelingExerciseService: ModelingExerciseService;
    let textExerciseService: TextExerciseService;
    let quizExerciseService: QuizExerciseService;
    let fileUploadExerciseService: FileUploadExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;

    const modelingExercise = { id: 123, type: ExerciseType.MODELING } as ModelingExercise;
    const textExercise = { id: 234, type: ExerciseType.TEXT } as TextExercise;
    const quizExercise = { id: 345, type: ExerciseType.QUIZ } as QuizExercise;
    const fileUploadExercise = { id: 456, type: ExerciseType.FILE_UPLOAD } as FileUploadExercise;
    const programmingExercise = { id: 963, type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
    const quizResponse = { body: { id: 789, type: ExerciseType.QUIZ, quizQuestions: {} } as QuizExercise };

    let deleteTextExerciseStub: SinonStub;
    let deleteModelingExerciseStub: SinonStub;
    let deleteQuizExerciseStub: SinonStub;
    let deleteFileUploadExerciseStub: SinonStub;
    let deleteProgrammingExerciseStub: SinonStub;
    let quizExerciseServiceFindStub: SinonStub;

    let onDeleteExerciseEmitSpy: SinonSpy;
    let quizExerciseExportSpy: SinonSpy;

    let fixture: ComponentFixture<ExamExerciseRowButtonsComponent>;
    let component: ExamExerciseRowButtonsComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                RouterTestingModule.withRoutes([
                    {
                        path: 'courses',
                        component: ExamExerciseRowButtonsComponent,
                    },
                ]),
            ],
            declarations: [ExamExerciseRowButtonsComponent, MockPipe(ArtemisTranslatePipe), MockDirective(NgbTooltip), MockDirective(DeleteButtonDirective)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DeviceDetectorService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamExerciseRowButtonsComponent);
                component = fixture.debugElement.componentInstance;
                textExerciseService = TestBed.inject(TextExerciseService);
                modelingExerciseService = TestBed.inject(ModelingExerciseService);
                fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
                quizExerciseService = TestBed.inject(QuizExerciseService);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                component.course = course;
                component.exam = exam;

                deleteTextExerciseStub = stub(textExerciseService, 'delete');
                deleteModelingExerciseStub = stub(modelingExerciseService, 'delete');
                deleteQuizExerciseStub = stub(quizExerciseService, 'delete');
                deleteFileUploadExerciseStub = stub(fileUploadExerciseService, 'delete');
                deleteProgrammingExerciseStub = stub(programmingExerciseService, 'delete');
                quizExerciseServiceFindStub = stub(quizExerciseService, 'find');
                onDeleteExerciseEmitSpy = sinon.spy(component.onDeleteExercise, 'emit');
                quizExerciseExportSpy = sinon.spy(quizExerciseService, 'exportQuiz');
            });
    });
    describe('isExamOver', () => {
        it('should return true if over', () => {
            component.latestIndividualEndDate = dayjs().subtract(1, 'hours');
            expect(component.isExamOver()).is.true;
        });
        it('should return false if not yet over', () => {
            component.latestIndividualEndDate = dayjs().add(1, 'hours');
            expect(component.isExamOver()).is.false;
        });
        it('should return false if endDate is undefined', () => {
            expect(component.isExamOver()).is.false;
        });
    });
    describe('hasExamStarted', () => {
        it('should return true if started', () => {
            component.exam.startDate = dayjs().subtract(1, 'hours');
            expect(component.hasExamStarted()).is.true;
        });
        it('should return false if not yet started', () => {
            component.exam.startDate = dayjs().add(1, 'hours');
            expect(component.hasExamStarted()).is.false;
        });
        it('should return false if startDate is undefined', () => {
            expect(component.hasExamStarted()).is.false;
        });
    });
    describe('deleteExercise', () => {
        describe('deleteTextExercise', () => {
            it('should deleteTextExercise', () => {
                deleteTextExerciseStub.returns(of({}));
                component.exercise = textExercise;
                component.deleteExercise();
                expect(deleteTextExerciseStub).to.have.been.calledOnce;
                expect(onDeleteExerciseEmitSpy).to.have.been.calledOnce;
            });
            it('should handle error for textexercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteTextExerciseStub.returns(throwError(error));
                component.exercise = textExercise;
                component.deleteExercise();
                expect(deleteTextExerciseStub).to.have.been.calledOnce;
                expect(onDeleteExerciseEmitSpy).to.not.have.been.calledOnce;
            });
        });
        describe('deleteModelingExercise', () => {
            it('should deleteModelingExercise', () => {
                deleteModelingExerciseStub.returns(of({}));
                component.exercise = modelingExercise;
                component.deleteExercise();
                expect(deleteModelingExerciseStub).to.have.been.calledOnce;
                expect(onDeleteExerciseEmitSpy).to.have.been.calledOnce;
            });
            it('should handle error for modelingexercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteModelingExerciseStub.returns(throwError(error));
                component.exercise = modelingExercise;
                component.deleteExercise();
                expect(deleteModelingExerciseStub).to.have.been.calledOnce;
                expect(onDeleteExerciseEmitSpy).to.not.have.been.called;
            });
        });
        describe('deleteFileUploadExercise', () => {
            it('should deleteFileUploadExercise', () => {
                deleteFileUploadExerciseStub.returns(of({}));
                component.exercise = fileUploadExercise;
                component.deleteExercise();
                expect(deleteFileUploadExerciseStub).to.have.been.calledOnce;
                expect(onDeleteExerciseEmitSpy).to.have.been.calledOnce;
            });
            it('should handle error for fileupload exercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteFileUploadExerciseStub.returns(throwError(error));
                component.exercise = fileUploadExercise;
                component.deleteExercise();
                expect(deleteFileUploadExerciseStub).to.have.been.calledOnce;
                expect(onDeleteExerciseEmitSpy).to.not.have.been.called;
            });
        });
        describe('should deleteQuizExercise', () => {
            it('should deleteQuizExercise', () => {
                deleteQuizExerciseStub.returns(of({}));
                component.exercise = quizExercise;
                component.deleteExercise();
                expect(deleteQuizExerciseStub).to.have.been.calledOnce;
                expect(onDeleteExerciseEmitSpy).to.have.been.calledOnce;
            });
            it('should handle error for quizexercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteQuizExerciseStub.returns(throwError(error));
                component.exercise = quizExercise;
                component.deleteExercise();
                expect(deleteQuizExerciseStub).to.have.been.calledOnce;
                expect(onDeleteExerciseEmitSpy).to.not.have.been.called;
            });
        });
    });
    describe('deleteProgrammingExercise', () => {
        it('should deleteProgrammingExercise', () => {
            deleteProgrammingExerciseStub.returns(of({}));
            component.exercise = programmingExercise;
            component.deleteProgrammingExercise({});
            expect(deleteProgrammingExerciseStub).to.have.been.calledOnce;
            expect(onDeleteExerciseEmitSpy).to.have.been.calledOnce;
        });
        it('should handle error for programmingExercise', () => {
            const error = { message: 'error occurred!' } as HttpErrorResponse;
            deleteProgrammingExerciseStub.returns(throwError(error));
            component.exercise = programmingExercise;
            component.deleteProgrammingExercise({});
            expect(deleteProgrammingExerciseStub).to.have.been.calledOnce;
            expect(onDeleteExerciseEmitSpy).to.not.have.been.called;
        });
    });
    describe('exportQuizById', () => {
        it('should export Quiz, exportAll true', () => {
            quizExerciseServiceFindStub.returns(of(quizResponse));
            component.exercise = quizExercise;
            component.exportQuizById(true);
            expect(quizExerciseExportSpy).to.have.been.called;
            expect(quizExerciseExportSpy).to.have.been.calledWith({}, true, quizExercise.title);
        });
        it('should export Quiz, exportAll false', () => {
            quizExerciseServiceFindStub.returns(of(quizResponse));
            component.exercise = quizExercise;
            component.exportQuizById(false);
            expect(quizExerciseExportSpy).to.have.been.called;
            expect(quizExerciseExportSpy).to.have.been.calledWith({}, false, quizExercise.title);
        });
    });
});
