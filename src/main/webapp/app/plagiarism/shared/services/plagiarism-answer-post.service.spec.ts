import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';

import { PlagiarismAnswerPostService } from 'app/plagiarism/shared/services/plagiarism-answer-post.service';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PlagiarismAnswerPostCreationDTO } from 'app/plagiarism/shared/entities/PlagiarismAnswerPostCreationDTO';

describe('Plagiarism Answer Post Service', () => {
    let service: PlagiarismAnswerPostService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(PlagiarismAnswerPostService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create plagiarism answer post', fakeAsync(() => {
        const courseId = 7;

        const dto: PlagiarismAnswerPostCreationDTO = {
            postId: 42,
            content: 'hello',
            resolvesPost: true,
            hasForwardedMessages: false,
        };

        const returnedFromService = {
            id: 100,
            content: dto.content,
            resolvesPost: dto.resolvesPost,
        } as AnswerPost;

        service
            .createAnswerPost(courseId, dto)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp).toEqual(returnedFromService);
            });

        const req = httpMock.expectOne({ method: 'POST', url: `api/plagiarism/courses/${courseId}/answer-posts` });
        expect(req.request.body).toEqual(dto);

        req.flush(returnedFromService);
        tick();
    }));

    it('should post minimal payload when optional fields are undefined', fakeAsync(() => {
        const courseId = 1;

        const dto: PlagiarismAnswerPostCreationDTO = {
            postId: 5,
            // intentionally omit optional fields
        };

        const returnedFromService = {
            id: 10,
            content: undefined,
        } as AnswerPost;

        service.createAnswerPost(courseId, dto).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'POST', url: `api/plagiarism/courses/${courseId}/answer-posts` });
        expect(req.request.body).toEqual(dto);

        req.flush(returnedFromService);
        tick();
    }));

    it('should handle error response', fakeAsync(() => {
        const courseId = 2;

        const dto: PlagiarismAnswerPostCreationDTO = {
            postId: 99,
            content: 'x',
        };

        service
            .createAnswerPost(courseId, dto)
            .pipe(take(1))
            .subscribe({
                next: () => {
                    throw new Error('expected an error');
                },
                error: (error) => {
                    expect(error.status).toBe(400);
                },
            });

        const req = httpMock.expectOne({ method: 'POST', url: `api/plagiarism/courses/${courseId}/answer-posts` });
        req.flush('Invalid data', { status: 400, statusText: 'Bad Request' });
        tick();
    }));
});
