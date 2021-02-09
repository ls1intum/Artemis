import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { TranslatePipe } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ActivatedRoute, Data } from '@angular/router';
import { JhiTranslateDirective } from 'ng-jhipster';
import { RouterTestingModule } from '@angular/router/testing';
import { By } from '@angular/platform-browser';
import { Location } from '@angular/common';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { Exam } from 'app/entities/exam.model';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Course } from 'app/entities/course.model';
import { Component } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ExamChecklistCheckComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-check/exam-checklist-check.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';

chai.use(sinonChai);
const expect = chai.expect;

@Component({
    template: '',
})
class DummyComponent {}

describe('ExamDetailComponent', () => {
    let examDetailComponentFixture: ComponentFixture<ExamDetailComponent>;
    let examDetailComponent: ExamDetailComponent;

    const exampleHTML = '<h1>Sample Markdown</h1>';
    const exam = new Exam();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes([
                    { path: 'course-management/:courseId/exams/:examId/edit', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/exercise-groups', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/tutor-exam-dashboard', component: DummyComponent },
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
                MockPipe(TranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
                MockComponent(FaIconComponent),
                MockDirective(JhiTranslateDirective),
                MockDirective(HasAnyAuthorityDirective),
                ExamChecklistComponent,
                ExamChecklistCheckComponent,
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
            });
    });

    beforeEach(function () {
        // reset exam
        exam.id = 1;
        exam.course = new Course();
        exam.course.id = 1;
        exam.title = 'Example Exam';
        exam.numberOfGeneratedStudentExams = 1;
        exam.numberOfRegisteredUsers = 3;
        exam.maxPoints = 100;
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
        const dashboardButton = examDetailComponentFixture.debugElement.query(By.css('#tutor-exam-dashboard-button')).nativeElement;
        dashboardButton.click();
        examDetailComponentFixture.whenStable().then(() => {
            expect(location.path()).to.equal('/course-management/1/exams/1/tutor-exam-dashboard');
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
});
