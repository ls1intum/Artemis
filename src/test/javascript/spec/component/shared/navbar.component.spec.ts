import { HttpResponse } from '@angular/common/http';
import { Directive, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NgbCollapse, NgbDropdown } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { LectureService } from 'app/lecture/lecture.service';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ActiveMenuDirective } from 'app/shared/layouts/navbar/active-menu.directive';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { LoadingNotificationComponent } from 'app/shared/notification/loading-notification/loading-notification.component';
import { NotificationSidebarComponent } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../test.module';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';

chai.use(sinonChai);
const expect = chai.expect;

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[routerLink]' })
export class MockRouterLinkDirective {
    @Input('routerLink') data: any;
}

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[routerLinkActiveOptions]' })
export class MockRouterLinkActiveOptionsDirective {
    @Input('routerLinkActiveOptions') data: any;
}

class MockBreadcrumb {
    label: string;
    uri: string;
    translate: boolean;
}

describe('NavbarComponent', () => {
    let fixture: ComponentFixture<NavbarComponent>;
    let component: NavbarComponent;
    let courseManagementStub: sinon.SinonStub;
    let exerciseStub: sinon.SinonStub;

    const router = new MockRouter();
    router.setUrl('');

    const courseManagementCrumb = {
        label: 'global.menu.course',
        translate: true,
        uri: '/course-management/',
    } as MockBreadcrumb;

    const testCourseCrumb = {
        label: 'Test Course',
        translate: false,
        uri: '/course-management/1/',
    } as MockBreadcrumb;

    const programmingExercisesCrumb = {
        label: 'artemisApp.course.exercises',
        translate: true,
        uri: '/course-management/1/programming-exercises/',
    } as MockBreadcrumb;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                NavbarComponent,
                MockDirective(NgbCollapse),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(NgbDropdown),
                MockDirective(ActiveMenuDirective),
                MockDirective(TranslateDirective),
                MockDirective(MockRouterLinkDirective),
                MockDirective(MockRouterLinkActiveOptionsDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(FindLanguageFromKeyPipe),
                MockComponent(NotificationSidebarComponent),
                MockComponent(GuidedTourComponent),
                MockComponent(LoadingNotificationComponent),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(NavbarComponent);
                component = fixture.componentInstance;

                const courseManagementService = fixture.debugElement.injector.get(CourseManagementService);
                courseManagementStub = sinon.stub(courseManagementService, 'getTitle').returns(of({ body: 'Test Course' } as HttpResponse<string>));

                const exerciseService = fixture.debugElement.injector.get(ExerciseService);
                exerciseStub = sinon.stub(exerciseService, 'getTitle').returns(of({ body: 'Test Exercise' } as HttpResponse<string>));
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should not build breadcrumbs for students', () => {
        const testUrl = '/courses/1/exercises';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).to.equal(0);
    });

    it('should build breadcrumbs for course management', () => {
        const testUrl = '/course-management';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).to.equal(1);

        // Use matching here to ignore non-semantic differences between objects
        sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
    });

    it('should ignore query parameters', () => {
        const testUrl = '/course-management?query=param';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).to.equal(1);

        // Use matching here to ignore non-semantic differences between objects
        sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
    });

    it('should build breadcrumbs for system notification management', () => {
        const testUrl = '/admin/system-notification-management/1/edit';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).to.equal(3);

        // Use matching here to ignore non-semantic differences between objects
        const systemBreadcrumb = { label: 'artemisApp.systemNotification.systemNotifications', translate: true, uri: '/admin/system-notification-management/' } as MockBreadcrumb;
        sinon.assert.match(component.breadcrumbs[0], systemBreadcrumb);
        sinon.assert.match(component.breadcrumbs[1], { label: '1', translate: false, uri: '/admin/system-notification-management/1/' } as MockBreadcrumb);
        sinon.assert.match(component.breadcrumbs[2], { label: 'global.generic.edit', translate: true, uri: '/admin/system-notification-management/1/edit/' } as MockBreadcrumb);
    });

    it('should build breadcrumbs for user management', () => {
        const testUrl = '/admin/user-management/test_user';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).to.equal(2);

        // Use matching here to ignore non-semantic differences between objects
        sinon.assert.match(component.breadcrumbs[0], { label: 'userManagement.home.title', translate: true, uri: '/admin/user-management/' } as MockBreadcrumb);
        sinon.assert.match(component.breadcrumbs[1], { label: 'test_user', translate: false, uri: '/admin/user-management/test_user/' } as MockBreadcrumb);
    });

    it('should build breadcrumbs for organization management', () => {
        const testUrl = '/admin/organization-management/1';
        router.setUrl(testUrl);

        const organizationService = fixture.debugElement.injector.get(OrganizationManagementService);
        const organizationStub = sinon.stub(organizationService, 'getTitle').returns(of({ body: 'Organization Name' } as HttpResponse<string>));

        fixture.detectChanges();

        expect(organizationStub).to.have.been.calledWith(1);
        expect(component.breadcrumbs.length).to.equal(2);

        // Use matching here to ignore non-semantic differences between objects
        sinon.assert.match(component.breadcrumbs[0], { label: 'organizationManagement.title', translate: true, uri: '/admin/organization-management/' } as MockBreadcrumb);
        sinon.assert.match(component.breadcrumbs[1], { label: 'Organization Name', translate: false, uri: '/admin/organization-management/1/' } as MockBreadcrumb);
    });

    it('should not error without translation', () => {
        const testUrl = '/admin/route-without-translation';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).to.equal(1);

        // Use matching here to ignore non-semantic differences between objects
        sinon.assert.match(component.breadcrumbs[0], { label: 'route-without-translation', translate: false, uri: '/admin/route-without-translation/' } as MockBreadcrumb);
    });

    describe('Special Cases for Breadcrumbs', function () {
        it('programming exercise import', () => {
            const testUrl = '/course-management/1/programming-exercises/import/2';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);

            const importCrumb = {
                label: 'artemisApp.exercise.import.table.doImport',
                translate: true,
                uri: '/course-management/1/programming-exercises/import/2/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).to.equal(4);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], programmingExercisesCrumb);
            sinon.assert.match(component.breadcrumbs[3], importCrumb);
        });

        it('programming exercise grading', () => {
            const testUrl = '/course-management/1/programming-exercises/2/grading/test-cases';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(exerciseStub).to.have.been.calledWith(2);

            const gradingCrumb = {
                label: 'artemisApp.programmingExercise.configureGrading.shortTitle',
                translate: true,
                uri: '/course-management/1/programming-exercises/2/grading/test-cases/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).to.equal(5);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], programmingExercisesCrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Test Exercise', translate: false, uri: '/course-management/1/programming-exercises/2/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[4], gradingCrumb);
        });

        it('programming exercise new assessment', () => {
            const testUrl = '/course-management/1/programming-exercises/2/code-editor/new/assessment';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(exerciseStub).to.have.been.calledWith(2);

            const assessmentCrumb = {
                label: 'artemisApp.assessment.assessment',
                translate: true,
                uri: '/course-management/1/programming-exercises/2/code-editor/new/assessment/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).to.equal(5);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], programmingExercisesCrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Test Exercise', translate: false, uri: '/course-management/1/programming-exercises/2/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[4], assessmentCrumb);
        });

        it('programming exercise hints', () => {
            const testUrl = '/course-management/1/exercises/2/hints/3';
            router.setUrl(testUrl);

            const hintService = fixture.debugElement.injector.get(ExerciseHintService);
            const hintsStub = sinon.stub(hintService, 'getTitle').returns(of({ body: 'Exercise Hint' } as HttpResponse<string>));

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(exerciseStub).to.have.been.calledWith(2);
            expect(hintsStub).to.have.been.calledWith(3);

            const hintsCrumb = {
                label: 'artemisApp.exerciseHint.home.title',
                translate: true,
                uri: '/course-management/1/exercises/2/hints/',
            } as MockBreadcrumb;

            const hintCrumb = {
                label: 'Exercise Hint',
                translate: false,
                uri: '/course-management/1/exercises/2/hints/3/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).to.equal(6);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], { label: 'artemisApp.course.exercises', translate: true, uri: '/course-management/1/exercises/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Test Exercise', translate: false, uri: '/course-management/1/exercises/2/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[4], hintsCrumb);
            sinon.assert.match(component.breadcrumbs[5], hintCrumb);
        });

        it('text exercise feedback conflict', () => {
            const testUrl = '/course-management/1/text-exercises/2/submissions/3/text-feedback-conflict/4';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(exerciseStub).to.have.been.calledWith(2);

            const submissionsCrumb = {
                label: 'artemisApp.exercise.submissions',
                translate: true,
                uri: '/course-management/1/text-exercises/2/submissions/',
            } as MockBreadcrumb;

            const conflictCrumb = {
                label: 'artemisApp.textAssessment.title',
                translate: true,
                uri: '/course-management/1/text-exercises/2/submissions/3/text-feedback-conflict/4/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).to.equal(6);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], { label: 'artemisApp.course.exercises', translate: true, uri: '/course-management/1/text-exercises/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Test Exercise', translate: false, uri: '/course-management/1/text-exercises/2/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[4], submissionsCrumb);
            sinon.assert.match(component.breadcrumbs[5], conflictCrumb);
        });

        it('exercise assessment dashboard', () => {
            let courseId = 1;
            let exerciseId = 2;
            const testUrl = `/course-management/${courseId}/assessment-dashboard/${exerciseId}`;
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(courseId);
            expect(exerciseStub).to.have.been.calledWith(exerciseId);

            expect(component.breadcrumbs.length).to.equal(4);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], {
                label: 'artemisApp.assessmentDashboard.home.title',
                translate: true,
                uri: '/course-management/1/assessment-dashboard/',
            } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[3], {
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/assessment-dashboard/2/'
            } as MockBreadcrumb);
        });

        it('modeling exercise example submission', () => {
            const testUrl = '/course-management/1/modeling-exercises/2/example-submissions/new';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(exerciseStub).to.have.been.calledWith(2);

            const submissionCrumb = {
                label: 'artemisApp.exampleSubmission.home.title',
                translate: true,
                uri: '/course-management/1/modeling-exercises/2/example-submissions/',
            } as MockBreadcrumb;

            const editorSubmissionCrumb = {
                label: 'artemisApp.exampleSubmission.home.editor',
                translate: true,
                uri: '/course-management/1/modeling-exercises/2/example-submissions/new/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).to.equal(6);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], {
                label: 'artemisApp.course.exercises',
                translate: true,
                uri: '/course-management/1/modeling-exercises/',
            } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Test Exercise', translate: false, uri: '/course-management/1/modeling-exercises/2/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[4], submissionCrumb);
            sinon.assert.match(component.breadcrumbs[5], editorSubmissionCrumb);
        });

        it('modeling exercise example submission', () => {
            const testUrl = '/course-management/1/modeling-exercises/2/example-submissions/3';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(exerciseStub).to.have.been.calledWith(2);

            const submissionCrumb = {
                label: 'artemisApp.exampleSubmission.home.title',
                translate: true,
                uri: '/course-management/1/modeling-exercises/2/example-submissions/',
            } as MockBreadcrumb;

            const editorSubmissionCrumb = {
                label: 'artemisApp.exampleSubmission.home.editor',
                translate: true,
                uri: '/course-management/1/modeling-exercises/2/example-submissions/3/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).to.equal(6);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], {
                label: 'artemisApp.course.exercises',
                translate: true,
                uri: '/course-management/1/modeling-exercises/',
            } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Test Exercise', translate: false, uri: '/course-management/1/modeling-exercises/2/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[4], submissionCrumb);
            sinon.assert.match(component.breadcrumbs[5], editorSubmissionCrumb);
        });

        it('lecture units', () => {
            const testUrl = '/course-management/1/lectures/2/unit-management/text-units/create';
            router.setUrl(testUrl);

            const lectureService = fixture.debugElement.injector.get(LectureService);
            const lectureStub = sinon.stub(lectureService, 'getTitle').returns(of({ body: 'Test Lecture' } as HttpResponse<string>));

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(lectureStub).to.have.been.calledWith(2);

            const unitManagementCrumb = {
                label: 'artemisApp.lectureUnit.home.title',
                translate: true,
                uri: '/course-management/1/lectures/2/unit-management/',
            } as MockBreadcrumb;

            const createCrumb = {
                label: 'global.generic.create',
                translate: true,
                uri: '/course-management/1/lectures/2/unit-management/text-units/create/',
            };

            expect(component.breadcrumbs.length).to.equal(6);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], { label: 'artemisApp.lecture.home.title', translate: true, uri: '/course-management/1/lectures/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Test Lecture', translate: false, uri: '/course-management/1/lectures/2/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[4], unitManagementCrumb);
            sinon.assert.match(component.breadcrumbs[5], createCrumb);
        });

        it('apollon diagrams', () => {
            const testUrl = '/course-management/1/apollon-diagrams/2';
            router.setUrl(testUrl);

            const apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
            const apollonStub = sinon.stub(apollonDiagramService, 'getTitle').returns(of({ body: 'Apollon Diagram' } as HttpResponse<string>));

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(apollonStub).to.have.been.calledWith(2);

            expect(component.breadcrumbs.length).to.equal(4);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], {
                label: 'artemisApp.apollonDiagram.home.title',
                translate: true,
                uri: '/course-management/1/apollon-diagrams/',
            } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Apollon Diagram', translate: false, uri: '/course-management/1/apollon-diagrams/2/' } as MockBreadcrumb);
        });

        it('exam exercise groups', () => {
            const testUrl = '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/new';
            router.setUrl(testUrl);

            const examService = fixture.debugElement.injector.get(ExamManagementService);
            const examStub = sinon.stub(examService, 'getTitle').returns(of({ body: 'Test Exam' } as HttpResponse<string>));

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(examStub).to.have.been.calledWith(2);

            const exerciseGroupsCrumb = {
                label: 'artemisApp.examManagement.exerciseGroups',
                translate: true,
                uri: '/course-management/1/exams/2/exercise-groups/',
            };
            const createCrumb = {
                label: 'global.generic.create',
                translate: true,
                uri: '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/new/',
            };

            expect(component.breadcrumbs.length).to.equal(6);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], { label: 'artemisApp.examManagement.title', translate: true, uri: '/course-management/1/exams/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Test Exam', translate: false, uri: '/course-management/1/exams/2/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[4], exerciseGroupsCrumb);
            sinon.assert.match(component.breadcrumbs[5], createCrumb);
        });

        it('exam exercise plagiarism', () => {
            const testUrl = '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/4/plagiarism';
            router.setUrl(testUrl);

            const examService = fixture.debugElement.injector.get(ExamManagementService);
            const examStub = sinon.stub(examService, 'getTitle').returns(of({ body: 'Test Exam' } as HttpResponse<string>));

            fixture.detectChanges();

            expect(courseManagementStub).to.have.been.calledWith(1);
            expect(examStub).to.have.been.calledWith(2);
            expect(exerciseStub).to.have.been.calledWith(4);

            const exerciseGroupsCrumb = {
                label: 'artemisApp.examManagement.exerciseGroups',
                translate: true,
                uri: '/course-management/1/exams/2/exercise-groups/',
            };
            const exerciseCrumb = {
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/4/',
            };
            const plagiarismCrumb = {
                label: 'artemisApp.plagiarism.plagiarism-detection',
                translate: true,
                uri: '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/4/plagiarism/',
            };

            expect(component.breadcrumbs.length).to.equal(7);

            // Use matching here to ignore non-semantic differences between objects
            sinon.assert.match(component.breadcrumbs[0], courseManagementCrumb);
            sinon.assert.match(component.breadcrumbs[1], testCourseCrumb);
            sinon.assert.match(component.breadcrumbs[2], { label: 'artemisApp.examManagement.title', translate: true, uri: '/course-management/1/exams/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[3], { label: 'Test Exam', translate: false, uri: '/course-management/1/exams/2/' } as MockBreadcrumb);
            sinon.assert.match(component.breadcrumbs[4], exerciseGroupsCrumb);
            sinon.assert.match(component.breadcrumbs[5], exerciseCrumb);
            sinon.assert.match(component.breadcrumbs[6], plagiarismCrumb);
        });
    });
});
