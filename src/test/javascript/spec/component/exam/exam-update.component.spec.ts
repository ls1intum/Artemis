import { ComponentFixture, TestBed, tick, fakeAsync } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { TranslateModule } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { MockComponent, MockProvider, MockModule, MockPipe, MockDirective } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { Course } from 'app/entities/course.model';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import dayjs from 'dayjs';
import { Component } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, convertToParamMap, Params } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';

chai.use(sinonChai);
const expect = chai.expect;

@Component({
    template: '',
})
class DummyComponent {}

describe('Exam Update Component', function () {
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
                MockComponent(AlertErrorComponent),
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
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.exam).to.exist;
        expect(component.exam.course).to.deep.equal(course);
        expect(component.exam.gracePeriod).to.equal(180);
        expect(component.exam.numberOfCorrectionRoundsInExam).to.equal(1);
    });

    it('should validate the dates correctly', () => {
        exam.visibleDate = dayjs().add(1, 'hours');
        exam.startDate = dayjs().add(2, 'hours');
        exam.endDate = dayjs().add(3, 'hours');
        fixture.detectChanges();
        expect(component.isValidConfiguration).is.true;

        exam.publishResultsDate = dayjs().add(4, 'hours');
        exam.examStudentReviewStart = dayjs().add(5, 'hours');
        exam.examStudentReviewEnd = dayjs().add(6, 'hours');
        fixture.detectChanges();
        expect(component.isValidConfiguration).is.true;

        exam.visibleDate = undefined;
        exam.startDate = undefined;
        exam.endDate = undefined;
        fixture.detectChanges();
        expect(component.isValidConfiguration).is.false;

        exam.visibleDate = moment().add(1, 'hours');
        exam.startDate = moment().add(2, 'hours');
        exam.endDate = moment().add(3, 'hours');
        exam.examStudentReviewEnd = undefined;
        fixture.detectChanges();
        expect(component.isValidConfiguration).is.false;

        exam.examStudentReviewStart = moment().add(6, 'hours');
        exam.examStudentReviewEnd = moment().add(5, 'hours');
        fixture.detectChanges();
        expect(component.isValidConfiguration).is.false;

        exam.examStudentReviewStart = undefined;
        fixture.detectChanges();
        expect(component.isValidConfiguration).is.false;
    });

    it('should update', fakeAsync(() => {
        fixture.detectChanges();

        const updateSpy = sinon.spy(examManagementService, 'update');

        // trigger save
        component.save();
        tick();
        expect(updateSpy).to.have.been.calledOnce;
        expect(component.isSaving).is.false;
    }));

    it('should correctly catch HTTPError when updating the exam', fakeAsync(() => {
        const alertService = TestBed.inject(AlertService);
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        fixture.detectChanges();

        const alertServiceSpy = sinon.spy(alertService, 'error');
        const updateStub = sinon.stub(examManagementService, 'update').returns(throwError(httpError));

        // trigger save
        component.save();
        tick();
        expect(alertServiceSpy).to.have.been.calledOnce;
        expect(component.isSaving).is.false;

        updateStub.restore();
    }));

    it('should create', fakeAsync(() => {
        exam.id = undefined;
        fixture.detectChanges();

        const createSpy = sinon.spy(examManagementService, 'create');

        // trigger save
        component.save();
        tick();
        expect(createSpy).to.have.been.calledOnce;
        expect(component.isSaving).is.false;
    }));

    it('should correctly catch HTTPError when creating the exam', fakeAsync(() => {
        const alertService = TestBed.inject(AlertService);
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        fixture.detectChanges();

        const alertServiceSpy = sinon.spy(alertService, 'error');
        const createStub = sinon.stub(examManagementService, 'create').returns(throwError(httpError));

        // trigger save
        component.save();
        tick();
        expect(alertServiceSpy).to.have.been.calledOnce;
        expect(component.isSaving).is.false;

        createStub.restore();
    }));
});
