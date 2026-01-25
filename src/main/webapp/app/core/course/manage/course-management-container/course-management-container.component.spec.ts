import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, NavigationEnd, Params, Router } from '@angular/router';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MatSidenavModule } from '@angular/material/sidenav';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';

import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';

import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { EventManager } from 'app/shared/service/event-manager.service';

import { AlertService } from 'app/shared/service/alert.service';

import { MockModule, MockProvider } from 'ng-mocks';
import { AfterViewInit, Component, EventEmitter, TemplateRef, viewChild } from '@angular/core';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { CourseManagementContainerComponent } from 'app/core/course/manage/course-management-container/course-management-container.component';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_LECTURE, MODULE_FEATURE_LTI, PROFILE_IRIS, PROFILE_PROD } from 'app/app.constants';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseAdminService } from 'app/core/course/manage/services/course-admin.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { CourseAccessStorageService } from 'app/core/course/shared/services/course-access-storage.service';
import { CourseSidebarService } from 'app/core/course/overview/services/course-sidebar.service';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { CourseOperationStatus, CourseOperationType } from 'app/core/course/shared/entities/course-operation-progress.model';

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
    isAtLeastInstructor: true,
    isAtLeastEditor: true,
    isAtLeastTutor: true,
    description:
        'Nihilne te nocturnum praesidium Palati, nihil urbis vigiliae. Salutantibus vitae elit libero, a pharetra augue. Quam diu etiam furor iste tuus nos eludet? ' +
        'Fabio vel iudice vincam, sunt in culpa qui officia. Quam temere in vitiis, legem sancimus haerentia. Quisque ut dolor gravida, placerat libero vel, euismod.',
    courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    courseIconPath: 'api/core/files/path/to/icon.png',
    onlineCourse: true,
    faqEnabled: true,
};

const course2: Course = {
    id: 2,
    title: 'Course2',
    exercises: [exercise2],
    isAtLeastInstructor: true,
    exams: [exam2],
    description: 'Short description of course 2',
    shortName: 'shortName2',
    isAtLeastEditor: true,
    tutorialGroupsConfiguration: {},
};

const coursesDropdown: Course[] = [course1, course2];
@Component({
    template: '<ng-template #controls><button id="test-button">TestButton</button></ng-template>',
})
class ControlsTestingComponent implements BarControlConfigurationProvider, AfterViewInit {
    controlsRendered = new EventEmitter<void>();

    private controls = viewChild<TemplateRef<any>>('controls');
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
    };

    ngAfterViewInit(): void {
        this.controlConfiguration.subject!.next(this.controls()!);
    }
}

describe('CourseManagementContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseManagementContainerComponent;
    let fixture: ComponentFixture<CourseManagementContainerComponent>;
    let courseService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    let courseAdminService: CourseAdminService;
    let courseAccessStorageService: CourseAccessStorageService;
    let eventManager: EventManager;
    let featureToggleService: FeatureToggleService;
    let metisConversationService: MetisConversationService;
    let profileService: ProfileService;
    let localStorageService: LocalStorageService;
    let router: Router;
    let route: ActivatedRoute;

    let findSpy: ReturnType<typeof vi.spyOn>;
    let findOneForDashboardSpy: ReturnType<typeof vi.spyOn>;
    let getCourseSummarySpy: ReturnType<typeof vi.spyOn>;
    let deleteSpy: ReturnType<typeof vi.spyOn>;
    let courseSidebarService: CourseSidebarService;
    let websocketService: WebsocketService;

    const course = {
        id: 1,
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    } as Course;

    beforeEach(async () => {
        route = {
            firstChild: {
                params: of({ courseId: course1.id }) as Params,
            },
            snapshot: { firstChild: { routeConfig: { path: `course-management/${course1.id}/exercises` }, data: {} } },
        } as unknown as ActivatedRoute;

        await TestBed.configureTestingModule({
            imports: [CourseManagementContainerComponent, MockModule(MatSidenavModule), MockModule(NgbTooltipModule)],
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(CourseStorageService),
                MockProvider(CourseAccessStorageService),
                MockProvider(CourseAdminService),
                MockProvider(EventManager),
                MockProvider(FeatureToggleService),
                MockProvider(WebsocketService),
                MockProvider(ArtemisServerDateService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: HasAnyAuthorityDirective, useClass: MockHasAnyAuthorityDirective },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseManagementContainerComponent);
        component = fixture.componentInstance;

        component.isShownViaLti.set(false);
        courseService = TestBed.inject(CourseManagementService);
        courseStorageService = TestBed.inject(CourseStorageService);
        courseAdminService = TestBed.inject(CourseAdminService);
        courseAccessStorageService = TestBed.inject(CourseAccessStorageService);
        eventManager = TestBed.inject(EventManager);
        featureToggleService = TestBed.inject(FeatureToggleService);
        profileService = TestBed.inject(ProfileService);
        localStorageService = TestBed.inject(LocalStorageService);
        courseSidebarService = TestBed.inject(CourseSidebarService);
        router = TestBed.inject(Router);
        websocketService = TestBed.inject(WebsocketService);

        // Mock WebsocketService.subscribe to return an empty observable
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(EMPTY);

        findSpy = vi.spyOn(courseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: course1,
                    headers: new HttpHeaders(),
                }),
            ),
        );
        metisConversationService = fixture.debugElement.injector.get(MetisConversationService);

        findOneForDashboardSpy = vi.spyOn(courseService, 'findOneForDashboard').mockReturnValue(
            of(
                new HttpResponse({
                    body: course1,
                    headers: new HttpHeaders(),
                }),
            ),
        );

        vi.spyOn(courseService, 'findAllForDropdown').mockReturnValue(
            of(
                new HttpResponse({
                    body: coursesDropdown,
                    headers: new HttpHeaders(),
                }),
            ),
        );

        getCourseSummarySpy = vi.spyOn(courseAdminService, 'getCourseSummary').mockReturnValue(
            of(
                new HttpResponse({
                    body: {
                        numberOfStudents: 100,
                        numberOfTutors: 10,
                        numberOfEditors: 5,
                        numberOfInstructors: 2,
                        numberOfParticipations: 500,
                        numberOfSubmissions: 1000,
                        numberOfResults: 800,
                        numberOfConversations: 5,
                        numberOfPosts: 20,
                        numberOfAnswerPosts: 15,
                        numberOfCompetencies: 8,
                        numberOfCompetencyProgress: 50,
                        numberOfLearnerProfiles: 100,
                        numberOfIrisChatSessions: 10,
                        numberOfLLMTraces: 25,
                        numberOfBuilds: 10,
                        numberOfExams: 2,
                        numberOfExercises: 11,
                        numberOfProgrammingExercises: 5,
                        numberOfTextExercises: 2,
                        numberOfModelingExercises: 0,
                        numberOfQuizExercises: 3,
                        numberOfFileUploadExercises: 1,
                        numberOfLectures: 3,
                        numberOfFaqs: 5,
                        numberOfTutorialGroups: 2,
                    },
                    headers: new HttpHeaders(),
                }),
            ),
        );

        deleteSpy = vi.spyOn(courseAdminService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({
            activeModuleFeatures: [MODULE_FEATURE_ATLAS, MODULE_FEATURE_LECTURE, MODULE_FEATURE_LTI],
            activeProfiles: [PROFILE_IRIS, PROFILE_PROD],
        } as unknown as ProfileInfo);

        vi.spyOn(metisConversationService, 'course', 'get').mockReturnValue(course);
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(course1);
        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
    });
    afterEach(() => {
        component.ngOnDestroy();
        vi.restoreAllMocks();
        localStorageService.clear();
        TestBed.inject(SessionStorageService).clear();
    });

    it('should call necessary methods on init', async () => {
        const getCourseStub = vi.spyOn(courseStorageService, 'getCourse');
        const notifyAboutCourseAccessStub = vi.spyOn(courseAccessStorageService, 'onCourseAccessed');
        const getSidebarItems = vi.spyOn(component, 'getSidebarItems');
        const subscribeToCourseUpdates = vi.spyOn(component as any, 'subscribeToCourseUpdates');

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalledWith(1);
        expect(getSidebarItems).toHaveBeenCalledOnce();
        expect(subscribeToCourseUpdates).toHaveBeenCalledWith(1);
        expect(notifyAboutCourseAccessStub).toHaveBeenCalledWith(
            1,
            CourseAccessStorageService.STORAGE_KEY,
            CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW,
        );
        expect(notifyAboutCourseAccessStub).toHaveBeenCalledWith(
            1,
            CourseAccessStorageService.STORAGE_KEY_DROPDOWN,
            CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_DROPDOWN,
        );
    });

    it('should subscribe to profileService and set values correctly', async () => {
        await component.ngOnInit();

        expect(component.isProduction).toBe(true);
        expect(component.isTestServer).toBe(false);
        expect(component.atlasEnabled).toBe(true);
        expect(component.irisEnabled).toBe(true);
        expect(component.ltiEnabled).toBe(true);
        expect(component.localCIActive).toBe(false);
    });

    it('should handle courseId change correctly', () => {
        const subscribeToCourseUpdates = vi.spyOn(component as any, 'subscribeToCourseUpdates');

        component.handleCourseIdChange(2);

        expect(component.courseId()).toBe(2);
        expect(subscribeToCourseUpdates).toHaveBeenCalledWith(2);
    });

    it('should load course successfully', () => {
        component.courseId.set(1);
        component.loadCourse().subscribe(() => {
            expect(component.course()).toEqual(course1);
        });

        expect(findOneForDashboardSpy).toHaveBeenCalledWith(1);
    });

    it('should create sidebar items based on course properties', async () => {
        component.course.set({
            ...course1,
            isAtLeastEditor: true,
            isAtLeastInstructor: true,
            tutorialGroupsConfiguration: {},
            faqEnabled: true,
            onlineCourse: true,
        });
        fixture.detectChanges();

        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
        await component.ngOnInit();
        const sidebarItems = component.sidebarItems();

        expect(sidebarItems.find((item) => item.title === 'Overview')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Exams')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Exercises')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Statistics')).toBeTruthy();

        expect(sidebarItems.find((item) => item.title === 'Lectures')).toBeTruthy();

        expect(sidebarItems.find((item) => item.title === 'IRIS Settings')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Competency Management')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Learning Path')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Scores')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'LTI Configuration')).toBeTruthy();

        expect(sidebarItems.find((item) => item.title === 'Communication')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Tutorials')).toBeTruthy();

        expect(sidebarItems.find((item) => item.title === 'FAQs')).toBeTruthy();
    });
    it('should not include sidebar items for disabled features for non-instructors', async () => {
        const courseWithDisabledFeatures = {
            ...course1,
            isAtLeastEditor: true,
            isAtLeastInstructor: false,
            faqEnabled: false,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED,
        };
        component.course.set(courseWithDisabledFeatures);
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems.find((item) => item.title === 'Communication')).toBeUndefined();
        expect(sidebarItems.find((item) => item.title === 'FAQs')).toBeUndefined();
    });

    it('should subscribe to course updates when handleCourseIdChange is called', () => {
        component.handleCourseIdChange(2);

        expect(findSpy).toHaveBeenCalledWith(2);
    });

    it('should toggle sidebar for CourseConversationsComponent', () => {
        const mockConversationsComponent = {
            isCollapsed: false,
            toggleSidebar: vi.fn(),
        } as unknown as CourseConversationsComponent;
        // we have to set this to trick the component into believing it is a CourseConversationsComponent
        Object.setPrototypeOf(mockConversationsComponent, CourseConversationsComponent.prototype);

        component.activatedComponentReference.set(mockConversationsComponent);

        component.handleToggleSidebar();
        expect(mockConversationsComponent.toggleSidebar).toHaveBeenCalled();
        expect(component.isSidebarCollapsed()).toBe(false);
    });

    it('should not toggle sidebar for non-CourseConversationsComponent', () => {
        component.activatedComponentReference.set(undefined);
        component.handleToggleSidebar();

        // No error should occur, and isCollapsed remains unchanged
        expect(component.isSidebarCollapsed()).toBe(false);
    });

    it('should fetch course deletion summary correctly', () => {
        component.course.set({
            ...course1,
            testCourse: true,
        });

        let receivedCategories: any[] | undefined;
        component.fetchCourseDeletionSummary().subscribe((categories) => {
            receivedCategories = categories;
        });

        expect(getCourseSummarySpy).toHaveBeenCalledWith(1);
        expect(receivedCategories).toBeDefined();
        expect(receivedCategories!.length).toBeGreaterThan(0);

        // Check users category
        const usersCategory = receivedCategories!.find((c) => c.titleKey === 'artemisApp.course.delete.summary.category.users');
        expect(usersCategory).toBeDefined();
        expect(usersCategory?.items.find((i: any) => i.labelKey === 'artemisApp.course.delete.summary.numberOfStudents')?.value).toBe(100);
        expect(usersCategory?.items.find((i: any) => i.labelKey === 'artemisApp.course.delete.summary.numberOfTutors')?.value).toBe(10);

        // Check exercises category
        const exercisesCategory = receivedCategories!.find((c) => c.titleKey === 'artemisApp.course.delete.summary.category.exercises');
        expect(exercisesCategory).toBeDefined();
        expect(exercisesCategory?.items.find((i: any) => i.labelKey === 'artemisApp.course.delete.summary.numberOfProgrammingExercises')?.value).toBe(5);

        // Check communication category
        const communicationCategory = receivedCategories!.find((c) => c.titleKey === 'artemisApp.course.delete.summary.category.communication');
        expect(communicationCategory).toBeDefined();
        expect(communicationCategory?.items.find((i: any) => i.labelKey === 'artemisApp.course.delete.summary.numberOfPosts')?.value).toBe(20);
    });

    it('should return empty array if course id is undefined when fetching deletion summary', () => {
        component.course.set({});

        component.fetchCourseDeletionSummary().subscribe((categories) => {
            expect(categories).toEqual([]);
        });
    });

    it('should return empty array if deletion summary is null', () => {
        component.course.set(course1);
        getCourseSummarySpy.mockReturnValue(of(new HttpResponse({ body: null })));

        component.fetchCourseDeletionSummary().subscribe((categories) => {
            expect(categories).toEqual([]);
        });
    });

    it('should delete course and broadcast event', () => {
        const eventManagerSpy = vi.spyOn(eventManager, 'broadcast');
        const dialogErrorSourceSpy = vi.spyOn(component.dialogErrorSource, 'next');

        component.deleteCourse(1);

        expect(deleteSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(eventManagerSpy).toHaveBeenCalledExactlyOnceWith({
            name: 'courseListModification',
            content: 'artemisApp.course.deleted',
        });
        expect(dialogErrorSourceSpy).toHaveBeenCalledExactlyOnceWith('');
        // Navigation happens when progress overlay is closed, not immediately after delete
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should handle error when deleting course', () => {
        const error = { message: 'Error deleting course' };
        deleteSpy.mockReturnValue(throwError(() => error));
        const dialogErrorSourceSpy = vi.spyOn(component.dialogErrorSource, 'next');

        component.deleteCourse(1);

        expect(dialogErrorSourceSpy).toHaveBeenCalledWith(error.message);
    });

    it('should navigate to course management when closing delete progress', () => {
        component.operationProgress.set({
            operationType: CourseOperationType.DELETE,
            status: CourseOperationStatus.COMPLETED,
            stepsCompleted: 10,
            totalSteps: 10,
            itemsProcessed: 0,
            totalItems: 0,
            failed: 0,
            weightedProgressPercent: 100,
        });

        component.closeProgress();

        expect(component.operationProgress()).toBeUndefined();
        expect(router.navigate).toHaveBeenCalledExactlyOnceWith(['/course-management']);
    });

    it('should not navigate when closing non-delete progress', () => {
        component.operationProgress.set({
            operationType: CourseOperationType.RESET,
            status: CourseOperationStatus.COMPLETED,
            stepsCompleted: 10,
            totalSteps: 10,
            itemsProcessed: 0,
            totalItems: 0,
            failed: 0,
            weightedProgressPercent: 100,
        });

        component.closeProgress();

        expect(component.operationProgress()).toBeUndefined();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should render controls if child has configuration', () => {
        const stubSubComponent = TestBed.createComponent(ControlsTestingComponent);
        fixture.detectChanges();
        component.courseBody()!.nativeElement = { scrollTop: 123 } as HTMLElement;
        component.onSubRouteActivate(stubSubComponent.componentInstance);
        stubSubComponent.detectChanges();
        expect(component.courseBody()?.nativeElement.scrollTop).toBe(0);

        const expectedButton = fixture.debugElement.query(By.css('#test-button'));
        expect(expectedButton).toBeNull();
    });

    it('should set hasSidebar when onSubRouteActivate is called', () => {
        vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/communication');

        component.onSubRouteActivate({});

        expect(component.communicationRouteLoaded()).toBe(true);
        expect(component.hasSidebar()).toBe(true);
    });

    it('should set up conversation service if course has communication enabled', () => {
        const setUpConversationServiceSpy = vi.spyOn(metisConversationService, 'setUpConversationService').mockImplementation(() => {
            return new Observable((subscriber) => subscriber.complete());
        });

        component.course.set({
            ...course1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        });
        vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/communication');
        component.onSubRouteActivate({});
        fixture.detectChanges();

        expect(component.communicationRouteLoaded()).toBe(true);
        expect(setUpConversationServiceSpy).toHaveBeenCalled();
    });

    it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
        component.isNavbarCollapsed.set(false);
        expect(component.isNavbarCollapsed()).toBe(false);

        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBe(true);

        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBe(false);
    });

    it('should get collapse state from localStorage on init', async () => {
        localStorageService.store<boolean>('navbar.collapseState', true);

        await component.ngOnInit();

        expect(component.isNavbarCollapsed()).toBe(true);

        localStorageService.store<boolean>('navbar.collapseState', false);

        component.getCollapseStateFromStorage();

        expect(component.isNavbarCollapsed()).toBe(false);
    });

    it('should set isNavbarCollapsed to false by default if not in localStorageService', async () => {
        localStorageService.remove('navbar.collapseState');

        await component.ngOnInit();

        expect(component.isNavbarCollapsed()).toBe(false);
    });

    it('should save collapse state to localStorage when toggleCollapseState is called', () => {
        component.isNavbarCollapsed.set(false);

        component.toggleCollapseState();

        expect(localStorageService.retrieve<boolean>('navbar.collapseState')).toBe(true);

        component.toggleCollapseState();

        expect(localStorageService.retrieve<boolean>('navbar.collapseState')).toBe(false);
    });

    it('should correctly determine if course is active', () => {
        const activeCourse = { ...course1, endDate: dayjs().add(1, 'day') } as Course;
        const inactiveCourse = { ...course1, endDate: dayjs().subtract(1, 'day') } as Course;
        const noEndDateCourse = { ...course1, endDate: null } as unknown as Course;

        expect(component.isCourseActive(activeCourse)).toBe(true);
        expect(component.isCourseActive(inactiveCourse)).toBe(false);
        expect(component.isCourseActive(noEndDateCourse)).toBe(true);
    });

    it('should subscribe to course modifications', async () => {
        const eventSubscription = vi.spyOn(eventManager, 'subscribe');
        await component.ngOnInit();

        expect(eventSubscription).toHaveBeenCalledWith('courseModification', expect.any(Function));
    });

    it('should unsubscribe from all subscriptions on destroy', () => {
        const ngUnsubscribeNextSpy = vi.spyOn(component.ngUnsubscribe, 'next');
        const ngUnsubscribeCompleteSpy = vi.spyOn(component.ngUnsubscribe, 'complete');

        component.ngOnDestroy();

        expect(ngUnsubscribeNextSpy).toHaveBeenCalled();
        expect(ngUnsubscribeCompleteSpy).toHaveBeenCalled();
    });

    it('should toggle sidebar via keyboard shortcut', () => {
        const handleToggleSidebarSpy = vi.spyOn(component, 'handleToggleSidebar');

        const event = new KeyboardEvent('keydown', {
            key: 'b',
            ctrlKey: true,
            shiftKey: true,
        });

        component.onKeyDownControlShiftB(event);

        expect(handleToggleSidebarSpy).toHaveBeenCalled();
    });

    it('should toggle collapse state via keyboard shortcut', () => {
        const toggleCollapseStateSpy = vi.spyOn(component, 'toggleCollapseState');

        const event = new KeyboardEvent('keydown', {
            key: 'm',
            ctrlKey: true,
        });

        component.onKeyDownControlM(event);

        expect(toggleCollapseStateSpy).toHaveBeenCalled();
    });

    it('should get page title from route data', () => {
        route.snapshot.firstChild!.data = { pageTitle: 'Test Page Title' };

        component.getPageTitle();

        expect(component.pageTitle()).toBe('Test Page Title');
    });

    it('should set empty page title when no route data available', () => {
        route.snapshot.firstChild!.data = {};

        component.getPageTitle();

        expect(component.pageTitle()).toBe('');
    });

    it('should initialize courses attribute for dropdown', async () => {
        component.courseId.set(course1.id!);
        await component.updateRecentlyAccessedCourses();

        expect(component.courses()).toEqual([course2]);
    });

    it('should filter out current course from dropdown courses', async () => {
        component.courseId.set(course2.id!);

        await component.updateRecentlyAccessedCourses();

        expect(component.courses()).not.toContain(course2);
    });

    it('should switch course and navigate to the correct URL', async () => {
        const navigateSpy = vi.spyOn(router, 'navigate');
        vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/communication');

        component.switchCourse(course2);
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', course2.id]);
    });

    it('should handle component activation with controls', () => {
        const getPageTitleSpy = vi.spyOn(component, 'getPageTitle');
        const tryRenderControlsSpy = vi.spyOn(component as any, 'tryRenderControls').mockImplementation(() => {});

        const controlsComponent = {
            controlConfiguration: {
                subject: new Subject<TemplateRef<any>>(),
            },
            controlsRendered: new Subject<void>(),
        };

        component.onSubRouteActivate(controlsComponent);

        expect(getPageTitleSpy).toHaveBeenCalled();
        expect(component.controlConfiguration()).toBe(controlsComponent.controlConfiguration);

        const template = {} as TemplateRef<any>;
        controlsComponent.controlConfiguration.subject.next(template);

        controlsComponent.controlsRendered.next();

        expect(component.controls()).toBe(template);
        expect(tryRenderControlsSpy).toHaveBeenCalled();
    });

    it('should handle component deactivation and clean up controls', () => {
        const removeCurrentControlsViewSpy = vi.spyOn(component as any, 'removeCurrentControlsView');

        component.onSubRouteDeactivate();

        expect(removeCurrentControlsViewSpy).toHaveBeenCalled();
        expect(component.controls()).toBeUndefined();
        expect(component.controlConfiguration()).toBeUndefined();
    });

    it('should check for unread messages if messaging is enabled', () => {
        const checkForUnreadMessagesSpy = vi.spyOn(metisConversationService, 'checkForUnreadMessages');
        const subscribeToHasUnreadMessagesSpy = vi.spyOn(component as any, 'subscribeToHasUnreadMessages');
        const courseWithMessaging = {
            ...course1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        };
        component.course.set(courseWithMessaging);

        component.setupConversationService();
        expect(checkForUnreadMessagesSpy).toHaveBeenCalled();
        expect(checkForUnreadMessagesSpy).toHaveBeenCalledExactlyOnceWith(courseWithMessaging);
        expect(subscribeToHasUnreadMessagesSpy).toHaveBeenCalledExactlyOnceWith();
        expect(component.checkedForUnreadMessages()).toBe(true);
    });

    it('should not check for unread messages if communication is disabled', () => {
        const checkForUnreadMessagesSpy = vi.spyOn(metisConversationService, 'checkForUnreadMessages');

        component.course.set({
            ...course1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED,
        });

        component.setupConversationService();

        expect(checkForUnreadMessagesSpy).not.toHaveBeenCalled();
    });

    it('should toggle sidebar collapse state when courseSidebarService emits events', () => {
        fixture.detectChanges();

        courseSidebarService.openSidebar();
        expect(component.isSidebarCollapsed()).toBe(true);

        courseSidebarService.closeSidebar();
        expect(component.isSidebarCollapsed()).toBe(false);
    });

    it('should set isSettingsPage to false when not on settings page', async () => {
        vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/exercises');
        await component.ngOnInit();
        expect(component.isSettingsPage()).toBe(false);
    });
    it('should set isSettingsPage to true when on settings page', async () => {
        vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/settings');
        vi.spyOn(router, 'events', 'get').mockReturnValue(of(new NavigationEnd(0, '/course-management/1/settings', '')));
        await component.ngOnInit();
        expect(component.isSettingsPage()).toBe(true);
    });
    describe('determineStudentViewLink', () => {
        beforeEach(() => {
            component.courseId.set(123);
        });
        it('should set exams link when URL includes "exams"', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/exams/1/edit');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'exams']);
        });

        it('should set exercises link when URL includes "exercises"', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/exercises/new');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'exercises']);
        });

        it('should set lectures link when URL includes "lectures"', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/lectures/1/details');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'lectures']);
        });

        it('should set communication link when URL includes "communication"', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/communication?conversationId=123');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'communication']);
        });

        it('should set learning-path link when URL includes "learning-path-management"', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/learning-path-management');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'learning-path']);
        });

        it('should set competencies link when URL includes "competency-management"', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/competency-management');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'competencies']);
        });

        it('should set faq link when URL includes "faqs"', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/faqs/new');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'faq']);
        });

        it('should set tutorial-groups link when URL includes "tutorial-groups"', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/tutorial-groups/configuration/new');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'tutorial-groups']);
        });

        it('should set tutorial-groups link when URL includes "tutorial-groups-checklist"', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/tutorial-groups-checklist');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'tutorial-groups']);
        });

        it('should set statistics link when URL includes course-statistics', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/course-statistics');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'statistics']);
        });

        it('should default to dashboard link when URL does not match any condition', () => {
            vi.spyOn(router, 'url', 'get').mockReturnValue('courses/123/iris-settings');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'dashboard']);
        });
    });
});
