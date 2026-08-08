import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
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

describe('CourseOverviewExercisesService', () => {
    let service: CourseOverviewExercisesService;
    let courseManagementService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    let fetchSpy: ReturnType<typeof vi.spyOn>;

    const exercise = { id: 42 } as Exercise;
    const data = { exercises: [exercise] } as CourseExercisesForOverviewDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }, provideHttpClient(), MockProvider(AlertService)],
        });
        service = TestBed.inject(CourseOverviewExercisesService);
        service.clear();
        courseManagementService = TestBed.inject(CourseManagementService);
        courseStorageService = TestBed.inject(CourseStorageService);
        fetchSpy = vi.spyOn(courseManagementService, 'findCourseExercisesForOverview').mockReturnValue(of(data));
    });

    afterEach(() => {
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

    it('should not publish anything when the course is not stored yet', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(undefined);
        const updateSpy = vi.spyOn(courseStorageService, 'updateCourse');

        service.load(1).subscribe();

        expect(updateSpy).not.toHaveBeenCalled();
    });

    it('should drop the held data on clear so the next visit refetches', () => {
        service.loadIfNeeded(1).subscribe();
        service.clear();
        service.loadIfNeeded(1).subscribe();
        expect(fetchSpy).toHaveBeenCalledTimes(2);
    });
});
