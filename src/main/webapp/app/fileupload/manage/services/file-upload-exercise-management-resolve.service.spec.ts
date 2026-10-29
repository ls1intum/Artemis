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

import { FileUploadExerciseManagementResolve } from './file-upload-exercise-management-resolve.service';
import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

describe('FileUploadExerciseManagementResolve', () => {
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
                        findForExam: vi.fn(),
                    },
                },
                {
                    provide: CourseManagementService,
                    useValue: {
                        findForManagement: vi.fn(),
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

            const snapshot = { params: { exerciseId: 456 }, pathFromRoot: [{ params: {} }] } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(fileUploadExerciseService.find).toHaveBeenCalledWith(456);
            expect(result).toEqual(exercise);
        });

        it('should not call course or exercise group service when exerciseId is present', async () => {
            const exercise = createExercise(createCourse());
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            const snapshot = { params: { exerciseId: 456 }, pathFromRoot: [{ params: {} }] } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.findForManagement).not.toHaveBeenCalled();
            expect(exerciseGroupService.find).not.toHaveBeenCalled();
        });
    });

    describe('resolve with courseId only', () => {
        it('should create new exercise for course', async () => {
            const course = createCourse(789);
            vi.spyOn(courseManagementService, 'findForManagement').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = { params: { courseId: 789 }, pathFromRoot: [{ params: {} }] } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.findForManagement).toHaveBeenCalledWith(789);
            expect(result.course).toEqual(course);
            expect(result.filePattern).toBe('pdf, png');
        });

        it('should not call fileUploadExerciseService when only courseId is present', async () => {
            const course = createCourse();
            vi.spyOn(courseManagementService, 'findForManagement').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = { params: { courseId: 123 }, pathFromRoot: [{ params: {} }] } as unknown as ActivatedRouteSnapshot;

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
                pathFromRoot: [{ params: {} }],
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
                pathFromRoot: [{ params: {} }],
            } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.findForManagement).not.toHaveBeenCalled();
        });
    });

    describe('resolve with courseId and examId only', () => {
        it('should create exercise for course when examId is present but not exerciseGroupId', async () => {
            const course = createCourse(456);
            vi.spyOn(courseManagementService, 'findForManagement').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = {
                params: { courseId: 456, examId: 4 },
                pathFromRoot: [{ params: {} }],
            } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.findForManagement).toHaveBeenCalledWith(456);
            expect(result.course).toEqual(course);
        });
    });

    describe('resolve with empty params', () => {
        it('should return empty exercise when no params provided', async () => {
            const snapshot = { params: {}, pathFromRoot: [{ params: {} }] } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(result).toBeInstanceOf(FileUploadExercise);
            expect(result.course).toBeUndefined();
            expect(result.exerciseGroup).toBeUndefined();
        });

        it('should not call any service when params are empty', async () => {
            const snapshot = { params: {}, pathFromRoot: [{ params: {} }] } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(fileUploadExerciseService.find).not.toHaveBeenCalled();
            expect(courseManagementService.findForManagement).not.toHaveBeenCalled();
            expect(exerciseGroupService.find).not.toHaveBeenCalled();
        });
    });

    describe('default file pattern', () => {
        it('should set default file pattern for course exercise', async () => {
            const course = createCourse();
            vi.spyOn(courseManagementService, 'findForManagement').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = { params: { courseId: 123 }, pathFromRoot: [{ params: {} }] } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(result.filePattern).toBe('pdf, png');
        });

        it('should set default file pattern for exam exercise', async () => {
            const exerciseGroup = createExerciseGroup();
            vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));

            const snapshot = {
                params: { courseId: 123, examId: 4, exerciseGroupId: 5 },
                pathFromRoot: [{ params: {} }],
            } as unknown as ActivatedRouteSnapshot;

            const result = await firstValueFrom(service.resolve(snapshot));

            expect(result.filePattern).toBe('pdf, png');
        });
    });

    describe('service calls verification', () => {
        it('should call find with correct exercise ID', async () => {
            const exercise = createExercise();
            vi.spyOn(fileUploadExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            const snapshot = { params: { exerciseId: 999 }, pathFromRoot: [{ params: {} }] } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(fileUploadExerciseService.find).toHaveBeenCalledWith(999);
        });

        it('should call courseManagementService.findForManagement with correct course ID', async () => {
            const course = createCourse();
            vi.spyOn(courseManagementService, 'findForManagement').mockReturnValue(of(new HttpResponse({ body: course })));

            const snapshot = { params: { courseId: 888 }, pathFromRoot: [{ params: {} }] } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(courseManagementService.findForManagement).toHaveBeenCalledWith(888);
        });

        it('should call exerciseGroupService.find with correct IDs', async () => {
            const exerciseGroup = createExerciseGroup();
            vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));

            const snapshot = {
                params: { courseId: 111, examId: 222, exerciseGroupId: 333 },
                pathFromRoot: [{ params: {} }],
            } as unknown as ActivatedRouteSnapshot;

            await firstValueFrom(service.resolve(snapshot));

            expect(exerciseGroupService.find).toHaveBeenCalledWith(111, 222, 333);
        });
    });
    it('should resolve exam details from an ancestor with the same options', () => {
        const detailService = TestBed.inject(FileUploadExerciseService);
        const exercise = new FileUploadExercise(undefined, undefined);
        const examFind = vi.spyOn(detailService, 'findForExam').mockReturnValue(of(new HttpResponse({ body: exercise })));
        vi.spyOn(detailService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
        const snapshot = new ActivatedRouteSnapshot();
        snapshot.params = { exerciseId: 123 };
        const ancestor = new ActivatedRouteSnapshot();
        ancestor.params = { examId: 7 };
        vi.spyOn(snapshot, 'pathFromRoot', 'get').mockReturnValue([ancestor, snapshot]);
        service.resolve(snapshot).subscribe((result) => expect(result).toBe(exercise));
        expect(examFind).toHaveBeenCalledExactlyOnceWith(123);
        expect(detailService.find).not.toHaveBeenCalled();
    });

    it.each([
        ['true', false],
        ['false', true],
        [undefined, true],
    ])('uses import source flag %s before target exam context %s after reload', (sourceFlag, targetIsExam) => {
        const detailService = TestBed.inject(FileUploadExerciseService);
        const exercise = new FileUploadExercise(undefined, undefined);
        const courseFind = vi.spyOn(detailService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
        const examFind = vi.spyOn(detailService, 'findForExam').mockReturnValue(of(new HttpResponse({ body: exercise })));
        const snapshot = new ActivatedRouteSnapshot();
        snapshot.params = { exerciseId: 123, ...(targetIsExam ? { examId: 7 } : {}) };
        snapshot.queryParams = sourceFlag === undefined ? {} : { sourceIsExamExercise: sourceFlag };
        vi.spyOn(snapshot, 'routeConfig', 'get').mockReturnValue({ path: 'exercises/:exerciseId/import' });
        vi.spyOn(snapshot, 'pathFromRoot', 'get').mockReturnValue([snapshot]);
        service.resolve(snapshot).subscribe((result) => expect(result).toBe(exercise));
        expect(sourceFlag === 'true' ? examFind : courseFind).toHaveBeenCalledExactlyOnceWith(123);
        expect(sourceFlag === 'true' ? courseFind : examFind).not.toHaveBeenCalled();
    });
});
