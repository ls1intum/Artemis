import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { EMPTY, Observable, Subject, of, throwError } from 'rxjs';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { RouterTestingModule } from '@angular/router/testing';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseRegistrationComponent } from 'app/overview/course-registration/course-registration.component';
import { CourseCardComponent } from 'app/overview/course-card.component';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/entities/exercise.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { AlertService } from 'app/core/util/alert.service';
import { AfterViewInit, ChangeDetectorRef, Component, TemplateRef, ViewChild } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TeamAssignmentPayload } from 'app/entities/team.model';
import { Exam } from 'app/entities/exam.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { Competency } from 'app/entities/competency.model';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { generateExampleTutorialGroupsConfiguration } from '../tutorial-groups/helpers/tutorialGroupsConfigurationExampleModels';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../helpers/mocks/service/mock-notification.service';
import { MockMetisConversationService } from '../../helpers/mocks/service/mock-metis-conversation.service';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatSidenavModule } from '@angular/material/sidenav';
import { TranslateDirective } from 'app/shared/language/translate.directive';

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
const quizExercise: QuizExercise = {
    id: 7,
    numberOfAssessmentsOfCorrectionRounds: [],
    studentAssignedTeamIdComputed: false,
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
    courseIcon: 'path/to/icon.png',
};
const course2: Course = {
    id: 2,
    title: 'Course2',
    exercises: [exercise2],
    exams: [exam2],
    description: 'Short description of course 2',
    shortName: 'shortName2',
    competencies: [new Competency()],
    tutorialGroups: [new TutorialGroup()],
    prerequisites: [new Competency()],
    numberOfCompetencies: 1,
    numberOfPrerequisites: 1,
    numberOfTutorialGroups: 1,
};

@Component({
    template: '<ng-template #controls><button id="test-button">TestButton</button></ng-template>',
})
class ControlsTestingComponent implements BarControlConfigurationProvider, AfterViewInit {
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
    let teamService: TeamService;
    let tutorialGroupsService: TutorialGroupsService;
    let tutorialGroupsConfigurationService: TutorialGroupsConfigurationService;
    let jhiWebsocketService: JhiWebsocketService;
    let courseAccessStorageService: CourseAccessStorageService;
    let router: MockRouter;
    let jhiWebsocketServiceReceiveStub: jest.SpyInstance;
    let jhiWebsocketServiceSubscribeSpy: jest.SpyInstance;
    let findOneForDashboardStub: jest.SpyInstance;
    let route: ActivatedRoute;
    let findOneForRegistrationStub: jest.SpyInstance;

    let metisConversationService: MetisConversationService;

    const course = {
        id: 1,
        courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING,
    } as Course;

    beforeEach(fakeAsync(() => {
        route = {
            params: of({ courseId: course1.id }) as Params,
            snapshot: { firstChild: { routeConfig: { path: `courses/${course1.id}/exercises` } } },
        } as ActivatedRoute;
        router = new MockRouter();

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(MatSidenavModule), MockModule(NgbTooltipModule), MockModule(BrowserAnimationsModule)],
            declarations: [
                CourseOverviewComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(OrionFilterDirective),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockDirective(FeatureToggleHideDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(CourseExercisesComponent),
                MockComponent(CourseRegistrationComponent),
                MockComponent(CourseCardComponent),
                MockComponent(SecuredImageComponent),
                MockComponent(HeaderCourseComponent),
            ],
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(CourseExerciseService),
                MockProvider(CompetencyService),
                MockProvider(TeamService),
                MockProvider(JhiWebsocketService),
                MockProvider(ArtemisServerDateService),
                MockProvider(AlertService),
                MockProvider(ChangeDetectorRef),
                MockProvider(ProfileService),
                MockProvider(TutorialGroupsService),
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(MetisConversationService),
                MockProvider(CourseAccessStorageService),
                { provide: Router, useValue: router },
                { provide: ActivatedRoute, useValue: route },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: NotificationService, useClass: MockNotificationService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseOverviewComponent);

                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                courseStorageService = TestBed.inject(CourseStorageService);
                teamService = TestBed.inject(TeamService);
                tutorialGroupsService = TestBed.inject(TutorialGroupsService);
                tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService);
                jhiWebsocketService = TestBed.inject(JhiWebsocketService);
                courseAccessStorageService = TestBed.inject(CourseAccessStorageService);
                metisConversationService = fixture.debugElement.injector.get(MetisConversationService);
                jhiWebsocketServiceReceiveStub = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(quizExercise));
                jhiWebsocketServiceSubscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
                jest.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockResolvedValue(of(new TeamAssignmentPayload()));
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
            });
    }));

    afterEach(() => {
        component.ngOnDestroy();
        jest.restoreAllMocks();
        localStorage.clear();
        sessionStorage.clear();
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
        expect(notifyAboutCourseAccessStub).toHaveBeenCalledExactlyOnceWith(course1.id);
    });

    it('should create sidebar items with default items', () => {
        component.course = { id: 123, lectures: [], exams: [] };
        const sidebarItems = component.getSidebarItems();
        expect(sidebarItems.length).toBeGreaterThan(0);
        expect(sidebarItems[0].title).toContain('Exercises');
        expect(sidebarItems[1].title).toContain('Lectures');
    });

    it('loads conversations when switching to message tab once', async () => {
        const metisConversationServiceStub = jest.spyOn(metisConversationService, 'setUpConversationService').mockReturnValue(EMPTY);
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        getCourseStub.mockReturnValue(course1);

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalled();

        expect(metisConversationServiceStub).toHaveBeenCalledTimes(0);

        const tabs = ['messages', 'exercises', 'messages'];
        tabs.forEach((tab) => {
            route.snapshot.firstChild!.routeConfig!.path = tab;
            component.onSubRouteActivate({ controlConfiguration: undefined });

            expect(metisConversationServiceStub).toHaveBeenCalledOnce();
        });
    });

    it.each([true, false])('should determine once if there are unread messages', async (hasNewMessages: boolean) => {
        const spy = jest.spyOn(metisConversationService, 'checkForUnreadMessages');
        metisConversationService._hasUnreadMessages$.next(hasNewMessages);
        jest.spyOn(metisConversationService, 'setUpConversationService').mockReturnValue(of());

        await component.ngOnInit();

        route.snapshot.firstChild!.routeConfig!.path = 'exercises';
        component.onSubRouteActivate({ controlConfiguration: undefined });

        expect(component.hasUnreadMessages).toBe(hasNewMessages);

        const tabs = ['messages', 'exercises', 'messages'];
        tabs.forEach((tab) => {
            route.snapshot.firstChild!.routeConfig!.path = tab;
            component.onSubRouteActivate({ controlConfiguration: undefined });

            expect(spy).toHaveBeenCalledOnce();
        });
    });

    it('should not try to load message related data when not activated for course', () => {
        const unreadMessagesSpy = jest.spyOn(metisConversationService, 'checkForUnreadMessages');
        const setUpConversationServiceSpy = jest.spyOn(metisConversationService, 'setUpConversationService');

        component.course = { courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED };

        const tabs = ['exercises', 'messages', 'exercises', 'messages'];
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

        component.loadCourse().subscribe(
            () => {
                throw new Error('should not happen');
            },
            (error) => {
                expect(error).toBeDefined();
            },
        );

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

        // test that canRegisterForCourse throws
        component.canRegisterForCourse().subscribe(
            () => {
                throw new Error('should not be called');
            },
            (error) => {
                expect(error).toEqual(new HttpResponse({ status: 404 }));
            },
        );

        // wait for the observable to complete
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

        component.loadCourse(true);

        expect(subscribeStub).toHaveBeenCalledOnce();
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
        expect(component.course?.competencies).not.toBeEmpty();
        expect(component.course?.prerequisites).not.toBeEmpty();
        expect(component.course?.tutorialGroups).not.toBeEmpty();
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

        expect(jhiWebsocketServiceSubscribeSpy).toHaveBeenCalledOnce();
        expect(jhiWebsocketServiceReceiveStub).toHaveBeenCalledOnce();
    });

    it('should do ngOnDestroy', () => {
        const jhiWebsocketServiceStub = jest.spyOn(jhiWebsocketService, 'unsubscribe');

        component.ngOnInit();
        component.subscribeForQuizChanges(); // to have quizExercisesChannel set
        component.ngOnDestroy();

        expect(jhiWebsocketServiceStub).toHaveBeenCalledOnce();
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
        component.isNavbarCollapsed = true;
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.container-closed')).not.toBeNull();

        component.isNavbarCollapsed = false;
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.container-closed')).toBeNull();
    });

    it('should display course title when navbar is not collapsed', () => {
        component.isNavbarCollapsed = false;
        fixture.detectChanges();

        const titleElement = fixture.nativeElement.querySelector('#test-course-title');
        expect(titleElement.textContent).toContain(component.course?.title);
    });

    it('should display course icon when available', () => {
        fixture.detectChanges();

        const iconElement = fixture.nativeElement.querySelector('jhi-secured-image');
        expect(iconElement).not.toBeNull();
    });

    it('should not display course icon when not available', () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        fixture.detectChanges();

        const iconElement = fixture.nativeElement.querySelector('jhi-secured-image');
        const iconElement2 = fixture.nativeElement.querySelector('.course-circle');
        expect(iconElement).toBeNull();
        expect(iconElement2).not.toBeNull();
    });

    it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
        component.toggleCollapseState();
        expect(component.isNavbarCollapsed).toBeTrue();

        component.toggleCollapseState();
        expect(component.isNavbarCollapsed).toBeFalse();
    });

    it('should call courseActionItemClick on course action item click', () => {
        // Mock a courseActionItem if not already done
        component.courseActionItems = [
            {
                title: 'Unenroll',
                translation: 'artemisApp.courseOverview.exerciseList.details.unenrollmentButton',
            },
        ];
        fixture.detectChanges();

        jest.spyOn(component, 'courseActionItemClick');
        const actionItem = fixture.nativeElement.querySelector('.nav-link-sidebar');
        actionItem.click();
        expect(component.courseActionItemClick).toHaveBeenCalledWith(component.courseActionItems[0]);
    });
});
