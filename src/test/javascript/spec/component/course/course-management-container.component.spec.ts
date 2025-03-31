import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Params, Router, RouterModule } from '@angular/router';
import { EMPTY, of, Subject, throwError } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatSidenavModule } from '@angular/material/sidenav';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import dayjs from 'dayjs/esm';

import { CourseSidebarComponent } from 'app/core/course/shared/course-sidebar/course-sidebar.component';
import { CourseTitleBarComponent } from 'app/core/course/shared/course-title-bar/course-title-bar.component';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';

import { Course, CourseInformationSharingConfiguration } from 'app/core/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';

import { CourseManagementService } from 'app/core/course/manage/course-management.service';
import { CourseStorageService } from 'app/core/course/manage/course-storage.service';
import { CourseAccessStorageService } from 'app/core/course/shared/course-access-storage.service';
import { CourseSidebarService } from 'app/core/course/overview/course-sidebar.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { CourseAdminService } from 'app/core/course/manage/course-admin.service';
import { MetisConversationService } from 'app/communication/metis-conversation.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { AlertService } from 'app/shared/service/alert.service';

import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockProfileService } from '../../helpers/mocks/service/mock-profile.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockMetisConversationService } from '../../helpers/mocks/service/mock-metis-conversation.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AfterViewInit, Component, EventEmitter, TemplateRef, ViewChild } from '@angular/core';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { CourseManagementContainerComponent } from 'app/core/course/manage/course-management-container/course-management-container.component';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';

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
    let router: MockRouter;
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

        router = new MockRouter();

        TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([]), MockModule(MatSidenavModule), MockModule(NgbTooltipModule), MockModule(BrowserAnimationsModule)],
            declarations: [
                CourseManagementContainerComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(FeatureToggleHideDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseSidebarComponent),
                MockComponent(CourseTitleBarComponent),
                MockComponent(CourseExamArchiveButtonComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
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
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: route },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementContainerComponent);
                component = fixture.componentInstance;

                component.isShownViaLti.set(false);

                // Get services
                courseService = TestBed.inject(CourseManagementService);
                courseStorageService = TestBed.inject(CourseStorageService);
                courseAdminService = TestBed.inject(CourseAdminService);
                courseAccessStorageService = TestBed.inject(CourseAccessStorageService);
                eventManager = TestBed.inject(EventManager);
                featureToggleService = TestBed.inject(FeatureToggleService);
                metisConversationService = TestBed.inject(MetisConversationService);
                profileService = TestBed.inject(ProfileService);
                courseSidebarService = TestBed.inject(CourseSidebarService);

                // Set up spies
                findSpy = jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

                findOneForDashboardSpy = jest.spyOn(courseService, 'findOneForDashboard').mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

                jest.spyOn(courseService, 'findAllForDropdown').mockReturnValue(of(new HttpResponse({ body: coursesDropdown, headers: new HttpHeaders() })));

                getDeletionSummarySpy = jest.spyOn(courseAdminService, 'getDeletionSummary').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: {
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

                deleteSpy = jest.spyOn(courseAdminService, 'delete').mockReturnValue(of());

                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(
                    of({
                        inProduction: true,
                        activeProfiles: ['prod', 'atlas', 'iris', 'lti'],
                        testServer: false,
                    } as ProfileInfo),
                );

                jest.spyOn(metisConversationService, 'course', 'get').mockReturnValue(course);
                jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(course1);
                jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            });
    }));

    afterEach(() => {
        component.ngOnDestroy();
        jest.restoreAllMocks();
        localStorage.clear();
        sessionStorage.clear();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
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

        expect(component.isProduction()).toBeTrue();
        expect(component.isTestServer()).toBeFalse();
        expect(component.atlasEnabled()).toBeTrue();
        expect(component.irisEnabled()).toBeTrue();
        expect(component.ltiEnabled()).toBeTrue();
        expect(component.localCIActive()).toBeFalse();
    });

    it('should handle courseId change correctly', () => {
        const subscribeToCourseUpdates = jest.spyOn(component as any, 'subscribeToCourseUpdates');

        component.handleCourseIdChange(2);

        expect(component.courseId()).toEqual(2);
        expect(subscribeToCourseUpdates).toHaveBeenCalledWith(2);
    });

    it('should load course successfully', () => {
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

        // Set feature toggle active
        jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
        await component.ngOnInit();

        // await component.ngOnInit();
        const sidebarItems = component.sidebarItems();

        // Check for management default items
        expect(sidebarItems.find((item) => item.title === 'Overview')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Exams')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Exercises')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Statistics')).toBeTruthy();

        expect(sidebarItems.find((item) => item.title === 'Lectures')).toBeTruthy();

        // Check for instructor+ items
        expect(sidebarItems.find((item) => item.title === 'IRIS Settings')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Competency Management')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Learning Path')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'Scores')).toBeTruthy();
        expect(sidebarItems.find((item) => item.title === 'LTI Configuration')).toBeTruthy();

        // Check for communication item
        expect(sidebarItems.find((item) => item.title === 'Communication')).toBeTruthy();

        // Check for tutorial groups item
        expect(sidebarItems.find((item) => item.title === 'Tutorials')).toBeTruthy();

        // Check for FAQ item
        expect(sidebarItems.find((item) => item.title === 'FAQs')).toBeTruthy();
    });

    it('should subscribe to course updates when handleCourseIdChange is called', () => {
        component.handleCourseIdChange(2);

        expect(findSpy).toHaveBeenCalledWith(2);
    });

    // it('should toggle sidebar for CourseConversationsComponent', () => {
    //
    //     conversationsComponent.isCollapsed = false;
    //     conversationsComponent.toggleSidebar = jest.fn();
    //
    //     component.activatedComponentReference.set(conversationsComponent);
    //     component.handleToggleSidebar();
    //
    //     expect(conversationsComponent.toggleSidebar).toHaveBeenCalled();
    //     expect(component.isSidebarCollapsed()).toBeFalse();
    // });

    it('should not toggle sidebar for non-CourseConversationsComponent', () => {
        component.activatedComponentReference.set(undefined);
        component.handleToggleSidebar();

        // No error should occur, and isCollapsed remains unchanged
        expect(component.isSidebarCollapsed()).toBeFalse();
    });

    it('should get existing summary entries correctly', () => {
        component.course.set({
            ...course1,
            numberOfStudents: 100,
            numberOfTeachingAssistants: 10,
            numberOfEditors: 5,
            numberOfInstructors: 2,
            testCourse: true,
            exercises: [
                { type: ExerciseType.PROGRAMMING } as ProgrammingExercise,
                { type: ExerciseType.PROGRAMMING } as ProgrammingExercise,
                { type: ExerciseType.TEXT } as TextExercise,
                { type: ExerciseType.QUIZ } as QuizExercise,
            ],
        });

        const summary = (component as any).getExistingSummaryEntries();

        expect(summary['artemisApp.course.delete.summary.numberStudents']).toEqual(100);
        expect(summary['artemisApp.course.delete.summary.numberTutors']).toEqual(10);
        expect(summary['artemisApp.course.delete.summary.numberEditors']).toEqual(5);
        expect(summary['artemisApp.course.delete.summary.numberInstructors']).toEqual(2);
        expect(summary['artemisApp.course.delete.summary.isTestCourse']).toEqual(true);
    });

    it('should fetch course deletion summary correctly', () => {
        component.course.set(course1);

        component.fetchCourseDeletionSummary().subscribe((summary: EntitySummary) => {
            expect(summary['artemisApp.course.delete.summary.numberExams']).toEqual(2);
            expect(summary['artemisApp.course.delete.summary.numberLectures']).toEqual(3);
            expect(summary['artemisApp.course.delete.summary.numberProgrammingExercises']).toEqual(5);
            expect(summary['artemisApp.course.delete.summary.numberTextExercises']).toEqual(2);
            expect(summary['artemisApp.course.delete.summary.numberFileUploadExercises']).toEqual(1);
            expect(summary['artemisApp.course.delete.summary.numberQuizExercises']).toEqual(3);
            expect(summary['artemisApp.course.delete.summary.numberModelingExercises']).toEqual(0);
            expect(summary['artemisApp.course.delete.summary.numberBuilds']).toEqual(10);
            expect(summary['artemisApp.course.delete.summary.numberCommunicationPosts']).toEqual(20);
            expect(summary['artemisApp.course.delete.summary.numberAnswerPosts']).toEqual(15);
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
            expect(summary).toEqual(jasmine.any(Object));
            // Should contain only existing entries
            expect(Object.keys(summary).length).toBeGreaterThan(0);
        });
    });

    it('should delete course and broadcast event', () => {
        const eventManagerSpy = jest.spyOn(eventManager, 'broadcast');
        const dialogErrorSourceSpy = jest.spyOn(component.dialogErrorSource, 'next');

        component.deleteCourse(1);

        expect(deleteSpy).toHaveBeenCalledWith(1);
        expect(eventManagerSpy).toHaveBeenCalledWith({
            name: 'courseListModification',
            content: 'Deleted an course',
        });
        expect(dialogErrorSourceSpy).toHaveBeenCalledWith('');
        expect(router.navigate).toHaveBeenCalledWith(['/course-management']);
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
        component.onSubRouteActivate(stubSubComponent.componentInstance);
        fixture.detectChanges();
        stubSubComponent.detectChanges();

        const expectedButton = fixture.debugElement.query(By.css('#test-button'));
        expect(expectedButton).not.toBeNull();
        expect(expectedButton.nativeElement.innerHTML).toBe('TestButton');
    });

    it('should set hasSidebar when onSubRouteActivate is called', () => {
        // Setup router URL for communication route
        const url = `/course-management/${course1.id}/communication`;
        router.url = url;

        component.onSubRouteActivate({});

        expect(component.communicationRouteLoaded()).toBeTrue();
        expect(component.hasSidebar()).toBeTrue();
    });

    it('should set up conversation service if course has communication enabled', () => {
        const setUpConversationServiceSpy = jest.spyOn(metisConversationService, 'setUpConversationService').mockReturnValue(EMPTY);

        component.course.set({
            ...course1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        });

        // Setup router URL for communication route
        const url = `/course-management/${course1.id}/communication`;
        router.url = url;

        component.onSubRouteActivate({});

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
        localStorage.setItem('navbar.collapseState', 'true');

        await component.ngOnInit();

        expect(component.isNavbarCollapsed()).toBeTrue();

        localStorage.setItem('navbar.collapseState', 'false');

        component.getCollapseStateFromStorage();

        expect(component.isNavbarCollapsed()).toBeFalse();
    });

    it('should set isNavbarCollapsed to false by default if not in localStorage', async () => {
        localStorage.removeItem('navbar.collapseState');

        await component.ngOnInit();

        expect(component.isNavbarCollapsed()).toBeFalse();
    });

    it('should save collapse state to localStorage when toggleCollapseState is called', () => {
        component.isNavbarCollapsed.set(false);

        component.toggleCollapseState();

        expect(localStorage.getItem('navbar.collapseState')).toBe('true');

        component.toggleCollapseState();

        expect(localStorage.getItem('navbar.collapseState')).toBe('false');
    });

    it('should correctly determine if course is active', () => {
        const activeCourse = { ...course1, endDate: new dayjs.Dayjs().add(1, 'day') } as Course;
        const inactiveCourse = { ...course1, endDate: new dayjs.Dayjs().subtract(1, 'day') } as Course;
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

        expect(component.courses()).toEqual([course1, course2]);
    });

    it('should filter out current course from dropdown courses', async () => {
        component.courseId.set(course2.id!);

        await component.updateRecentlyAccessedCourses();

        expect(component.courses()).not.toContain(course2);
    });

    it('should switch course and navigate to the correct URL', () => {
        const navigateByUrlSpy = jest.spyOn(router, 'navigateByUrl');
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.switchCourse(course2);

        expect(navigateByUrlSpy).toHaveBeenCalledWith('/', { skipLocationChange: true });
        // After the navigateByUrl promise resolves, it should navigate to the course
        navigateByUrlSpy.mock.calls[0][2]?.(); // Call the 'then' callback

        expect(navigateSpy).toHaveBeenCalledWith(['course-management', course2.id, 'exercises']);
    });

    it('should handle component activation with controls', () => {
        const getPageTitleSpy = jest.spyOn(component, 'getPageTitle');
        const setUpConversationServiceSpy = jest.spyOn(component as any, 'setUpConversationService');
        const tryRenderControlsSpy = jest.spyOn(component as any, 'tryRenderControls');

        const controlsComponent = {
            controlConfiguration: {
                subject: new Subject<TemplateRef<any>>(),
                controlsRendered: new Subject<void>(),
            },
        };

        component.onSubRouteActivate(controlsComponent);

        expect(getPageTitleSpy).toHaveBeenCalled();
        expect(setUpConversationServiceSpy).toHaveBeenCalled();
        expect(component.controlConfiguration()).toBe(controlsComponent.controlConfiguration);

        // Emit control template
        const template = {} as TemplateRef<any>;
        controlsComponent.controlConfiguration.subject.next(template);

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

        component.course.set({
            ...course1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
        });

        component.setUpConversationService();

        expect(checkForUnreadMessagesSpy).toHaveBeenCalled();
        expect(subscribeToHasUnreadMessagesSpy).toHaveBeenCalled();
        expect(component.checkedForUnreadMessages()).toBeTrue();
    });

    it('should not check for unread messages if communication is disabled', () => {
        const checkForUnreadMessagesSpy = jest.spyOn(metisConversationService, 'checkForUnreadMessages');

        component.course.set({
            ...course1,
            courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED,
        });

        component.setUpConversationService();

        expect(checkForUnreadMessagesSpy).not.toHaveBeenCalled();
    });

    it('should toggle sidebar collapse state when courseSidebarService emits events', () => {
        fixture.detectChanges();

        courseSidebarService.openSidebar();
        expect(component.isSidebarCollapsed()).toBeTrue();

        courseSidebarService.closeSidebar();
        expect(component.isSidebarCollapsed()).toBeFalse();
    });

    it('should apply proper CSS classes based on signals', () => {
        component.isNavbarCollapsed.set(true);
        component.isProduction.set(false);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.container-closed')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('.sidenav-height-dev')).not.toBeNull();
    });
});
