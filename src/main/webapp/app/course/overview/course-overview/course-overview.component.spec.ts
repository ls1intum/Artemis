import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { CourseLecturesComponent } from 'app/lecture/shared/course-lectures/course-lectures.component';
import { FeatureToggleHideDirective } from 'app/foundation/feature-toggle/feature-toggle-hide.directive';
import { BehaviorSubject, EMPTY, Subject, of, throwError } from 'rxjs';
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
import { TutorialGroupSummary } from 'app/openapi/model/tutorial-group-summary';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { NgbDropdown, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
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
import { CourseAvailableTabs } from 'app/course/shared/entities/course-available-tabs.model';
import { CourseAvailableTabsService } from 'app/course/overview/services/course-available-tabs.service';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseOverviewTabDataService } from 'app/course/overview/services/course-overview-tab-data.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { CourseAccessStorageService } from 'app/course/shared/services/course-access-storage.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { CourseSidebarService } from 'app/course/overview/services/course-sidebar.service';
import { CourseSidebarItemService } from 'app/course/shared/services/sidebar-item.service';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
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
    let component: CourseOverviewComponent;
    let fixture: ComponentFixture<CourseOverviewComponent>;
    let courseService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    let examParticipationService: ExamParticipationService;
    let teamService: TeamService;
    let tutorialGroupApiService: TutorialGroupApi;
    let tutorialGroupsConfigurationService: TutorialGroupsConfigurationService;
    let courseAccessStorageService: CourseAccessStorageService;
    let router: MockRouter;
    let findCourseForOverviewStub: ReturnType<typeof vi.spyOn>;
    let route: ActivatedRoute;
    let findOneForRegistrationStub: ReturnType<typeof vi.spyOn>;
    let getCourseAvailableTabsStub: ReturnType<typeof vi.spyOn>;
    let availableTabsService: CourseAvailableTabsService;
    let courseTabRefreshService: CourseTabRefreshService;
    let courseSidebarService: CourseSidebarService;
    let courseSidebarItemService: CourseSidebarItemService;
    let profileService: ProfileService;

    let metisConversationService: MetisConversationService;

    const course = {
        id: 1,
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    } as Course;

    /** Builds a full set of tab flags, overriding the ones a test cares about. */
    const availableTabs = (overrides: Partial<CourseAvailableTabs> = {}): CourseAvailableTabs => ({
        lectures: false,
        exams: false,
        competencies: false,
        tutorialGroups: false,
        iris: false,
        faq: false,
        learningPaths: false,
        communication: false,
        training: false,
        ...overrides,
    });

    beforeEach(async () => {
        route = {
            params: of({ courseId: course1.id }) as Params,
            data: of({}),
            snapshot: { firstChild: { routeConfig: { path: 'exercises' } } },
        } as ActivatedRoute;
        router = new MockRouter();

        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([]),
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
                MockProvider(TutorialGroupApi),
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(MetisConversationService),
                MockProvider(CourseAccessStorageService),
                MockProvider(CourseOverviewTabDataService),
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
        courseSidebarItemService = TestBed.inject(CourseSidebarItemService);
        courseService = TestBed.inject(CourseManagementService);
        courseStorageService = TestBed.inject(CourseStorageService);
        examParticipationService = TestBed.inject(ExamParticipationService);
        teamService = TestBed.inject(TeamService);
        profileService = TestBed.inject(ProfileService);
        tutorialGroupApiService = TestBed.inject(TutorialGroupApi);
        tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService);
        courseAccessStorageService = TestBed.inject(CourseAccessStorageService);
        metisConversationService = fixture.debugElement.injector.get(MetisConversationService);
        vi.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockResolvedValue(of(new TeamAssignmentPayload()));
        // default for findCourseForOverviewStub is to return the course
        findCourseForOverviewStub = vi.spyOn(courseService, 'findCourseForOverview').mockReturnValue(
            of(
                new HttpResponse({
                    body: course1,
                    headers: new HttpHeaders(),
                }),
            ),
        );
        availableTabsService = TestBed.inject(CourseAvailableTabsService);
        courseTabRefreshService = TestBed.inject(CourseTabRefreshService);
        availableTabsService.clear();
        getCourseAvailableTabsStub = vi.spyOn(courseService, 'getCourseAvailableTabs').mockReturnValue(of(availableTabs()));
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
        const notifyAboutCourseAccessStub = vi.spyOn(courseAccessStorageService, 'onCourseAccessed');
        const getSidebarItems = vi.spyOn(component, 'getSidebarItems');
        const getCourseActionItems = vi.spyOn(component, 'getCourseActionItems');
        findCourseForOverviewStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        getCourseStub.mockReturnValue(course1);

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalled();
        // The sidebar is built twice on purpose: once up front with the always-available items, and again when the
        // available tabs arrive and fill in the rest
        expect(getSidebarItems).toHaveBeenCalled();
        expect(getCourseActionItems).toHaveBeenCalledOnce();
        expect(notifyAboutCourseAccessStub).toHaveBeenCalledWith(
            course1.id,
            CourseAccessStorageService.STORAGE_KEY,
            CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW,
        );
    });

    it('should adopt the tabs a guard fetched for this navigation, so the sidebar cannot disagree with it', () => {
        // The container survives child-tab navigations, so without this the sidebar keeps whatever the course load
        // found on entry while the guard decides each guarded navigation from a freshly fetched answer
        component.courseId.set(1);
        component.availableTabs.set(availableTabs({ lectures: true }));

        // A guard runs for the next navigation and finds the lectures tab gone and the exams tab newly available
        getCourseAvailableTabsStub.mockReturnValue(of(availableTabs({ lectures: false, exams: true })));
        availableTabsService.loadIfNeeded(1).subscribe();
        (component as any).handleNavigationEndActions();

        expect(component.availableTabs()?.lectures).toBe(false);
        expect(component.availableTabs()?.exams).toBe(true);
    });

    it('should not fetch tabs of its own, so a chain of navigations costs no extra request', () => {
        component.courseId.set(1);
        component.availableTabs.set(availableTabs({ lectures: true }));
        getCourseAvailableTabsStub.mockClear();

        // No guard ran for this navigation, so nothing has contradicted the current flags
        availableTabsService.clear();
        (component as any).handleNavigationEndActions();

        expect(getCourseAvailableTabsStub).not.toHaveBeenCalled();
        expect(component.availableTabs()?.lectures).toBe(true);
    });

    it('should reconcile the tabs when the user selected a tab and no guard answer survived the navigation', () => {
        // A guard that denies a removed tab cancels its navigation and redirects, so the answer it fetched is filed
        // under the cancelled navigation and the redirect cannot read it. Unguarded tabs fetch nothing at all. Without
        // reconciling, the sidebar would keep offering a link that can never be opened.
        component.courseId.set(1);
        component.availableTabs.set(availableTabs({ lectures: true }));
        availableTabsService.clear();
        getCourseAvailableTabsStub.mockClear();
        getCourseAvailableTabsStub.mockReturnValue(of(availableTabs({ lectures: false })));

        courseTabRefreshService.notifyTabSelected('lectures');
        (component as any).handleNavigationEndActions();

        expect(getCourseAvailableTabsStub).toHaveBeenCalledOnce();
        expect(component.availableTabs()?.lectures).toBe(false);
    });

    it('should reconcile only once per selection, so a chain of navigations still costs one request', () => {
        component.courseId.set(1);
        component.availableTabs.set(availableTabs({ lectures: true }));
        availableTabsService.clear();
        getCourseAvailableTabsStub.mockClear();
        getCourseAvailableTabsStub.mockReturnValue(of(availableTabs({ lectures: false })));

        courseTabRefreshService.notifyTabSelected('lectures');
        // Selecting a tab is a chain: the tab, then whatever child it auto-selects
        (component as any).handleNavigationEndActions();
        availableTabsService.clear();
        (component as any).handleNavigationEndActions();

        expect(getCourseAvailableTabsStub).toHaveBeenCalledOnce();
    });

    it('should ignore a course load that lands after the user switched course', () => {
        // The very first load is subscribed by the base class through firstValueFrom and is not held in
        // loadCourseSubscription, so an in-place switch cannot cancel it. Without discarding it, the slower of the two
        // overlapping loads wins and restores the course the user has already left.
        const slowFirstCourse = new Subject<HttpResponse<Course>>();
        findCourseForOverviewStub.mockReturnValue(slowFirstCourse);
        component.courseId.set(1);
        component.loadCourse().subscribe({ error: () => {} });

        // The user switches to course 2 before course 1 answers
        component.courseId.set(2);
        component.course.set(course2);
        slowFirstCourse.next(new HttpResponse({ body: course1 }));
        slowFirstCourse.complete();

        expect(component.course()?.id).toBe(2);
    });

    it('should create sidebar items with default items', () => {
        component.lectureEnabled = true;
        component.availableTabs.set(availableTabs({ lectures: true }));
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems.length).toBeGreaterThan(0);
        expect(sidebarItems[0].title).toContain('Exercises');
        expect(sidebarItems[1].title).toContain('Lectures');
    });

    it('should create sidebar items for student if questions are available for practice', () => {
        component.lectureEnabled = true;
        component.availableTabs.set(availableTabs({ lectures: true, training: true }));
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems.length).toBeGreaterThan(0);
        expect(sidebarItems[0].title).toContain('Exercises');
        expect(sidebarItems[1].title).toContain('Training');
        expect(sidebarItems[2].title).toContain('Lectures');
    });

    it('should create competencies and learning path item if competencies or prerequisites are available and learning paths are enabled', () => {
        component.availableTabs.set(availableTabs({ competencies: true, learningPaths: true }));
        component.atlasEnabled = true;
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems[3].title).toContain('Competencies');
        expect(sidebarItems[4].title).toContain('Learning Path');
    });

    it('should create faq item when accepted faqs exist', () => {
        component.availableTabs.set(availableTabs({ faq: true }));
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems[3].title).toContain('FAQs');
    });

    it('loads conversations when switching to message tab once', async () => {
        const metisConversationServiceStub = vi.spyOn(metisConversationService, 'setUpConversationService').mockReturnValue(EMPTY);
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        findCourseForOverviewStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
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

    it('should pass the page title to the exercises component', () => {
        const exercisesComponent = Object.create(CourseExercisesComponent.prototype) as CourseExercisesComponent;
        exercisesComponent.setPageTitle = vi.fn();
        Object.defineProperty(exercisesComponent, 'isCollapsed', { value: signal(true) });
        component.pageTitle.set('overview.exercises');

        (component as any).handleComponentActivation(exercisesComponent);

        expect(exercisesComponent.setPageTitle).toHaveBeenCalledWith('overview.exercises');
        expect(component.isSidebarCollapsed()).toBe(true);
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
        findCourseForOverviewStub.mockReturnValue(
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
        findCourseForOverviewStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalled();
    });

    it('should show an alert when loading the course fails', async () => {
        findCourseForOverviewStub.mockReturnValue(throwError(() => new HttpResponse({ status: 404 })));
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

    it('should fetch the course content exactly once when navigating into the course', async () => {
        await component.ngOnInit();
        expect(findCourseForOverviewStub).toHaveBeenCalledExactlyOnceWith(course1.id);
    });

    it('should fetch the available tabs once per course visit and again for a different course', async () => {
        // The tabs are shared with the CourseOverviewGuard and keyed by course id: entering a course costs one request,
        // and switching to another course in place must not reuse the previous course's tabs.
        const paramsSubject = new BehaviorSubject<Params>({ courseId: course1.id });
        (route as any).params = paramsSubject.asObservable();
        await component.ngOnInit();
        expect(getCourseAvailableTabsStub).toHaveBeenCalledExactlyOnceWith(course1.id);

        paramsSubject.next({ courseId: 999 });

        expect(getCourseAvailableTabsStub).toHaveBeenCalledTimes(2);
        expect(getCourseAvailableTabsStub).toHaveBeenLastCalledWith(999);
    });

    it('should reuse the tabs the guard already fetched for this course visit', async () => {
        // The guard runs first on a deep link into a guarded tab; the container must not fetch them a second time.
        availableTabsService.load(course1.id!).subscribe();
        getCourseAvailableTabsStub.mockClear();

        await component.ngOnInit();

        expect(getCourseAvailableTabsStub).not.toHaveBeenCalled();
    });

    it('should reload the course content when navigating to a different course in place', async () => {
        const paramsSubject = new BehaviorSubject<Params>({ courseId: course1.id });
        (route as any).params = paramsSubject.asObservable();
        const clearTabDataSpy = vi.spyOn(TestBed.inject(CourseOverviewTabDataService), 'clear');
        await component.ngOnInit();
        expect(findCourseForOverviewStub).toHaveBeenCalledExactlyOnceWith(course1.id);

        const getSidebarItemsSpy = vi.spyOn(component, 'getSidebarItems');
        paramsSubject.next({ courseId: 999 });

        expect(component.courseId()).toBe(999);
        expect(findCourseForOverviewStub).toHaveBeenCalledTimes(2);
        expect(findCourseForOverviewStub).toHaveBeenLastCalledWith(999);
        expect(getSidebarItemsSpy).toHaveBeenCalled();
        expect(clearTabDataSpy).toHaveBeenCalledOnce();
    });

    it('should clear the previous course tab flags immediately and keep only safe sidebar items when the new tab request fails', async () => {
        const paramsSubject = new BehaviorSubject<Params>({ courseId: course1.id });
        const newCourseTabs = new Subject<CourseAvailableTabs>();
        (route as any).params = paramsSubject.asObservable();
        getCourseAvailableTabsStub.mockImplementation((courseId: number) =>
            courseId === course1.id ? of(availableTabs({ exams: true, lectures: true, faq: true })) : newCourseTabs.asObservable(),
        );
        component.lectureEnabled = true;
        await component.ngOnInit();
        expect(component.availableTabs()?.exams).toBe(true);
        expect(component.sidebarItems().some((item) => item.title?.includes('Exams'))).toBe(true);

        paramsSubject.next({ courseId: 999 });

        expect(component.availableTabs()).toBeUndefined();
        expect(component.sidebarItems().some((item) => item.title?.includes('Exercises'))).toBe(true);
        expect(component.sidebarItems().some((item) => item.title?.includes('Exams'))).toBe(false);
        expect(component.sidebarItems().some((item) => item.title?.includes('Lectures'))).toBe(false);
        expect(component.sidebarItems().some((item) => item.title?.includes('FAQ'))).toBe(false);

        newCourseTabs.error(new Error('network'));
        expect(component.availableTabs()).toBeUndefined();
        expect(component.sidebarItems().some((item) => item.title?.includes('Exams'))).toBe(false);
    });

    it('should show the exams item when the exams tab is available', () => {
        component.availableTabs.set(availableTabs({ exams: true }));
        expect(component.getSidebarItems()[0].title).toContain('Exams');
    });

    it('should not show the exams item when the exams tab is unavailable', () => {
        component.availableTabs.set(availableTabs({ exams: false }));
        expect(component.getSidebarItems().some((item) => item.title?.includes('Exams'))).toBe(false);
    });

    it('should include each server-enabled conditional sidebar item when its client module is enabled', () => {
        component.tutorialGroupEnabled = true;
        component.availableTabs.set(availableTabs({ communication: true, tutorialGroups: true, iris: true }));

        const items = component.getSidebarItems();

        expect(items).toContainEqual(courseSidebarItemService.getCommunicationsItem());
        expect(items).toContainEqual(courseSidebarItemService.getTutorialGroupsItem());
        expect(items).toContainEqual(courseSidebarItemService.getIrisItem());
    });

    it('should render only the always-available items before the tabs have arrived', () => {
        component.availableTabs.set(undefined);
        const titles = component.getSidebarItems().map((item) => item.title);
        expect(titles.some((title) => title?.includes('Exercises'))).toBe(true);
        expect(titles.some((title) => title?.includes('Lectures'))).toBe(false);
        expect(titles.some((title) => title?.includes('FAQ'))).toBe(false);
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

        const tutorialGroupsResponse: HttpResponse<TutorialGroupSummary[]> = new HttpResponse({
            body: [{ id: 1 }],
            status: 200,
        });
        const configurationResponse: HttpResponse<TutorialGroupConfigurationDTO> = new HttpResponse({
            body: generateExampleTutorialGroupsConfigurationDTO({}),
            status: 200,
        });

        vi.spyOn(tutorialGroupApiService, 'getTutorialGroupsForCourse').mockReturnValue(of(tutorialGroupsResponse.body!));
        vi.spyOn(tutorialGroupsConfigurationService, 'getOneOfCourse').mockReturnValue(of(configurationResponse));

        getCourseStub.mockReturnValue(course2);
        findCourseForOverviewStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        expect(component.course()?.competencies).toHaveLength(1);
        expect(component.course()?.prerequisites).toHaveLength(1);
        expect(component.course()?.tutorialGroups).toHaveLength(1);
    });

    it('should drop the per-visit tab and exercise state on destroy so re-entering the course refetches', () => {
        const clearTabsSpy = vi.spyOn(availableTabsService, 'clear');
        const clearExercisesSpy = vi.spyOn(TestBed.inject(CourseOverviewExercisesService), 'clear');
        const clearTabDataSpy = vi.spyOn(TestBed.inject(CourseOverviewTabDataService), 'clear');

        component.ngOnInit();
        component.ngOnDestroy();

        expect(clearTabsSpy).toHaveBeenCalled();
        expect(clearExercisesSpy).toHaveBeenCalled();
        expect(clearTabDataSpy).toHaveBeenCalled();
    });

    it('should render controls if child has configuration', () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findCourseForOverviewStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

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

    it('should hide the sidebar while an exam is started and show it otherwise', () => {
        component.isExamStarted.set(true);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar')?.hidden).toBe(true);

        component.isExamStarted.set(false);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.sidebar')?.hidden).toBe(false);
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

    describe('sidebar toggle relocation', () => {
        type CourseOverviewInternals = {
            handleComponentActivation(componentRef: unknown): void;
            showCourseTitleBar(): boolean;
        };
        const internals = (): CourseOverviewInternals => component as unknown as CourseOverviewInternals;

        it('should push the page title into the activated list tab', () => {
            const setPageTitle = vi.fn();
            const fakeLectures: CourseLecturesComponent = Object.assign(Object.create(CourseLecturesComponent.prototype), { isCollapsed: signal(false), setPageTitle });
            component.pageTitle.set('overview.lectures');

            internals().handleComponentActivation(fakeLectures);

            expect(setPageTitle).toHaveBeenCalledWith('overview.lectures');
        });

        it('should show the title bar on every page without its own sidebar', () => {
            // Calendar, statistics, competencies, learning path and quiz training project nothing and previously had
            // no bar at all, which left the student overview inconsistent with course management and administration.
            component.hasSidebar.set(false);

            expect(internals().showCourseTitleBar()).toBe(true);
        });

        it('should leave the title bar to a sidebar page unless it projects content', () => {
            const titleBarService = TestBed.inject(CourseTitleBarService);
            // A sidebar page carries the page identity in its sidebar header, so a second bar would only repeat it.
            component.hasSidebar.set(true);
            expect(internals().showCourseTitleBar()).toBe(false);

            // Projecting content still opens the bar — this is what keeps the communication search visible.
            titleBarService.setActionsTemplate({} as TemplateRef<unknown>);
            expect(internals().showCourseTitleBar()).toBe(true);

            titleBarService.setActionsTemplate(undefined);
            expect(internals().showCourseTitleBar()).toBe(false);
        });
    });
});
