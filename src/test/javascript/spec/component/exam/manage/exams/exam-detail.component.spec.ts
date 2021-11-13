import { Location } from '@angular/common';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Data } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExamChecklistCheckComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-check/exam-checklist-check.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';

chai.use(sinonChai);
const expect = chai.expect;

@Component({
    template: '',
})
class DummyComponent {}

describe('ExamDetailComponent', () => {
    let examDetailComponentFixture: ComponentFixture<ExamDetailComponent>;
    let examDetailComponent: ExamDetailComponent;
    let service: ExamManagementService;

    const exampleHTML = '<h1>Sample Markdown</h1>';
    const exam = new Exam();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes([
                    { path: 'course-management/:courseId/exams/:examId/edit', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/exercise-groups', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/assessment-dashboard', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/scores', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/student-exams', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/test-runs', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/students', component: DummyComponent },
                ]),
                HttpClientTestingModule,
            ],
            declarations: [
                ExamDetailComponent,
                DummyComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockDirective(HasAnyAuthorityDirective),
                ExamChecklistComponent,
                ExamChecklistCheckComponent,
                ExamChecklistExerciseGroupTableComponent,
                ProgressBarComponent,
                MockDirective(NgbTooltip),
                MockComponent(CourseExamArchiveButtonComponent),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: {
                            subscribe: (fn: (value: Data) => void) =>
                                fn({
                                    exam,
                                }),
                        },
                        snapshot: {},
                    },
                },
                MockProvider(AccountService, {
                    isAtLeastInstructorInCourse: () => true,
                    isAtLeastEditorInCourse: () => true,
                }),
                MockProvider(ArtemisMarkdownService, {
                    safeHtmlForMarkdown: () => exampleHTML,
                }),
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                examDetailComponentFixture = TestBed.createComponent(ExamDetailComponent);
                examDetailComponent = examDetailComponentFixture.componentInstance;
                service = TestBed.inject(ExamManagementService);
            });
    });

    beforeEach(function () {
        // reset exam
        exam.id = 1;
        exam.course = new Course();
        exam.course.id = 1;
        exam.title = 'Example Exam';
        exam.numberOfRegisteredUsers = 3;
        exam.maxPoints = 100;
        exam.exerciseGroups = [];
        examDetailComponent.exam = exam;
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should load exam from route and display it to user', () => {
        examDetailComponentFixture.detectChanges();
        expect(examDetailComponent).to.be.ok;
        // stand in for other properties too who are simply loaded from the exam and displayed in spans
        const titleSpan = examDetailComponentFixture.debugElement.query(By.css('#examTitle')).nativeElement;
        expect(titleSpan).to.be.ok;
        expect(titleSpan.innerHTML).to.equal(exam.title);
    });

    it('should correctly route to edit subpage', fakeAsync(() => {
        const location = examDetailComponentFixture.debugElement.injector.get(Location);
        examDetailComponentFixture.detectChanges();
        const editButton = examDetailComponentFixture.debugElement.query(By.css('#editButton')).nativeElement;
        editButton.click();
        examDetailComponentFixture.whenStable().then(() => {
            expect(location.path()).to.equal('/course-management/1/exams/1/edit');
        });
    }));

    it('should correctly route to student exams subpage', fakeAsync(() => {
        const location = examDetailComponentFixture.debugElement.injector.get(Location);
        examDetailComponentFixture.detectChanges();
        const studentExamsButton = examDetailComponentFixture.debugElement.query(By.css('#studentExamsButton')).nativeElement;
        studentExamsButton.click();
        examDetailComponentFixture.whenStable().then(() => {
            expect(location.path()).to.equal('/course-management/1/exams/1/student-exams');
        });
    }));

    it('should correctly route to dashboard', fakeAsync(() => {
        const location = examDetailComponentFixture.debugElement.injector.get(Location);
        examDetailComponentFixture.detectChanges();
        const dashboardButton = examDetailComponentFixture.debugElement.query(By.css('#assessment-dashboard-button')).nativeElement;
        dashboardButton.click();
        examDetailComponentFixture.whenStable().then(() => {
            expect(location.path()).to.equal('/course-management/1/exams/1/assessment-dashboard');
        });
    }));

    it('should correctly route to exercise groups', fakeAsync(() => {
        const location = examDetailComponentFixture.debugElement.injector.get(Location);
        examDetailComponentFixture.detectChanges();
        const dashboardButton = examDetailComponentFixture.debugElement.query(By.css('#exercises-button-groups')).nativeElement;
        dashboardButton.click();
        examDetailComponentFixture.whenStable().then(() => {
            expect(location.path()).to.equal('/course-management/1/exams/1/exercise-groups');
        });
    }));

    it('should correctly route to scores', fakeAsync(() => {
        const location = examDetailComponentFixture.debugElement.injector.get(Location);
        examDetailComponentFixture.detectChanges();
        const scoresButton = examDetailComponentFixture.debugElement.query(By.css('#scores-button')).nativeElement;
        scoresButton.click();
        examDetailComponentFixture.whenStable().then(() => {
            expect(location.path()).to.equal('/course-management/1/exams/1/scores');
        });
    }));

    it('should correctly route to students', fakeAsync(() => {
        const location = examDetailComponentFixture.debugElement.injector.get(Location);
        examDetailComponentFixture.detectChanges();
        const studentsButton = examDetailComponentFixture.debugElement.query(By.css('#students-button')).nativeElement;
        studentsButton.click();
        examDetailComponentFixture.whenStable().then(() => {
            expect(location.path()).to.equal('/course-management/1/exams/1/students');
        });
    }));

    it('should correctly route to test runs', fakeAsync(() => {
        const location = examDetailComponentFixture.debugElement.injector.get(Location);
        examDetailComponentFixture.detectChanges();
        const studentsButton = examDetailComponentFixture.debugElement.query(By.css('#testrun-button')).nativeElement;
        studentsButton.click();
        examDetailComponentFixture.whenStable().then(() => {
            expect(location.path()).to.equal('/course-management/1/exams/1/test-runs');
        });
    }));

    it('should return general routes correctly', () => {
        const route = examDetailComponent.getExamRoutesByIdentifier('edit');
        expect(JSON.stringify(route)).to.be.equal(JSON.stringify(['/course-management', exam.course!.id, 'exams', exam.id, 'edit']));
    });

    it('Should reset an exam when reset exam is called', () => {
        // GIVEN
        examDetailComponent.exam = { ...exam, studentExams: [{ id: 1 }] };
        const responseFakeReset = { body: exam } as HttpResponse<Exam>;
        sinon.replace(service, 'reset', sinon.fake.returns(of(responseFakeReset)));

        // WHEN
        examDetailComponent.resetExam();

        // THEN
        expect(service.reset).to.have.been.calledOnce;
        expect(examDetailComponent.exam).to.deep.eq(exam);
    });
});
