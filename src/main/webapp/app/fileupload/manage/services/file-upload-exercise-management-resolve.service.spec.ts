/**
 * Vitest tests for FileUploadExerciseManagementResolve.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { firstValueFrom, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { FileUploadExerciseManagementResolve } from './file-upload-exercise-management-resolve.service';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

describe('FileUploadExerciseManagementResolve', () => {
    setupTestBed({ zoneless: true });

    let service: FileUploadExerciseManagementResolve;
    let fileUploadExerciseService: FileUploadExerciseService;
    let courseManagementService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;

    const createCourse = (id = 123): Course => {
        const course = new Course();
        course.id = id;
        return course;
    };

    const createExercise = (course?: Course, exerciseGroup?: ExerciseGroup): FileUploadExercise => {
        const exercise = new FileUploadExercise(course, exerciseGroup);
        exercise.id = 456;
        exercise.title = 'Test Exercise';
        exercise.filePattern = 'pdf,png';
        return exercise;
    };

    const createExerciseGroup = (id = 1): ExerciseGroup => {
        const group = new ExerciseGroup();
        group.id = id;
        return group;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FileUploadExerciseManagementResolve,
                {
                    provide: FileUploadExerciseService,
                    useValue: {
                        find: vi.fn(),
                    },
                },
                {
                    provide: CourseManagementService,
                    useValue: {
                        find: vi.fn(),
                    },
                },
                {
                    provide: ExerciseGroupService,
                    useValue: {
                        find: vi.fn(),
                    },
                },
            ],
        });

        service = TestBed.inject(FileUploadExerciseManagementResolve);
        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        courseManagementService = TestBed.inject(CourseManagementService);
        exerciseGroupService = TestBed.inject(ExerciseGroupService);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    describe('resolve with exerciseId', () => {
        it('should resolve existing exercise by ID', async () => {
            const exercise = createExercise(createCourse());
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            const snapshot = { params: { exerciseId: 456 } } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(fileUploadExerciseService.find).toHaveBeenCalledWith(456);
            expect(result).toEqual(exercise);
        });

        it('should not call course or exercise group service when exerciseId is present', async () => {
            const exercise = createExercise(createCourse());
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            const snapshot = { params: { exerciseId: 456 } } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.find).not.toHaveBeenCalled();
            expect(exerciseGroupService.find).not.toHaveBeenCalled();
        });
    });

    describe('resolve with courseId only', () => {
        it('should create new exercise for course', async () => {
            const course = createCourse(789);
            vi.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = { params: { courseId: 789 } } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.find).toHaveBeenCalledWith(789);
            expect(result.course).toEqual(course);
            expect(result.filePattern).toBe('pdf, png');
        });

        it('should not call fileUploadExerciseService when only courseId is present', async () => {
            const course = createCourse();
            vi.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = { params: { courseId: 123 } } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(fileUploadExerciseService.find).not.toHaveBeenCalled();
        });
    });

    describe('resolve with courseId and examId and exerciseGroupId', () => {
        it('should create new exercise for exercise group', async () => {
            const exerciseGroup = createExerciseGroup(5);
            vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));

            const snapshot = {
                params: { courseId: 123, examId: 4, exerciseGroupId: 5 },
            } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(exerciseGroupService.find).toHaveBeenCalledWith(123, 4, 5);
            expect(result.exerciseGroup).toEqual(exerciseGroup);
            expect(result.filePattern).toBe('pdf, png');
        });

        it('should not call courseManagementService when exerciseGroupId is present', async () => {
            const exerciseGroup = createExerciseGroup();
            vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));

            const snapshot = {
                params: { courseId: 123, examId: 4, exerciseGroupId: 5 },
            } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.find).not.toHaveBeenCalled();
        });
    });

    describe('resolve with courseId and examId only', () => {
        it('should create exercise for course when examId is present but not exerciseGroupId', async () => {
            const course = createCourse(456);
            vi.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = {
                params: { courseId: 456, examId: 4 },
            } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.find).toHaveBeenCalledWith(456);
            expect(result.course).toEqual(course);
        });
    });

    describe('resolve with empty params', () => {
        it('should return empty exercise when no params provided', async () => {
            const snapshot = { params: {} } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(result).toBeInstanceOf(FileUploadExercise);
            expect(result.course).toBeUndefined();
            expect(result.exerciseGroup).toBeUndefined();
        });

        it('should not call any service when params are empty', async () => {
            const snapshot = { params: {} } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(fileUploadExerciseService.find).not.toHaveBeenCalled();
            expect(courseManagementService.find).not.toHaveBeenCalled();
            expect(exerciseGroupService.find).not.toHaveBeenCalled();
        });
    });

    describe('default file pattern', () => {
        it('should set default file pattern for course exercise', async () => {
            const course = createCourse();
            vi.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = { params: { courseId: 123 } } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(result.filePattern).toBe('pdf, png');
        });

        it('should set default file pattern for exam exercise', async () => {
            const exerciseGroup = createExerciseGroup();
            vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));

            const snapshot = {
                params: { courseId: 123, examId: 4, exerciseGroupId: 5 },
            } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(result.filePattern).toBe('pdf, png');
        });
    });

    describe('service calls verification', () => {
        it('should call find with correct exercise ID', async () => {
            const exercise = createExercise();
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            const snapshot = { params: { exerciseId: 999 } } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(fileUploadExerciseService.find).toHaveBeenCalledWith(999);
        });

        it('should call courseManagementService.find with correct course ID', async () => {
            const course = createCourse();
            vi.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = { params: { courseId: 888 } } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.find).toHaveBeenCalledWith(888);
        });

        it('should call exerciseGroupService.find with correct IDs', async () => {
            const exerciseGroup = createExerciseGroup();
            vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));

            const snapshot = {
                params: { courseId: 111, examId: 222, exerciseGroupId: 333 },
            } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(exerciseGroupService.find).toHaveBeenCalledWith(111, 222, 333);
        });
    });
});
