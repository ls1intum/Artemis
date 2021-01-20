import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementComponent } from 'app/course/manage/course-management.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs/internal/observable/of';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
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

describe('CoursesComponent', () => {
    let component: CoursesComponent;
    let fixture: ComponentFixture<CoursesComponent>;
    let courseService: CourseManagementService;
    let guidedTourService: GuidedTourService;
    let courseScoreCalculationService: CourseScoreCalculationService;
    let serverDateService: ArtemisServerDateService;
    let jhiAlertService: JhiAlertService;
    let exerciseService: ExerciseService;
    const router = new MockRouter();

    const route = ({ data: of({ courseId: course1.id }), children: [] } as any) as ActivatedRoute;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MomentModule],
            declarations: [
                CoursesComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockPipe(TranslatePipe),
                MockDirective(JhiSortDirective),
                MockDirective(JhiSortByDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(CourseExercisesComponent),
                MockComponent(CourseRegistrationSelectorComponent),
                MockComponent(CourseCardComponent),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: CourseExerciseRowComponent },
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: Router, useValue: router },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CoursesComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                guidedTourService = TestBed.inject(GuidedTourService);
                courseScoreCalculationService = TestBed.inject(CourseScoreCalculationService);
                serverDateService = TestBed.inject(ArtemisServerDateService);
                jhiAlertService = TestBed.inject(JhiAlertService);
                exerciseService = TestBed.inject(ExerciseService);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        component.ngOnDestroy();
        sinon.restore();
    });

    describe('OnInit', () => {
        it('Should call loadAndFilterCourses on init', () => {
            const loadAndFilterCoursesSpy = sinon.spy(component, 'loadAndFilterCourses');

            component.ngOnInit();

            expect(loadAndFilterCoursesSpy).to.have.been.called;
        });

        it('Should load courses on init', () => {
            let findAllForDashboardStub = stub(courseService, 'findAllForDashboard');
            let courseScoreCalculationServiceStub = stub(courseScoreCalculationService, 'setCourses');
            let serverDateServiceStub = stub(serverDateService, 'now');
            let findNextRelevantExerciseSpy = sinon.spy(component, 'findNextRelevantExercise');
            findAllForDashboardStub.returns(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            serverDateServiceStub.returns(moment());

            component.ngOnInit();

            expect(findAllForDashboardStub).to.have.been.called;
            expect(findNextRelevantExerciseSpy).to.have.been.called;
            expect(component.courses).to.equal(courses);
            expect(courseScoreCalculationServiceStub).to.have.been.called;
            expect(component.exams.length).to.equal(2);
            expect(component.exams).to.contain(exam1);
            expect(component.exams).to.contain(exam2);
            expect(component.nextRelevantExams?.length).to.equal(1);
            expect(component.nextRelevantExams?.[0]).to.equal(exam1);
        });

        it('Should load exercises on init', () => {
            const mockFunction = (arg1: Exercise[]) => {
                switch (arg1[0].id) {
                    case exercise1.id:
                        return exercise1;
                    case exercise2.id:
                        return exercise2;
                }
            };

            let findAllForDashboardStub = stub(courseService, 'findAllForDashboard');
            let getNextExerciseForHoursStub = stub(exerciseService, 'getNextExerciseForHours').callsFake(mockFunction);
            let serverDateServiceStub = stub(serverDateService, 'now');

            findAllForDashboardStub.returns(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));

            serverDateServiceStub.returns(moment());

            component.ngOnInit();

            expect(getNextExerciseForHoursStub).to.have.been.calledWith(course1.exercises);
            expect(component.nextRelevantExercise).to.equal(exercise2);
            expect(component.nextRelevantCourse).to.equal(exercise2.course);
        });
    });

    it('Should load next relevant exam', () => {
        const navigateSpy = sinon.spy(router, 'navigate');
        component.nextRelevantCourseForExam = course1;
        component.nextRelevantExams = exams;
        let findAllForDashboardStub = stub(courseService, 'findAllForDashboard');
        findAllForDashboardStub.returns(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));

        component.openExam();

        expect(navigateSpy).to.have.been.calledWith(['courses', 1, 'exams', 3]);
    });
});
