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
import { ActivatedRoute, convertToParamMap, Params } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisExamModePickerModule } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.module';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';

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
    ];

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

    afterEach(() => {
        jest.restoreAllMocks();
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

    it('should correctly validate the number of correction rounds in a testExams', () => {
        exam.testExam = true;
        exam.numberOfCorrectionRoundsInExam = 1;
        fixture.detectChanges();

        expect(component.exam.numberOfCorrectionRoundsInExam).toBe(0);
        expect(component.isValidNumberOfCorrectionRounds).toBeTrue();

        exam.numberOfCorrectionRoundsInExam = 0;
        fixture.detectChanges();

        expect(component.exam.numberOfCorrectionRoundsInExam).toBe(0);
        expect(component.isValidNumberOfCorrectionRounds).toBeTrue();
    });

    it('should correctly validate the number of correction rounds in a realExam', () => {
        exam.testExam = false;

        exam.numberOfCorrectionRoundsInExam = undefined;
        fixture.detectChanges();

        expect(component.exam.numberOfCorrectionRoundsInExam).toBe(1);
        expect(component.isValidNumberOfCorrectionRounds).toBeTrue();

        exam.numberOfCorrectionRoundsInExam = 1;
        fixture.detectChanges();

        expect(component.exam.numberOfCorrectionRoundsInExam).toBe(1);
        expect(component.isValidNumberOfCorrectionRounds).toBeTrue();

        exam.numberOfCorrectionRoundsInExam = 2;
        fixture.detectChanges();

        expect(component.exam.numberOfCorrectionRoundsInExam).toBe(2);
        expect(component.isValidNumberOfCorrectionRounds).toBeTrue();

        exam.numberOfCorrectionRoundsInExam = 3;
        fixture.detectChanges();

        expect(component.exam.numberOfCorrectionRoundsInExam).toBe(3);
        expect(component.isValidNumberOfCorrectionRounds).toBeFalse();
    });
});
