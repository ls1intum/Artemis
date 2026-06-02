import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/course/shared/entities/course.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockProvider } from 'ng-mocks';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { ExamExerciseRowButtonsComponent } from 'app/exercise/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';

const setExerciseInput = (fixture: ComponentFixture<ExamExerciseRowButtonsComponent>, exercise: Exercise) => {
    fixture.componentRef.setInput('exercise', exercise);
};

describe('ExamExerciseRowButtonsComponent', () => {
    setupTestBed({ zoneless: true });

    const course = { id: 3 } as Course;
    let exam: Exam;

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

    let deleteTextExerciseStub: ReturnType<typeof vi.spyOn>;
    let deleteModelingExerciseStub: ReturnType<typeof vi.spyOn>;
    let deleteQuizExerciseStub: ReturnType<typeof vi.spyOn>;
    let deleteFileUploadExerciseStub: ReturnType<typeof vi.spyOn>;
    let deleteProgrammingExerciseStub: ReturnType<typeof vi.spyOn>;
    let quizExerciseServiceFindStub: ReturnType<typeof vi.spyOn>;

    let onDeleteExerciseEmitSpy: ReturnType<typeof vi.spyOn>;
    let quizExerciseExportSpy: ReturnType<typeof vi.spyOn>;

    let fixture: ComponentFixture<ExamExerciseRowButtonsComponent>;
    let component: ExamExerciseRowButtonsComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamExerciseRowButtonsComponent],
            providers: [
                MockProvider(TextExerciseService),
                MockProvider(FileUploadExerciseService),
                MockProvider(ProgrammingExerciseService),
                MockProvider(ModelingExerciseService),
                MockProvider(QuizExerciseService),
                MockProvider(ExerciseService),
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(EventManager),
            ],
        })
            .overrideTemplate(ExamExerciseRowButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExamExerciseRowButtonsComponent);
        component = fixture.componentInstance;
        textExerciseService = TestBed.inject(TextExerciseService);
        modelingExerciseService = TestBed.inject(ModelingExerciseService);
        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        quizExerciseService = TestBed.inject(QuizExerciseService);
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        exam = { id: 4 } as Exam;
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('exerciseGroupId', 5);

        deleteTextExerciseStub = vi.spyOn(textExerciseService, 'delete');
        deleteModelingExerciseStub = vi.spyOn(modelingExerciseService, 'delete');
        deleteQuizExerciseStub = vi.spyOn(quizExerciseService, 'delete');
        deleteFileUploadExerciseStub = vi.spyOn(fileUploadExerciseService, 'delete');
        deleteProgrammingExerciseStub = vi.spyOn(programmingExerciseService, 'delete');
        quizExerciseServiceFindStub = vi.spyOn(quizExerciseService, 'find');
        onDeleteExerciseEmitSpy = vi.spyOn(component.onDeleteExercise, 'emit');
        quizExerciseExportSpy = vi.spyOn(quizExerciseService, 'exportQuiz');
    });

    describe('isExamOver', () => {
        it('should return true if over', () => {
            fixture.componentRef.setInput('latestIndividualEndDate', dayjs().subtract(1, 'hours'));
            expect(component.isExamOver()).toBe(true);
        });
        it('should return false if not yet over', () => {
            fixture.componentRef.setInput('latestIndividualEndDate', dayjs().add(1, 'hours'));
            expect(component.isExamOver()).toBe(false);
        });
        it('should return false if endDate is undefined', () => {
            expect(component.isExamOver()).toBe(false);
        });
    });
    describe('hasExamStarted', () => {
        it('should return true if started', () => {
            exam.startDate = dayjs().subtract(1, 'hours');
            expect(component.hasExamStarted()).toBe(true);
        });
        it('should return false if not yet started', () => {
            exam.startDate = dayjs().add(1, 'hours');
            expect(component.hasExamStarted()).toBe(false);
        });
        it('should return false if startDate is undefined', () => {
            expect(component.hasExamStarted()).toBe(false);
        });
    });
    describe('deleteExercise', () => {
        describe('deleteTextExercise', () => {
            it('should deleteTextExercise', () => {
                deleteTextExerciseStub.mockReturnValue(of({}));
                setExerciseInput(fixture, textExercise);
                component.deleteExercise();
                expect(deleteTextExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
            });
            it('should handle error for textexercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteTextExerciseStub.mockReturnValue(throwError(() => error));
                setExerciseInput(fixture, textExercise);
                component.deleteExercise();
                expect(deleteTextExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
            });
        });
        describe('deleteModelingExercise', () => {
            it('should deleteModelingExercise', () => {
                deleteModelingExerciseStub.mockReturnValue(of({}));
                setExerciseInput(fixture, modelingExercise);
                component.deleteExercise();
                expect(deleteModelingExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
            });
            it('should handle error for modelingexercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteModelingExerciseStub.mockReturnValue(throwError(() => error));
                setExerciseInput(fixture, modelingExercise);
                component.deleteExercise();
                expect(deleteModelingExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
            });
        });
        describe('deleteFileUploadExercise', () => {
            it('should deleteFileUploadExercise', () => {
                deleteFileUploadExerciseStub.mockReturnValue(of({}));
                setExerciseInput(fixture, fileUploadExercise);
                component.deleteExercise();
                expect(deleteFileUploadExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
            });
            it('should handle error for fileupload exercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteFileUploadExerciseStub.mockReturnValue(throwError(() => error));
                setExerciseInput(fixture, fileUploadExercise);
                component.deleteExercise();
                expect(deleteFileUploadExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
            });
        });
        describe('should deleteQuizExercise', () => {
            it('should deleteQuizExercise', () => {
                deleteQuizExerciseStub.mockReturnValue(of({}));
                setExerciseInput(fixture, quizExercise);
                component.deleteExercise();
                expect(deleteQuizExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
            });
            it('should handle error for quizexercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteQuizExerciseStub.mockReturnValue(throwError(() => error));
                setExerciseInput(fixture, quizExercise);
                component.deleteExercise();
                expect(deleteQuizExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
            });
        });
    });
    describe('deleteProgrammingExercise', () => {
        it('should deleteProgrammingExercise', () => {
            deleteProgrammingExerciseStub.mockReturnValue(of({}));
            setExerciseInput(fixture, programmingExercise);
            component.deleteProgrammingExercise({});
            expect(deleteProgrammingExerciseStub).toHaveBeenCalledOnce();
            expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
        });
        it('should handle error for programmingExercise', () => {
            const error = { message: 'error occurred!' } as HttpErrorResponse;
            deleteProgrammingExerciseStub.mockReturnValue(throwError(() => error));
            setExerciseInput(fixture, programmingExercise);
            component.deleteProgrammingExercise({});
            expect(deleteProgrammingExerciseStub).toHaveBeenCalledOnce();
            expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
        });
    });
    describe('exportQuizById', () => {
        it('should export Quiz, exportAll true', () => {
            quizExerciseServiceFindStub.mockReturnValue(of(quizResponse));
            setExerciseInput(fixture, quizExercise);
            component.exportQuizById(true);
            expect(quizExerciseExportSpy).toHaveBeenCalledOnce();
            expect(quizExerciseExportSpy).toHaveBeenCalledWith({}, true, quizExercise.title);
        });
        it('should export Quiz, exportAll false', () => {
            quizExerciseServiceFindStub.mockReturnValue(of(quizResponse));
            setExerciseInput(fixture, quizExercise);
            component.exportQuizById(false);
            expect(quizExerciseExportSpy).toHaveBeenCalledOnce();
            expect(quizExerciseExportSpy).toHaveBeenCalledWith({}, false, quizExercise.title);
        });
    });
});
