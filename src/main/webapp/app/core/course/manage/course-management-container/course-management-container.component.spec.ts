import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, NavigationEnd, Params, Router } from '@angular/router';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Observable, Subject, of, throwError } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatSidenavModule } from '@angular/material/sidenav';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';

import { CourseSidebarComponent } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { CourseTitleBarComponent } from 'app/core/course/shared/course-title-bar/course-title-bar.component';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/buttons/course-exam-archive-button/course-exam-archive-button.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';

import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';

import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { EventManager } from 'app/shared/service/event-manager.service';

import { AlertService } from 'app/shared/service/alert.service';

import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AfterViewInit, Component, EventEmitter, TemplateRef, ViewChild } from '@angular/core';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { CourseManagementContainerComponent } from 'app/core/course/manage/course-management-container/course-management-container.component';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

import { MODULE_FEATURE_ATLAS, PROFILE_IRIS, PROFILE_LTI, PROFILE_PROD } from 'app/app.constants';
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
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
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

    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
    };

    ngAfterViewInit(): void {
        this.controlConfiguration.subject!.next(this.controls);
    }
}

describe('CourseManagementContainerComponent', () => {
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

    let findSpy: jest.SpyInstance;
    let findOneForDashboardSpy: jest.SpyInstance;
    let getDeletionSummarySpy: jest.SpyInstance;
    let deleteSpy: jest.SpyInstance;
    let courseSidebarService: CourseSidebarService;

    const course = {
        id: 1,
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    } as Course;

    beforeEach(fakeAsync(() => {
        route = {
            firstChild: {
                params: of({ courseId: course1.id }) as Params,
            },
            snapshot: { firstChild: { routeConfig: { path: `course-management/${course1.id}/exercises` }, data: {} } },
        } as unknown as ActivatedRoute;

        TestBed.configureTestingModule({
            imports: [MockModule(MatSidenavModule), MockModule(NgbTooltipModule), MockModule(BrowserAnimationsModule)],
            declarations: [
                CourseManagementContainerComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(FeatureToggleHideDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseSidebarComponent),
                MockComponent(CourseTitleBarComponent),
                MockComponent(CourseExamArchiveButtonComponent),
                MockDirective(DeleteButtonDirective),
            ],
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
        })
            .compileComponents()
            .then(() => {
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
                findSpy = jest.spyOn(courseService, 'find').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: course1,
                            headers: new HttpHeaders(),
                        }),
                    ),
                );
                metisConversationService = fixture.debugElement.injector.get(MetisConversationService);

                findOneForDashboardSpy = jest.spyOn(courseService, 'findOneForDashboard').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: course1,
                            headers: new HttpHeaders(),
                        }),
                    ),
                );

                jest.spyOn(courseService, 'findAllForDropdown').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: coursesDropdown,
                            headers: new HttpHeaders(),
                        }),
                    ),
                );

                getDeletionSummarySpy = jest.spyOn(courseAdminService, 'getDeletionSummary').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: {
                                numberOfStudents: 100,
                                numberOfTutors: 10,
                                numberOfEditors: 5,
                                numberOfInstructors: 2,
                                numberExams: 2,
                                numberLectures: 3,
                                numberProgrammingExercises: 5,
                                numberTextExercises: 2,
                                numberFileUploadExercises: 1,
                                numberQuizExercises: 3,
                                numberModelingExercises: 0,
                                numberOfBuilds: 10,
                                numberOfCommunicationPosts: 20,
                                numberOfAnswerPosts: 15,
                            },
                            headers: new HttpHeaders(),
                        }),
                    ),
                );

                deleteSpy = jest.spyOn(courseAdminService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue({
                    activeModuleFeatures: [MODULE_FEATURE_ATLAS],
                    activeProfiles: [PROFILE_IRIS, PROFILE_LTI, PROFILE_PROD],
                } as unknown as ProfileInfo);

                jest.spyOn(metisConversationService, 'course', 'get').mockReturnValue(course);
                jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(course1);
                jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            });
    }));
    afterEach(() => {
        component.ngOnDestroy();
        jest.restoreAllMocks();
        localStorageService.clear();
        TestBed.inject(SessionStorageService).clear();
    });

    it('should call necessary methods on init', async () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        const notifyAboutCourseAccessStub = jest.spyOn(courseAccessStorageService, 'onCourseAccessed');
        const getSidebarItems = jest.spyOn(component, 'getSidebarItems');
        const subscribeToCourseUpdates = jest.spyOn(component as any, 'subscribeToCourseUpdates');

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

        expect(component.isProduction).toBeTrue();
        expect(component.isTestServer).toBeFalse();
        expect(component.atlasEnabled).toBeTrue();
        expect(component.irisEnabled).toBeTrue();
        expect(component.ltiEnabled).toBeTrue();
        expect(component.localCIActive).toBeFalse();
    });

    it('should handle courseId change correctly', () => {
        const subscribeToCourseUpdates = jest.spyOn(component as any, 'subscribeToCourseUpdates');

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

        jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
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
            toggleSidebar: jest.fn(),
        } as unknown as CourseConversationsComponent;
        // we have to set this to trick the component into believing it is a CourseConversationsComponent
        Object.setPrototypeOf(mockConversationsComponent, CourseConversationsComponent.prototype);

        component.activatedComponentReference.set(mockConversationsComponent);

        component.handleToggleSidebar();
        expect(mockConversationsComponent.toggleSidebar).toHaveBeenCalled();
        expect(component.isSidebarCollapsed()).toBeFalse();
    });

    it('should not toggle sidebar for non-CourseConversationsComponent', () => {
        component.activatedComponentReference.set(undefined);
        component.handleToggleSidebar();

        // No error should occur, and isCollapsed remains unchanged
        expect(component.isSidebarCollapsed()).toBeFalse();
    });

    it('should fetch course deletion summary correctly', () => {
        component.course.set({
            ...course1,
            testCourse: true,
        });

        component.fetchCourseDeletionSummary().subscribe((summary: EntitySummary) => {
            expect(summary['artemisApp.course.delete.summary.isTestCourse']).toBeTrue();
            expect(summary['artemisApp.course.delete.summary.numberStudents']).toBe(100);
            expect(summary['artemisApp.course.delete.summary.numberTutors']).toBe(10);
            expect(summary['artemisApp.course.delete.summary.numberEditors']).toBe(5);
            expect(summary['artemisApp.course.delete.summary.numberInstructors']).toBe(2);
            expect(summary['artemisApp.course.delete.summary.numberExams']).toBe(2);
            expect(summary['artemisApp.course.delete.summary.numberLectures']).toBe(3);
            expect(summary['artemisApp.course.delete.summary.numberProgrammingExercises']).toBe(5);
            expect(summary['artemisApp.course.delete.summary.numberTextExercises']).toBe(2);
            expect(summary['artemisApp.course.delete.summary.numberFileUploadExercises']).toBe(1);
            expect(summary['artemisApp.course.delete.summary.numberQuizExercises']).toBe(3);
            expect(summary['artemisApp.course.delete.summary.numberModelingExercises']).toBe(0);
            expect(summary['artemisApp.course.delete.summary.numberBuilds']).toBe(10);
            expect(summary['artemisApp.course.delete.summary.numberCommunicationPosts']).toBe(20);
            expect(summary['artemisApp.course.delete.summary.numberAnswerPosts']).toBe(15);
        });

        expect(getDeletionSummarySpy).toHaveBeenCalledWith(1);
    });

    it('should return empty object if course id is undefined when fetching deletion summary', () => {
        component.course.set({});

        component.fetchCourseDeletionSummary().subscribe((summary: EntitySummary) => {
            expect(summary).toEqual({});
        });
    });

    it('should return existing entries if deletion summary is null', () => {
        component.course.set(course1);
        getDeletionSummarySpy.mockReturnValue(of(new HttpResponse({ body: null })));

        component.fetchCourseDeletionSummary().subscribe((summary: EntitySummary) => {
            expect(summary).toEqual(expect.any(Object));
            // Should contain only existing entries
            expect(Object.keys(summary).length).toBeGreaterThan(0);
        });
    });

    it('should delete course and broadcast event', () => {
        const eventManagerSpy = jest.spyOn(eventManager, 'broadcast');
        const dialogErrorSourceSpy = jest.spyOn(component.dialogErrorSource, 'next');

        component.deleteCourse(1);

        expect(deleteSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(eventManagerSpy).toHaveBeenCalledExactlyOnceWith({
            name: 'courseListModification',
            content: 'artemisApp.course.deleted',
        });
        expect(dialogErrorSourceSpy).toHaveBeenCalledExactlyOnceWith('');
        expect(router.navigate).toHaveBeenCalledExactlyOnceWith(['/course-management']);
    });

    it('should handle error when deleting course', () => {
        const error = { message: 'Error deleting course' };
        deleteSpy.mockReturnValue(throwError(() => error));
        const dialogErrorSourceSpy = jest.spyOn(component.dialogErrorSource, 'next');

        component.deleteCourse(1);

        expect(dialogErrorSourceSpy).toHaveBeenCalledWith(error.message);
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
        jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/communication');

        component.onSubRouteActivate({});

        expect(component.communicationRouteLoaded()).toBeTrue();
        expect(component.hasSidebar()).toBeTrue();
    });

    it('should set up conversation service if course has communication enabled', () => {
        const setUpConversationServiceSpy = jest.spyOn(metisConversationService, 'setUpConversationService').mockImplementation(() => {
            return new Observable((subscriber) => subscriber.complete());
        });

        component.course.set({
            ...course1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        });
        jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/communication');
        component.onSubRouteActivate({});
        fixture.detectChanges();

        expect(component.communicationRouteLoaded()).toBeTrue();
        expect(setUpConversationServiceSpy).toHaveBeenCalled();
    });

    it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
        component.isNavbarCollapsed.set(false);
        expect(component.isNavbarCollapsed()).toBeFalse();

        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBeTrue();

        component.toggleCollapseState();
        expect(component.isNavbarCollapsed()).toBeFalse();
    });

    it('should get collapse state from localStorage on init', async () => {
        localStorageService.store<boolean>('navbar.collapseState', true);

        await component.ngOnInit();

        expect(component.isNavbarCollapsed()).toBeTrue();

        localStorageService.store<boolean>('navbar.collapseState', false);

        component.getCollapseStateFromStorage();

        expect(component.isNavbarCollapsed()).toBeFalse();
    });

    it('should set isNavbarCollapsed to false by default if not in localStorageService', async () => {
        localStorageService.remove('navbar.collapseState');

        await component.ngOnInit();

        expect(component.isNavbarCollapsed()).toBeFalse();
    });

    it('should save collapse state to localStorage when toggleCollapseState is called', () => {
        component.isNavbarCollapsed.set(false);

        component.toggleCollapseState();

        expect(localStorageService.retrieve<boolean>('navbar.collapseState')).toBeTrue();

        component.toggleCollapseState();

        expect(localStorageService.retrieve<boolean>('navbar.collapseState')).toBeFalse();
    });

    it('should correctly determine if course is active', () => {
        const activeCourse = { ...course1, endDate: dayjs().add(1, 'day') } as Course;
        const inactiveCourse = { ...course1, endDate: dayjs().subtract(1, 'day') } as Course;
        const noEndDateCourse = { ...course1, endDate: null } as unknown as Course;

        expect(component.isCourseActive(activeCourse)).toBeTrue();
        expect(component.isCourseActive(inactiveCourse)).toBeFalse();
        expect(component.isCourseActive(noEndDateCourse)).toBeTrue();
    });

    it('should subscribe to course modifications', async () => {
        const eventSubscription = jest.spyOn(eventManager, 'subscribe');
        await component.ngOnInit();

        expect(eventSubscription).toHaveBeenCalledWith('courseModification', expect.any(Function));
    });

    it('should unsubscribe from all subscriptions on destroy', () => {
        const ngUnsubscribeNextSpy = jest.spyOn(component.ngUnsubscribe, 'next');
        const ngUnsubscribeCompleteSpy = jest.spyOn(component.ngUnsubscribe, 'complete');

        component.ngOnDestroy();

        expect(ngUnsubscribeNextSpy).toHaveBeenCalled();
        expect(ngUnsubscribeCompleteSpy).toHaveBeenCalled();
    });

    it('should toggle sidebar via keyboard shortcut', () => {
        const handleToggleSidebarSpy = jest.spyOn(component, 'handleToggleSidebar');

        const event = new KeyboardEvent('keydown', {
            key: 'b',
            ctrlKey: true,
            shiftKey: true,
        });

        component.onKeyDownControlShiftB(event);

        expect(handleToggleSidebarSpy).toHaveBeenCalled();
    });

    it('should toggle collapse state via keyboard shortcut', () => {
        const toggleCollapseStateSpy = jest.spyOn(component, 'toggleCollapseState');

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
        await component.updateRecentlyAccessedCourses();

        expect(component.courses()).toEqual([course2]);
    });

    it('should filter out current course from dropdown courses', async () => {
        component.courseId.set(course2.id!);

        await component.updateRecentlyAccessedCourses();

        expect(component.courses()).not.toContain(course2);
    });

    it('should switch course and navigate to the correct URL', async () => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/communication');

        component.switchCourse(course2);
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', course2.id]);
    });

    it('should handle component activation with controls', () => {
        const getPageTitleSpy = jest.spyOn(component, 'getPageTitle');
        const tryRenderControlsSpy = jest.spyOn(component as any, 'tryRenderControls').mockImplementation(() => {});

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
        const removeCurrentControlsViewSpy = jest.spyOn(component as any, 'removeCurrentControlsView');

        component.onSubRouteDeactivate();

        expect(removeCurrentControlsViewSpy).toHaveBeenCalled();
        expect(component.controls()).toBeUndefined();
        expect(component.controlConfiguration()).toBeUndefined();
    });

    it('should check for unread messages if messaging is enabled', () => {
        const checkForUnreadMessagesSpy = jest.spyOn(metisConversationService, 'checkForUnreadMessages');
        const subscribeToHasUnreadMessagesSpy = jest.spyOn(component as any, 'subscribeToHasUnreadMessages');
        const courseWithMessaging = {
            ...course1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        };
        component.course.set(courseWithMessaging);

        component.setupConversationService();
        expect(checkForUnreadMessagesSpy).toHaveBeenCalled();
        expect(checkForUnreadMessagesSpy).toHaveBeenCalledExactlyOnceWith(courseWithMessaging);
        expect(subscribeToHasUnreadMessagesSpy).toHaveBeenCalledExactlyOnceWith();
        expect(component.checkedForUnreadMessages()).toBeTrue();
    });

    it('should not check for unread messages if communication is disabled', () => {
        const checkForUnreadMessagesSpy = jest.spyOn(metisConversationService, 'checkForUnreadMessages');

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
        expect(component.isSidebarCollapsed()).toBeTrue();

        courseSidebarService.closeSidebar();
        expect(component.isSidebarCollapsed()).toBeFalse();
    });

    it('should set isSettingsPage to false when not on settings page', async () => {
        jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/exercises');
        await component.ngOnInit();
        expect(component.isSettingsPage()).toBeFalse();
    });
    it('should set isSettingsPage to true when on settings page', async () => {
        jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/1/settings');
        jest.spyOn(router, 'events', 'get').mockReturnValue(of(new NavigationEnd(0, '/course-management/1/settings', '')));
        await component.ngOnInit();
        expect(component.isSettingsPage()).toBeTrue();
    });
    describe('determineStudentViewLink', () => {
        beforeEach(() => {
            component.courseId.set(123);
        });
        it('should set exams link when URL includes "exams"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/exams/1/edit');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'exams']);
        });

        it('should set exercises link when URL includes "exercises"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/exercises/new');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'exercises']);
        });

        it('should set lectures link when URL includes "lectures"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/lectures/1/details');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'lectures']);
        });

        it('should set communication link when URL includes "communication"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/communication?conversationId=123');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'communication']);
        });

        it('should set learning-path link when URL includes "learning-path-management"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/learning-path-management');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'learning-path']);
        });

        it('should set competencies link when URL includes "competency-management"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/competency-management');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'competencies']);
        });

        it('should set faq link when URL includes "faqs"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/faqs/new');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'faq']);
        });

        it('should set tutorial-groups link when URL includes "tutorial-groups"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/tutorial-groups/configuration/new');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'tutorial-groups']);
        });

        it('should set tutorial-groups link when URL includes "tutorial-groups-checklist"', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/tutorial-groups-checklist');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'tutorial-groups']);
        });

        it('should set statistics link when URL includes course-statistics', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/course-management/123/course-statistics');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'statistics']);
        });

        it('should default to dashboard link when URL does not match any condition', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('courses/123/iris-settings');
            component.determineStudentViewLink();
            expect(component.studentViewLink()).toEqual(['/courses', '123', 'dashboard']);
        });
    });
});
