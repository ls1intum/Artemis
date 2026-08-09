import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { MockProvider } from 'ng-mocks';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { CourseExercisesForOverviewDTO } from 'app/course/shared/entities/course-exercises-for-overview-dto';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { TeamService } from 'app/exercise/team/team.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('CourseOverviewExercisesService', () => {
    let service: CourseOverviewExercisesService;
    let courseManagementService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    let websocketService: MockWebsocketService;
    let alertService: AlertService;
    let fetchSpy: ReturnType<typeof vi.spyOn>;
    let teamUpdates: Subject<TeamAssignmentPayload>;
    let convertExerciseDatesFromServer: ReturnType<typeof vi.fn>;

    const exercise = { id: 42 } as Exercise;
    const data = { exercises: [exercise] } as CourseExercisesForOverviewDTO;

    beforeEach(() => {
        teamUpdates = new Subject<TeamAssignmentPayload>();
        convertExerciseDatesFromServer = vi.fn((updatedExercise: Exercise) => updatedExercise);
        TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: TeamService, useValue: { teamAssignmentUpdates: Promise.resolve(teamUpdates) } },
                { provide: CourseExerciseService, useValue: { convertExerciseDatesFromServer } },
                provideHttpClient(),
                MockProvider(AlertService),
            ],
        });
        service = TestBed.inject(CourseOverviewExercisesService);
        service.clear();
        courseManagementService = TestBed.inject(CourseManagementService);
        courseStorageService = TestBed.inject(CourseStorageService);
        courseStorageService.setCourses([]);
        websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
        alertService = TestBed.inject(AlertService);
        fetchSpy = vi.spyOn(courseManagementService, 'findCourseExercisesForOverview').mockReturnValue(of(data));
    });

    afterEach(() => {
        service.clear();
        teamUpdates.complete();
        vi.restoreAllMocks();
    });

    it('should not report a hit for an undefined course id on empty state', () => {
        expect(service.dataFor(undefined as unknown as number)).toBeUndefined();
    });

    it('should fetch only once per course when using loadIfNeeded, so the exercises and statistics tabs share one load', () => {
        service.loadIfNeeded(1).subscribe();
        service.loadIfNeeded(1).subscribe();
        expect(fetchSpy).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('should refetch for a different course', () => {
        service.loadIfNeeded(1).subscribe();
        service.loadIfNeeded(2).subscribe();
        expect(fetchSpy).toHaveBeenCalledTimes(2);
        expect(service.dataFor(1)).toBeUndefined();
    });

    it('should publish the loaded exercises on the stored course under a new reference so signals notify', () => {
        const storedCourse = { id: 1, title: 'Course' } as Course;
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(storedCourse);
        const updateSpy = vi.spyOn(courseStorageService, 'updateCourse');

        service.load(1).subscribe();

        expect(updateSpy).toHaveBeenCalledOnce();
        const published = updateSpy.mock.calls[0][0] as Course;
        expect(published.exercises).toEqual([exercise]);
        expect(published.title).toBe('Course');
        expect(published).not.toBe(storedCourse);
    });

    it('should publish after the lean course arrives when the exercise request wins the race', () => {
        const updateSpy = vi.spyOn(courseStorageService, 'updateCourse');

        service.load(1).subscribe();

        expect(updateSpy).not.toHaveBeenCalled();

        const storedCourse = { id: 1, title: 'Course' } as Course;
        courseStorageService.updateCourse(storedCourse);

        expect(updateSpy).toHaveBeenCalledTimes(2);
        expect(updateSpy.mock.calls[1][0]).toEqual({ ...storedCourse, exercises: [exercise] });
        expect(courseStorageService.getCourse(1)?.exercises).toEqual([exercise]);
    });

    it('should share an in-flight request between parallel tab subscribers', () => {
        const response = new Subject<CourseExercisesForOverviewDTO>();
        fetchSpy.mockReturnValue(response);
        const exercisesResult = vi.fn();
        const statisticsResult = vi.fn();

        service.loadIfNeeded(1).subscribe(exercisesResult);
        service.loadIfNeeded(1).subscribe(statisticsResult);

        expect(fetchSpy).toHaveBeenCalledExactlyOnceWith(1);
        response.next(data);
        response.complete();
        expect(exercisesResult).toHaveBeenCalledExactlyOnceWith(data);
        expect(statisticsResult).toHaveBeenCalledExactlyOnceWith(data);
    });

    it('should show a fallback alert, complete cleanly, and allow a retry after an unstructured load error', () => {
        fetchSpy.mockReturnValue(throwError(() => new Error('network')));
        const alertSpy = vi.spyOn(alertService, 'error');
        const next = vi.fn();
        const error = vi.fn();
        const complete = vi.fn();

        service.loadIfNeeded(1).subscribe({ next, error, complete });

        expect(next).not.toHaveBeenCalled();
        expect(error).not.toHaveBeenCalled();
        expect(complete).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.courseOverview.exerciseLoadFailed');
        expect(service.dataFor(1)).toBeUndefined();

        service.loadIfNeeded(1).subscribe();
        expect(fetchSpy).toHaveBeenCalledTimes(2);
    });

    it('should not duplicate the global alert for a structured HTTP error', () => {
        const structuredError = new HttpErrorResponse({ status: 500, error: { title: 'Server error', errorKey: 'serverError' } });
        fetchSpy.mockReturnValue(throwError(() => structuredError));
        const alertSpy = vi.spyOn(alertService, 'error');
        const error = vi.fn();
        const complete = vi.fn();

        service.load(1).subscribe({ error, complete });

        expect(error).not.toHaveBeenCalled();
        expect(complete).toHaveBeenCalledOnce();
        expect(alertSpy).not.toHaveBeenCalled();
        expect(service.dataFor(1)).toBeUndefined();
    });

    it('should show the fallback alert when the global handler deliberately suppresses a 404', () => {
        fetchSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
        const alertSpy = vi.spyOn(alertService, 'error');

        service.load(1).subscribe();

        expect(alertSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.courseOverview.exerciseLoadFailed');
        expect(service.dataFor(1)).toBeUndefined();
    });

    it('should ignore a late response from a course that has already been left', () => {
        const firstResponse = new Subject<CourseExercisesForOverviewDTO>();
        const secondResponse = new Subject<CourseExercisesForOverviewDTO>();
        const secondData = { exercises: [{ id: 84 } as Exercise] } as CourseExercisesForOverviewDTO;
        fetchSpy.mockImplementation((courseId: number) => (courseId === 1 ? firstResponse : secondResponse));
        courseStorageService.setCourses([{ id: 1 } as Course, { id: 2 } as Course]);

        service.load(1).subscribe();
        service.load(2).subscribe();
        firstResponse.next(data);
        firstResponse.complete();

        expect(service.dataFor(1)).toBeUndefined();
        expect(service.dataFor(2)).toBeUndefined();
        expect(courseStorageService.getCourse(1)?.exercises).toBeUndefined();
        expect(courseStorageService.getCourse(2)?.exercises).toBeUndefined();

        secondResponse.next(secondData);
        secondResponse.complete();

        expect(service.dataFor(2)).toBe(secondData);
        expect(courseStorageService.getCourse(2)?.exercises).toEqual(secondData.exercises);
        expect(courseStorageService.getCourse(1)?.exercises).toBeUndefined();
    });

    it('should persist quiz websocket updates in both the shared DTO and stored course while the tab is absent', () => {
        const originalQuiz = { id: 7, type: ExerciseType.QUIZ, title: 'Waiting quiz' } as QuizExercise;
        const otherExercise = { id: 8, title: 'Keep me' } as Exercise;
        const initialData = { ...data, exercises: [originalQuiz, otherExercise] };
        fetchSpy.mockReturnValue(of(initialData));
        courseStorageService.setCourses([{ id: 1, exercises: initialData.exercises } as Course]);
        service.load(1).subscribe();

        const startedQuiz = { id: 7, type: ExerciseType.QUIZ, title: 'Started quiz', quizBatches: [{ started: true }] } as QuizExercise;
        websocketService.emit('/topic/courses/1/quizExercises', startedQuiz);

        expect(convertExerciseDatesFromServer).toHaveBeenCalledExactlyOnceWith(startedQuiz);
        expect(service.dataFor(1)?.exercises).toHaveLength(2);
        expect(service.dataFor(1)?.exercises.find((candidate) => candidate.id === 7)).toBe(startedQuiz);
        expect(courseStorageService.getCourse(1)?.exercises).toEqual([otherExercise, startedQuiz]);
    });

    it('should persist team assignments and preserve newer participation snapshots from the stored course', async () => {
        const projectedParticipation = { id: 10 } as StudentParticipation;
        const newerParticipation = { id: 10, submissions: [{ id: 99 }] } as StudentParticipation;
        const teamExercise = { id: 7, studentParticipations: [projectedParticipation] } as Exercise;
        const initialData = { ...data, exercises: [teamExercise] };
        fetchSpy.mockReturnValue(of(initialData));
        courseStorageService.setCourses([{ id: 1, exercises: initialData.exercises } as Course]);
        service.load(1).subscribe();
        // Simulate exercise-details enriching the shared course after the original projection was cached.
        courseStorageService.updateCourse({ id: 1, exercises: [{ ...teamExercise, studentParticipations: [newerParticipation] }] } as Course);
        await Promise.resolve();

        teamUpdates.next({ exerciseId: 7, teamId: 23, studentParticipations: [newerParticipation] });

        const updatedExercise = courseStorageService.getCourse(1)?.exercises?.[0];
        expect(updatedExercise?.studentAssignedTeamId).toBe(23);
        expect(updatedExercise?.studentParticipations).toEqual([newerParticipation]);
        expect(service.dataFor(1)?.exercises[0]).toEqual(updatedExercise);
    });

    it('should stop applying live updates after the per-visit state is cleared', () => {
        const storedCourse = { id: 1, exercises: [exercise] } as Course;
        courseStorageService.setCourses([storedCourse]);
        service.load(1).subscribe();
        service.clear();

        websocketService.emit('/topic/courses/1/quizExercises', { id: 99, type: ExerciseType.QUIZ } as QuizExercise);

        expect(courseStorageService.getCourse(1)?.exercises).toEqual([exercise]);
        expect(service.dataFor(1)).toBeUndefined();
    });

    it('should drop the held data on clear so the next visit refetches', () => {
        service.loadIfNeeded(1).subscribe();
        service.clear();
        service.loadIfNeeded(1).subscribe();
        expect(fetchSpy).toHaveBeenCalledTimes(2);
    });
});
