import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { FileUploadExercisePagingService } from './file-upload-exercise-paging.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ExerciseMode, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';

describe('FileUploadExercisePagingService', () => {
    let service: FileUploadExercisePagingService;
    let httpMock: HttpTestingController;
    const searchParams = (url: string) => new URL(url, 'http://localhost').searchParams;
    const path = (url: string) => new URL(url, 'http://localhost').pathname;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), FileUploadExercisePagingService] });
        service = TestBed.inject(FileUploadExercisePagingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('adapts paged response DTOs to file upload exercise models', async () => {
        const resultPromise = firstValueFrom(
            service.search(
                { page: 0, pageSize: 10, sortingOrder: SortingOrder.ASCENDING, sortedColumn: 'id', searchTerm: 'upload' },
                { isCourseFilter: false, isExamFilter: true },
            ),
        );

        const request = httpMock.expectOne((req) => req.method === 'GET' && path(req.url) === '/api/fileupload/file-upload-exercises');
        const params = searchParams(request.request.url);
        expect(Object.fromEntries(params.entries())).toEqual({
            page: '0',
            pageSize: '10',
            sortingOrder: 'ASCENDING',
            sortedColumn: 'id',
            searchTerm: 'upload',
            isCourseFilter: 'false',
            isExamFilter: 'true',
        });
        request.flush({
            resultsOnPage: [
                {
                    id: 42,
                    type: 'file-upload',
                    title: 'Upload',
                    mode: 'TEAM',
                    teamMode: true,
                    gradingInstructionFeedbackUsed: false,
                    course: { id: 7, title: 'Course' },
                },
            ],
            numberOfPages: 3,
        });

        const result = await resultPromise;
        expect(result.numberOfPages).toBe(3);
        expect(result.resultsOnPage[0]).toBeInstanceOf(FileUploadExercise);
        expect(result.resultsOnPage[0].teamMode).toBe(true);
        expect(result.resultsOnPage[0].mode).toBe(ExerciseMode.TEAM);
        expect(result.resultsOnPage[0].type).toBe(ExerciseType.FILE_UPLOAD);
        expect(result.resultsOnPage[0].course?.title).toBe('Course');
    });

    it('normalizes an omitted empty result page to an empty array', async () => {
        const resultPromise = firstValueFrom(
            service.search(
                { page: 0, pageSize: 10, sortingOrder: SortingOrder.ASCENDING, sortedColumn: 'id', searchTerm: 'missing' },
                { isCourseFilter: true, isExamFilter: true },
            ),
        );

        const request = httpMock.expectOne((req) => path(req.url) === '/api/fileupload/file-upload-exercises');
        request.flush({ numberOfPages: 0 });

        await expect(resultPromise).resolves.toEqual({ resultsOnPage: [], numberOfPages: 0 });
    });
});
