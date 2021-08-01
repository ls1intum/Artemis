import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, UrlSegment } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';
import { InstructorCourseDashboardComponent } from 'app/course/dashboards/instructor-course-dashboard/instructor-course-dashboard.component';
import { ChartsModule } from 'ng2-charts';
import { TutorLeaderboardComponent } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { SinonStub, stub } from 'sinon';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { SortService } from 'app/shared/service/sort.service';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { instructorCourseDashboardRoute } from 'app/course/dashboards/instructor-course-dashboard/instructor-course-dashboard.route';
import { RouterTestingModule } from '@angular/router/testing';
import { AlertService } from 'app/core/util/alert.service';
import { SortDirective } from 'app/shared/sort/sort.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('InstructorCourseDashboardComponent', () => {
    let component: InstructorCourseDashboardComponent;
    let fixture: ComponentFixture<InstructorCourseDashboardComponent>;
    let service: CourseManagementService;
    let alertService: AlertService;
    let sortService: SortService;
    let accountService: AccountService;

    const assessmentDueDate = moment().subtract(1, 'days');
    const textExercise = { id: 234, type: ExerciseType.TEXT, assessmentDueDate } as TextExercise;
    const modelingExercise = { id: 234, type: ExerciseType.MODELING, assessmentDueDate: moment() } as ModelingExercise;
    const expectedCourse = {
        complaintsEnabled: true,
        exercises: [textExercise, modelingExercise],
        id: 10,
        isAtLeastEditor: false,
        isAtLeastInstructor: false,
        isAtLeastTutor: false,
        maxComplaintTimeDays: 7,
        maxComplaints: 3,
        maxRequestMoreFeedbackTimeDays: 7,
        maxTeamComplaints: 3,
        onlineCourse: false,
        presentationScore: 0,
        registrationEnabled: false,
        requestMoreFeedbackEnabled: true,
        postsEnabled: true,
    } as Course;
    const course = { id: 10, exercises: [textExercise, modelingExercise] } as Course;
    const user = { id: 99, name: 'admin' } as User;
    const stats = {
        numberOfSubmissions: { inTime: 10, late: 5 } as DueDateStat,
        totalNumberOfAssessments: { inTime: 8, late: 1 } as DueDateStat,
        numberOfAutomaticAssistedAssessments: { inTime: 4, late: 0 } as DueDateStat,
    } as unknown as StatsForDashboard;

    const route = {
        snapshot: {
            paramMap: convertToParamMap({ courseId: course.id }),
            url: { path: '/course-management/10/assessment-locks', parameterMap: {}, parameters: {} } as UrlSegment,
        },
    } as any as ActivatedRoute;

    const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
    const responseFakeStats = { body: stats as StatsForDashboard } as HttpResponse<StatsForDashboard>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, ChartsModule, RouterTestingModule.withRoutes([instructorCourseDashboardRoute[0]])],
            declarations: [
                InstructorCourseDashboardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
                MockDirective(SortDirective),
                MockDirective(AlertComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisDatePipe),
                MockComponent(TutorLeaderboardComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(InstructorCourseDashboardComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(CourseManagementService);
        alertService = TestBed.inject(AlertService);
        sortService = TestBed.inject(SortService);
        accountService = TestBed.inject(AccountService);
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', fakeAsync(() => {
        spyOn(service, 'findWithExercisesAndParticipations').and.returnValue(of(responseFakeCourse));
        spyOn(service, 'getStatsForInstructors').and.returnValue(of(responseFakeStats));
        const sortByPropertySpy = sinon.spy(sortService, 'sortByProperty');
        spyOn(accountService, 'identity').and.returnValue(Promise.resolve(user));

        component.ngOnInit();
        tick();

        expect(component).to.be.ok;
        expect(sortByPropertySpy).to.have.been.calledOnce;
        expect(component.course).to.deep.equal(expectedCourse);
        expect(component.instructor).to.deep.equal(user);
    }));

    describe('tests with failing server responses', () => {
        it('should initialize with failing server response for course fetching', () => {
            const error = { status: 404 };
            spyOn(service, 'findWithExercisesAndParticipations').and.returnValue(throwError(new HttpErrorResponse(error)));
            let alertServiceStub: SinonStub;
            alertServiceStub = stub(alertService, 'error');

            fixture.detectChanges();
            expect(alertServiceStub).to.have.been.called;
        });

        it('should initialize with failing server response for stats fetching', fakeAsync(() => {
            const error = { status: 404 };
            spyOn(service, 'getStatsForInstructors').and.returnValue(throwError(new HttpErrorResponse(error)));
            let alertServiceStub: SinonStub;
            alertServiceStub = stub(alertService, 'error');
            spyOn(service, 'findWithExercisesAndParticipations').and.returnValue(of(responseFakeCourse));

            component.ngOnInit();
            tick();
            expect(alertServiceStub).to.have.been.called;
        }));
    });

    it('should calculate percentages', () => {
        expect(component.calculatePercentage(10000, 0)).to.deep.equal(0);
        expect(component.calculatePercentage(23, 56)).to.deep.equal(41);
    });

    it('should calculate the class', () => {
        expect(component.calculateClass(23, 56)).to.equal('bg-danger');
        expect(component.calculateClass(23, 25)).to.equal('bg-warning');
        expect(component.calculateClass(23, 23)).to.equal('bg-success');
    });

    it('should sort the exercises', fakeAsync(() => {
        spyOn(service, 'findWithExercisesAndParticipations').and.returnValue(of(responseFakeCourse));
        spyOn(service, 'getStatsForInstructors').and.returnValue(of(responseFakeStats));
        spyOn(accountService, 'identity').and.returnValue(Promise.resolve(user));
        const sortSpy = sinon.spy(sortService, 'sortByProperty');

        component.ngOnInit();
        tick();

        component.sortRows();

        expect(sortSpy).to.have.been.calledWith(course.exercises, 'assessmentDueDate', false);
    }));
});
