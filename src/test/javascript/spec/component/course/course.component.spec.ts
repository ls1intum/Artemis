import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseRegistrationComponent } from 'app/overview/course-registration/course-registration.component';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { CoursesComponent } from 'app/overview/courses.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { AlertService } from 'app/core/util/alert.service';
import { Component } from '@angular/core';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';

const endDate1 = dayjs().add(1, 'days');
const visibleDate1 = dayjs().subtract(1, 'days');
const endDate2 = dayjs().subtract(1, 'days');
const visibleDate2 = dayjs().subtract(2, 'days');
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

const exam1 = { id: 3, endDate: endDate1, visibleDate: visibleDate1, course: courseEmpty };
const exam2 = { id: 4, endDate: endDate2, visibleDate: visibleDate2, course: courseEmpty };
const exams = [exam1, exam2];
const course1 = { id: 1, exams, exercises: [exercise1] };
const course2 = { id: 2, exercises: [exercise2] };
const courses: Course[] = [course1, course2];

@Component({
    template: '',
})
class DummyComponent {}

describe('CoursesComponent', () => {
    let component: CoursesComponent;
    let fixture: ComponentFixture<CoursesComponent>;
    let courseService: CourseManagementService;
    let courseScoreCalculationService: CourseScoreCalculationService;
    let serverDateService: ArtemisServerDateService;
    let exerciseService: ExerciseService;
    let router: Router;
    let location: Location;

    const route = { data: of({ courseId: course1.id }), children: [] } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([{ path: 'courses/:courseId/exams/:examId', component: DummyComponent }])],
            declarations: [
                CoursesComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(CourseExercisesComponent),
                MockComponent(CourseRegistrationComponent),
                MockComponent(CourseCardComponent),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: CourseExerciseRowComponent },
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CoursesComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                router = TestBed.inject(Router);
                location = TestBed.inject(Location);
                TestBed.inject(GuidedTourService);
                courseScoreCalculationService = TestBed.inject(CourseScoreCalculationService);
                serverDateService = TestBed.inject(ArtemisServerDateService);
                TestBed.inject(AlertService);
                exerciseService = TestBed.inject(ExerciseService);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        component.ngOnDestroy();
        jest.restoreAllMocks();
    });

    describe('OnInit', () => {
        it('Should call loadAndFilterCourses on init', () => {
            const loadAndFilterCoursesSpy = jest.spyOn(component, 'loadAndFilterCourses');

            component.ngOnInit();

            expect(loadAndFilterCoursesSpy).toHaveBeenCalledOnce();
        });

        it('Should load courses on init', () => {
            const findAllForDashboardSpy = jest.spyOn(courseService, 'findAllForDashboard');
            const courseScoreCalculationServiceSpy = jest.spyOn(courseScoreCalculationService, 'setCourses');
            const serverDateServiceSpy = jest.spyOn(serverDateService, 'now');
            const findNextRelevantExerciseSpy = jest.spyOn(component, 'findNextRelevantExercise');
            findAllForDashboardSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            serverDateServiceSpy.mockReturnValue(dayjs());

            component.ngOnInit();

            expect(findAllForDashboardSpy).toHaveBeenCalledOnce();
            expect(findNextRelevantExerciseSpy).toHaveBeenCalledOnce();
            expect(component.courses).toEqual(courses);
            expect(courseScoreCalculationServiceSpy).toHaveBeenCalledOnce();
            expect(component.exams).toEqual([exam1, exam2]);
            expect(component.nextRelevantExams).toHaveLength(1);
            expect(component.nextRelevantExams?.[0]).toEqual(exam1);
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

            const findAllForDashboardSpy = jest.spyOn(courseService, 'findAllForDashboard');
            const getNextExerciseForHoursSpy = jest.spyOn(exerciseService, 'getNextExerciseForHours').mockImplementation(mockFunction);
            const serverDateServiceSpy = jest.spyOn(serverDateService, 'now');

            findAllForDashboardSpy.mockReturnValue(of(new HttpResponse({ body: courses, headers: new HttpHeaders() })));
            serverDateServiceSpy.mockReturnValue(dayjs());

            component.ngOnInit();

            expect(getNextExerciseForHoursSpy).toHaveBeenCalledWith(course1.exercises);
            expect(component.nextRelevantExercise).toEqual(exercise2);
            expect(component.nextRelevantCourse).toEqual(exercise2.course);
        });
    });

    it('Should load next relevant exam', fakeAsync(() => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.nextRelevantCourseForExam = course1;
        component.nextRelevantExams = [exam1];
        component.openExam();
        tick();

        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'exams', 3]);
        expect(location.path()).toEqual('/courses/1/exams/3');
    }));

    it('Should load next relevant exam ignoring testExams', fakeAsync(() => {
        const testExam1 = {
            id: 5,
            startDate: dayjs().add(1, 'hour'),
            endDate: endDate1,
            visibleDate: visibleDate1.subtract(10, 'minutes'),
            course: courseEmpty,
            workingTime: 3600,
            testExam: true,
        };
        const course3 = { id: 3, exams: [testExam1], exercises: [exercise1] };
        const coursesWithTestExam = [course1, course2, course3];

        const findAllForDashboardSpy = jest.spyOn(courseService, 'findAllForDashboard');
        const courseScoreCalculationServiceSpy = jest.spyOn(courseScoreCalculationService, 'setCourses');
        const serverDateServiceSpy = jest.spyOn(serverDateService, 'now');
        const findNextRelevantExerciseSpy = jest.spyOn(component, 'findNextRelevantExercise');
        findAllForDashboardSpy.mockReturnValue(
            of(
                new HttpResponse({
                    body: coursesWithTestExam,
                    headers: new HttpHeaders(),
                }),
            ),
        );
        serverDateServiceSpy.mockReturnValue(dayjs());

        component.ngOnInit();
        tick(1000);

        expect(findAllForDashboardSpy).toHaveBeenCalledOnce();
        expect(findNextRelevantExerciseSpy).toHaveBeenCalledOnce();
        expect(component.courses).toEqual(coursesWithTestExam);
        expect(courseScoreCalculationServiceSpy).toHaveBeenCalledOnce();
        expect(component.exams).toEqual([exam1, exam2, testExam1]);
        expect(component.nextRelevantExams).toEqual([exam1]);
    }));
});
