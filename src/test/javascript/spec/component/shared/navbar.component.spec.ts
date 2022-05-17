import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, UrlSerializer } from '@angular/router';
import { NgbCollapse, NgbDropdown } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
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
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../test.module';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { MockRouterLinkActiveOptionsDirective, MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { JhiConnectionWarningComponent } from 'app/shared/connection-warning/connection-warning.component';

class MockBreadcrumb {
    label: string;
    uri: string;
    translate: boolean;
}

describe('NavbarComponent', () => {
    let fixture: ComponentFixture<NavbarComponent>;
    let component: NavbarComponent;
    let courseManagementStub: jest.SpyInstance;
    let exerciseTitleStub: jest.SpyInstance;
    let exerciseService: ExerciseService;

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
                MockRouterLinkDirective,
                MockRouterLinkActiveOptionsDirective,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(FindLanguageFromKeyPipe),
                MockComponent(NotificationSidebarComponent),
                MockComponent(GuidedTourComponent),
                MockComponent(LoadingNotificationComponent),
                MockComponent(JhiConnectionWarningComponent),
            ],
            providers: [
                MockProvider(UrlSerializer),
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
                courseManagementStub = jest.spyOn(courseManagementService, 'getTitle').mockReturnValue(of({ body: 'Test Course' } as HttpResponse<string>));

                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                exerciseTitleStub = jest.spyOn(exerciseService, 'getTitle').mockReturnValue(of({ body: 'Test Exercise' } as HttpResponse<string>));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should not build breadcrumbs for students', () => {
        const testUrl = '/courses/1/exercises';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).toEqual(0);
    });

    it('should build breadcrumbs for course management', () => {
        const testUrl = '/course-management';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).toEqual(1);
        expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
    });

    it('should ignore query parameters', () => {
        const testUrl = '/course-management?query=param';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).toEqual(1);
        expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
    });

    it('should build breadcrumbs for system notification management', () => {
        const testUrl = '/admin/system-notification-management/1/edit';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).toEqual(3);

        const systemBreadcrumb = { label: 'artemisApp.systemNotification.systemNotifications', translate: true, uri: '/admin/system-notification-management/' } as MockBreadcrumb;
        expect(component.breadcrumbs[0]).toEqual(systemBreadcrumb);
        expect(component.breadcrumbs[1]).toEqual({ label: '1', translate: false, uri: '/admin/system-notification-management/1/' } as MockBreadcrumb);
        expect(component.breadcrumbs[2]).toEqual({ label: 'global.generic.edit', translate: true, uri: '/admin/system-notification-management/1/edit/' } as MockBreadcrumb);
    });

    it('should build breadcrumbs for user management', () => {
        const testUrl = '/admin/user-management/test_user';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).toEqual(2);

        expect(component.breadcrumbs[0]).toEqual({ label: 'userManagement.home.title', translate: true, uri: '/admin/user-management/' } as MockBreadcrumb);
        expect(component.breadcrumbs[1]).toEqual({ label: 'test_user', translate: false, uri: '/admin/user-management/test_user/' } as MockBreadcrumb);
    });

    it('should build breadcrumbs for organization management', () => {
        const testUrl = '/admin/organization-management/1';
        router.setUrl(testUrl);

        const organizationService = fixture.debugElement.injector.get(OrganizationManagementService);
        const organizationStub = jest.spyOn(organizationService, 'getTitle').mockReturnValue(of({ body: 'Organization Name' } as HttpResponse<string>));

        fixture.detectChanges();

        expect(organizationStub).toHaveBeenCalledWith(1);
        expect(component.breadcrumbs.length).toEqual(2);

        expect(component.breadcrumbs[0]).toEqual({ label: 'organizationManagement.title', translate: true, uri: '/admin/organization-management/' } as MockBreadcrumb);
        expect(component.breadcrumbs[1]).toEqual({ label: 'Organization Name', translate: false, uri: '/admin/organization-management/1/' } as MockBreadcrumb);
    });

    it('should not error without translation', () => {
        const testUrl = '/admin/route-without-translation';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs.length).toEqual(1);

        expect(component.breadcrumbs[0]).toEqual({ label: 'route-without-translation', translate: false, uri: '/admin/route-without-translation/' } as MockBreadcrumb);
    });

    describe('Special Cases for Breadcrumbs', () => {
        it('programming exercise import', () => {
            const testUrl = '/course-management/1/programming-exercises/import/2';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);

            const importCrumb = {
                label: 'artemisApp.exercise.import.table.doImport',
                translate: true,
                uri: '/course-management/1/programming-exercises/import/2/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).toEqual(4);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual(programmingExercisesCrumb);
            expect(component.breadcrumbs[3]).toEqual(importCrumb);
        });

        it('programming exercise grading', () => {
            const testUrl = '/course-management/1/programming-exercises/2/grading/test-cases';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(exerciseTitleStub).toHaveBeenCalledWith(2);

            const gradingCrumb = {
                label: 'artemisApp.programmingExercise.configureGrading.shortTitle',
                translate: true,
                uri: '/course-management/1/programming-exercises/2/grading/test-cases/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).toEqual(5);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual(programmingExercisesCrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Test Exercise', translate: false, uri: '/course-management/1/programming-exercises/2/' } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(gradingCrumb);
        });

        it('programming exercise new assessment', () => {
            const testUrl = '/course-management/1/programming-exercises/2/code-editor/new/assessment';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(exerciseTitleStub).toHaveBeenCalledWith(2);

            const assessmentCrumb = {
                label: 'artemisApp.assessment.assessment',
                translate: true,
                uri: '/course-management/1/programming-exercises/2/code-editor/new/assessment/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).toEqual(5);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual(programmingExercisesCrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Test Exercise', translate: false, uri: '/course-management/1/programming-exercises/2/' } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(assessmentCrumb);
        });

        it('programming exercise hints', () => {
            const testUrl = '/course-management/1/exercises/2/exercise-hints/3';
            router.setUrl(testUrl);

            const hintService = fixture.debugElement.injector.get(ExerciseHintService);
            const hintsStub = jest.spyOn(hintService, 'getTitle').mockReturnValue(of({ body: 'Exercise Hint' } as HttpResponse<string>));
            const findStub = jest
                .spyOn(exerciseService, 'find')
                .mockReturnValue(of({ body: { title: 'Test Exercise', type: ExerciseType.PROGRAMMING } } as HttpResponse<Exercise>));

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(findStub).toHaveBeenCalledWith(2);
            expect(hintsStub).toHaveBeenCalledWith(2, 3);

            const hintsCrumb = {
                label: 'artemisApp.exerciseHint.home.title',
                translate: true,
                uri: '/course-management/1/exercises/2/exercise-hints/',
            } as MockBreadcrumb;

            const hintCrumb = {
                label: 'Exercise Hint',
                translate: false,
                uri: '/course-management/1/exercises/2/exercise-hints/3/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs.length).toEqual(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({ label: 'artemisApp.course.exercises', translate: true, uri: '/course-management/1/exercises/' } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Test Exercise', translate: false, uri: '/course-management/1/programming-exercises/2/' } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(hintsCrumb);
            expect(component.breadcrumbs[5]).toEqual(hintCrumb);
        });

        it('text exercise feedback conflict', () => {
            const testUrl = '/course-management/1/text-exercises/2/submissions/3/text-feedback-conflict/4';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(exerciseTitleStub).toHaveBeenCalledWith(2);

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

            expect(component.breadcrumbs.length).toEqual(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({ label: 'artemisApp.course.exercises', translate: true, uri: '/course-management/1/text-exercises/' } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Test Exercise', translate: false, uri: '/course-management/1/text-exercises/2/' } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(submissionsCrumb);
            expect(component.breadcrumbs[5]).toEqual(conflictCrumb);
        });

        it('exercise assessment dashboard', () => {
            const courseId = 1;
            const exerciseId = 2;
            const testUrl = `/course-management/${courseId}/assessment-dashboard/${exerciseId}`;
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(courseId);
            expect(exerciseTitleStub).toHaveBeenCalledWith(exerciseId);

            expect(component.breadcrumbs.length).toEqual(4);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.assessmentDashboard.home.title',
                translate: true,
                uri: '/course-management/1/assessment-dashboard/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/assessment-dashboard/2/',
            } as MockBreadcrumb);
        });

        it('modeling exercise example submission', () => {
            const testUrl = '/course-management/1/modeling-exercises/2/example-submissions/new';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(exerciseTitleStub).toHaveBeenCalledWith(2);

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

            expect(component.breadcrumbs.length).toEqual(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.course.exercises',
                translate: true,
                uri: '/course-management/1/modeling-exercises/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Test Exercise', translate: false, uri: '/course-management/1/modeling-exercises/2/' } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(submissionCrumb);
            expect(component.breadcrumbs[5]).toEqual(editorSubmissionCrumb);
        });

        it('modeling exercise example submission', () => {
            const testUrl = '/course-management/1/modeling-exercises/2/example-submissions/3';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(exerciseTitleStub).toHaveBeenCalledWith(2);

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

            expect(component.breadcrumbs.length).toEqual(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.course.exercises',
                translate: true,
                uri: '/course-management/1/modeling-exercises/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Test Exercise', translate: false, uri: '/course-management/1/modeling-exercises/2/' } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(submissionCrumb);
            expect(component.breadcrumbs[5]).toEqual(editorSubmissionCrumb);
        });

        it('lecture units', () => {
            const testUrl = '/course-management/1/lectures/2/unit-management/text-units/create';
            router.setUrl(testUrl);

            const lectureService = fixture.debugElement.injector.get(LectureService);
            const lectureStub = jest.spyOn(lectureService, 'getTitle').mockReturnValue(of({ body: 'Test Lecture' } as HttpResponse<string>));

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(lectureStub).toHaveBeenCalledWith(2);

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

            expect(component.breadcrumbs.length).toEqual(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({ label: 'artemisApp.lecture.home.title', translate: true, uri: '/course-management/1/lectures/' } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Test Lecture', translate: false, uri: '/course-management/1/lectures/2/' } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(unitManagementCrumb);
            expect(component.breadcrumbs[5]).toEqual(createCrumb);
        });

        it('apollon diagrams', () => {
            const testUrl = '/course-management/1/apollon-diagrams/2';
            router.setUrl(testUrl);

            const apollonDiagramService = fixture.debugElement.injector.get(ApollonDiagramService);
            const apollonStub = jest.spyOn(apollonDiagramService, 'getTitle').mockReturnValue(of({ body: 'Apollon Diagram' } as HttpResponse<string>));

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(apollonStub).toHaveBeenCalledWith(2);

            expect(component.breadcrumbs.length).toEqual(4);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.apollonDiagram.home.title',
                translate: true,
                uri: '/course-management/1/apollon-diagrams/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Apollon Diagram', translate: false, uri: '/course-management/1/apollon-diagrams/2/' } as MockBreadcrumb);
        });

        it('exam exercise groups', () => {
            const testUrl = '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/new';
            router.setUrl(testUrl);

            const examService = fixture.debugElement.injector.get(ExamManagementService);
            const examStub = jest.spyOn(examService, 'getTitle').mockReturnValue(of({ body: 'Test Exam' } as HttpResponse<string>));

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(examStub).toHaveBeenCalledWith(2);

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

            expect(component.breadcrumbs.length).toEqual(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({ label: 'artemisApp.examManagement.title', translate: true, uri: '/course-management/1/exams/' } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Test Exam', translate: false, uri: '/course-management/1/exams/2/' } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(exerciseGroupsCrumb);
            expect(component.breadcrumbs[5]).toEqual(createCrumb);
        });

        it('exam exercise plagiarism', () => {
            const testUrl = '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/4/plagiarism';
            router.setUrl(testUrl);

            const examService = fixture.debugElement.injector.get(ExamManagementService);
            const examStub = jest.spyOn(examService, 'getTitle').mockReturnValue(of({ body: 'Test Exam' } as HttpResponse<string>));

            fixture.detectChanges();

            expect(courseManagementStub).toHaveBeenCalledWith(1);
            expect(examStub).toHaveBeenCalledWith(2);
            expect(exerciseTitleStub).toHaveBeenCalledWith(4);

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
                label: 'artemisApp.plagiarism.plagiarismDetection',
                translate: true,
                uri: '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/4/plagiarism/',
            };

            expect(component.breadcrumbs.length).toEqual(7);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({ label: 'artemisApp.examManagement.title', translate: true, uri: '/course-management/1/exams/' } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({ label: 'Test Exam', translate: false, uri: '/course-management/1/exams/2/' } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(exerciseGroupsCrumb);
            expect(component.breadcrumbs[5]).toEqual(exerciseCrumb);
            expect(component.breadcrumbs[6]).toEqual(plagiarismCrumb);
        });
    });
});
