import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseRegistrationSelectorComponent } from 'app/overview/course-registration-selector/course-registration-selector.component';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { CoursesComponent } from 'app/overview/courses.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import * as chai from 'chai';
import * as moment from 'moment';
import { JhiAlertService, JhiSortByDirective, JhiSortDirective } from 'ng-jhipster';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { MomentModule } from 'ngx-moment';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs/internal/observable/of';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';

chai.use(sinonChai);
const expect = chai.expect;
const endDate1 = moment().add(1, 'days');
const visibleDate1 = moment().subtract(1, 'days');
const endDate2 = moment().subtract(1, 'days');
const visibleDate2 = moment().subtract(2, 'days');
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
    let courseScoreCalculationService: CourseScoreCalculationService;
    let serverDateService: ArtemisServerDateService;
    let exerciseService: ExerciseService;
    const router = new MockRouter();

    const route = ({ data: of({ courseId: course1.id }), children: [] } as any) as ActivatedRoute;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MomentModule],
            declarations: [
                CoursesComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
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
                TestBed.inject(GuidedTourService);
                courseScoreCalculationService = TestBed.inject(CourseScoreCalculationService);
                serverDateService = TestBed.inject(ArtemisServerDateService);
                TestBed.inject(JhiAlertService);
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
            const findAllForDashboardStub = stub(courseService, 'findAllForDashboard');
            const courseScoreCalculationServiceStub = stub(courseScoreCalculationService, 'setCourses');
            const serverDateServiceStub = stub(serverDateService, 'now');
            const findNextRelevantExerciseSpy = sinon.spy(component, 'findNextRelevantExercise');
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

            const findAllForDashboardStub = stub(courseService, 'findAllForDashboard');
            const getNextExerciseForHoursStub = stub(exerciseService, 'getNextExerciseForHours').callsFake(mockFunction);
            const serverDateServiceStub = stub(serverDateService, 'now');

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

        component.openExam();

        expect(navigateSpy).to.have.been.calledWith(['courses', 1, 'exams', 3]);
    });
});
