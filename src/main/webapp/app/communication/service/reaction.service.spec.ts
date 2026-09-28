import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { Reaction, ReactionDTO } from 'app/communication/shared/entities/reaction.model';
import { PostingType } from 'app/communication/shared/entities/posting.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { ReactionService } from 'app/communication/service/reaction.service';
import { metisReactionToCreate, metisReactionUser2 } from 'test/helpers/sample/metis-sample-data';
import { provideHttpClient } from '@angular/common/http';

describe('Reaction Service', () => {
    let service: ReactionService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(ReactionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.useRealTimers();
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should create a Reaction', () => {
            const returnedFromService = { ...metisReactionToCreate };
            const expected = { ...returnedFromService };
            service
                .create(1, new Reaction())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should say which kind of posting the reaction belongs to', () => {
            // The id alone does not identify a posting: posts and answer posts are numbered independently, so the same value regularly
            // denotes one of each and the server cannot tell them apart without being told.
            const post = { id: 42 } as Post;
            const reactionOnPost = { emojiId: 'smiley', post } as Reaction;
            expect(ReactionDTO.fromReaction(reactionOnPost)).toEqual(new ReactionDTO('smiley', 42, PostingType.POST));

            const answerPost = { id: 42 } as AnswerPost;
            const reactionOnAnswer = { emojiId: 'smiley', answerPost } as Reaction;
            expect(ReactionDTO.fromReaction(reactionOnAnswer)).toEqual(new ReactionDTO('smiley', 42, PostingType.ANSWER));
        });

        it('should delete a Reaction', () => {
            service.delete(1, metisReactionUser2).subscribe((resp) => expect(resp.ok).toBe(true));
            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            vi.advanceTimersByTime(0);
        });
    });
});
