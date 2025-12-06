import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Params, Router, RouterModule } from '@angular/router';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ImageComponent } from 'app/shared/image/image.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AfterViewInit, ChangeDetectorRef, Component, EventEmitter, TemplateRef, ViewChild } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { NgbDropdown, NgbModal, NgbModalRef, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatSidenavModule } from '@angular/material/sidenav';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { CourseSidebarComponent } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { TeamService } from 'app/exercise/team/team.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { CourseExercisesComponent } from 'app/core/course/overview/course-exercises/course-exercises.component';
import { CourseRegistrationComponent } from 'app/core/course/overview/course-registration/course-registration.component';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MODULE_FEATURE_ATLAS, PROFILE_IRIS, PROFILE_LTI, PROFILE_PROD } from 'app/app.constants';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { CourseOverviewComponent } from 'app/core/course/overview/course-overview/course-overview.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { CourseAccessStorageService } from 'app/core/course/shared/services/course-access-storage.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { CourseSidebarService } from 'app/core/course/overview/services/course-sidebar.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { CourseExerciseRowComponent } from 'app/core/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { CourseNotificationSettingService } from 'app/communication/course-notification/course-notification-setting.service';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotificationSettingPreset } from 'app/communication/shared/entities/course-notification/course-notification-setting-preset';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationInfo } from 'app/communication/shared/entities/course-notification/course-notification-info';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

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
const coursesDropdown: Course[] = [course1, course2];
const courses: Course[] = [course2];

@Component({
    template: '<ng-template #controls><button id="test-button">TestButton</button></ng-template>',
})
class ControlsTestingComponent implements BarControlConfigurationProvider, AfterViewInit {
    controlsRendered = new EventEmitter<void>();

    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
    };

    ngAfterViewInit(): void {
        this.controlConfiguration.subject!.next(this.controls);
    }
}

describe('CourseOverviewComponent', () => {
    let component: CourseOverviewComponent;
    let fixture: ComponentFixture<CourseOverviewComponent>;
    let courseService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    let examParticipationService: ExamParticipationService;
    let teamService: TeamService;
    let tutorialGroupsService: TutorialGroupsService;
    let tutorialGroupsConfigurationService: TutorialGroupsConfigurationService;
    let jhiWebsocketService: WebsocketService;
    let courseAccessStorageService: CourseAccessStorageService;
    let router: MockRouter;
    let jhiWebsocketServiceSubscribeSpy: jest.SpyInstance;
    let findOneForDashboardStub: jest.SpyInstance;
    let route: ActivatedRoute;
    let findOneForRegistrationStub: jest.SpyInstance;
    let findAllForDropdownSpy: jest.SpyInstance;
    let courseSidebarService: CourseSidebarService;
    let profileService: ProfileService;
    let modalService: NgbModal;
    let courseNotificationSettingService: CourseNotificationSettingService;
    let courseNotificationService: CourseNotificationService;

    let metisConversationService: MetisConversationService;

    const course = {
        id: 1,
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    } as Course;

    const mockCourseId = 123;

    const mockNotificationSettingPresets: CourseNotificationSettingPreset[] = [
        { typeId: 1, identifier: 'All Notifications', presetMap: { test: { PUSH: true, EMAIL: true, WEBAPP: true } } },
        { typeId: 2, identifier: 'Important Only', presetMap: { test: { PUSH: true, EMAIL: false, WEBAPP: true } } },
        { typeId: 3, identifier: 'Minimal', presetMap: { test: { PUSH: false, EMAIL: false, WEBAPP: true } } },
    ];

    const mockSettingInfo: CourseNotificationSettingInfo = {
        selectedPreset: 1,
        notificationTypeChannels: { test: { PUSH: true, EMAIL: true, WEBAPP: true } },
    };

    const mockNotificationInfo: CourseNotificationInfo = {
        presets: mockNotificationSettingPresets,
        notificationTypes: {},
    };

    beforeEach(fakeAsync(() => {
        route = {
            params: of({ courseId: course1.id }) as Params,
            snapshot: { firstChild: { routeConfig: { path: `courses/${course1.id}/exercises` } } },
        } as ActivatedRoute;
        router = new MockRouter();

        TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([]), MockModule(MatSidenavModule), MockModule(NgbTooltipModule), MockModule(BrowserAnimationsModule), FaIconComponent],
            declarations: [
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
                MockProvider(TutorialGroupsService),
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(MetisConversationService),
                MockProvider(CourseAccessStorageService),
                MockProvider(NgbModal),
                MockProvider(CourseNotificationSettingService),
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
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseOverviewComponent);
                component = fixture.componentInstance;

                component.isShownViaLti.set(false);
                courseSidebarService = TestBed.inject(CourseSidebarService);
                courseService = TestBed.inject(CourseManagementService);
                courseStorageService = TestBed.inject(CourseStorageService);
                examParticipationService = TestBed.inject(ExamParticipationService);
                teamService = TestBed.inject(TeamService);
                profileService = TestBed.inject(ProfileService);
                modalService = TestBed.inject(NgbModal);
                tutorialGroupsService = TestBed.inject(TutorialGroupsService);
                tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService);
                jhiWebsocketService = TestBed.inject(WebsocketService);
                courseAccessStorageService = TestBed.inject(CourseAccessStorageService);
                metisConversationService = fixture.debugElement.injector.get(MetisConversationService);
                jhiWebsocketServiceSubscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
                jest.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockResolvedValue(of(new TeamAssignmentPayload()));
                courseNotificationSettingService = TestBed.inject(CourseNotificationSettingService);
                courseNotificationService = TestBed.inject(CourseNotificationService);
                // default for findOneForDashboardStub is to return the course
                findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: course1,
                            headers: new HttpHeaders(),
                        }),
                    ),
                );
                // default for findOneForRegistrationStub is to return the course as well
                findOneForRegistrationStub = jest
                    .spyOn(courseService, 'findOneForRegistration')
                    .mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
                jest.spyOn(metisConversationService, 'course', 'get').mockReturnValue(course);
                findAllForDropdownSpy = jest
                    .spyOn(courseService, 'findAllForDropdown')
                    .mockReturnValue(of(new HttpResponse({ body: coursesDropdown, headers: new HttpHeaders() })));
                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue({
                    activeModuleFeatures: [MODULE_FEATURE_ATLAS],
                    activeProfiles: [PROFILE_IRIS, PROFILE_LTI, PROFILE_PROD],
                    testServer: false,
                } as unknown as ProfileInfo);
                jest.spyOn(courseNotificationSettingService, 'getSettingInfo').mockReturnValue(of(mockSettingInfo));
                jest.spyOn(courseNotificationService, 'getInfo').mockReturnValue(of(new HttpResponse({ body: mockNotificationInfo })));
                jest.spyOn(courseNotificationSettingService, 'setSettingPreset').mockImplementation();
            });
    }));

    afterEach(() => {
        component.ngOnDestroy();
        jest.restoreAllMocks();
        TestBed.inject(LocalStorageService).clear();
        TestBed.inject(SessionStorageService).clear();
    });

    it('should call all methods on init', async () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        const subscribeToTeamAssignmentUpdatesStub = jest.spyOn(component, 'subscribeToTeamAssignmentUpdates');
        const subscribeForQuizChangesStub = jest.spyOn(component, 'subscribeForQuizChanges');
        const notifyAboutCourseAccessStub = jest.spyOn(courseAccessStorageService, 'onCourseAccessed');
        const getSidebarItems = jest.spyOn(component, 'getSidebarItems');
        const getCourseActionItems = jest.spyOn(component, 'getCourseActionItems');
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
        expect(notifyAboutCourseAccessStub).toHaveBeenCalledWith(
            course1.id,
            CourseAccessStorageService.STORAGE_KEY_DROPDOWN,
            CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_DROPDOWN,
        );
    });

    it('should create sidebar item for student course analytics dashboard if the feature is active', () => {
        component.course.set({ id: 123, lectures: [], exams: [], studentCourseAnalyticsDashboardEnabled: true });
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems.length).toBeGreaterThan(0);
        expect(sidebarItems[0].title).toContain('Dashboard');
        expect(sidebarItems[1].title).toContain('Exercises');
        expect(sidebarItems[2].title).toContain('Lectures');
    });

    it('should create sidebar items with default items', () => {
        component.course.set({ id: 123, lectures: [], exams: [] });
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems.length).toBeGreaterThan(0);
        expect(sidebarItems[0].title).toContain('Exercises');
        expect(sidebarItems[1].title).toContain('Lectures');
    });

    it('should create sidebar items for student if questions are available for practice', () => {
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

    it('should create faq item when faqs are enabled', () => {
        component.course.set({ id: 123, faqEnabled: true });
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems[3].title).toContain('FAQs');
    });

    it('loads conversations when switching to message tab once', async () => {
        const metisConversationServiceStub = jest.spyOn(metisConversationService, 'setUpConversationService').mockReturnValue(EMPTY);
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        getCourseStub.mockReturnValue(course1);

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalled();

        expect(metisConversationServiceStub).toHaveBeenCalledTimes(0);
        const baseUrl = '/' + 'courses/' + course1.id;
        const tabs = ['communication', 'exercises', 'communication'];
        tabs.forEach((tab) => {
            jest.spyOn(router, 'url', 'get').mockReturnValue(baseUrl + '/' + tab);
            component.onSubRouteActivate({ controlConfiguration: undefined });
            fixture.detectChanges();
        });
        expect(metisConversationServiceStub).toHaveBeenCalledOnce();
    });

    it.each([true, false])('should determine once if there are unread messages', async (hasNewMessages: boolean) => {
        const spy = jest.spyOn(metisConversationService, 'checkForUnreadMessages');
        metisConversationService._hasUnreadMessages$.next(hasNewMessages);
        jest.spyOn(metisConversationService, 'setUpConversationService').mockReturnValue(of());
        jest.spyOn(router, 'url', 'get').mockReturnValue('/courses/1/communication');

        await component.ngOnInit();

        route.snapshot.firstChild!.routeConfig!.path = 'exercises';
        component.onSubRouteActivate({ controlConfiguration: undefined });
        fixture.detectChanges();
        expect(component.hasUnreadMessages()).toBe(hasNewMessages);

        const tabs = ['communication', 'exercises', 'communication'];
        tabs.forEach((tab) => {
            route.snapshot.firstChild!.routeConfig!.path = tab;
            component.onSubRouteActivate({ controlConfiguration: undefined });
            fixture.detectChanges();

            expect(spy).toHaveBeenCalledOnce();
        });
    });

    it('should not try to load message related data when not activated for course', () => {
        const unreadMessagesSpy = jest.spyOn(metisConversationService, 'checkForUnreadMessages');
        const setUpConversationServiceSpy = jest.spyOn(metisConversationService, 'setUpConversationService');

        component.course.set({ courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED });

        const tabs = ['exercises', 'communication', 'exercises', 'communication'];
        tabs.forEach((tab) => {
            route.snapshot.firstChild!.routeConfig!.path = tab;
            component.onSubRouteActivate({ controlConfiguration: undefined });
        });

        expect(unreadMessagesSpy).not.toHaveBeenCalled();
        expect(setUpConversationServiceSpy).not.toHaveBeenCalled();
    });

    it('should redirect to the registration page if the API endpoint returned a 403, but the user can register', fakeAsync(() => {
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
        const findOneForRegistrationStub = jest.spyOn(courseService, 'findOneForRegistration');
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
        tick();

        expect(router.navigate).toHaveBeenCalledWith(['courses', course1.id, 'register']);
    }));

    it('should call load Course methods on init', async () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        const subscribeToTeamAssignmentUpdatesStub = jest.spyOn(component, 'subscribeToTeamAssignmentUpdates');
        const subscribeForQuizChangesStub = jest.spyOn(component, 'subscribeForQuizChanges');
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalledOnce();
        expect(subscribeForQuizChangesStub).toHaveBeenCalledOnce();
        expect(subscribeToTeamAssignmentUpdatesStub).toHaveBeenCalledOnce();
    });

    it('should show an alert when loading the course fails', async () => {
        findOneForDashboardStub.mockReturnValue(throwError(() => new HttpResponse({ status: 404 })));
        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'addAlert');

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

    it('should return false for canRegisterForCourse if the server returns 403', fakeAsync(() => {
        findOneForRegistrationStub.mockReturnValue(throwError(() => new HttpResponse({ status: 403 })));

        // test that canRegisterForCourse subscribe gives false
        component.canRegisterForCourse().subscribe((canRegister) => {
            expect(canRegister).toBeFalse();
        });

        // wait for the observable to complete
        tick();
    }));

    it('should throw for unexpected registration responses from the server', fakeAsync(() => {
        findOneForRegistrationStub.mockReturnValue(throwError(() => new HttpResponse({ status: 404 })));

        component.canRegisterForCourse().subscribe({
            next: () => {
                throw new Error('should not be called');
            },
            error: (error) => {
                expect(error).toEqual(new HttpResponse({ status: 404 }));
            },
        });

        tick();
    }));

    it('should load the course, even when just calling loadCourse by itself (for refreshing)', () => {
        // check that loadCourse already subscribes to the course itself

        // create observable httpResponse with course1, where we detect whether it was called
        const findOneForDashboardResponse = new Observable((subscriber) => {
            subscriber.next(course1);
            subscriber.complete();
        });
        const subscribeStub = jest.spyOn(findOneForDashboardResponse, 'subscribe');
        findOneForDashboardStub.mockReturnValue(findOneForDashboardResponse);

        // check that calendar events are refreshed
        const calendarService = TestBed.inject(CalendarService);
        const refreshSpy = jest.spyOn(calendarService, 'reloadEvents');

        component.loadCourse(true);

        expect(subscribeStub).toHaveBeenCalledOnce();
        expect(refreshSpy).toHaveBeenCalledOnce();
    });

    it('should have visible exams', () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        getCourseStub.mockReturnValue(course1);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        component.ngOnInit();

        const bool = component.hasVisibleExams();

        expect(bool).toBeTrue();
    });

    it('should not have visible exams', () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        const bool = component.hasVisibleExams();

        expect(bool).toBeFalse();
    });

    it('should contain unenrollment as course action when allowed', () => {
        component.course.set({ unenrollmentEnabled: true, unenrollmentEndDate: dayjs().add(1, 'days') });
        const courseActionItems = component.getCourseActionItems();
        expect(courseActionItems.length).toBeGreaterThan(0);
        expect(courseActionItems[0].title).toContain('Unenroll');
    });

    it('should open modal on triggering unenrollment option', () => {
        const mockModalRef = { componentInstance: {} } as NgbModalRef;
        const modalServiceSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef);
        component.courseActionItemClick(component.getUnenrollItem());
        expect(modalServiceSpy).toHaveBeenCalledOnce();
    });

    it('should have competencies and tutorial groups', () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');

        const tutorialGroupsResponse: HttpResponse<TutorialGroup[]> = new HttpResponse({
            body: [new TutorialGroup()],
            status: 200,
        });
        const configurationResponse: HttpResponse<TutorialGroupsConfiguration> = new HttpResponse({
            body: generateExampleTutorialGroupsConfiguration({}),
            status: 200,
        });

        jest.spyOn(tutorialGroupsService, 'getAllForCourse').mockReturnValue(of(tutorialGroupsResponse));
        jest.spyOn(tutorialGroupsConfigurationService, 'getOneOfCourse').mockReturnValue(of(configurationResponse));

        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        expect(component.hasCompetencies()).toBeTrue();
        expect(component.hasTutorialGroups()).toBeTrue();
        expect(component.course()?.competencies).not.toBeEmpty();
        expect(component.course()?.prerequisites).not.toBeEmpty();
        expect(component.course()?.tutorialGroups).not.toBeEmpty();
    });

    it('should subscribeToTeamAssignmentUpdates', () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        const teamAssignmentUpdatesStub = jest.spyOn(teamService, 'teamAssignmentUpdates', 'get');
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
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
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
        const quizUnsubscribeSpy = jest.spyOn(component.quizExercisesSubscription!, 'unsubscribe');

        component.ngOnDestroy();

        expect(quizUnsubscribeSpy).toHaveBeenCalledOnce();
    });

    it('should render controls if child has configuration', () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        const stubSubComponent = TestBed.createComponent(ControlsTestingComponent);
        component.onSubRouteActivate(stubSubComponent.componentInstance);
        fixture.detectChanges();
        stubSubComponent.detectChanges();

        const expectedButton = fixture.debugElement.query(By.css('#test-button'));
        expect(expectedButton).not.toBeNull();
        expect(expectedButton.nativeElement.innerHTML).toBe('TestButton');
    });

    it('should toggle sidebar based on isNavbarCollapsed', () => {
        component.isNavbarCollapsed.set(true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.container-closed')).not.toBeNull();

        component.isNavbarCollapsed.set(false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.container-closed')).toBeNull();
    });

    it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBeTrue();

        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBeFalse();
    });

    it('should apply exam-wrapper and exam-is-active if exam is started', () => {
        component.isExamStarted.set(true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.exam-wrapper')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('.exam-is-active')).not.toBeNull();

        component.isExamStarted.set(false);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.exam-wrapper')).toBeNull();
        expect(fixture.nativeElement.querySelector('.exam-is-active')).toBeNull();
    });

    it('should examStarted value to true when exam is started', async () => {
        (examParticipationService as any).examIsStarted$ = of(true);
        await component.ngOnInit();
        expect(component.isExamStarted()).toBeTrue();
    });

    it('should initialize courses attribute when page is loaded', async () => {
        await component.ngOnInit();

        expect(component.courses()).toEqual(courses);
        expect(component.courses()?.length).toBe(1);
    });

    it('should not initialize courses attribute when page has error while loading', async () => {
        findAllForDropdownSpy.mockReturnValue(throwError(() => new HttpResponse({ status: 404 })));

        await component.ngOnInit();
        expect(component.courses()?.length).toBeUndefined();
    });

    it('should not display current course in dropdown', async () => {
        await component.ngOnInit();

        expect(component.courses()).toEqual(courses);
        expect(component.courses()?.pop()).toBe(course2);
    });

    it('should unsubscribe from dashboardSubscription on ngOnDestroy', () => {
        component.updateRecentlyAccessedCourses();
        fixture.detectChanges();
        component.ngOnDestroy();

        expect(courseService.findAllForDropdown).toHaveBeenCalled();
        expect(component.dashboardSubscription.closed).toBeTrue();
    });

    it('should toggle isCollapsed when service emits corresponding event', () => {
        fixture.detectChanges();
        courseSidebarService.openSidebar();
        expect(component.isSidebarCollapsed()).toBeTrue();

        courseSidebarService.closeSidebar();
        expect(component.isSidebarCollapsed()).toBeFalse();

        courseSidebarService.toggleSidebar();
        expect(component.isSidebarCollapsed()).toBeTrue();
    });

    it('should switch course and navigate to the correct URL', async () => {
        const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl').mockReturnValue(Promise.resolve(true));
        const navigateSpy = jest.spyOn(router, 'navigate');
        jest.spyOn(router, 'url', 'get').mockReturnValue('/courses/1/dashboard');

        component.switchCourse(course2);
        await Promise.resolve();

        expect(navigateByUrlSpy).toHaveBeenCalledWith('/', { skipLocationChange: true });
        expect(navigateSpy).toHaveBeenCalledWith(['courses', course2.id, 'dashboard']);
    });
    describe('determineManageViewLink', () => {
        beforeEach(() => {
            component.courseId.set(123);
            component.course.set({ isAtLeastTutor: true });
        });

        it('should set exams link when URL includes "exams"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/exams/1/edit');
            component.course.set({ isAtLeastTutor: true });
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'exams']);
        });

        it('should set exercises link when URL includes "exercises"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/exercises/new');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'exercises']);
        });

        it('should set lectures link when URL includes "lectures"', () => {
            component.course.set({ isAtLeastEditor: true });
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/lectures/1/details');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'lectures']);
        });

        it('should set communication link when URL includes "communication"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/communication?conversationId=123');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'communication']);
        });

        it('should set learning-paths-management link when URL includes "learning-path + instructor"', () => {
            component.course.set({ isAtLeastInstructor: true });
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/learning-path');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'learning-paths-management']);
        });

        it('should set competency-management link when URL includes "competencies + instructor"', () => {
            component.course.set({ isAtLeastInstructor: true });
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/competencies');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'competency-management']);
        });

        it('should set faqs link when URL includes "faq"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/faq');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'faqs']);
        });

        it('should set tutorial-groups-checklist link when URL includes "tutorial-groups + instructor"', () => {
            component.course.set({ isAtLeastInstructor: true });
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/tutorial-groups');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'tutorial-groups-checklist']);
        });
        it('should set tutorial-groups-checklist link when URL includes "tutorial-groups + tutorial groups config' + ' exists + not instructor"', () => {
            component.course.set({ isAtLeastTutor: true, tutorialGroupsConfiguration: {} });
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/tutorial-groups');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'tutorial-groups-checklist']);
        });

        it('should default to course management base link when URL does not match any condition', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/courses/123/settings');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123']);
        });

        it('should set course statistics link when URL includes course statistics', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/statistics');
            component.determineManageViewLink();
            expect(component.manageViewLink()).toEqual(['/course-management', '123', 'course-statistics']);
        });
    });

    it('should initialize course notification values when both settingInfo and info are available', fakeAsync(() => {
        component.courseId.set(mockCourseId);
        component.ngOnInit();
        tick();

        const selectableSettingPresets = (component as any).selectableSettingPresets;
        const selectedSettingPreset = (component as any).selectedSettingPreset;

        expect(selectableSettingPresets).toBeDefined();
        expect(selectableSettingPresets).toEqual(mockNotificationSettingPresets);
        expect(selectedSettingPreset).toBeDefined();
        expect(selectedSettingPreset).toEqual(mockNotificationSettingPresets[0]);
    }));

    it('should select a new notification preset when presetSelected is called', fakeAsync(() => {
        const setSettingPresetSpy = jest.spyOn(courseNotificationSettingService, 'setSettingPreset');

        component.ngOnInit();
        tick();

        component.presetSelected(2);

        const selectedSettingPreset = (component as any).selectedSettingPreset;

        expect(selectedSettingPreset).toBeDefined();
        expect(selectedSettingPreset).toEqual(mockNotificationSettingPresets[1]);
        expect(setSettingPresetSpy).toHaveBeenCalledWith(1, 2, mockNotificationSettingPresets[0]);
    }));

    it('should set selectedSettingPreset to undefined when custom settings are selected', fakeAsync(() => {
        component.ngOnInit();
        tick();

        component.presetSelected(0);

        const selectedSettingPreset = (component as any).selectedSettingPreset;

        expect(selectedSettingPreset).toBeUndefined();
        expect(courseNotificationSettingService.setSettingPreset).toHaveBeenCalledWith(1, 0, mockNotificationSettingPresets[0]);
    }));

    it('should update notification settings when both services return data', fakeAsync(() => {
        component.courseId.set(mockCourseId);
        const getSettingInfoSpy = jest.spyOn(courseNotificationSettingService, 'getSettingInfo').mockImplementation(() => {
            return of(undefined);
        });

        component.ngOnInit();
        tick();

        expect((component as any).selectableSettingPresets).toBeUndefined();

        getSettingInfoSpy.mockReturnValue(of(mockSettingInfo));

        (component as any).settingInfo = mockSettingInfo;
        (component as any).initializeCourseNotificationValues();

        expect((component as any).selectableSettingPresets).toBeDefined();
        expect((component as any).selectedSettingPreset).toBeDefined();
    }));
});
