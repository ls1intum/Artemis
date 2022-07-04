import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { TranslateModule } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import { HttpClientModule, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule } from '@angular/forms';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { Course } from 'app/entities/course.model';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import dayjs from 'dayjs/esm';
import { Component } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, convertToParamMap, Params, UrlSegment } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisExamModePickerModule } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.module';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';
import { User } from 'app/core/user/user.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import cloneDeep from 'lodash-es/cloneDeep';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';

@Component({
    template: '',
})
class DummyComponent {}

describe('Exam Update Component', () => {
    let component: ExamUpdateComponent;
    let fixture: ComponentFixture<ExamUpdateComponent>;
    let examManagementService: ExamManagementService;
    const exam = new Exam();
    exam.id = 1;

    const course = new Course();
    course.id = 1;
    const routes = [
        { path: 'course-management/:courseId/exams/:examId', component: DummyComponent },
        { path: 'course-management/:courseId/exams', component: DummyComponent },
        { path: 'course-management/:courseId/exams/:examId/import', component: DummyComponent },
    ];

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('create and edit exams', () => {
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    RouterTestingModule.withRoutes(routes),
                    MockModule(NgbModule),
                    TranslateModule.forRoot(),
                    FontAwesomeTestingModule,
                    FormsModule,
                    HttpClientModule,
                    ArtemisExamModePickerModule,
                ],
                declarations: [
                    ExamUpdateComponent,
                    MockComponent(FormDateTimePickerComponent),
                    MockComponent(MarkdownEditorComponent),
                    MockComponent(DataTableComponent),
                    DummyComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(HelpIconComponent),
                    MockDirective(CustomMinDirective),
                    MockDirective(CustomMaxDirective),
                ],
                providers: [
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    MockDirective(TranslateDirective),
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            data: {
                                subscribe: (fn: (value: Params) => void) =>
                                    fn({
                                        exam,
                                    }),
                            },
                            snapshot: {
                                paramMap: convertToParamMap({
                                    courseId: '1',
                                }),
                            },
                            url: of([{ path: '' } as UrlSegment]),
                        },
                    },
                    MockProvider(AlertService),
                    MockProvider(CourseManagementService, {
                        find: () => {
                            return of(
                                new HttpResponse({
                                    body: course,
                                    status: 200,
                                }),
                            );
                        },
                    }),
                    MockProvider(GradingSystemService, {
                        findGradingScaleForExam: () => {
                            return of(
                                new HttpResponse({
                                    body: new GradingScale(),
                                    status: 200,
                                }),
                            );
                        },
                    }),
                    MockProvider(ExamManagementService, {
                        create: () => {
                            return of(
                                new HttpResponse({
                                    body: {},
                                    status: 200,
                                }),
                            );
                        },
                        update: () => {
                            return of(
                                new HttpResponse({
                                    body: {},
                                    status: 200,
                                }),
                            );
                        },
                    }),
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(ExamUpdateComponent);
            component = fixture.componentInstance;
            examManagementService = fixture.debugElement.injector.get(ExamManagementService);
        });

        it('should initialize', () => {
            fixture.detectChanges();
            expect(fixture).not.toBeNull();
            expect(component.exam).not.toBeNull();
            expect(component.exam.course).toEqual(course);
            expect(component.exam.gracePeriod).toBe(180);
            expect(component.exam.numberOfCorrectionRoundsInExam).toBe(1);
            expect(component.exam.testExam).toBeFalse();
            expect(component.exam.workingTime).toBe(0);
        });

        it('should validate the dates correctly', () => {
            exam.visibleDate = dayjs().add(1, 'hours');
            exam.startDate = dayjs().add(2, 'hours');
            exam.endDate = dayjs().add(3, 'hours');
            exam.workingTime = 3600;
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeTrue();

            exam.publishResultsDate = dayjs().add(4, 'hours');
            exam.examStudentReviewStart = dayjs().add(5, 'hours');
            exam.examStudentReviewEnd = dayjs().add(6, 'hours');
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeTrue();

            exam.visibleDate = undefined;
            exam.startDate = undefined;
            exam.endDate = undefined;
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeFalse();

            exam.visibleDate = dayjs().add(1, 'hours');
            exam.startDate = dayjs().add(2, 'hours');
            exam.endDate = dayjs().add(3, 'hours');
            exam.examStudentReviewEnd = undefined;
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeFalse();

            exam.examStudentReviewStart = dayjs().add(6, 'hours');
            exam.examStudentReviewEnd = dayjs().add(5, 'hours');
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeFalse();

            exam.examStudentReviewStart = undefined;
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeFalse();
        });

        it('should update', fakeAsync(() => {
            fixture.detectChanges();

            const updateSpy = jest.spyOn(examManagementService, 'update');

            // trigger save
            component.save();
            tick();
            expect(updateSpy).toHaveBeenCalledOnce();
            expect(component.isSaving).toBeFalse();
        }));

        it('should calculate the working time for RealExams correctly', () => {
            exam.testExam = false;

            exam.startDate = undefined;
            exam.endDate = dayjs().add(2, 'hours');
            fixture.detectChanges();
            // Without a valid startDate, the workingTime should be 0
            // exam.workingTime is stored in seconds
            expect(exam.workingTime).toBe(0);
            // the component returns the workingTime in Minutes
            expect(component.calculateWorkingTime).toBe(0);

            exam.startDate = dayjs().add(0, 'hours');
            exam.endDate = dayjs().add(2, 'hours');
            fixture.detectChanges();
            expect(exam.workingTime).toBe(7200);
            expect(component.calculateWorkingTime).toBe(120);

            exam.startDate = dayjs().add(0, 'hours');
            exam.endDate = undefined;
            fixture.detectChanges();
            // Without an endDate, the working time should be 0;
            expect(exam.workingTime).toBe(0);
            expect(component.calculateWorkingTime).toBe(0);
        });

        it('should not calculate the working time for testExams', () => {
            exam.testExam = true;
            exam.workingTime = 3600;
            exam.startDate = dayjs().add(0, 'hours');
            exam.endDate = dayjs().add(12, 'hours');
            fixture.detectChanges();
            expect(exam.workingTime).toBe(3600);
            expect(component.calculateWorkingTime).toBe(60);
        });

        it('validates the working time for TestExams correctly', () => {
            exam.testExam = true;
            exam.workingTime = undefined;
            fixture.detectChanges();
            expect(component.validateWorkingTime).toBeFalse();

            exam.startDate = undefined;
            exam.endDate = undefined;
            expect(component.validateWorkingTime).toBeFalse();

            exam.startDate = dayjs().add(0, 'hours');
            exam.workingTime = 3600;
            exam.endDate = dayjs().subtract(2, 'hours');
            expect(component.validateWorkingTime).toBeFalse();

            exam.endDate = dayjs().add(2, 'hours');
            expect(component.validateWorkingTime).toBeTrue();

            exam.workingTime = 7200;
            expect(component.validateWorkingTime).toBeTrue();

            exam.workingTime = 10800;
            expect(component.validateWorkingTime).toBeFalse();
        });

        it('validates the working time for RealExams correctly', () => {
            exam.testExam = false;

            exam.workingTime = undefined;
            exam.startDate = undefined;
            exam.endDate = undefined;
            fixture.detectChanges();
            expect(component.validateWorkingTime).toBeFalse();

            exam.workingTime = 3600;
            expect(component.validateWorkingTime).toBeFalse();

            exam.startDate = dayjs().add(0, 'hours');
            expect(component.validateWorkingTime).toBeFalse();

            exam.endDate = dayjs().add(1, 'hours');
            expect(component.validateWorkingTime).toBeTrue();
        });

        it('should correctly catch HTTPError when updating the exam', fakeAsync(() => {
            const alertService = TestBed.inject(AlertService);
            const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
            fixture.detectChanges();

            const alertServiceSpy = jest.spyOn(alertService, 'error');
            const updateStub = jest.spyOn(examManagementService, 'update').mockReturnValue(throwError(() => httpError));

            // trigger save
            component.save();
            tick();
            expect(alertServiceSpy).toHaveBeenCalledOnce();
            expect(component.isSaving).toBeFalse();

            updateStub.mockRestore();
        }));

        it('should create', fakeAsync(() => {
            exam.id = undefined;
            fixture.detectChanges();

            const createSpy = jest.spyOn(examManagementService, 'create');

            // trigger save
            component.save();
            tick();
            expect(createSpy).toHaveBeenCalledOnce();
            expect(component.isSaving).toBeFalse();
        }));

        it('should correctly catch HTTPError when creating the exam', fakeAsync(() => {
            const alertService = TestBed.inject(AlertService);
            const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
            fixture.detectChanges();

            const alertServiceSpy = jest.spyOn(alertService, 'error');
            const createStub = jest.spyOn(examManagementService, 'create').mockReturnValue(throwError(() => httpError));

            // trigger save
            component.save();
            tick();
            expect(alertServiceSpy).toHaveBeenCalledOnce();
            expect(component.isSaving).toBeFalse();

            createStub.mockRestore();
        }));
    });

    describe('import exams', () => {
        let alertService: AlertService;

        const course2 = new Course();
        course2.id = 2;

        // Initializing one Exercise Group per Exercise Type
        let exerciseGroup1 = { title: 'exerciseGroup1' } as ExerciseGroup;
        let modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup1);
        modelingExercise.id = 1;
        modelingExercise.title = 'ModelingExercise';
        exerciseGroup1.exercises = [modelingExercise];

        const timeNow = dayjs();

        const examForImport = new Exam();
        examForImport.id = 3;
        examForImport.title = 'RealExam for Testing';
        examForImport.testExam = false;
        examForImport.examiner = 'Bruegge';
        examForImport.moduleNumber = 'IN0006';
        examForImport.courseName = 'Artemis';
        examForImport.visibleDate = timeNow.subtract(2, 'hours');
        examForImport.startDate = timeNow.subtract(1, 'hours');
        examForImport.endDate = timeNow.add(1, 'hours');
        examForImport.workingTime = 2 * 60 * 60;
        examForImport.gracePeriod = 90;
        examForImport.maxPoints = 15;
        examForImport.numberOfExercisesInExam = 5;
        examForImport.randomizeExerciseOrder = true;
        examForImport.publishResultsDate = timeNow.add(1, 'days');
        examForImport.examStudentReviewStart = timeNow.add(2, 'days');
        examForImport.examStudentReviewEnd = timeNow.add(3, 'days');
        examForImport.numberOfCorrectionRoundsInExam = 2;
        examForImport.startText = 'Hello World';
        examForImport.endText = 'Goodbye World';
        examForImport.confirmationStartText = '111';
        examForImport.confirmationEndText = '222';
        examForImport.course = course2;
        examForImport.numberOfRegisteredUsers = 1;
        examForImport.exerciseGroups = [exerciseGroup1];
        examForImport.registeredUsers = [new User(5)];
        examForImport.studentExams = [new StudentExam()];

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    RouterTestingModule.withRoutes(routes),
                    MockModule(NgbModule),
                    TranslateModule.forRoot(),
                    FontAwesomeTestingModule,
                    FormsModule,
                    HttpClientModule,
                    ArtemisExamModePickerModule,
                ],
                declarations: [
                    ExamUpdateComponent,
                    ExamExerciseImportComponent,
                    MockComponent(FormDateTimePickerComponent),
                    MockComponent(MarkdownEditorComponent),
                    MockComponent(DataTableComponent),
                    DummyComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(HelpIconComponent),
                    MockDirective(CustomMinDirective),
                    MockDirective(CustomMaxDirective),
                    MockComponent(ButtonComponent),
                    MockComponent(HelpIconComponent),
                    MockComponent(DifficultyBadgeComponent),
                ],
                providers: [
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    MockDirective(TranslateDirective),
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            data: {
                                subscribe: (fn: (value: Params) => void) =>
                                    fn({
                                        exam: examForImport,
                                    }),
                            },
                            snapshot: {
                                paramMap: convertToParamMap({
                                    courseId: '1',
                                    examId: '3',
                                }),
                            },
                            url: of([{ path: 'import' } as UrlSegment]),
                        },
                    },
                    MockProvider(AlertService),
                    MockProvider(CourseManagementService, {
                        find: () => {
                            return of(
                                new HttpResponse({
                                    body: course,
                                    status: 200,
                                }),
                            );
                        },
                    }),
                    MockProvider(GradingSystemService, {
                        findGradingScaleForExam: () => {
                            return of(
                                new HttpResponse({
                                    body: new GradingScale(),
                                    status: 200,
                                }),
                            );
                        },
                    }),
                    /* MockProvider(ExamManagementService, {
                        import: () => {
                            return of(
                                new HttpResponse({
                                    body: {},
                                    status: 200,
                                }),
                            );
                        },
                    }),*/
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(ExamUpdateComponent);
            component = fixture.componentInstance;
            examManagementService = fixture.debugElement.injector.get(ExamManagementService);
            alertService = fixture.debugElement.injector.get(AlertService);
        });

        it('should initialize without id and dates set', () => {
            fixture.detectChanges();
            expect(fixture).not.toBeNull();
            expect(component.isImport).toBeTrue();
            expect(component.exam).not.toBeNull();
            expect(component.exam.id).toBeUndefined();
            expect(component.exam.title).toEqual('RealExam for Testing');
            expect(component.exam.testExam).toBeFalse();
            expect(component.exam.examiner).toEqual('Bruegge');
            expect(component.exam.moduleNumber).toEqual('IN0006');
            expect(component.exam.courseName).toEqual('Artemis');
            expect(component.exam.visibleDate).toBeUndefined();
            expect(component.exam.startDate).toBeUndefined();
            expect(component.exam.endDate).toBeUndefined();
            expect(component.exam.workingTime).toEqual(0);
            expect(component.exam.gracePeriod).toEqual(90);
            expect(component.exam.maxPoints).toEqual(15);
            expect(component.exam.numberOfExercisesInExam).toEqual(5);
            expect(component.exam.randomizeExerciseOrder).toEqual(true);
            expect(component.exam.publishResultsDate).toBeUndefined();
            expect(component.exam.examStudentReviewStart).toBeUndefined();
            expect(component.exam.examStudentReviewEnd).toBeUndefined();
            expect(component.exam.numberOfCorrectionRoundsInExam).toEqual(2);
            expect(component.exam.startText).toEqual('Hello World');
            expect(component.exam.endText).toEqual('Goodbye World');
            expect(component.exam.confirmationStartText).toEqual('111');
            expect(component.exam.confirmationEndText).toEqual('222');
            expect(component.exam.course).toEqual(course);
            expect(component.exam.numberOfRegisteredUsers).toEqual(1);
            expect(component.exam.registeredUsers).toBeUndefined();
            expect(component.exam.studentExams).toBeUndefined();
        });

        it('should  perform input of exercise groups successfully', () => {
            const importSpy = jest.spyOn(examManagementService, 'import').mockReturnValue(
                of(
                    new HttpResponse({
                        status: 200,
                        body: examForImport,
                    }),
                ),
            );
            const alertSpy = jest.spyOn(alertService, 'error');
            fixture.detectChanges();
            component.save();
            expect(importSpy).toHaveBeenCalledOnce();
            expect(importSpy).toHaveBeenCalledWith(1, examForImport);
            expect(alertSpy).toHaveBeenCalledTimes(0);
        });

        it('should  trigger an alarm for a wrong user input', () => {
            const importSpy = jest.spyOn(examManagementService, 'import').mockReturnValue(
                of(
                    new HttpResponse({
                        status: 200,
                        body: examForImport,
                    }),
                ),
            );
            const alertSpy = jest.spyOn(alertService, 'error');

            fixture.detectChanges();

            let exerciseGroup2 = { title: 'exerciseGroup2' } as ExerciseGroup;
            let modelingExercise2 = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup2);
            modelingExercise2.id = 2;
            exerciseGroup2.exercises = [modelingExercise2];
            const examWithError = cloneDeep(examForImport);
            examWithError.exerciseGroups = [exerciseGroup2];

            component.exam = examWithError;
            component.examExerciseImportComponent.exam = examWithError;
            component.examExerciseImportComponent.ngOnInit();
            fixture.detectChanges();
            component.save();

            expect(importSpy).toHaveBeenCalledTimes(0);
            expect(alertSpy).toHaveBeenCalledOnce();
        });

        it('should perform input of exercise groups AND correctly process conflict exception from server', () => {
            const preCheckError = new HttpErrorResponse({
                error: { errorKey: 'examContainsProgrammingExercisesWithInvalidKey', numberOfInvalidProgrammingExercises: 2, params: { exerciseGroups: [exerciseGroup1] } },
                status: 400,
            });
            const importSpy = jest.spyOn(examManagementService, 'import').mockReturnValue(throwError(() => preCheckError));
            const alertSpy = jest.spyOn(alertService, 'error');

            fixture.detectChanges();
            component.save();

            expect(importSpy).toHaveBeenCalledOnce();
            expect(importSpy).toHaveBeenCalledWith(1, examForImport);
            expect(alertSpy).toHaveBeenCalledOnce();
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: 2 });
        });

        it('should perform input of exercise groups AND correctly process arbitrary exception from server', () => {
            const error = new HttpErrorResponse({
                status: 400,
            });
            const importSpy = jest.spyOn(examManagementService, 'import').mockReturnValue(throwError(() => error));
            const alertSpy = jest.spyOn(alertService, 'error');

            fixture.detectChanges();
            component.save();
            expect(importSpy).toHaveBeenCalledOnce();
            expect(importSpy).toHaveBeenCalledWith(1, examForImport);
            expect(alertSpy).toHaveBeenCalledOnce();
        });
    });
});
