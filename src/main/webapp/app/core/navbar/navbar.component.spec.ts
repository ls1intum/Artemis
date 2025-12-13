import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, UrlSerializer } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { expectedProfileInfo } from 'app/core/layouts/profiles/shared/profile.service.spec';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FindLanguageFromKeyPipe } from 'app/shared/language/find-language-from-key.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockRouterLinkActiveOptionsDirective, MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { JhiConnectionWarningComponent } from 'app/shared/connection-warning/connection-warning.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { User } from 'app/core/user/user.model';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import dayjs from 'dayjs/esm';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ThemeSwitchComponent } from 'app/core/theme/theme-switch.component';
import { mockThemeSwitcherComponentViewChildren } from 'test/helpers/mocks/mock-instance.helper';
import { NavbarComponent } from 'app/core/navbar/navbar.component';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { ActiveMenuDirective } from 'app/core/navbar/active-menu.directive';
import { LoadingNotificationComponent } from 'app/core/loading-notification/loading-notification.component';
import { SystemNotificationComponent } from 'app/core/notification/system-notification/system-notification.component';

class MockBreadcrumb {
    label: string;
    uri: string;
    translate: boolean;
}

describe('NavbarComponent', () => {
    let fixture: ComponentFixture<NavbarComponent>;
    let component: NavbarComponent;
    let entityTitleServiceStub: jest.SpyInstance;
    let entityTitleService: EntityTitleService;
    let examParticipationService: ExamParticipationService;

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

    // Workaround for an error with MockComponent(). You can remove this once https://github.com/help-me-mom/ng-mocks/issues/8634 is resolved.
    mockThemeSwitcherComponentViewChildren();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                NavbarComponent,
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(ActiveMenuDirective),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
                MockRouterLinkActiveOptionsDirective,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(FindLanguageFromKeyPipe),
                MockComponent(LoadingNotificationComponent),
                MockComponent(JhiConnectionWarningComponent),
                MockComponent(SystemNotificationComponent),
                FaIconComponent,
                MockComponent(ThemeSwitchComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(UrlSerializer),
                { provide: AccountService, useClass: MockAccountService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: router },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
            ],
        })
            .overrideComponent(NavbarComponent, {
                remove: {
                    imports: [ThemeSwitchComponent],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(NavbarComponent);
        component = fixture.componentInstance;
        examParticipationService = TestBed.inject(ExamParticipationService);
        entityTitleService = TestBed.inject(EntityTitleService);
        entityTitleServiceStub = jest.spyOn(entityTitleService, 'getTitle').mockImplementation((type) => of('Test ' + type.substring(0, 1) + type.substring(1).toLowerCase()));
        const profileService = TestBed.inject(ProfileService);
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(expectedProfileInfo);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should make api call when logged in user changes language', () => {
        const languageService = TestBed.inject(TranslateService);
        const useSpy = jest.spyOn(languageService, 'use');
        const accountService = TestBed.inject(AccountService);
        const languageChangeSpy = jest.spyOn(accountService, 'updateLanguage');

        fixture.detectChanges();
        component.changeLanguage('elvish');

        expect(useSpy).toHaveBeenCalledWith('elvish');
        expect(languageChangeSpy).toHaveBeenCalledWith('elvish');
    });

    it('should not make api call when anonymous user changes language', () => {
        const languageService = TestBed.inject(TranslateService);
        const useSpy = jest.spyOn(languageService, 'use');
        const accountService = TestBed.inject(AccountService);
        const languageChangeSpy = jest.spyOn(accountService, 'updateLanguage');

        fixture.detectChanges();
        component.currAccount = undefined;
        fixture.changeDetectorRef.detectChanges();
        component.changeLanguage('elvish');

        expect(useSpy).toHaveBeenCalledWith('elvish');
        expect(languageChangeSpy).not.toHaveBeenCalled();
    });

    it('should not build breadcrumbs for students', () => {
        const testUrl = '/courses/1/exercises';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs).toHaveLength(3);
    });

    it('should build breadcrumbs for course management', () => {
        const testUrl = '/course-management';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs).toHaveLength(1);
        expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
    });

    it('should ignore query parameters', () => {
        const testUrl = '/course-management?query=param';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs).toHaveLength(1);
        expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
    });

    it('should build breadcrumbs for system notification management', () => {
        const testUrl = '/admin/system-notification-management/1/edit';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs).toHaveLength(3);

        const systemBreadcrumb = {
            label: 'artemisApp.systemNotification.systemNotifications',
            translate: true,
            uri: '/admin/system-notification-management/',
        } as MockBreadcrumb;
        expect(component.breadcrumbs[0]).toEqual(systemBreadcrumb);
        expect(component.breadcrumbs[1]).toEqual({
            label: '1',
            translate: false,
            uri: '/admin/system-notification-management/1/',
        } as MockBreadcrumb);
        expect(component.breadcrumbs[2]).toEqual({
            label: 'global.generic.edit',
            translate: true,
            uri: '/admin/system-notification-management/1/edit/',
        } as MockBreadcrumb);
    });

    it('should build breadcrumbs for user management', () => {
        const testUrl = '/admin/user-management/test_user';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs).toHaveLength(2);

        expect(component.breadcrumbs[0]).toEqual({
            label: 'artemisApp.userManagement.home.title',
            translate: true,
            uri: '/admin/user-management/',
        } as MockBreadcrumb);
        expect(component.breadcrumbs[1]).toEqual({
            label: 'test_user',
            translate: false,
            uri: '/admin/user-management/test_user/',
        } as MockBreadcrumb);
    });

    it('should build breadcrumbs for organization management', () => {
        const testUrl = '/admin/organization-management/1';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(entityTitleServiceStub).toHaveBeenCalledOnce();
        expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.ORGANIZATION, [1]);
        expect(component.breadcrumbs).toHaveLength(2);

        expect(component.breadcrumbs[0]).toEqual({
            label: 'artemisApp.organizationManagement.title',
            translate: true,
            uri: '/admin/organization-management/',
        } as MockBreadcrumb);
        expect(component.breadcrumbs[1]).toEqual({
            label: 'Test Organization',
            translate: false,
            uri: '/admin/organization-management/1/',
        } as MockBreadcrumb);
    });

    it('should not error without translation', () => {
        const testUrl = '/admin/route-without-translation';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs).toHaveLength(1);

        expect(component.breadcrumbs[0]).toEqual({
            label: 'route-without-translation',
            translate: false,
            uri: '/admin/route-without-translation/',
        } as MockBreadcrumb);
    });

    it('should hide breadcrumb when exam is started', () => {
        (examParticipationService as any).examIsStarted$ = of(true);
        const testUrl = '/courses/1/exams/2';
        router.setUrl(testUrl);

        fixture.detectChanges();
        component.isExamActive = true;
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.breadcrumb')).toBeNull();

        component.isExamStarted = false;
        component.isExamActive = false;
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.breadcrumb')).not.toBeNull();
    });

    it('should have correct git info', fakeAsync(() => {
        const profileService = TestBed.inject(ProfileService);
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(expectedProfileInfo);

        fixture.detectChanges();

        expect(component.gitCommitId).toBe('95ef2a');
        expect(component.gitBranchName).toBe('code-button');
        expect(component.gitTimestamp).toBe('Sun, 20 Nov 2022 20:35:01 GMT');
        expect(component.gitUsername).toBe('Max Musterman');
    }));

    it('should set the exam active state correctly', fakeAsync(() => {
        const now = dayjs();
        const examParticipationService = TestBed.inject(ExamParticipationService);
        const activatedRoute = TestBed.inject(ActivatedRoute) as MockActivatedRoute;

        fixture.detectChanges();
        activatedRoute.setParameters({ examId: 1 });
        router.setUrl('/course/2/exams/1');

        examParticipationService.currentlyLoadedStudentExam.next({
            workingTime: 60,
            exam: {
                id: 1,
                startDate: now.add(1, 'minute'),
                endDate: now.add(2, 'minutes'),
                gracePeriod: 180,
            },
        } as StudentExam);
        fixture.changeDetectorRef.detectChanges();

        expect(component.isExamActive).toBeFalse();
        tick(61000);
        expect(component.isExamActive).toBeTrue();
        tick(61000);
        expect(component.isExamActive).toBeTrue();
        tick(180000);
        expect(component.isExamActive).toBeFalse();
    }));

    describe('Special Cases for Breadcrumbs', () => {
        it('programming exercise import', () => {
            const testUrl = '/course-management/1/programming-exercises/import/2';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledOnce();
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);

            const importCrumb = {
                label: 'artemisApp.exercise.import.table.doImport',
                translate: true,
                uri: '/course-management/1/programming-exercises/import/2/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs).toHaveLength(4);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual(programmingExercisesCrumb);
            expect(component.breadcrumbs[3]).toEqual(importCrumb);
        });

        it('programming exercise grading', () => {
            const testUrl = '/course-management/1/programming-exercises/2/grading/test-cases';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.EXERCISE, [2]);

            const gradingCrumb = {
                label: 'artemisApp.programmingExercise.configureGrading.shortTitle',
                translate: true,
                uri: '/course-management/1/programming-exercises/2/grading/test-cases/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs).toHaveLength(5);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual(programmingExercisesCrumb);
            expect(component.breadcrumbs[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/programming-exercises/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(gradingCrumb);
        });

        it('programming exercise new assessment', () => {
            const testUrl = '/course-management/1/programming-exercises/2/code-editor/new/assessment';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.EXERCISE, [2]);

            const assessmentCrumb = {
                label: 'artemisApp.assessment.assessment',
                translate: true,
                uri: '/course-management/1/programming-exercises/2/code-editor/new/assessment/',
            } as MockBreadcrumb;

            expect(component.breadcrumbs).toHaveLength(5);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual(programmingExercisesCrumb);
            expect(component.breadcrumbs[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/programming-exercises/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(assessmentCrumb);
        });

        it('exercise assessment dashboard', () => {
            const courseId = 1;
            const exerciseId = 2;
            const testUrl = `/course-management/${courseId}/assessment-dashboard/${exerciseId}`;
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [courseId]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.EXERCISE, [exerciseId]);

            expect(component.breadcrumbs).toHaveLength(4);

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

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.EXERCISE, [2]);

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

            expect(component.breadcrumbs).toHaveLength(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.course.exercises',
                translate: true,
                uri: '/course-management/1/modeling-exercises/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/modeling-exercises/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(submissionCrumb);
            expect(component.breadcrumbs[5]).toEqual(editorSubmissionCrumb);
        });

        it('existing modeling exercise example submission', () => {
            const testUrl = '/course-management/1/modeling-exercises/2/example-submissions/3';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.EXERCISE, [2]);

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

            expect(component.breadcrumbs).toHaveLength(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.course.exercises',
                translate: true,
                uri: '/course-management/1/modeling-exercises/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/modeling-exercises/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(submissionCrumb);
            expect(component.breadcrumbs[5]).toEqual(editorSubmissionCrumb);
        });

        it('lecture units', () => {
            const testUrl = '/course-management/1/lectures/2/unit-management/text-units/create';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.LECTURE, [2]);

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

            expect(component.breadcrumbs).toHaveLength(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.lecture.home.title',
                translate: true,
                uri: '/course-management/1/lectures/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({
                label: 'Test Lecture',
                translate: false,
                uri: '/course-management/1/lectures/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(unitManagementCrumb);
            expect(component.breadcrumbs[5]).toEqual(createCrumb);
        });

        it('apollon diagrams', () => {
            const testUrl = '/course-management/1/apollon-diagrams/2';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.DIAGRAM, [2]);

            expect(component.breadcrumbs).toHaveLength(4);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.apollonDiagram.home.title',
                translate: true,
                uri: '/course-management/1/apollon-diagrams/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({
                label: 'Test Diagram',
                translate: false,
                uri: '/course-management/1/apollon-diagrams/2/',
            } as MockBreadcrumb);
        });

        it('exam exercise groups', () => {
            const testUrl = '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/new';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.EXAM, [2]);

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

            expect(component.breadcrumbs).toHaveLength(6);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.examManagement.title',
                translate: true,
                uri: '/course-management/1/exams/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({
                label: 'Test Exam',
                translate: false,
                uri: '/course-management/1/exams/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(exerciseGroupsCrumb);
            expect(component.breadcrumbs[5]).toEqual(createCrumb);
        });

        it('exam exercise plagiarism', () => {
            const testUrl = '/course-management/1/exams/2/exercise-groups/3/quiz-exercises/4/plagiarism';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(3);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.EXAM, [2]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.EXERCISE, [4]);

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

            expect(component.breadcrumbs).toHaveLength(7);

            expect(component.breadcrumbs[0]).toEqual(courseManagementCrumb);
            expect(component.breadcrumbs[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs[2]).toEqual({
                label: 'artemisApp.examManagement.title',
                translate: true,
                uri: '/course-management/1/exams/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[3]).toEqual({
                label: 'Test Exam',
                translate: false,
                uri: '/course-management/1/exams/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs[4]).toEqual(exerciseGroupsCrumb);
            expect(component.breadcrumbs[5]).toEqual(exerciseCrumb);
            expect(component.breadcrumbs[6]).toEqual(plagiarismCrumb);
        });
    });

    describe('Special repository route breadcrumb cases', () => {
        const baseRoute = '/course-management/1/programming-exercises/2/repository/';

        it.each([
            {
                url: baseRoute + 'USER/5',
                label: 'artemisApp.repository.userRepository.title',
            },
            {
                url: baseRoute + 'TEMPLATE',
                label: 'artemisApp.repository.templateRepository.title',
            },
            {
                url: baseRoute + 'SOLUTION',
                label: 'artemisApp.repository.solutionRepository.title',
            },
            {
                url: baseRoute + 'TESTS',
                label: 'artemisApp.repository.testsRepository.title',
            },
            {
                url: baseRoute + 'AUXILIARY/5',
                label: 'artemisApp.repository.auxiliaryRepository.title',
            },
        ])('should calculated correct repository  breadcrumbs', ({ url, label }) => {
            router.setUrl(url);

            fixture.detectChanges();
            expect(component.breadcrumbs).toHaveLength(5);
            expect(component.breadcrumbs[4]).toMatchObject({ uri: url + '/', label: label });
        });
    });

    describe('Special student route breadcrumb cases', () => {
        it.each(['programming-exercises', 'modeling-exercises', 'text-exercises'])('should not show exercise types in URI on backlinking breadcrumbs', (exType: string) => {
            const testUrl = `/courses/1/exercises/${exType}/2`;
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.EXERCISE, [2]);

            expect(component.breadcrumbs).toHaveLength(4);
            expect(component.breadcrumbs[0]).toMatchObject({ uri: '/courses/', label: 'artemisApp.course.home.title' });
            expect(component.breadcrumbs[1]).toMatchObject({ uri: '/courses/1/', label: 'Test Course' });
            expect(component.breadcrumbs[2]).toMatchObject({
                uri: '/courses/1/exercises/',
                label: 'artemisApp.courseOverview.menu.exercises',
            });
            expect(component.breadcrumbs[3]).toMatchObject({ uri: '/courses/1/exercises/2/', label: 'Test Exercise' });
        });
    });

    it.each([
        {
            width: 1200,
            account: { login: 'test' },
            roles: [Authority.ADMIN],
            expected: { isCollapsed: false, isNavbarNavVertical: false, iconsMovedToMenu: false },
        },
        {
            width: 1100,
            account: { login: 'test' },
            roles: [Authority.ADMIN],
            expected: { isCollapsed: true, isNavbarNavVertical: false, iconsMovedToMenu: false },
        },
        {
            width: 600,
            account: { login: 'test' },
            roles: [Authority.ADMIN],
            expected: { isCollapsed: true, isNavbarNavVertical: false, iconsMovedToMenu: true },
        },
        {
            width: 550,
            account: { login: 'test' },
            roles: [Authority.ADMIN],
            expected: { isCollapsed: true, isNavbarNavVertical: true, iconsMovedToMenu: true },
        },
        {
            width: 1000,
            account: { login: 'test' },
            roles: [Authority.INSTRUCTOR],
            expected: { isCollapsed: false, isNavbarNavVertical: false, iconsMovedToMenu: false },
        },
        {
            width: 850,
            account: { login: 'test' },
            roles: [Authority.INSTRUCTOR],
            expected: { isCollapsed: true, isNavbarNavVertical: false, iconsMovedToMenu: false },
        },
        {
            width: 600,
            account: { login: 'test' },
            roles: [Authority.INSTRUCTOR],
            expected: { isCollapsed: true, isNavbarNavVertical: false, iconsMovedToMenu: true },
        },
        {
            width: 470,
            account: { login: 'test' },
            roles: [Authority.INSTRUCTOR],
            expected: { isCollapsed: true, isNavbarNavVertical: true, iconsMovedToMenu: true },
        },
        {
            width: 800,
            account: { login: 'test' },
            roles: [Authority.STUDENT],
            expected: { isCollapsed: false, isNavbarNavVertical: false, iconsMovedToMenu: false },
        },
        {
            width: 650,
            account: { login: 'test' },
            roles: [Authority.STUDENT],
            expected: { isCollapsed: true, isNavbarNavVertical: false, iconsMovedToMenu: false },
        },
        {
            width: 600,
            account: { login: 'test' },
            roles: [Authority.STUDENT],
            expected: { isCollapsed: true, isNavbarNavVertical: false, iconsMovedToMenu: true },
        },
        {
            width: 470,
            account: { login: 'test' },
            roles: [Authority.STUDENT],
            expected: { isCollapsed: true, isNavbarNavVertical: true, iconsMovedToMenu: true },
        },
        {
            width: 520,
            account: undefined,
            roles: [],
            expected: { isCollapsed: false, isNavbarNavVertical: false, iconsMovedToMenu: false },
        },
        {
            width: 500,
            account: undefined,
            roles: [],
            expected: { isCollapsed: true, isNavbarNavVertical: false, iconsMovedToMenu: false },
        },
        {
            width: 450,
            account: undefined,
            roles: [],
            expected: { isCollapsed: true, isNavbarNavVertical: true, iconsMovedToMenu: false },
        },
        {
            width: 400,
            account: undefined,
            roles: [],
            expected: { isCollapsed: true, isNavbarNavVertical: true, iconsMovedToMenu: true },
        },
    ])('should calculate correct breakpoints', ({ width, account, roles, expected }) => {
        const accountService = TestBed.inject(AccountService);
        jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockImplementation((authArray) => authArray.some((auth) => (roles as Authority[]).includes(auth)));

        component.currAccount = account as User;
        window['innerWidth'] = width;

        component.onResize();

        expect({
            isCollapsed: component.isCollapsed,
            isNavbarNavVertical: component.isNavbarNavVertical,
            iconsMovedToMenu: component.iconsMovedToMenu,
        }).toEqual(expected);
    });
});
