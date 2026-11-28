import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

/**
 * Wire contract of ExerciseGroupService.find: the server JSON of GET exercise-groups/{id} must give the object
 * that the hand-written client gave, which handed the parsed body through unchanged. The fixtures carry every field
 * of ExerciseGroupDTO for a single-group response (nested exam with ExamForExerciseGroupDTO and its
 * CourseForExerciseGroupDTO); the list-only field exercises is never sent there.
 *
 * The hand-written client returned an HttpResponse and the generated one returns the group; the pin reads both.
 */
describe('ExerciseGroupService.find wire contract', () => {
    let service: ExerciseGroupService;
    let httpMock: HttpTestingController;

    const fullGroup = {
        id: 3,
        title: 'Group 1',
        isMandatory: false,
        exam: {
            id: 2,
            examMode: 'TEST_WITH_SUPERVISED_SESSION',
            exampleSolutionPublicationDate: '2026-03-04T10:15:30.123Z',
            course: { id: 1, defaultProgrammingLanguage: 'PYTHON' },
        },
    };

    const bodyOf = (result: unknown) => (result instanceof HttpResponse ? result.body : result);

    const expectGet = (): TestRequest => httpMock.expectOne((req) => req.url.replace(/^\//, '') === 'api/exam/courses/1/exams/2/exercise-groups/3');

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(ExerciseGroupService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should send a plain GET without query parameters or body', () => {
        service.find(1, 2, 3).subscribe();

        const req = expectGet();
        expect(req.request.method).toBe('GET');
        expect(req.request.params.keys()).toEqual([]);
        expect(req.request.body).toBeNull();
        req.flush(fullGroup);
    });

    it('should give the group as the server sent it, with every nested field', () => {
        let result: unknown;
        service.find(1, 2, 3).subscribe((r) => (result = bodyOf(r)));

        expectGet().flush(fullGroup);

        expect(result).toEqual(fullGroup);
    });

    it('should give a group whose exam has no course and no publication date as sent', () => {
        let result: unknown;
        service.find(1, 2, 3).subscribe((r) => (result = bodyOf(r)));

        const slim = { id: 3, title: 'Group 1', isMandatory: true, exam: { id: 2, examMode: 'REAL' } };
        expectGet().flush(slim);

        expect(result).toEqual(slim);
    });

    it('should give a group without an exam as sent', () => {
        let result: unknown;
        service.find(1, 2, 3).subscribe((r) => (result = bodyOf(r)));

        expectGet().flush({ id: 3 });

        expect(result).toEqual({ id: 3 });
    });

    it('should propagate the error of the request', () => {
        let status: number | undefined;
        service.find(1, 2, 3).subscribe({ error: (error) => (status = error.status) });

        expectGet().flush(null, { status: 404, statusText: 'Not Found' });

        expect(status).toBe(404);
    });
});
