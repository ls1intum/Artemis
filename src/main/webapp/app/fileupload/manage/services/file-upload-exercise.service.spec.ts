/**
 * Vitest tests for FileUploadExerciseService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { FileUploadExerciseService } from './file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FileUploadExerciseDto } from 'app/fileupload/shared/entities/file-upload-exercise-dto';

describe('FileUploadExerciseService', () => {
    let service: FileUploadExerciseService;
    let httpMock: HttpTestingController;
    let exerciseService: ExerciseService;

    const resourceUrl = 'api/fileupload/file-upload-exercises';

    const createExercise = (id?: number): FileUploadExercise => {
        const course = new Course();
        course.id = 123;
        const exercise = new FileUploadExercise(course, undefined);
        exercise.id = id;
        exercise.title = 'Test Exercise';
        exercise.filePattern = 'pdf,png';
        exercise.releaseDate = dayjs('2023-01-01');
        exercise.dueDate = dayjs('2023-01-15');
        exercise.assessmentDueDate = dayjs('2023-01-20');
        return exercise;
    };

    const createExerciseDTO = (id: number, title = 'Test Exercise'): FileUploadExerciseDto => ({
        id,
        type: ExerciseType.FILE_UPLOAD,
        title,
        filePattern: 'pdf,png',
        releaseDate: '2023-01-01T00:00:00.000Z',
        dueDate: '2023-01-15T00:00:00.000Z',
        assessmentDueDate: '2023-01-20T00:00:00.000Z',
        teamMode: false,
        gradingInstructionFeedbackUsed: false,
        course: { id: 123 },
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FileUploadExerciseService,
                {
                    provide: ExerciseService,
                    useValue: {
                        processExerciseEntityResponse: vi.fn((res) => res),
                        convertExerciseCategoriesAsStringFromServer: vi.fn((cats) => cats),
                    },
                },
            ],
        });

        service = TestBed.inject(FileUploadExerciseService);
        httpMock = TestBed.inject(HttpTestingController);
        exerciseService = TestBed.inject(ExerciseService);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('create', () => {
        it('should create a new exercise', async () => {
            const exercise = createExercise();
            const expectedExercise = createExerciseDTO(1);

            const resultPromise = new Promise<HttpResponse<FileUploadExercise>>((resolve) => {
                service.create(exercise).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
            expect(req.request.body.courseId).toBe(123);
            expect(req.request.body.course).toBeUndefined();
            req.flush(expectedExercise);

            const response = await resultPromise;
            expect(response.body?.id).toBe(1);
        });

        it('should format file pattern before sending', async () => {
            const exercise = createExercise();
            exercise.filePattern = ' PDF , PNG ';

            service.create(exercise).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
            expect(req.request.body.filePattern).toBe('pdf,png');
            req.flush(createExerciseDTO(1));
        });

        it('should convert dates from client', async () => {
            const exercise = createExercise();
            exercise.releaseDate = dayjs('2023-01-01');

            service.create(exercise).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
            // Dates should be converted
            expect(req.request.body).toBeDefined();
            req.flush(createExerciseDTO(1));
        });
    });

    describe('update', () => {
        it('should update an existing exercise', async () => {
            const exercise = createExercise(456);
            const expectedExercise = createExerciseDTO(456, 'Updated');

            const resultPromise = new Promise<HttpResponse<FileUploadExercise>>((resolve) => {
                service.update(exercise).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/456` });
            expect(req.request.body.id).toBe(456);
            expect(req.request.body.title).toBe('Test Exercise');
            expect(req.request.body.courseId).toBe(123);
            expect(req.request.body.course).toBeUndefined();
            req.flush(expectedExercise);

            const response = await resultPromise;
            expect(response.body?.title).toBe('Updated');
        });

        it('should throw error when exercise has no ID', () => {
            const exercise = createExercise(); // No ID

            expect(() => service.update(exercise)).toThrow('Cannot update exercise without an ID');
        });

        it('should pass request options', async () => {
            const exercise = createExercise(456);
            const options = { notificationText: 'test' };

            service.update(exercise, options).subscribe();

            const req = httpMock.expectOne((r) => r.url === `${resourceUrl}/456`);
            expect(req.request.params.get('notificationText')).toBe('test');
            req.flush(createExerciseDTO(456));
        });

        it('should format file pattern before updating', async () => {
            const exercise = createExercise(456);
            exercise.filePattern = ' PDF , PNG ';

            service.update(exercise).subscribe();

            const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/456` });
            expect(req.request.body.filePattern).toBe('pdf,png');
            req.flush(createExerciseDTO(456));
        });
    });

    describe('find', () => {
        it('should find exercise by ID', async () => {
            const exercise = createExerciseDTO(456);

            const resultPromise = new Promise<HttpResponse<FileUploadExercise>>((resolve) => {
                service.find(456).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/456` });
            req.flush(exercise);

            const response = await resultPromise;
            expect(response.body?.id).toBe(456);
        });

        it('should call processExerciseEntityResponse', async () => {
            const exercise = createExerciseDTO(456);

            service.find(456).subscribe();

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/456` });
            req.flush(exercise);

            expect(exerciseService.processExerciseEntityResponse).toHaveBeenCalled();
        });
    });

    describe('delete', () => {
        it('should delete exercise by ID', async () => {
            const resultPromise = new Promise<HttpResponse<void>>((resolve) => {
                service.delete(456).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/456` });
            req.flush({ status: 200 });

            const response = await resultPromise;
            expect(response.ok).toBe(true);
        });
    });

    describe('reevaluateAndUpdate', () => {
        it('should re-evaluate and update exercise', async () => {
            const exercise = createExercise(456);
            const expectedExercise = createExerciseDTO(456);

            const resultPromise = new Promise<HttpResponse<FileUploadExercise>>((resolve) => {
                service.reevaluateAndUpdate(exercise).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/456/re-evaluate` });
            req.flush(expectedExercise);

            const response = await resultPromise;
            expect(response.body).toBeDefined();
        });

        it('should throw error when exercise has no ID', () => {
            const exercise = createExercise(); // No ID

            expect(() => service.reevaluateAndUpdate(exercise)).toThrow('Cannot re-evaluate exercise without an ID');
        });

        it('should pass request options', async () => {
            const exercise = createExercise(456);
            const options = { deleteFeedback: true };

            service.reevaluateAndUpdate(exercise, options).subscribe();

            const req = httpMock.expectOne((r) => r.url === `${resourceUrl}/456/re-evaluate`);
            expect(req.request.params.get('deleteFeedback')).toBe('true');
            req.flush(createExerciseDTO(456));
        });
    });

    describe('import', () => {
        it('should import exercise', async () => {
            const exercise = createExercise(123);
            const importedExercise = createExerciseDTO(789);

            const resultPromise = new Promise<HttpResponse<FileUploadExercise>>((resolve) => {
                service.import(exercise).subscribe((resp) => resolve(resp));
            });

            const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/import?sourceId=123` });
            req.flush(importedExercise);

            const response = await resultPromise;
            expect(response.body?.id).toBe(789);
        });

        it('should throw error when exercise has no ID', () => {
            const exercise = createExercise(); // No ID

            expect(() => service.import(exercise)).toThrow('Cannot import exercise without an ID');
        });

        it('should convert dates from client when importing', async () => {
            const exercise = createExercise(123);
            exercise.releaseDate = dayjs('2023-06-01');

            service.import(exercise).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/import?sourceId=123` });
            expect(req.request.body).toBeDefined();
            req.flush(createExerciseDTO(789));
        });
    });

    describe('formatFilePattern', () => {
        it('should remove whitespace from file pattern', async () => {
            const exercise = createExercise();
            exercise.filePattern = ' pdf , png , jpg ';

            service.create(exercise).subscribe();

            const req = httpMock.expectOne({ method: 'POST' });
            expect(req.request.body.filePattern).toBe('pdf,png,jpg');
            req.flush(createExerciseDTO(1));
        });

        it('should convert file pattern to lowercase', async () => {
            const exercise = createExercise();
            exercise.filePattern = 'PDF,PNG,JPG';

            service.create(exercise).subscribe();

            const req = httpMock.expectOne({ method: 'POST' });
            expect(req.request.body.filePattern).toBe('pdf,png,jpg');
            req.flush(createExerciseDTO(1));
        });

        it('should handle empty file pattern', async () => {
            const exercise = createExercise();
            exercise.filePattern = '';

            service.create(exercise).subscribe();

            const req = httpMock.expectOne({ method: 'POST' });
            expect(req.request.body.filePattern).toBe('');
            req.flush(createExerciseDTO(1));
        });

        it('should handle undefined file pattern', async () => {
            const exercise = createExercise();
            exercise.filePattern = undefined;

            service.create(exercise).subscribe();

            const req = httpMock.expectOne({ method: 'POST' });
            expect(req.request.body.filePattern).toBeUndefined();
            req.flush(createExerciseDTO(1));
        });
    });
});
