import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { expectedProfileInfo } from 'test/helpers/sample/profile-info-sample-data';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { FindLanguageFromKeyPipe } from 'app/foundation/language/find-language-from-key.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Component, input } from '@angular/core';

// Create stub components that don't have dependencies
@Component({ selector: 'jhi-connection-warning', template: '' })
class StubConnectionWarningComponent {}

@Component({ selector: 'jhi-loading-notification', template: '' })
class StubLoadingNotificationComponent {}

@Component({ selector: 'jhi-theme-switch', template: '' })
class StubThemeSwitchComponent {
    popoverPlacement = input<string>();
}

@Component({ selector: 'jhi-image', template: '' })
class StubImageComponent {
    src = input<string>();
}

@Component({ selector: 'jhi-course-notification-overview', template: '' })
class StubCourseNotificationOverviewComponent {
    courseId = input.required<number>();
}
import { of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockRouterLinkActiveOptionsDirective, MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { JhiConnectionWarningComponent } from 'app/shared-ui/connection-warning/connection-warning.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { Authority } from 'app/foundation/constants/authority.constants';
import { User } from 'app/account/user/user.model';
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
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { LoadingNotificationService } from 'app/core/loading-notification/loading-notification.service';
import { BehaviorSubject } from 'rxjs';
import { ImageComponent } from 'app/shared-ui/image/image.component';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { LoginService } from 'app/core/login/login.service';
import { CourseNotificationOverviewComponent } from 'app/notification/course-notification/course-notification-overview/course-notification-overview.component';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';

class MockBreadcrumb {
    label!: string;
    uri!: string;
    translate!: boolean;
}

describe('NavbarComponent', () => {
    let fixture: ComponentFixture<NavbarComponent>;
    let component: NavbarComponent;
    let entityTitleServiceStub: ReturnType<typeof vi.spyOn>;
    let entityTitleService: EntityTitleService;
    let courseStorageService: CourseStorageService;

    const setCurrentCourse = (course: Course) => {
        courseStorageService.updateCourse(course);
        courseStorageService.setCurrentCourse(course.id!);
    };

    const router = new MockRouter();
    router.setUrl('');

    const courseOverviewCrumb = {
        label: 'overview.title',
        translate: true,
        uri: '/courses',
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
            imports: [
                NavbarComponent,
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(ActiveMenuDirective),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
                MockRouterLinkActiveOptionsDirective,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(FindLanguageFromKeyPipe),
                MockComponent(SystemNotificationComponent),
                FaIconComponent,
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: router },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: LoginService, useValue: { logout: vi.fn() } },
                { provide: LoadingNotificationService, useValue: { loadingStatus: new BehaviorSubject(false) } },
            ],
        })
            .overrideComponent(NavbarComponent, {
                remove: {
                    imports: [ThemeSwitchComponent, JhiConnectionWarningComponent, LoadingNotificationComponent, ImageComponent, CourseNotificationOverviewComponent],
                },
                add: {
                    imports: [
                        StubThemeSwitchComponent,
                        StubConnectionWarningComponent,
                        StubLoadingNotificationComponent,
                        StubImageComponent,
                        StubCourseNotificationOverviewComponent,
                    ],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(NavbarComponent);
        component = fixture.componentInstance;
        router.navigate.mockClear();
        router.navigateByUrl.mockClear();
        entityTitleService = TestBed.inject(EntityTitleService);
        courseStorageService = TestBed.inject(CourseStorageService);
        courseStorageService.setCourses();
        courseStorageService.clearCurrentCourse();
        entityTitleServiceStub = vi.spyOn(entityTitleService, 'getTitle').mockImplementation((type) => of('Test ' + type.substring(0, 1) + type.substring(1).toLowerCase()));
        const profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(expectedProfileInfo);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should display the current course next to the logo', () => {
        const course = { id: 1, title: 'Course1', courseIconPath: 'path/to/icon.png' } as Course;
        setCurrentCourse(course);

        fixture.detectChanges();

        const titleElement = fixture.debugElement.query(By.css('#test-course-title'));
        expect(titleElement).toBeTruthy();
        expect(titleElement.nativeElement.textContent).toBe('Course1');
        expect(fixture.nativeElement.querySelector('jhi-image')).not.toBeNull();
    });

    it('should display a course initial if the current course has no icon', () => {
        setCurrentCourse({ id: 1, title: 'Course1' } as Course);

        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('jhi-image')).toBeNull();
        expect(fixture.nativeElement.querySelector('.course-circle')?.textContent.trim()).toBe('C');
    });

    it('should link the current course to the management overview when in a management route', () => {
        router.setUrl('/course-management/1/exercises');

        expect(component.getCourseLink(1)).toEqual(['/course-management', 1]);
    });

    it('should link the current course to the student overview when in a student route', () => {
        router.setUrl('/courses/1/exercises');

        expect(component.getCourseLink(1)).toEqual(['/courses', 1]);
    });

    describe('perspective switch links', () => {
        const tutorCourse = {
            id: 123,
            title: 'Course1',
            isAtLeastTutor: true,
            isAtLeastEditor: false,
            isAtLeastInstructor: false,
        } as Course;
        const editorCourse = {
            id: 123,
            title: 'Course1',
            isAtLeastTutor: true,
            isAtLeastEditor: true,
            isAtLeastInstructor: false,
        } as Course;
        const instructorCourse = {
            id: 123,
            title: 'Course1',
            isAtLeastTutor: true,
            isAtLeastEditor: true,
            isAtLeastInstructor: true,
        } as Course;

        it.each(['/courses/123', '/courses/123/exercises/1/problem-statement', '/course-management/123/exams/1'])(
            'should show the perspective switch in course context for %s',
            (url) => {
                router.setUrl(url);

                expect(component.perspectiveSwitchLinks()).toBeDefined();
            },
        );

        it.each(['/courses', '/course-management', '/admin/user-management', '/courses/archive'])('should hide the perspective switch outside course context for %s', (url) => {
            router.setUrl(url);

            expect(component.perspectiveSwitchLinks()).toBeUndefined();
        });

        it('should hide the perspective switch without management access in the course', () => {
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isAtLeastTutorInCourseWithId').mockReturnValue(false);
            router.setUrl('/courses/123/exercises');

            expect(component.perspectiveSwitchLinks()).toBeUndefined();
        });

        it('should derive perspective links from the route even when another course is in the shared context', () => {
            const accountService = TestBed.inject(AccountService);
            const tutorAccessSpy = vi.spyOn(accountService, 'isAtLeastTutorInCourseWithId');
            const editorAccessSpy = vi.spyOn(accountService, 'isAtLeastEditorInCourseWithId');
            const instructorAccessSpy = vi.spyOn(accountService, 'isAtLeastInstructorInCourseWithId');
            setCurrentCourse(instructorCourse);
            router.setUrl('/courses/456/exercises');

            expect(component.perspectiveSwitchLinks()).toBeDefined();
            expect(tutorAccessSpy).toHaveBeenCalledWith(456);
            expect(editorAccessSpy).toHaveBeenCalledWith(456);
            expect(instructorAccessSpy).toHaveBeenCalledWith(456);
        });

        it.each([
            ['/course-management/123/exams/1/edit', ['/courses', '123', 'exams']],
            ['/course-management/123/exercises/new', ['/courses', '123', 'exercises']],
            ['/course-management/123/lectures/1/details', ['/courses', '123', 'lectures', '1']],
            ['/course-management/123/communication?conversationId=123', ['/courses', '123', 'communication']],
            ['/course-management/123/learning-path-management', ['/courses', '123', 'learning-path']],
            ['/course-management/123/competency-management', ['/courses', '123', 'competencies']],
            ['/course-management/123/faqs/new', ['/courses', '123', 'faq']],
            ['/course-management/123/tutorial-groups/configuration/new', ['/courses', '123', 'tutorial-groups']],
            ['/course-management/123/tutorial-groups-checklist', ['/courses', '123', 'tutorial-groups']],
            ['/course-management/123/course-statistics', ['/courses', '123', 'statistics']],
        ])('should link from management route %s to corresponding student route', (url, expectedLink) => {
            router.setUrl(url);

            expect(component.perspectiveSwitchLinks()?.studentViewLink).toEqual(expectedLink);
        });

        it.each([
            '/course-management/123/text-exercises/41',
            '/course-management/123/modeling-exercises/41/edit',
            '/course-management/123/file-upload-exercises/41/exercise-statistics',
            '/course-management/123/programming-exercises/41/grading/test-cases',
            '/course-management/123/quiz-exercises/41/preview',
            '/course-management/123/exercises/41/teams',
        ])('should link from course exercise management route %s to the generic student exercise detail', (url) => {
            router.setUrl(url);

            expect(component.perspectiveSwitchLinks()?.studentViewLink).toEqual(['/courses', '123', 'exercises', '41']);
        });

        it.each(['/course-management/123/lectures/41', '/course-management/123/lectures/41/edit', '/course-management/123/lectures/41/unit-management'])(
            'should link from lecture management route %s to the student lecture detail',
            (url) => {
                router.setUrl(url);

                expect(component.perspectiveSwitchLinks()?.studentViewLink).toEqual(['/courses', '123', 'lectures', '41']);
            },
        );

        it.each(['/course-management/123/tutorial-groups/41', '/course-management/123/tutorial-groups/41/edit', '/course-management/123/tutorial-groups/41/registrations'])(
            'should link from tutorial group management route %s to the student tutorial group detail',
            (url) => {
                router.setUrl(url);

                expect(component.perspectiveSwitchLinks()?.studentViewLink).toEqual(['/courses', '123', 'tutorial-groups', '41']);
            },
        );

        it('should not treat a nested exam exercise as a course exercise', () => {
            router.setUrl('/course-management/123/exams/7/exercise-groups/8/text-exercises/41');

            expect(component.perspectiveSwitchLinks()?.studentViewLink).toEqual(['/courses', '123', 'exams']);
        });

        it('should default student view link to the course overview when route has no student equivalent', () => {
            router.setUrl('/course-management/123/build-overview');

            expect(component.perspectiveSwitchLinks()?.studentViewLink).toEqual(['/courses', '123']);
        });

        it.each(['/admin/upcoming-exams-and-exercises', '/exams/rooms', '/lti/exercises/123'])('should not provide perspective links outside course routes for %s', (url) => {
            router.setUrl(url);

            expect(component.perspectiveSwitchLinks()).toBeUndefined();
        });

        it.each([
            { url: '/courses/123/exams/1', course: tutorCourse, expected: ['/course-management', '123', 'exams'] },
            { url: '/courses/123/exercises/programming-exercises/1', course: tutorCourse, expected: ['/course-management', '123', 'exercises'] },
            { url: '/courses/123/lectures/1', course: editorCourse, expected: ['/course-management', '123', 'lectures', '1'] },
            { url: '/courses/123/communication?conversationId=123', course: tutorCourse, expected: ['/course-management', '123', 'communication'] },
            { url: '/courses/123/learning-path', course: instructorCourse, expected: ['/course-management', '123', 'learning-path-management'] },
            { url: '/courses/123/competencies', course: instructorCourse, expected: ['/course-management', '123', 'competency-management'] },
            { url: '/courses/123/faq', course: tutorCourse, expected: ['/course-management', '123', 'faqs'] },
            { url: '/courses/123/tutorial-groups', course: tutorCourse, expected: ['/course-management', '123', 'tutorial-groups'] },
            { url: '/courses/123/tutorial-groups/41', course: tutorCourse, expected: ['/course-management', '123', 'tutorial-groups', '41'] },
            { url: '/courses/123/statistics', course: tutorCourse, expected: ['/course-management', '123', 'course-statistics'] },
        ])('should link from student route $url to corresponding management route', ({ url, course, expected }) => {
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isAtLeastEditorInCourseWithId').mockReturnValue(course.isAtLeastEditor ?? false);
            vi.spyOn(accountService, 'isAtLeastInstructorInCourseWithId').mockReturnValue(course.isAtLeastInstructor ?? false);
            router.setUrl(url);

            expect(component.perspectiveSwitchLinks()?.managementViewLink).toEqual(expected);
        });

        it.each([
            { type: ExerciseType.TEXT, url: '/courses/123/exercises/41' },
            { type: ExerciseType.MODELING, url: '/courses/123/exercises/modeling-exercises/41/participate/52' },
            { type: ExerciseType.FILE_UPLOAD, url: '/courses/123/exercises/file-upload-exercises/41/participate/52' },
            { type: ExerciseType.PROGRAMMING, url: '/courses/123/exercises/programming-exercises/41/code-editor/52' },
            { type: ExerciseType.QUIZ, url: '/courses/123/exercises/quiz-exercises/41/live' },
        ])('should link from student $type exercise route to its management detail', ({ type, url }) => {
            courseStorageService.setCourses([{ ...tutorCourse, exercises: [{ id: 41, type } as Exercise] } as Course]);
            router.setUrl(url);

            expect(component.perspectiveSwitchLinks()?.managementViewLink).toEqual(['/course-management', '123', `${type}-exercises`, '41']);
        });

        it('should keep the management exercise overview fallback when the current course does not contain the exercise', () => {
            courseStorageService.setCourses([{ ...tutorCourse, exercises: [] } as Course]);
            router.setUrl('/courses/123/exercises/41');

            expect(component.perspectiveSwitchLinks()?.managementViewLink).toEqual(['/course-management', '123', 'exercises']);
        });

        it('should use the exercise-enriched course storage when the shared course context is lean', () => {
            setCurrentCourse(tutorCourse);
            courseStorageService.setCourses([{ ...tutorCourse, exercises: [{ id: 41, type: ExerciseType.TEXT } as Exercise] } as Course]);
            router.setUrl('/courses/123/exercises/41');

            expect(component.perspectiveSwitchLinks()?.managementViewLink).toEqual(['/course-management', '123', 'text-exercises', '41']);
        });

        it('should default management view link to the course management overview when route has no management equivalent', () => {
            router.setUrl('/courses/123/settings');

            expect(component.perspectiveSwitchLinks()?.managementViewLink).toEqual(['/course-management', '123']);
        });

        it.each([
            { course: tutorCourse, url: '/courses/123/lectures/1', expected: ['/course-management', '123'] },
            { course: editorCourse, url: '/courses/123/learning-path', expected: ['/course-management', '123'] },
            { course: editorCourse, url: '/courses/123/competencies', expected: ['/course-management', '123'] },
        ])('should default management view link when access is missing for $url', ({ course, url, expected }) => {
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isAtLeastEditorInCourseWithId').mockReturnValue(course.isAtLeastEditor ?? false);
            vi.spyOn(accountService, 'isAtLeastInstructorInCourseWithId').mockReturnValue(course.isAtLeastInstructor ?? false);
            router.setUrl(url);

            expect(component.perspectiveSwitchLinks()?.managementViewLink).toEqual(expected);
        });

        it('should provide perspective links without a current course', () => {
            courseStorageService.clearCurrentCourse();
            router.setUrl('/courses/123');

            expect(component.perspectiveSwitchLinks()).toBeDefined();
        });
    });

    it('should make api call when logged in user changes language', () => {
        const languageService = TestBed.inject(TranslateService);
        const useSpy = vi.spyOn(languageService, 'use');
        const accountService = TestBed.inject(AccountService);
        const languageChangeSpy = vi.spyOn(accountService, 'updateLanguage');

        fixture.detectChanges();
        component.changeLanguage('elvish');

        expect(useSpy).toHaveBeenCalledWith('elvish');
        expect(languageChangeSpy).toHaveBeenCalledWith('elvish');
    });

    it('should not make api call when anonymous user changes language', () => {
        const languageService = TestBed.inject(TranslateService);
        const useSpy = vi.spyOn(languageService, 'use');
        const accountService = TestBed.inject(AccountService);
        const languageChangeSpy = vi.spyOn(accountService, 'updateLanguage');

        fixture.detectChanges();
        component.currAccount.set(undefined);
        fixture.changeDetectorRef.detectChanges();
        component.changeLanguage('elvish');

        expect(useSpy).toHaveBeenCalledWith('elvish');
        expect(languageChangeSpy).not.toHaveBeenCalled();
    });

    it('should not build breadcrumbs for students', () => {
        const testUrl = '/courses/1/exercises';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs()).toHaveLength(0);
        expect(fixture.nativeElement.querySelector('.breadcrumb-container')).toBeNull();
    });

    it('should not build breadcrumbs when creating a course', () => {
        router.setUrl('/course-management/new');

        fixture.detectChanges();

        expect(component.breadcrumbs()).toHaveLength(0);
        expect(fixture.nativeElement.querySelector('.breadcrumb-container')).toBeNull();
    });

    it('should not build breadcrumbs for administration routes', () => {
        const testUrl = '/admin/user-management/test_user';
        router.setUrl(testUrl);

        fixture.detectChanges();

        expect(component.breadcrumbs()).toHaveLength(0);
        expect(fixture.nativeElement.querySelector('.breadcrumb-container')).toBeNull();
    });

    it('should have correct git info', () => {
        const profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(expectedProfileInfo);

        fixture.detectChanges();

        expect(component.gitCommitId()).toBe('95ef2a');
        expect(component.gitBranchName()).toBe('code-button');
        expect(component.gitTimestamp()).toBe('Sun, 20 Nov 2022 20:35:01 GMT');
        expect(component.gitUsername()).toBe('Max Musterman');
    });

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

            expect(component.breadcrumbs()).toHaveLength(4);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual(programmingExercisesCrumb);
            expect(component.breadcrumbs()[3]).toEqual(importCrumb);
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

            expect(component.breadcrumbs()).toHaveLength(5);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual(programmingExercisesCrumb);
            expect(component.breadcrumbs()[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/programming-exercises/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[4]).toEqual(gradingCrumb);
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

            expect(component.breadcrumbs()).toHaveLength(5);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual(programmingExercisesCrumb);
            expect(component.breadcrumbs()[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/programming-exercises/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[4]).toEqual(assessmentCrumb);
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

            expect(component.breadcrumbs()).toHaveLength(4);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual({
                label: 'artemisApp.assessmentDashboard.home.title',
                translate: true,
                uri: '/course-management/1/assessment-dashboard/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/assessment-dashboard/2/',
            } as MockBreadcrumb);
        });

        it('should show the exercise title and correct exercise link for generic exercise routes', () => {
            const exerciseService = TestBed.inject(ExerciseService);
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: { title: 'Programming Exercise', type: ExerciseType.PROGRAMMING } as Exercise })));
            const testUrl = '/course-management/1/exercises/2';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(exerciseService.find).toHaveBeenCalledOnce();
            expect(exerciseService.find).toHaveBeenCalledWith(2);
            expect(component.breadcrumbs()).toHaveLength(4);
            expect(component.breadcrumbs()[3]).toEqual({
                label: 'Programming Exercise',
                translate: false,
                uri: '/course-management/1/programming-exercises/2/',
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

            expect(component.breadcrumbs()).toHaveLength(6);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual({
                label: 'artemisApp.course.exercises',
                translate: true,
                uri: '/course-management/1/modeling-exercises/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/modeling-exercises/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[4]).toEqual(submissionCrumb);
            expect(component.breadcrumbs()[5]).toEqual(editorSubmissionCrumb);
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

            expect(component.breadcrumbs()).toHaveLength(6);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual({
                label: 'artemisApp.course.exercises',
                translate: true,
                uri: '/course-management/1/modeling-exercises/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[3]).toEqual({
                label: 'Test Exercise',
                translate: false,
                uri: '/course-management/1/modeling-exercises/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[4]).toEqual(submissionCrumb);
            expect(component.breadcrumbs()[5]).toEqual(editorSubmissionCrumb);
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

            expect(component.breadcrumbs()).toHaveLength(6);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual({
                label: 'artemisApp.lecture.home.title',
                translate: true,
                uri: '/course-management/1/lectures/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[3]).toEqual({
                label: 'Test Lecture',
                translate: false,
                uri: '/course-management/1/lectures/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[4]).toEqual(unitManagementCrumb);
            expect(component.breadcrumbs()[5]).toEqual(createCrumb);
        });

        it('apollon diagrams', () => {
            const testUrl = '/course-management/1/apollon-diagrams/2';
            router.setUrl(testUrl);

            fixture.detectChanges();

            expect(entityTitleServiceStub).toHaveBeenCalledTimes(2);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.COURSE, [1]);
            expect(entityTitleServiceStub).toHaveBeenCalledWith(EntityType.DIAGRAM, [2]);

            expect(component.breadcrumbs()).toHaveLength(4);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual({
                label: 'artemisApp.apollonDiagram.home.title',
                translate: true,
                uri: '/course-management/1/apollon-diagrams/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[3]).toEqual({
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

            expect(component.breadcrumbs()).toHaveLength(6);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual({
                label: 'artemisApp.examManagement.title',
                translate: true,
                uri: '/course-management/1/exams/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[3]).toEqual({
                label: 'Test Exam',
                translate: false,
                uri: '/course-management/1/exams/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[4]).toEqual(exerciseGroupsCrumb);
            expect(component.breadcrumbs()[5]).toEqual(createCrumb);
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

            expect(component.breadcrumbs()).toHaveLength(7);

            expect(component.breadcrumbs()[0]).toEqual(courseOverviewCrumb);
            expect(component.breadcrumbs()[1]).toEqual(testCourseCrumb);
            expect(component.breadcrumbs()[2]).toEqual({
                label: 'artemisApp.examManagement.title',
                translate: true,
                uri: '/course-management/1/exams/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[3]).toEqual({
                label: 'Test Exam',
                translate: false,
                uri: '/course-management/1/exams/2/',
            } as MockBreadcrumb);
            expect(component.breadcrumbs()[4]).toEqual(exerciseGroupsCrumb);
            expect(component.breadcrumbs()[5]).toEqual(exerciseCrumb);
            expect(component.breadcrumbs()[6]).toEqual(plagiarismCrumb);
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
            expect(component.breadcrumbs()).toHaveLength(5);
            expect(component.breadcrumbs()[4]).toMatchObject({ uri: url + '/', label: label });
        });
    });

    describe('course controls in navbar', () => {
        it('should render the notification overview when a course is active', () => {
            setCurrentCourse({ id: 1 } as Course);
            router.setUrl('/courses/1/exercises');

            fixture.detectChanges();

            const notificationOverview = fixture.nativeElement.querySelector('jhi-course-notification-overview');
            const themeSwitch = fixture.nativeElement.querySelector('jhi-theme-switch');
            expect(notificationOverview).not.toBeNull();
            expect(notificationOverview.closest('#navbar-icon-menu')).not.toBeNull();
            expect(notificationOverview.compareDocumentPosition(themeSwitch) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);
        });

        it('should render the notification overview for instructors in course management view', () => {
            setCurrentCourse({ id: 1, isAtLeastTutor: true } as Course);
            router.setUrl('/course-management/1/exercises');

            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('jhi-course-notification-overview')).not.toBeNull();
        });

        it('should not render the notification overview when no course is active', () => {
            courseStorageService.clearCurrentCourse();
            router.setUrl('/courses');

            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('jhi-course-notification-overview')).toBeNull();
        });

        it('should not render the notification overview during an active or started exam', () => {
            setCurrentCourse({ id: 1 } as Course);
            router.setUrl('/courses/1/exercises');
            component.isExamActive.set(true);

            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('jhi-course-notification-overview')).toBeNull();

            component.isExamActive.set(false);
            component.isExamStarted.set(true);

            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('jhi-course-notification-overview')).toBeNull();
        });
    });

    it('should collapse and toggle the navbar', () => {
        fixture.detectChanges();
        component.iconsMovedToMenu.set(true);
        fixture.detectChanges();
        component.isNavbarCollapsed.set(false);
        const toggler = fixture.nativeElement.querySelector('.toggler');

        component.collapseNavbar();
        fixture.detectChanges();
        expect(component.isNavbarCollapsed()).toBe(true);
        expect(toggler.getAttribute('aria-expanded')).toBe('false');

        component.toggleNavbar();
        fixture.detectChanges();
        expect(component.isNavbarCollapsed()).toBe(false);
        expect(toggler.getAttribute('aria-expanded')).toBe('true');
    });

    it('should only render the navbar toggler when the icon menu is moved into the collapsible region', () => {
        component.iconsMovedToMenu.set(false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.toggler')).toBeNull();

        component.iconsMovedToMenu.set(true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.toggler')).not.toBeNull();
    });

    it('should collapse navbar, navigate to sign-in, and clear participation state on logout', async () => {
        const participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
        const loginService = TestBed.inject(LoginService);
        const resetLocalCacheSpy = vi.spyOn(participationWebsocketService, 'resetLocalCache');
        const logoutSpy = vi.spyOn(loginService, 'logout');

        component.logout();
        await Promise.resolve();

        expect(router.navigate).toHaveBeenCalledWith(['/sign-in']);
        expect(resetLocalCacheSpy).toHaveBeenCalledOnce();
        expect(logoutSpy).toHaveBeenCalledWith(true);
        expect(component.isNavbarCollapsed()).toBe(true);
    });

    it.each([
        {
            width: 1200,
            account: { login: 'test' },
            roles: [Authority.ADMIN],
            expected: { isCollapsed: false, isIconMenuCompact: false, iconsMovedToMenu: false },
        },
        {
            width: 1100,
            account: { login: 'test' },
            roles: [Authority.ADMIN],
            expected: { isCollapsed: false, isIconMenuCompact: false, iconsMovedToMenu: false },
        },
        {
            width: 600,
            account: { login: 'test' },
            roles: [Authority.ADMIN],
            expected: { isCollapsed: true, isIconMenuCompact: false, iconsMovedToMenu: true },
        },
        {
            width: 550,
            account: { login: 'test' },
            roles: [Authority.ADMIN],
            expected: { isCollapsed: true, isIconMenuCompact: false, iconsMovedToMenu: true },
        },
        {
            width: 1000,
            account: { login: 'test' },
            roles: [Authority.INSTRUCTOR],
            expected: { isCollapsed: false, isIconMenuCompact: false, iconsMovedToMenu: false },
        },
        {
            width: 850,
            account: { login: 'test' },
            roles: [Authority.INSTRUCTOR],
            expected: { isCollapsed: true, isIconMenuCompact: false, iconsMovedToMenu: false },
        },
        {
            width: 600,
            account: { login: 'test' },
            roles: [Authority.INSTRUCTOR],
            expected: { isCollapsed: true, isIconMenuCompact: false, iconsMovedToMenu: true },
        },
        {
            width: 470,
            account: { login: 'test' },
            roles: [Authority.INSTRUCTOR],
            expected: { isCollapsed: true, isIconMenuCompact: true, iconsMovedToMenu: true },
        },
        {
            width: 800,
            account: { login: 'test' },
            roles: [Authority.STUDENT],
            expected: { isCollapsed: false, isIconMenuCompact: false, iconsMovedToMenu: false },
        },
        {
            width: 650,
            account: { login: 'test' },
            roles: [Authority.STUDENT],
            expected: { isCollapsed: true, isIconMenuCompact: false, iconsMovedToMenu: true },
        },
        {
            width: 600,
            account: { login: 'test' },
            roles: [Authority.STUDENT],
            expected: { isCollapsed: true, isIconMenuCompact: false, iconsMovedToMenu: true },
        },
        {
            width: 470,
            account: { login: 'test' },
            roles: [Authority.STUDENT],
            expected: { isCollapsed: true, isIconMenuCompact: true, iconsMovedToMenu: true },
        },
        {
            width: 520,
            account: undefined,
            roles: [],
            expected: { isCollapsed: false, isIconMenuCompact: false, iconsMovedToMenu: false },
        },
        {
            width: 500,
            account: undefined,
            roles: [],
            expected: { isCollapsed: true, isIconMenuCompact: false, iconsMovedToMenu: false },
        },
        {
            width: 450,
            account: undefined,
            roles: [],
            expected: { isCollapsed: true, isIconMenuCompact: true, iconsMovedToMenu: false },
        },
        {
            width: 400,
            account: undefined,
            roles: [],
            expected: { isCollapsed: true, isIconMenuCompact: true, iconsMovedToMenu: true },
        },
    ])('should calculate correct breakpoints', ({ width, account, roles, expected }) => {
        const accountService = TestBed.inject(AccountService);
        vi.spyOn(accountService, 'hasAnyAuthorityDirect').mockImplementation((authArray) => authArray.some((auth) => (roles as Authority[]).includes(auth)));

        component.currAccount.set(account as User);
        window['innerWidth'] = width;

        component.onResize();

        expect({
            isCollapsed: component.isCollapsed(),
            isIconMenuCompact: component.isIconMenuCompact(),
            iconsMovedToMenu: component.iconsMovedToMenu(),
        }).toEqual(expected);
    });
});
