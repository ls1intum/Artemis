import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { EMPTY, Subject, of, throwError } from 'rxjs';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
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
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/overview/tab-bar/tab-bar';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { generateExampleTutorialGroupsConfiguration } from '../tutorial-groups/helpers/tutorialGroupsConfigurationExampleModels';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

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
const quizExercise: QuizExercise = { id: 7, numberOfAssessmentsOfCorrectionRounds: [], studentAssignedTeamIdComputed: false, secondCorrectionEnabled: true };

const courseEmpty: Course = {};

const exam1: Exam = { id: 3, endDate: endDate1, visibleDate: visibleDate1, course: courseEmpty };
const exam2: Exam = { id: 4, course: courseEmpty };
const exams = [exam1, exam2];
const course1: Course = {
    id: 1,
    exams,
    exercises: [exercise1],
    description:
        'Nihilne te nocturnum praesidium Palati, nihil urbis vigiliae. Salutantibus vitae elit libero, a pharetra augue. Quam diu etiam furor iste tuus nos eludet? ' +
        'Fabio vel iudice vincam, sunt in culpa qui officia. Quam temere in vitiis, legem sancimus haerentia. Quisque ut dolor gravida, placerat libero vel, euismod.',
};
const course2: Course = {
    id: 2,
    exercises: [exercise2],
    exams: [exam2],
    description: 'Short description of course 2',
    shortName: 'shortName2',
    learningGoals: [new LearningGoal()],
    tutorialGroups: [new TutorialGroup()],
    prerequisites: [new LearningGoal()],
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
    let learningGoalService: LearningGoalService;
    let tutorialGroupsService: TutorialGroupsService;
    let tutorialGroupsConfigurationService: TutorialGroupsConfigurationService;
    let jhiWebsocketService: JhiWebsocketService;
    let router: MockRouter;
    let jhiWebsocketServiceReceiveStub: jest.SpyInstance;
    let jhiWebsocketServiceSubscribeSpy: jest.SpyInstance;
    let findOneForDashboardStub: jest.SpyInstance;

    let metisConversationService: MetisConversationService;
    const course = { id: 1 } as Course;

    beforeEach(fakeAsync(() => {
        metisConversationService = {} as MetisConversationService;
        router = new MockRouter();

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [
                CourseOverviewComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(OrionFilterDirective),
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
                MockProvider(LearningGoalService),
                {
                    provide: ActivatedRoute,
                    useValue: { params: of({ courseId: course1.id }), snapshot: { firstChild: { routeConfig: { path: `courses/${course1.id}/exercises` } } } },
                },
                MockProvider(TeamService),
                MockProvider(JhiWebsocketService),
                MockProvider(ArtemisServerDateService),
                MockProvider(AlertService),
                MockProvider(ChangeDetectorRef),
                MockProvider(ProfileService),
                MockProvider(TutorialGroupsService),
                MockProvider(TutorialGroupsConfigurationService),
                MockProvider(MetisConversationService),
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.overrideComponent(CourseOverviewComponent, {
                    set: {
                        providers: [
                            {
                                provide: MetisConversationService,
                                useValue: metisConversationService,
                            },
                        ],
                    },
                }).createComponent(CourseOverviewComponent);

                Object.defineProperty(metisConversationService, 'course', { get: () => course });
                Object.defineProperty(metisConversationService, 'setUpConversationService', { value: () => EMPTY });

                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                courseStorageService = TestBed.inject(CourseStorageService);
                teamService = TestBed.inject(TeamService);
                learningGoalService = TestBed.inject(LearningGoalService);
                tutorialGroupsService = TestBed.inject(TutorialGroupsService);
                tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService);
                jhiWebsocketService = TestBed.inject(JhiWebsocketService);
                jhiWebsocketServiceReceiveStub = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(quizExercise));
                jhiWebsocketServiceSubscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
                jest.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockResolvedValue(of(new TeamAssignmentPayload()));
                // default for findOneForDashboardStub is to return the course
                findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard').mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
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
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        getCourseStub.mockReturnValue(course1);

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalled();
        expect(subscribeForQuizChangesStub).toHaveBeenCalledOnce();
        expect(subscribeToTeamAssignmentUpdatesStub).toHaveBeenCalledOnce();
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

    it('should have learning goals and tutorial groups', () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');

        const learningGoalsResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [new LearningGoal()],
            status: 200,
        });
        const tutorialGroupsResponse: HttpResponse<TutorialGroup[]> = new HttpResponse({
            body: [new TutorialGroup()],
            status: 200,
        });
        const configurationResponse: HttpResponse<TutorialGroupsConfiguration> = new HttpResponse({
            body: generateExampleTutorialGroupsConfiguration({}),
            status: 200,
        });

        jest.spyOn(learningGoalService, 'getAllPrerequisitesForCourse').mockReturnValue(of(learningGoalsResponse));
        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(learningGoalsResponse));
        jest.spyOn(tutorialGroupsService, 'getAllForCourse').mockReturnValue(of(tutorialGroupsResponse));
        jest.spyOn(tutorialGroupsConfigurationService, 'getOneOfCourse').mockReturnValue(of(configurationResponse));

        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        expect(component.hasLearningGoals()).toBeTrue();
        expect(component.hasTutorialGroups()).toBeTrue();
        expect(component.course?.learningGoals).not.toBeEmpty();
        expect(component.course?.prerequisites).not.toBeEmpty();
        expect(component.course?.tutorialGroups).not.toBeEmpty();
    });

    it('should subscribeToTeamAssignmentUpdates', () => {
        const getCourseStub = jest.spyOn(courseStorageService, 'getCourse');
        const teamAssignmentUpdatesStub = jest.spyOn(teamService, 'teamAssignmentUpdates', 'get');
        getCourseStub.mockReturnValue(course2);
        teamAssignmentUpdatesStub.mockReturnValue(Promise.resolve(of({ exerciseId: 6, teamId: 1, studentParticipations: [] })));
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
});
