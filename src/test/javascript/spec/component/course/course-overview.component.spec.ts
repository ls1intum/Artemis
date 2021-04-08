import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import { Subject } from 'rxjs';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs/internal/observable/of';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { RouterTestingModule } from '@angular/router/testing';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { JhiAlertService, JhiSortByDirective, JhiSortDirective } from 'ng-jhipster';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MomentModule } from 'ngx-moment';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseRegistrationSelectorComponent } from 'app/overview/course-registration-selector/course-registration-selector.component';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import * as moment from 'moment';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { Exercise } from 'app/entities/exercise.model';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';

chai.use(sinonChai);
const expect = chai.expect;
const endDate1 = moment().add(1, 'days');
const visibleDate1 = moment().subtract(1, 'days');
const dueDateStat1: DueDateStat = { inTime: 1, late: 0, total: 1 };
const exercise1: Exercise = {
    id: 5,
    numberOfAssessmentsOfCorrectionRounds: [dueDateStat1],
    studentAssignedTeamIdComputed: false,
    dueDate: moment().add(2, 'days'),
    secondCorrectionEnabled: true,
};
const exercise2: Exercise = {
    id: 6,
    numberOfAssessmentsOfCorrectionRounds: [dueDateStat1],
    studentAssignedTeamIdComputed: false,
    dueDate: moment().add(1, 'days'),
    secondCorrectionEnabled: true,
};
const quizExercise: QuizExercise = { id: 7, numberOfAssessmentsOfCorrectionRounds: [], studentAssignedTeamIdComputed: false, secondCorrectionEnabled: true };

const courseEmpty: Course = {};

const exam1 = { id: 3, endDate: endDate1, visibleDate: visibleDate1, course: courseEmpty };
const exam2 = { id: 4, course: courseEmpty };
const exams = [exam1, exam2];
const course1 = { id: 1, exams, exercises: [exercise1], description: 'description of course 1' };
const course2 = { id: 2, exercises: [exercise2], exams: [exam2], description: 'description of course 2', shortName: 'shortName1' };

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

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MomentModule],
            declarations: [
                CourseOverviewComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(OrionFilterDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(JhiSortDirective),
                MockDirective(JhiSortByDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(CourseExercisesComponent),
                MockComponent(CourseRegistrationSelectorComponent),
                MockComponent(CourseCardComponent),
                MockComponent(SecuredImageComponent),
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: CourseExerciseRowComponent },
                { provide: JhiAlertService, useClass: MockAlertService },
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
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        component.ngOnDestroy();
        sinon.restore();
    });

    it('Should call all methods on init', fakeAsync(() => {
        const getCourseStub = stub(courseScoreCalculationService, 'getCourse');
        const subscribeToTeamAssignmentUpdatesStub = stub(component, 'subscribeToTeamAssignmentUpdates');
        const subscribeForQuizChangesStub = stub(component, 'subscribeForQuizChanges');
        const adjustCourseDescriptionStub = stub(component, 'adjustCourseDescription');
        const findOneForDashboardStub = stub(courseService, 'findOneForDashboard');
        findOneForDashboardStub.returns(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));
        getCourseStub.returns(course1);

        component.ngOnInit();
        tick(1000);

        expect(getCourseStub).to.have.been.called;
        expect(adjustCourseDescriptionStub).to.have.been.called;
        expect(subscribeForQuizChangesStub).to.have.been.called;
        expect(subscribeToTeamAssignmentUpdatesStub).to.have.been.called;
    }));

    it('Should call load Course methods on init', fakeAsync(() => {
        const getCourseStub = stub(courseScoreCalculationService, 'getCourse');
        const subscribeToTeamAssignmentUpdatesStub = stub(component, 'subscribeToTeamAssignmentUpdates');
        const subscribeForQuizChangesStub = stub(component, 'subscribeForQuizChanges');
        const adjustCourseDescriptionStub = stub(component, 'adjustCourseDescription');
        const findOneForDashboardStub = stub(courseService, 'findOneForDashboard');
        findOneForDashboardStub.returns(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        component.ngOnInit();
        tick(600);

        expect(getCourseStub).to.have.been.called;
        expect(adjustCourseDescriptionStub).to.have.been.called;
        expect(subscribeForQuizChangesStub).to.have.been.called;
        expect(subscribeToTeamAssignmentUpdatesStub).to.have.been.called;
    }));

    it('should set Long Description', () => {
        component.longTextShown = false;

        component.showLongDescription();

        expect(component.courseDescription).to.equal('');
        expect(component.longTextShown).to.equal(true);
    });

    it('should set short Description', () => {
        component.longTextShown = true;
        const findOneForDashboardStub = stub(courseService, 'findOneForDashboard');
        const getCourseStub = stub(courseScoreCalculationService, 'getCourse');
        getCourseStub.returns(course1);
        findOneForDashboardStub.returns(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        component.ngOnInit();

        component.showShortDescription();

        expect(component.courseDescription).to.equal('description of course 1...');
        expect(component.longTextShown).to.equal(false);
    });

    it('should have visible exams', () => {
        const findOneForDashboardStub = stub(courseService, 'findOneForDashboard');
        const getCourseStub = stub(courseScoreCalculationService, 'getCourse');
        getCourseStub.returns(course1);
        findOneForDashboardStub.returns(of(new HttpResponse({ body: course1, headers: new HttpHeaders() })));

        component.ngOnInit();

        const bool = component.hasVisibleExams();

        expect(bool).to.equal(true);
    });

    it('should not have visible exams', () => {
        const findOneForDashboardStub = stub(courseService, 'findOneForDashboard');
        const getCourseStub = stub(courseScoreCalculationService, 'getCourse');
        getCourseStub.returns(course2);
        findOneForDashboardStub.returns(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        const bool = component.hasVisibleExams();

        expect(bool).to.equal(false);
    });

    it('should subscribeToTeamAssignmentUpdates', () => {
        const findOneForDashboardStub = stub(courseService, 'findOneForDashboard');
        const getCourseStub = stub(courseScoreCalculationService, 'getCourse');
        const teamAssignmentUpdatesStub = stub(teamService, 'teamAssignmentUpdates');
        getCourseStub.returns(course2);
        teamAssignmentUpdatesStub.returns(Promise.resolve(of({ exerciseId: 6, teamId: 1, studentParticipations: [] })));
        findOneForDashboardStub.returns(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();

        component.subscribeToTeamAssignmentUpdates();
    });

    it('should adjustCourseDescription', () => {
        const findOneForDashboardStub = stub(courseService, 'findOneForDashboard');
        const getCourseStub = stub(courseScoreCalculationService, 'getCourse');
        getCourseStub.returns(course2);
        findOneForDashboardStub.returns(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));
        const showLongDescriptionStub = stub(component, 'showLongDescription');
        component.enableShowMore = true;

        component.ngOnInit();
        component.adjustCourseDescription();

        expect(component.enableShowMore).to.equal(false);
        expect(showLongDescriptionStub).to.have.been.called;
        expect(localStorage.getItem('isDescriptionReadshortName1')).to.equal('true');
    });
    it('should subscribeForQuizChanges', () => {
        const findOneForDashboardStub = stub(courseService, 'findOneForDashboard');
        const getCourseStub = stub(courseScoreCalculationService, 'getCourse');
        const jhiWebsocketServiceReceiveStub = stub(jhiWebsocketService, 'receive');
        jhiWebsocketServiceReceiveStub.returns(of(quizExercise));
        const jhiWebsocketServiceSubscribeStub = stub(jhiWebsocketService, 'subscribe');
        getCourseStub.returns(course2);
        findOneForDashboardStub.returns(of(new HttpResponse({ body: course2, headers: new HttpHeaders() })));

        component.ngOnInit();
        component.subscribeForQuizChanges();

        expect(jhiWebsocketServiceSubscribeStub).to.have.been.called;
        expect(jhiWebsocketServiceReceiveStub).to.have.been.called;
    });

    it('should do ngOnDestroy', () => {
        const jhiWebsocketServiceStub = stub(jhiWebsocketService, 'unsubscribe');

        component.ngOnInit();
        component.subscribeForQuizChanges(); // to have quizExercisesChannel set
        component.ngOnDestroy();

        expect(jhiWebsocketServiceStub).to.have.been.called;
    });
});
