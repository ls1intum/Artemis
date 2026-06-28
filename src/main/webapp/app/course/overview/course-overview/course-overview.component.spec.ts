import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FeatureToggleHideDirective } from 'app/foundation/feature-toggle/feature-toggle-hide.directive';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Params, Router, RouterModule } from '@angular/router';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ImageComponent } from 'app/shared-ui/image/image.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AfterViewInit, ChangeDetectorRef, Component, EventEmitter, TemplateRef, viewChild } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared-ui/tab-bar/tab-bar';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { NgbDropdown, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MatSidenavModule } from '@angular/material/sidenav';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { CourseSidebarComponent } from 'app/course/shared/course-sidebar/course-sidebar.component';
import { TeamService } from 'app/exercise/team/team.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { CourseExercisesComponent } from 'app/course/overview/course-exercises/course-exercises.component';
import { CourseRegistrationComponent } from 'app/course/overview/course-registration/course-registration.component';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_IRIS, MODULE_FEATURE_LECTURE, MODULE_FEATURE_LTI, PROFILE_PROD } from 'app/app.constants';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { CourseOverviewComponent } from 'app/course/overview/course-overview/course-overview.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { CourseAccessStorageService } from 'app/course/shared/services/course-access-storage.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { CourseSidebarService } from 'app/course/overview/services/course-sidebar.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { CourseExerciseRowComponent } from 'app/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { generateExampleTutorialGroupsConfigurationDTO } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { TutorialGroupConfigurationDTO } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

const endDate1 = dayjs().add(1, 'days');
const visibleDate1 = dayjs().subtract(1, 'days');
const dueDateStat1: DueDateStat = { inTime: 1, late: 0, total: 1 };
const exercise1: Exercise = {
    id: 5,
    numberOfAssessmentsOfCorrectionRounds: [dueDateStat1],
    studentAssignedTeamIdComputed: false,
    dueDate: dayjs().add(2, 'days'),
    secondCorrectionEnabled: true,
};
const exercise2: Exercise = {
    id: 6,
    numberOfAssessmentsOfCorrectionRounds: [dueDateStat1],
    studentAssignedTeamIdComputed: false,
    dueDate: dayjs().add(1, 'days'),
    secondCorrectionEnabled: true,
};
const courseEmpty: Course = {};

const exam1: Exam = { id: 3, endDate: endDate1, visibleDate: visibleDate1, course: courseEmpty };
const exam2: Exam = { id: 4, course: courseEmpty };
const exams: Exam[] = [exam1, exam2];
const course1: Course = {
    id: 1,
    title: 'Course1',
    exams,
    exercises: [exercise1],
    description:
        'Nihilne te nocturnum praesidium Palati, nihil urbis vigiliae. Salutantibus vitae elit libero, a pharetra augue. Quam diu etiam furor iste tuus nos eludet? ' +
        'Fabio vel iudice vincam, sunt in culpa qui officia. Quam temere in vitiis, legem sancimus haerentia. Quisque ut dolor gravida, placerat libero vel, euismod.',
    courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    courseIconPath: 'api/core/files/path/to/icon.png',
};
const course2: Course = {
    id: 2,
    title: 'Course2',
    exercises: [exercise2],
    exams: [exam2],
    description: 'Short description of course 2',
    shortName: 'shortName2',
    competencies: [{}],
    tutorialGroups: [new TutorialGroup()],
    prerequisites: [{}],
    numberOfCompetencies: 1,
    numberOfPrerequisites: 1,
    numberOfTutorialGroups: 1,
};
@Component({
    template: '<ng-template #controls><button id="test-button">TestButton</button></ng-template>',
})
class ControlsTestingComponent implements BarControlConfigurationProvider, AfterViewInit {
    controlsRendered = new EventEmitter<void>();

    private readonly controls = viewChild<TemplateRef<any>>('controls');
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
    };

    ngAfterViewInit(): void {
        this.controlConfiguration.subject!.next(this.controls()!);
    }
}

describe('CourseOverviewComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseOverviewComponent;
    let fixture: ComponentFixture<CourseOverviewComponent>;
    let courseService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    let examParticipationService: ExamParticipationService;
    let teamService: TeamService;
    let tutorialGroupApiService: TutorialGroupApiService;
    let tutorialGroupsConfigurationService: TutorialGroupsConfigurationService;
    let jhiWebsocketService: WebsocketService;
    let courseAccessStorageService: CourseAccessStorageService;
    let router: MockRouter;
    let jhiWebsocketServiceSubscribeSpy: ReturnType<typeof vi.spyOn>;
    let findOneForDashboardStub: ReturnType<typeof vi.spyOn>;
    let route: ActivatedRoute;
    let findOneForRegistrationStub: ReturnType<typeof vi.spyOn>;
    let courseSidebarService: CourseSidebarService;
    let profileService: ProfileService;
    let metisConversationService: MetisConversationService;

    const course = {
        id: 1,
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    } as Course;

    beforeEach(async () => {
        route = {
            params: of({ courseId: course1.id }) as Params,
            data: of({}),
            snapshot: { firstChild: { routeConfig: { path: `courses/${course1.id}/exercises` } } },
        } as ActivatedRoute;
        router = new MockRouter();

        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([]),
                MockModule(MatSidenavModule),
                MockModule(NgbTooltipModule),
                FaIconComponent,
                CourseOverviewComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockDirective(FeatureToggleHideDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(CourseExercisesComponent),
                MockComponent(CourseRegistrationComponent),
                MockComponent(ImageComponent),
                MockComponent(CourseSidebarComponent),
            ],
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(CourseExerciseService),
                MockProvider(CompetencyService),
                MockProvider(TeamService),
                { provide: WebsocketService, useClass: MockWebsocketService },
                MockProvider(ArtemisServerDateService),
                MockProvider(CalendarService),
                MockProvider(AlertService),
                MockProvider(ChangeDetectorRef),
                MockProvider(TutorialGroupApiService),
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(MetisConversationService),
                MockProvider(CourseAccessStorageService),
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: route },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: NgbDropdown, useClass: MockDirective(NgbDropdown) },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseOverviewComponent);
        component = fixture.componentInstance;

        component.isShownViaLti.set(false);
        courseSidebarService = TestBed.inject(CourseSidebarService);
        courseService = TestBed.inject(CourseManagementService);
        courseStorageService = TestBed.inject(CourseStorageService);
        examParticipationService = TestBed.inject(ExamParticipationService);
        teamService = TestBed.inject(TeamService);
        profileService = TestBed.inject(ProfileService);
        tutorialGroupApiService = TestBed.inject(TutorialGroupApiService);
        tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService);
        jhiWebsocketService = TestBed.inject(WebsocketService);
        courseAccessStorageService = TestBed.inject(CourseAccessStorageService);
        metisConversationService = fixture.debugElement.injector.get(MetisConversationService);
        jhiWebsocketServiceSubscribeSpy = vi.spyOn(jhiWebsocketService, 'subscribe');
        vi.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockResolvedValue(of(new TeamAssignmentPayload()));
        // default for findOneForDashboardStub is to return the course
        findOneForDashboardStub = vi.spyOn(courseService, 'findOneForDashboard').mockReturnValue(
            of(
                new HttpResponse({
                    body: course1,
                    headers: new HttpHeaders(),
                }),
            ),
        );
        // default for findOneForRegistrationStub is to return the course as well
        findOneForRegistrationStub = vi.spyOn(courseService, 'findOneForRegistration').mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        vi.spyOn(metisConversationService, 'course', 'get').mockReturnValue(course);
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({
            activeModuleFeatures: [MODULE_FEATURE_ATLAS, MODULE_FEATURE_IRIS, MODULE_FEATURE_LECTURE, MODULE_FEATURE_LTI],
            activeProfiles: [PROFILE_PROD],
            testServer: false,
        } as unknown as ProfileInfo);
    });

    afterEach(() => {
        component.ngOnDestroy();
        vi.restoreAllMocks();
        TestBed.inject(LocalStorageService).clear();
        TestBed.inject(SessionStorageService).clear();
    });

    it('should call all methods on init', async () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        const subscribeToTeamAssignmentUpdatesStub = vi.spyOn(component, 'subscribeToTeamAssignmentUpdates');
        const subscribeForQuizChangesStub = vi.spyOn(component, 'subscribeForQuizChanges');
        const notifyAboutCourseAccessStub = vi.spyOn(courseAccessStorageService, 'onCourseAccessed');
        const getSidebarItems = vi.spyOn(component, 'getSidebarItems');
        const getCourseActionItems = vi.spyOn(component, 'getCourseActionItems');
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        getCourseStub.mockReturnValue(course1);

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalled();
        expect(subscribeForQuizChangesStub).toHaveBeenCalledOnce();
        expect(subscribeToTeamAssignmentUpdatesStub).toHaveBeenCalledOnce();
        expect(getSidebarItems).toHaveBeenCalledOnce();
        expect(getCourseActionItems).toHaveBeenCalledOnce();
        expect(notifyAboutCourseAccessStub).toHaveBeenCalledWith(
            course1.id,
            CourseAccessStorageService.STORAGE_KEY,
            CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW,
        );
    });

    it('should create sidebar item for student course analytics dashboard if the feature is active', () => {
        component.lectureEnabled = true;
        component.course.set({ id: 123, lectures: [], exams: [], studentCourseAnalyticsDashboardEnabled: true });
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems.length).toBeGreaterThan(0);
        expect(sidebarItems[0].title).toContain('Dashboard');
        expect(sidebarItems[1].title).toContain('Exercises');
        expect(sidebarItems[2].title).toContain('Lectures');
    });

    it('should create sidebar items with default items', () => {
        component.lectureEnabled = true;
        component.course.set({ id: 123, lectures: [], exams: [] });
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems.length).toBeGreaterThan(0);
        expect(sidebarItems[0].title).toContain('Exercises');
        expect(sidebarItems[1].title).toContain('Lectures');
    });

    it('should create sidebar items for student if questions are available for practice', () => {
        component.lectureEnabled = true;
        component.course.set({ id: 123, lectures: [], exams: [], trainingEnabled: true });
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems.length).toBeGreaterThan(0);
        expect(sidebarItems[0].title).toContain('Exercises');
        expect(sidebarItems[1].title).toContain('Training');
        expect(sidebarItems[2].title).toContain('Lectures');
    });

    it('should create competencies and learning path item if competencies or prerequisites are available and learning paths are enabled', () => {
        component.course.set({ id: 123, numberOfPrerequisites: 3, learningPathsEnabled: true });
        component.atlasEnabled = true;
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems[3].title).toContain('Competencies');
        expect(sidebarItems[4].title).toContain('Learning Path');
    });

    it('should create faq item when accepted faqs exist', () => {
        component.course.set({ id: 123, numberOfAcceptedFaqs: 3 });
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems[3].title).toContain('FAQs');
    });

    it('loads conversations when switching to message tab once', async () => {
        const metisConversationServiceStub = vi.spyOn(metisConversationService, 'setUpConversationService').mockReturnValue(EMPTY);
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        getCourseStub.mockReturnValue(course1);

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalled();

        expect(metisConversationServiceStub).toHaveBeenCalledTimes(0);
        const baseUrl = '/' + 'courses/' + course1.id;
        const tabs = ['communication', 'exercises', 'communication'];
        tabs.forEach((tab) => {
            vi.spyOn(router, 'url', 'get').mockReturnValue(baseUrl + '/' + tab);
            component.onSubRouteActivate({ controlConfiguration: undefined });
            fixture.changeDetectorRef.detectChanges();
        });
        expect(metisConversationServiceStub).toHaveBeenCalledOnce();
    });

    it('should pass the page title to the exercises component and hide the top title bar', () => {
        const exercisesComponent = Object.create(CourseExercisesComponent.prototype) as CourseExercisesComponent;
        exercisesComponent.setPageTitle = vi.fn();
        Object.defineProperty(exercisesComponent, 'isCollapsed', { value: true });
        component.pageTitle.set('overview.exercises');

        (component as any).handleComponentActivation(exercisesComponent);

        expect(exercisesComponent.setPageTitle).toHaveBeenCalledWith('overview.exercises');
        expect(component.isSidebarCollapsed()).toBe(true);
        expect((component as any).showCourseTitleBar()).toBe(false);
    });

    it.each([true, false])('should determine once if there are unread messages', async (hasNewMessages: boolean) => {
        const spy = vi.spyOn(metisConversationService, 'checkForUnreadMessages');
        metisConversationService._hasUnreadMessages$.next(hasNewMessages);
        vi.spyOn(metisConversationService, 'setUpConversationService').mockReturnValue(of());
        vi.spyOn(router, 'url', 'get').mockReturnValue('/courses/1/communication');

        await component.ngOnInit();

        route.snapshot.firstChild!.routeConfig!.path = 'exercises';
        component.onSubRouteActivate({ controlConfiguration: undefined });
        fixture.changeDetectorRef.detectChanges();
        expect(component.hasUnreadMessages()).toBe(hasNewMessages);

        const tabs = ['communication', 'exercises', 'communication'];
        tabs.forEach((tab) => {
            route.snapshot.firstChild!.routeConfig!.path = tab;
            component.onSubRouteActivate({ controlConfiguration: undefined });
            fixture.changeDetectorRef.detectChanges();

            expect(spy).toHaveBeenCalledOnce();
        });
    });

    it('should not try to load message related data when not activated for course', () => {
        const unreadMessagesSpy = vi.spyOn(metisConversationService, 'checkForUnreadMessages');
        const setUpConversationServiceSpy = vi.spyOn(metisConversationService, 'setUpConversationService');

        component.course.set({ courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED });

        const tabs = ['exercises', 'communication', 'exercises', 'communication'];
        tabs.forEach((tab) => {
            route.snapshot.firstChild!.routeConfig!.path = tab;
            component.onSubRouteActivate({ controlConfiguration: undefined });
        });

        expect(unreadMessagesSpy).not.toHaveBeenCalled();
        expect(setUpConversationServiceSpy).not.toHaveBeenCalled();
    });

    it('should redirect to the registration page if the API endpoint returned a 403, but the user can register', async () => {
        // mock error response
        findOneForDashboardStub.mockReturnValue(
            throwError(
                () =>
                    new HttpResponse({
                        body: course1,
                        headers: new HttpHeaders(),
                        status: 403,
                    }),
            ),
        );
        const findOneForRegistrationStub = vi.spyOn(courseService, 'findOneForRegistration');
        findOneForRegistrationStub.mockReturnValue(
            of(
                new HttpResponse({
                    body: course1,
                    headers: new HttpHeaders(),
                    status: 200,
                }),
            ),
        );

        fixture.detectChanges();
        await fixture.whenStable();

        // When user can register, component should redirect to registration page
        expect(router.navigate).toHaveBeenCalledWith(['courses', course1.id, 'register']);
    });

    it('should call load Course methods on init', async () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        const subscribeToTeamAssignmentUpdatesStub = vi.spyOn(component, 'subscribeToTeamAssignmentUpdates');
        const subscribeForQuizChangesStub = vi.spyOn(component, 'subscribeForQuizChanges');
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalledOnce();
        expect(subscribeForQuizChangesStub).toHaveBeenCalledOnce();
        expect(subscribeToTeamAssignmentUpdatesStub).toHaveBeenCalledOnce();
    });

    it('should show an alert when loading the course fails', async () => {
        findOneForDashboardStub.mockReturnValue(throwError(() => new HttpResponse({ status: 404 })));
        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = vi.spyOn(alertService, 'addAlert');

        component.loadCourse().subscribe({
            next: () => {
                throw new Error('should not happen');
            },
            error: (error) => {
                expect(error).toBeDefined();
            },
        });

        expect(alertServiceSpy).toHaveBeenCalled();
    });

    it('should return false for canRegisterForCourse if the server returns 403', async () => {
        findOneForRegistrationStub.mockReturnValue(throwError(() => new HttpResponse({ status: 403 })));

        // test that canRegisterForCourse subscribe gives false
        return new Promise<void>((resolve) => {
            component.canRegisterForCourse().subscribe((canRegister) => {
                expect(canRegister).toBe(false);
                resolve();
            });
        });
    });

    it('should throw for unexpected registration responses from the server', async () => {
        findOneForRegistrationStub.mockReturnValue(throwError(() => new HttpResponse({ status: 404 })));

        return new Promise<void>((resolve) => {
            component.canRegisterForCourse().subscribe({
                next: () => {
                    throw new Error('should not be called');
                },
                error: (error) => {
                    expect(error).toEqual(new HttpResponse({ status: 404 }));
                    resolve();
                },
            });
        });
    });

    it('should load the course, even when just calling loadCourse by itself (for refreshing)', () => {
        // check that loadCourse already subscribes to the course itself

        // create observable httpResponse with course1, where we detect whether it was called
        const findOneForDashboardResponse = new Observable((subscriber) => {
            subscriber.next(course1);
            subscriber.complete();
        });
        const subscribeStub = vi.spyOn(findOneForDashboardResponse, 'subscribe');
        findOneForDashboardStub.mockReturnValue(findOneForDashboardResponse);

        // check that calendar events are refreshed
        const calendarService = TestBed.inject(CalendarService);
        const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');

        component.loadCourse(true);

        expect(subscribeStub).toHaveBeenCalledOnce();
        expect(refreshSpy).toHaveBeenCalledOnce();
    });

    it('should have visible exams', () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        getCourseStub.mockReturnValue(course1);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        component.ngOnInit();

        const bool = component.hasVisibleExams();

        expect(bool).toBe(true);
    });

    it('should not have visible exams', () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        const bool = component.hasVisibleExams();

        expect(bool).toBe(false);
    });

    it('should contain unenrollment as course action when allowed', () => {
        component.course.set({ unenrollmentEnabled: true, unenrollmentEndDate: dayjs().add(1, 'days') });
        const courseActionItems = component.getCourseActionItems();
        expect(courseActionItems.length).toBeGreaterThan(0);
        expect(courseActionItems[0].title).toContain('Unenroll');
    });

    it('should set showUnenrollModal to true on triggering unenrollment option', () => {
        expect(component.showUnenrollModal()).toBe(false);
        component.courseActionItemClick(component.getUnenrollItem());
        expect(component.showUnenrollModal()).toBe(true);
    });

    it('should have competencies and tutorial groups', () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');

        const tutorialGroupsResponse: HttpResponse<TutorialGroup[]> = new HttpResponse({
            body: [new TutorialGroup()],
            status: 200,
        });
        const configurationResponse: HttpResponse<TutorialGroupConfigurationDTO> = new HttpResponse({
            body: generateExampleTutorialGroupsConfigurationDTO({}),
            status: 200,
        });

        vi.spyOn(tutorialGroupApiService, 'getTutorialGroupsForCourse').mockReturnValue(of(tutorialGroupsResponse));
        vi.spyOn(tutorialGroupsConfigurationService, 'getOneOfCourse').mockReturnValue(of(configurationResponse));

        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        expect(component.hasCompetencies()).toBe(true);
        expect(component.hasTutorialGroups()).toBe(true);
        expect(component.course()?.competencies).toHaveLength(1);
        expect(component.course()?.prerequisites).toHaveLength(1);
        expect(component.course()?.tutorialGroups).toHaveLength(1);
    });

    it('should subscribeToTeamAssignmentUpdates', () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        const teamAssignmentUpdatesStub = vi.spyOn(teamService, 'teamAssignmentUpdates', 'get');
        getCourseStub.mockReturnValue(course2);
        teamAssignmentUpdatesStub.mockReturnValue(
            Promise.resolve(
                of({
                    exerciseId: 6,
                    teamId: 1,
                    studentParticipations: [],
                }),
            ),
        );
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        component.subscribeToTeamAssignmentUpdates();
    });

    it('should subscribeForQuizChanges', () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();
        component.subscribeForQuizChanges();

        expect(jhiWebsocketServiceSubscribeSpy).toHaveBeenCalledWith('/topic/courses/' + course.id + '/quizExercises');
    });

    it('should do ngOnDestroy', () => {
        component.ngOnInit();
        component.subscribeForQuizChanges(); // to have quizExercisesSubscription set
        // @ts-ignore
        const quizUnsubscribeSpy = vi.spyOn(component.quizExercisesSubscription!, 'unsubscribe');

        component.ngOnDestroy();

        expect(quizUnsubscribeSpy).toHaveBeenCalledOnce();
    });

    it('should render controls if child has configuration', () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        const stubSubComponent = TestBed.createComponent(ControlsTestingComponent);
        component.onSubRouteActivate(stubSubComponent.componentInstance);
        fixture.changeDetectorRef.detectChanges();
        stubSubComponent.changeDetectorRef.detectChanges();

        const expectedButton = fixture.debugElement.query(By.css('#test-button'));
        expect(expectedButton).not.toBeNull();
        expect(expectedButton.nativeElement.innerHTML).toBe('TestButton');
    });

    it('should toggle sidebar based on isNavbarCollapsed', () => {
        component.isNavbarCollapsed.set(true);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.container-closed')).not.toBeNull();

        component.isNavbarCollapsed.set(false);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.container-closed')).toBeNull();
    });

    it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBe(true);

        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBe(false);
    });

    it('should apply exam-wrapper and exam-is-active if exam is started', () => {
        component.isExamStarted.set(true);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.exam-wrapper')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('.exam-is-active')).not.toBeNull();

        component.isExamStarted.set(false);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.exam-wrapper')).toBeNull();
        expect(fixture.nativeElement.querySelector('.exam-is-active')).toBeNull();
    });

    it('should examStarted value to true when exam is started', async () => {
        (examParticipationService as any).examIsStarted$ = of(true);
        await component.ngOnInit();
        expect(component.isExamStarted()).toBe(true);
    });

    it('should toggle isCollapsed when service emits corresponding event', () => {
        fixture.detectChanges();
        courseSidebarService.openSidebar();
        expect(component.isSidebarCollapsed()).toBe(true);

        courseSidebarService.closeSidebar();
        expect(component.isSidebarCollapsed()).toBe(false);

        courseSidebarService.toggleSidebar();
        expect(component.isSidebarCollapsed()).toBe(true);
    });
});
