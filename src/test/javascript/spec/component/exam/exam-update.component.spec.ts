import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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
import { ActivatedRoute, Params, UrlSegment, convertToParamMap } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisExamModePickerModule } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.module';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
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
    const examWithoutExercises = new Exam();
    examWithoutExercises.id = 1;

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
                                        exam: examWithoutExercises,
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
            examWithoutExercises.visibleDate = dayjs().add(1, 'hours');
            examWithoutExercises.startDate = dayjs().add(2, 'hours');
            examWithoutExercises.endDate = dayjs().add(3, 'hours');
            examWithoutExercises.workingTime = 3600;
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeTrue();

            examWithoutExercises.publishResultsDate = dayjs().add(4, 'hours');
            examWithoutExercises.examStudentReviewStart = dayjs().add(5, 'hours');
            examWithoutExercises.examStudentReviewEnd = dayjs().add(6, 'hours');
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeTrue();

            examWithoutExercises.visibleDate = undefined;
            examWithoutExercises.startDate = undefined;
            examWithoutExercises.endDate = undefined;
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeFalse();

            examWithoutExercises.visibleDate = dayjs().add(1, 'hours');
            examWithoutExercises.startDate = dayjs().add(2, 'hours');
            examWithoutExercises.endDate = dayjs().add(3, 'hours');
            examWithoutExercises.examStudentReviewEnd = undefined;
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeFalse();

            examWithoutExercises.examStudentReviewStart = dayjs().add(6, 'hours');
            examWithoutExercises.examStudentReviewEnd = dayjs().add(5, 'hours');
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeFalse();

            examWithoutExercises.examStudentReviewStart = undefined;
            fixture.detectChanges();
            expect(component.isValidConfiguration).toBeFalse();
        });

        it('should update', fakeAsync(() => {
            fixture.detectChanges();

            const updateSpy = jest.spyOn(examManagementService, 'update').mockReturnValue(of(new HttpResponse<Exam>({ body: { ...examWithoutExercises, id: 1 } })));

            // trigger save
            component.save();
            tick();
            expect(updateSpy).toHaveBeenCalledOnce();
            expect(component.isSaving).toBeFalse();
        }));

        it('should calculate the working time for real exams correctly', () => {
            examWithoutExercises.testExam = false;

            examWithoutExercises.startDate = undefined;
            examWithoutExercises.endDate = dayjs().add(2, 'hours');
            fixture.detectChanges();
            // Without a valid startDate, the workingTime should be 0
            // examWithoutExercises.workingTime is stored in seconds
            expect(examWithoutExercises.workingTime).toBe(0);
            // the component returns the workingTime in Minutes
            expect(component.calculateWorkingTime).toBe(0);

            examWithoutExercises.startDate = dayjs().add(0, 'hours');
            examWithoutExercises.endDate = dayjs().add(2, 'hours');
            fixture.detectChanges();
            expect(examWithoutExercises.workingTime).toBe(7200);
            expect(component.calculateWorkingTime).toBe(120);

            examWithoutExercises.startDate = dayjs().add(0, 'hours');
            examWithoutExercises.endDate = undefined;
            fixture.detectChanges();
            // Without an endDate, the working time should be 0;
            expect(examWithoutExercises.workingTime).toBe(0);
            expect(component.calculateWorkingTime).toBe(0);
        });

        it('should not calculate the working time for test exams', () => {
            examWithoutExercises.testExam = true;
            examWithoutExercises.workingTime = 3600;
            examWithoutExercises.startDate = dayjs().add(0, 'hours');
            examWithoutExercises.endDate = dayjs().add(12, 'hours');
            fixture.detectChanges();
            expect(examWithoutExercises.workingTime).toBe(3600);
            expect(component.calculateWorkingTime).toBe(60);
        });

        it('validates the working time for test exams correctly', () => {
            examWithoutExercises.testExam = true;
            examWithoutExercises.workingTime = undefined;
            fixture.detectChanges();
            expect(component.validateWorkingTime).toBeFalse();

            examWithoutExercises.startDate = undefined;
            examWithoutExercises.endDate = undefined;
            expect(component.validateWorkingTime).toBeFalse();

            examWithoutExercises.startDate = dayjs().add(0, 'hours');
            examWithoutExercises.workingTime = 3600;
            examWithoutExercises.endDate = dayjs().subtract(2, 'hours');
            expect(component.validateWorkingTime).toBeFalse();

            examWithoutExercises.endDate = dayjs().add(2, 'hours');
            expect(component.validateWorkingTime).toBeTrue();

            examWithoutExercises.workingTime = 7200;
            expect(component.validateWorkingTime).toBeTrue();

            examWithoutExercises.workingTime = 10800;
            expect(component.validateWorkingTime).toBeFalse();
        });

        it('validates the working time for real exams correctly', () => {
            examWithoutExercises.testExam = false;

            examWithoutExercises.workingTime = undefined;
            examWithoutExercises.startDate = undefined;
            examWithoutExercises.endDate = undefined;
            fixture.detectChanges();
            expect(component.validateWorkingTime).toBeFalse();

            examWithoutExercises.workingTime = 3600;
            expect(component.validateWorkingTime).toBeFalse();

            examWithoutExercises.startDate = dayjs().add(0, 'hours');
            expect(component.validateWorkingTime).toBeFalse();

            examWithoutExercises.endDate = dayjs().add(1, 'hours');
            expect(component.validateWorkingTime).toBeTrue();
        });

        it('should correctly catch HTTPError when updating the examWithoutExercises', fakeAsync(() => {
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
            examWithoutExercises.id = undefined;
            fixture.detectChanges();

            const createSpy = jest.spyOn(examManagementService, 'create').mockReturnValue(of(new HttpResponse<Exam>({ body: { ...examWithoutExercises, id: 1 } })));

            // trigger save
            component.save();
            tick();
            expect(createSpy).toHaveBeenCalledOnce();
            expect(component.isSaving).toBeFalse();
        }));

        it('should correctly catch HTTPError when creating the examWithoutExercises', fakeAsync(() => {
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

        it('should call the back method on the nav util service on previousState', () => {
            const navUtilService = TestBed.inject(ArtemisNavigationUtilService);
            const spy = jest.spyOn(navUtilService, 'navigateBackWithOptional').mockImplementation();
            component.course = course;
            component.exam = examWithoutExercises;
            examWithoutExercises.id = 1;
            component.previousState();
            expect(spy).toHaveBeenCalledOnce();
            expect(spy).toHaveBeenCalledWith(['course-management', course.id!.toString(), 'exams'], examWithoutExercises.id!.toString());
        });

        it('should correctly validate the number of correction rounds in a test Exams', () => {
            examWithoutExercises.testExam = true;
            examWithoutExercises.numberOfCorrectionRoundsInExam = 1;
            fixture.detectChanges();

            examWithoutExercises.numberOfCorrectionRoundsInExam = 0;
            fixture.detectChanges();

            expect(component.exam.numberOfCorrectionRoundsInExam).toBe(0);
            expect(component.isValidNumberOfCorrectionRounds).toBeTrue();
        });

        it('should correctly validate the number of correction rounds in a realExam', () => {
            examWithoutExercises.testExam = false;

            examWithoutExercises.numberOfCorrectionRoundsInExam = undefined;
            fixture.detectChanges();

            expect(component.exam.numberOfCorrectionRoundsInExam).toBe(1);
            expect(component.isValidNumberOfCorrectionRounds).toBeTrue();

            examWithoutExercises.numberOfCorrectionRoundsInExam = 1;
            fixture.detectChanges();

            expect(component.exam.numberOfCorrectionRoundsInExam).toBe(1);
            expect(component.isValidNumberOfCorrectionRounds).toBeTrue();

            examWithoutExercises.numberOfCorrectionRoundsInExam = 2;
            fixture.detectChanges();

            expect(component.exam.numberOfCorrectionRoundsInExam).toBe(2);
            expect(component.isValidNumberOfCorrectionRounds).toBeTrue();

            examWithoutExercises.numberOfCorrectionRoundsInExam = 3;
            fixture.detectChanges();

            expect(component.exam.numberOfCorrectionRoundsInExam).toBe(3);
            expect(component.isValidNumberOfCorrectionRounds).toBeFalse();
        });
    });

    describe('import exams', () => {
        let alertService: AlertService;

        const course2 = new Course();
        course2.id = 2;

        // Initializing one Exercise Group per Exercise Type
        const exerciseGroup1 = { title: 'exerciseGroup1' } as ExerciseGroup;
        const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup1);
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
            expect(component.exam.title).toBe('RealExam for Testing');
            expect(component.exam.testExam).toBeFalse();
            expect(component.exam.examiner).toBe('Bruegge');
            expect(component.exam.moduleNumber).toBe('IN0006');
            expect(component.exam.courseName).toBe('Artemis');
            expect(component.exam.visibleDate).toBeUndefined();
            expect(component.exam.startDate).toBeUndefined();
            expect(component.exam.endDate).toBeUndefined();
            expect(component.exam.workingTime).toBe(0);
            expect(component.exam.gracePeriod).toBe(90);
            expect(component.exam.maxPoints).toBe(15);
            expect(component.exam.numberOfExercisesInExam).toBe(5);
            expect(component.exam.randomizeExerciseOrder).toBeTrue();
            expect(component.exam.publishResultsDate).toBeUndefined();
            expect(component.exam.examStudentReviewStart).toBeUndefined();
            expect(component.exam.examStudentReviewEnd).toBeUndefined();
            expect(component.exam.numberOfCorrectionRoundsInExam).toBe(2);
            expect(component.exam.startText).toBe('Hello World');
            expect(component.exam.endText).toBe('Goodbye World');
            expect(component.exam.confirmationStartText).toBe('111');
            expect(component.exam.confirmationEndText).toBe('222');
            expect(component.exam.course).toEqual(course);
            expect(component.exam.numberOfRegisteredUsers).toBe(1);
            expect(component.exam.registeredUsers).toBeUndefined();
            expect(component.exam.studentExams).toBeUndefined();
        });

        it('should  perform input of an examWithoutExercises with exercises successfully', () => {
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
            expect(alertSpy).not.toHaveBeenCalled();
        });

        it('should  trigger an alarm for a wrong user input in the examWithoutExercises exercises', () => {
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

            // We need to "fake" a wrong user input here. To not affect the other tests, the wrong examWithoutExercises is a deep clone of the examForImport
            const exerciseGroup2 = { title: 'exerciseGroup2' } as ExerciseGroup;
            const modelingExercise2 = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup2);
            modelingExercise2.id = 2;
            exerciseGroup2.exercises = [modelingExercise2];
            const examWithError = cloneDeep(examForImport);
            examWithError.exerciseGroups = [exerciseGroup2];

            component.exam = examWithError;
            component.examExerciseImportComponent.exam = examWithError;
            component.examExerciseImportComponent.ngOnInit();

            fixture.detectChanges();
            component.save();

            expect(importSpy).not.toHaveBeenCalled();
            expect(alertSpy).toHaveBeenCalledOnce();
        });

        it('should perform import of examWithoutExercises AND correctly process conflict exception from server', () => {
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
