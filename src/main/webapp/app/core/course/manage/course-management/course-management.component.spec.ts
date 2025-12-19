import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementComponent } from 'app/core/course/manage/course-management/course-management.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementOverviewStatisticsDto } from 'app/core/course/manage/overview/course-management-overview-statistics-dto.model';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/core/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockProvider } from 'ng-mocks';
import { CourseAccessStorageService } from 'app/core/course/shared/services/course-access-storage.service';

describe('CourseManagementComponent', () => {
    let fixture: ComponentFixture<CourseManagementComponent>;
    let component: CourseManagementComponent;
    let service: CourseManagementService;
    let courseAccessStorageService: CourseAccessStorageService;

    const pastExercise = {
        dueDate: dayjs().subtract(6, 'days'),
        assessmentDueDate: dayjs().subtract(1, 'days'),
    } as Exercise;

    const currentExercise = {
        dueDate: dayjs().add(2, 'days'),
        releaseDate: dayjs().subtract(2, 'days'),
    } as Exercise;

    const futureExercise1 = {
        releaseDate: dayjs().add(4, 'days'),
    } as Exercise;

    const futureExercise2 = {
        releaseDate: dayjs().add(6, 'days'),
    } as Exercise;

    const courseWithExercises187 = {
        courseId: 187,
        exerciseDetails: [pastExercise, currentExercise, futureExercise2, futureExercise1],
    } as Course;

    const courseWithExercises188 = {
        courseId: 188,
        exerciseDetails: [],
    } as Course;

    const course187 = {
        id: 187,
        testCourse: false,
        semester: 'SS19',
    } as Course;

    const course188 = {
        id: 188,
        testCourse: false,
        semester: 'WS19/20',
    } as Course;

    const courseDetails187 = new Course();
    courseDetails187.id = 187;
    courseDetails187.semester = 'SS19';

    const courseDetails188 = new Course();
    courseDetails188.id = 188;
    courseDetails188.semester = 'WS19/20';

    const courseStatisticsDTO = new CourseManagementOverviewStatisticsDto();
    const exerciseDTO = new CourseManagementOverviewExerciseStatisticsDTO();
    exerciseDTO.exerciseId = 1;
    exerciseDTO.exerciseMaxPoints = 10;
    exerciseDTO.averageScoreInPercent = 50;
    courseStatisticsDTO.exerciseDTOS = [exerciseDTO];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(EventManager),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(CourseManagementService);
                courseAccessStorageService = TestBed.inject(CourseAccessStorageService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        jest.spyOn(service, 'getCourseOverview').mockReturnValue(of(new HttpResponse({ body: [courseDetails187, courseDetails188] })));
        jest.spyOn(service, 'getExercisesForManagementOverview').mockReturnValue(of(new HttpResponse({ body: [courseWithExercises187, courseWithExercises188] })));
        jest.spyOn(service, 'getStatsForManagementOverview').mockReturnValue(of(new HttpResponse({ body: [] })));
        jest.spyOn(service, 'getWithUserStats').mockReturnValue(of(new HttpResponse({ body: [course187, course188] })));

        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.showOnlyActive).toBeTrue();
        component.toggleShowOnlyActive();
        expect(component.showOnlyActive).toBeFalse();
        fixture.changeDetectorRef.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should correctly sort unique semester names', () => {
        const course1 = { id: 1, semester: 'SS20' } as Course;
        const course2 = { id: 2, semester: 'WS19' } as Course;
        const course3 = { id: 3, semester: 'SS19' } as Course;
        const course4 = { id: 4, semester: 'WS20' } as Course;
        const course5 = { id: 5, semester: '' } as Course; // course with no semester

        component.courses = [course1, course2, course3, course4, course5];
        const sortedSemesters = component['getUniqueSemesterNamesSorted'](component.courses);

        expect(sortedSemesters).toEqual(['WS20', 'SS20', 'WS19', 'SS19', '']);
    });

    it('should correctly sort courses into semesters', () => {
        const course1 = { id: 1, semester: 'SS20', testCourse: false } as Course;
        const course2 = { id: 2, semester: 'WS19', testCourse: false } as Course;
        const course3 = { id: 3, semester: 'SS19', testCourse: false } as Course;
        const course4 = { id: 4, semester: 'SS19', testCourse: false } as Course;
        const course5 = { id: 5, semester: '', testCourse: false } as Course; // course with no semester
        const course6 = { id: 6, semester: 'WS20', testCourse: true } as Course; // test course

        component.courses = [course1, course2, course3, course4, course5, course6];
        component.courseSemesters = ['SS20', 'WS19', 'SS19', ''];

        // Simulate that course1 and course2 were recently accessed
        jest.spyOn(courseAccessStorageService, 'getLastAccessedCourses').mockReturnValue([1, 2]);

        component['sortCoursesIntoSemesters']();

        expect(component.coursesBySemester['recent']).toEqual([course1, course2]);
        expect(component.coursesBySemester['SS20']).toEqual([]);
        expect(component.coursesBySemester['WS19']).toEqual([]);
        expect(component.coursesBySemester['SS19']).toEqual([course3, course4]);
        expect(component.coursesBySemester['']).toEqual([course5]);
        expect(component.coursesBySemester['test']).toEqual([course6]);
    });
});
