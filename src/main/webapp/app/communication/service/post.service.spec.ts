import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { Post } from 'app/communication/shared/entities/post.model';
import { PostService } from 'app/communication/service/post.service';
import { DisplayPriority } from 'app/communication/metis.util';
import { metisCourse, metisCoursePosts, metisPostExerciseUser1, metisPostToCreateUser1 } from 'test/helpers/sample/metis-sample-data';
import { provideHttpClient } from '@angular/common/http';

describe('Post Service', () => {
    setupTestBed({ zoneless: true });

    let service: PostService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(PostService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.useRealTimers();
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should create a Post', () => {
            const returnedFromService = { ...metisPostToCreateUser1 };
            const expected = { ...returnedFromService };
            service
                .create(1, new Post())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should update a Post', () => {
            const returnedFromService = { ...metisPostExerciseUser1, content: 'This is another test post' };
            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should pin a Post', () => {
            const newDisplayPriority = DisplayPriority.PINNED;
            const returnedFromService = { ...metisPostExerciseUser1, displayPriority: newDisplayPriority };
            const expected = { ...returnedFromService };
            service
                .updatePostDisplayPriority(1, metisPostExerciseUser1.id!, newDisplayPriority)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should archive a Post', () => {
            const newDisplayPriority = DisplayPriority.ARCHIVED;
            const returnedFromService = { ...metisPostExerciseUser1, displayPriority: newDisplayPriority };
            const expected = { ...returnedFromService };
            service
                .updatePostDisplayPriority(1, metisPostExerciseUser1.id!, newDisplayPriority)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should delete a Post', () => {
            service.delete(1, metisPostExerciseUser1).subscribe((resp) => expect(resp.ok).toBeTruthy());
            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            vi.advanceTimersByTime(0);
        });

        it('should return all student posts for a course', () => {
            const returnedFromService = metisCoursePosts;
            const expected = metisCoursePosts;
            service
                .getPosts(metisCourse.id!, {})
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should use /posts endpoints if plagiarismCaseId is provided in the postContextFilter', () => {
            const plagiarismCaseId = 123;
            const expectedUrl = `api/plagiarism/courses/${metisCourse.id}/posts?plagiarismCaseId=${plagiarismCaseId}`;
            const mockResponse: Post[] = [];

            service
                .getPosts(metisCourse.id!, { plagiarismCaseId })
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(mockResponse));
            const req = httpMock.expectOne({ method: 'GET', url: expectedUrl });

            req.flush(mockResponse);
            vi.advanceTimersByTime(0);
        });

        it('should use /messages endpoints if conversation ids are provided', () => {
            const conversationIds = [123];
            const expectedUrl = `api/communication/courses/${metisCourse.id}/messages?conversationIds=${conversationIds}`;
            const mockResponse: Post[] = [];

            service
                .getPosts(metisCourse.id!, { conversationIds })
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(mockResponse));
            const req = httpMock.expectOne({ method: 'GET', url: expectedUrl });

            req.flush(mockResponse);
            vi.advanceTimersByTime(0);
        });

        it('should get source posts by IDs', () => {
            const postIds = [1, 2, 3];
            const returnedFromService = metisCoursePosts.slice(0, 3);
            const expected = returnedFromService;

            service
                .getSourcePostsByIds(metisCourse.id!, postIds)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toEqual(expected));

            const req = httpMock.expectOne({
                method: 'GET',
                url: `api/communication/courses/${metisCourse.id}/messages-source-posts?postIds=${postIds.join(',')}`,
            });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });
    });
});
