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
            imports: [RouterTestingModule.withRoutes(routes), MockModule(NgbModule), TranslateModule.forRoot(), FontAwesomeTestingModule, FormsModule, HttpClientModule],
            declarations: [
                ExamUpdateComponent,
                MockComponent(FormDateTimePickerComponent),
                MockComponent(MarkdownEditorComponent),
                MockComponent(DataTableComponent),
                DummyComponent,
                MockPipe(ArtemisTranslatePipe),
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
        expect(component.exam.gracePeriod).toEqual(180);
        expect(component.exam.numberOfCorrectionRoundsInExam).toEqual(1);
    });

    it('should validate the dates correctly', () => {
        exam.visibleDate = dayjs().add(1, 'hours');
        exam.startDate = dayjs().add(2, 'hours');
        exam.endDate = dayjs().add(3, 'hours');
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
        expect(updateSpy).toHaveBeenCalledTimes(1);
        expect(component.isSaving).toBeFalse();
    }));

    it('should correctly catch HTTPError when updating the exam', fakeAsync(() => {
        const alertService = TestBed.inject(AlertService);
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        fixture.detectChanges();

        const alertServiceSpy = jest.spyOn(alertService, 'error');
        const updateStub = jest.spyOn(examManagementService, 'update').mockReturnValue(throwError(() => httpError));

        // trigger save
        component.save();
        tick();
        expect(alertServiceSpy).toHaveBeenCalledTimes(1);
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
        expect(createSpy).toHaveBeenCalledTimes(1);
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
        expect(alertServiceSpy).toHaveBeenCalledTimes(1);
        expect(component.isSaving).toBeFalse();

        createStub.mockRestore();
    }));
});
