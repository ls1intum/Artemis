import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { CourseManagementComponent } from 'app/course/manage/course-management.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs/internal/observable/of';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { RouterTestingModule } from '@angular/router/testing';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { JhiSortDirective, JhiSortByDirective } from 'ng-jhipster';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MomentModule } from 'ngx-moment';
import { CoursesComponent } from 'app/overview/courses.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseRegistrationSelectorComponent } from 'app/overview/course-registration-selector/course-registration-selector.component';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import * as moment from 'moment';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise } from 'app/entities/exercise.model';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';

chai.use(sinonChai);
const expect = chai.expect;
const endDate1 = moment().add(1, 'days');
const visibleDate1 = moment().subtract(1, 'days');
const endDate2 = moment().subtract(1, 'days');
const visibleDate2 = moment().subtract(2, 'days');
const dueDateStat1: DueDateStat = { inTime: 1, late: 0, total: 1 };
const exercise1: Exercise = { id: 5, numberOfAssessmentsOfCorrectionRounds: [dueDateStat1], studentAssignedTeamIdComputed: false, dueDate: moment().add(2, 'days') };
const exercise2: Exercise = { id: 6, numberOfAssessmentsOfCorrectionRounds: [dueDateStat1], studentAssignedTeamIdComputed: false, dueDate: moment().add(1, 'days') };

const courseEmpty: Course = {};

const exam1 = { id: 3, endDate: endDate1, visibleDate: visibleDate1, course: courseEmpty };
const exam2 = { id: 4, endDate: endDate2, visibleDate: visibleDate2, course: courseEmpty };
const exams = [exam1, exam2];
const course1 = { id: 1, exams, exercises: [exercise1] };
const course2 = { id: 2, exercises: [exercise2] };
const courses: Course[] = [course1, course2];
const errorBody: any[] = [];

describe('CourseOverviewComponent', () => {
    let component: CourseOverviewComponent;
    let fixture: ComponentFixture<CourseOverviewComponent>;
    let courseService: CourseManagementService;
    let guidedTourService: GuidedTourService;
    let courseScoreCalculationService: CourseScoreCalculationService;
    let serverDateService: ArtemisServerDateService;
    let jhiAlertService: JhiAlertService;
    let exerciseService: ExerciseService;
    let router: Router;

    const course: Course = { id: 123 } as Course;
    const route = ({ data: { paramMap: convertToParamMap({ courseId: course.id }) } } as any) as ActivatedRoute;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MomentModule],
            declarations: [
                CourseOverviewComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockDirective(OrionFilterDirective),
                MockPipe(TranslatePipe),
                MockDirective(JhiSortDirective),
                MockDirective(JhiSortByDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(CourseExercisesComponent),
                MockComponent(CourseRegistrationSelectorComponent),
                MockComponent(CourseCardComponent),
                MockComponent(SecuredImageComponent),
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent)
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: MockActivatedRouteWithSubjects },
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
                guidedTourService = TestBed.inject(GuidedTourService);
                courseScoreCalculationService = TestBed.inject(CourseScoreCalculationService);
                serverDateService = TestBed.inject(ArtemisServerDateService);
                jhiAlertService = TestBed.inject(JhiAlertService);
                exerciseService = TestBed.inject(ExerciseService);
                router = TestBed.inject(Router);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        component.ngOnDestroy();
        sinon.restore();
    });

    it('Should call getCourse on init', fakeAsync(() => {
        const getCourse = sinon.spy(courseScoreCalculationService, 'getCourse');

        component.ngOnInit();
        tick(1000);

        expect(getCourse).to.have.been.called;
    }));

});
