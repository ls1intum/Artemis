import { of, Subject } from 'rxjs';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { RouterTestingModule } from '@angular/router/testing';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseRegistrationComponent } from 'app/overview/course-registration/course-registration.component';
import { BarControlConfiguration, BarControlConfigurationProvider, CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/entities/exercise.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { AlertService } from 'app/core/util/alert.service';
import { AfterViewInit, Component, TemplateRef, ViewChild } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TeamAssignmentPayload } from 'app/entities/team.model';

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

const exam1 = { id: 3, endDate: endDate1, visibleDate: visibleDate1, course: courseEmpty };
const exam2 = { id: 4, course: courseEmpty };
const exams = [exam1, exam2];
const course1 = { id: 1, exams, exercises: [exercise1], description: 'description of course 1' };
const course2 = { id: 2, exercises: [exercise2], exams: [exam2], description: 'description of course 2', shortName: 'shortName1' };

@Component({
    template: '<ng-template #controls><button id="test-button">TestButton</button></ng-template>',
})
class ControlsTestingComponent implements BarControlConfigurationProvider, AfterViewInit {
    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
        useIndentation: true,
    };

    ngAfterViewInit(): void {
        this.controlConfiguration.subject!.next(this.controls);
    }
}

describe('CourseOverviewComponent', () => {
    let component: CourseOverviewComponent;
    let fixture: ComponentFixture<CourseOverviewComponent>;
    let courseService: CourseManagementService;
    let courseScoreCalculationService: CourseScoreCalculationService;
    let teamService: TeamService;
    let jhiWebsocketService: JhiWebsocketService;

    const route: MockActivatedRouteWithSubjects = new MockActivatedRouteWithSubjects();
    const params = new Subject<Params>();
    params.next({ courseId: course1.id });
    route.setSubject(params);

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [
                CourseOverviewComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(OrionFilterDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(CourseExercisesComponent),
                MockComponent(CourseRegistrationComponent),
                MockComponent(CourseCardComponent),
                MockComponent(SecuredImageComponent),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: CourseExerciseRowComponent },
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseOverviewComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                courseScoreCalculationService = TestBed.inject(CourseScoreCalculationService);
                teamService = TestBed.inject(TeamService);
                jhiWebsocketService = TestBed.inject(JhiWebsocketService);
            });
    }));

    afterEach(() => {
        component.ngOnDestroy();
        jest.restoreAllMocks();
    });

    it('Should call all methods on init', async () => {
        const getCourseStub = jest.spyOn(courseScoreCalculationService, 'getCourse');
        const subscribeToTeamAssignmentUpdatesStub = jest.spyOn(component, 'subscribeToTeamAssignmentUpdates');
        const subscribeForQuizChangesStub = jest.spyOn(component, 'subscribeForQuizChanges');
        const adjustCourseDescriptionStub = jest.spyOn(component, 'adjustCourseDescription');
        const findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard');
        jest.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockReturnValue(Promise.resolve(of(new TeamAssignmentPayload())));
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        getCourseStub.mockReturnValue(course1);

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalled();
        expect(adjustCourseDescriptionStub).toHaveBeenCalled();
        expect(subscribeForQuizChangesStub).toHaveBeenCalled();
        expect(subscribeToTeamAssignmentUpdatesStub).toHaveBeenCalled();
    });

    it('Should call load Course methods on init', async () => {
        const getCourseStub = jest.spyOn(courseScoreCalculationService, 'getCourse');
        const subscribeToTeamAssignmentUpdatesStub = jest.spyOn(component, 'subscribeToTeamAssignmentUpdates');
        const subscribeForQuizChangesStub = jest.spyOn(component, 'subscribeForQuizChanges');
        const adjustCourseDescriptionStub = jest.spyOn(component, 'adjustCourseDescription');
        const findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard');
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        jest.spyOn(teamService, 'teamAssignmentUpdates', 'get').mockReturnValue(Promise.resolve(of(new TeamAssignmentPayload())));

        await component.ngOnInit();

        expect(getCourseStub).toHaveBeenCalled();
        expect(adjustCourseDescriptionStub).toHaveBeenCalled();
        expect(subscribeForQuizChangesStub).toHaveBeenCalled();
        expect(subscribeToTeamAssignmentUpdatesStub).toHaveBeenCalled();
    });

    it('should set Long Description', () => {
        component.longTextShown = false;

        component.showLongDescription();

        expect(component.courseDescription).toBe('');
        expect(component.longTextShown).toBe(true);
    });

    it('should set short Description', () => {
        component.longTextShown = true;
        const findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard');
        const getCourseStub = jest.spyOn(courseScoreCalculationService, 'getCourse');
        getCourseStub.mockReturnValue(course1);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        component.ngOnInit();

        component.showShortDescription();

        expect(component.courseDescription).toBe('description of course 1...');
        expect(component.longTextShown).toBe(false);
    });

    it('should have visible exams', () => {
        const findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard');
        const getCourseStub = jest.spyOn(courseScoreCalculationService, 'getCourse');
        getCourseStub.mockReturnValue(course1);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        component.ngOnInit();

        const bool = component.hasVisibleExams();

        expect(bool).toBe(true);
    });

    it('should not have visible exams', () => {
        const findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard');
        const getCourseStub = jest.spyOn(courseScoreCalculationService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        const bool = component.hasVisibleExams();

        expect(bool).toBe(false);
    });

    it('should subscribeToTeamAssignmentUpdates', () => {
        const findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard');
        const getCourseStub = jest.spyOn(courseScoreCalculationService, 'getCourse');
        const teamAssignmentUpdatesStub = jest.spyOn(teamService, 'teamAssignmentUpdates', 'get');
        getCourseStub.mockReturnValue(course2);
        teamAssignmentUpdatesStub.mockReturnValue(Promise.resolve(of({ exerciseId: 6, teamId: 1, studentParticipations: [] })));
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        component.subscribeToTeamAssignmentUpdates();
    });

    it('should adjustCourseDescription', () => {
        const findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard');
        const getCourseStub = jest.spyOn(courseScoreCalculationService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));
        const showLongDescriptionStub = jest.spyOn(component, 'showLongDescription');
        component.enableShowMore = true;

        component.ngOnInit();
        component.adjustCourseDescription();

        expect(component.enableShowMore).toBe(false);
        expect(showLongDescriptionStub).toHaveBeenCalled();
        expect(localStorage.getItem('isDescriptionReadshortName1')).toBe('true');
    });
    it('should subscribeForQuizChanges', () => {
        const findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard');
        const getCourseStub = jest.spyOn(courseScoreCalculationService, 'getCourse');
        const jhiWebsocketServiceReceiveStub = jest.spyOn(jhiWebsocketService, 'receive');
        jhiWebsocketServiceReceiveStub.mockReturnValue(of(quizExercise));
        const jhiWebsocketServiceSubscribeStub = jest.spyOn(jhiWebsocketService, 'subscribe');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();
        component.subscribeForQuizChanges();

        expect(jhiWebsocketServiceSubscribeStub).toHaveBeenCalled();
        expect(jhiWebsocketServiceReceiveStub).toHaveBeenCalled();
    });

    it('should do ngOnDestroy', () => {
        const jhiWebsocketServiceStub = jest.spyOn(jhiWebsocketService, 'unsubscribe');

        component.ngOnInit();
        component.subscribeForQuizChanges(); // to have quizExercisesChannel set
        component.ngOnDestroy();

        expect(jhiWebsocketServiceStub).toHaveBeenCalled();
    });

    it('should render controls if child has configuration', () => {
        const findOneForDashboardStub = jest.spyOn(courseService, 'findOneForDashboard');
        const getCourseStub = jest.spyOn(courseScoreCalculationService, 'getCourse');
        getCourseStub.mockReturnValue(course2);
        findOneForDashboardStub.mockReturnValue(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        const stubSubComponent = TestBed.createComponent(ControlsTestingComponent);
        component.onSubRouteActivate(stubSubComponent.componentInstance);
        fixture.detectChanges();
        stubSubComponent.detectChanges();

        const expectedButton = fixture.debugElement.query(By.css('#test-button'));
        expect(expectedButton).not.toBe(null);
        expect(expectedButton.nativeElement.innerHTML).toBe('TestButton');
    });
});
