import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SavedPostService } from 'app/communication/saved-post.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { Posting, PostingType, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import Dayjs from 'dayjs/esm';

describe('SavedPostService', () => {
    let service: SavedPostService;
    let httpMock: HttpTestingController;
    const resourceUrl = 'api/communication/saved-posts/';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [SavedPostService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(SavedPostService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should create', () => {
        expect(service).toBeTruthy();
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Save post', () => {
        it('should save a post if given a Post model', () => {
            const post = new Post();
            post.id = 1;

            service.savePost(post).subscribe();

            const req = httpMock.expectOne(`${resourceUrl}1/${PostingType.POST}`);
            expect(req.request.method).toBe('POST');
        });

        it('should save an answer if given AnswerPost model', () => {
            const answerPost = new AnswerPost();
            answerPost.id = 1;

            service.savePost(answerPost).subscribe();

            const req = httpMock.expectOne(`${resourceUrl}1/${PostingType.ANSWER}`);
            expect(req.request.method).toBe('POST');
        });
    });

    describe('Remove saved post', () => {
        it('should remove a post when provided with Post model', () => {
            const post = new Post();
            post.id = 1;

            service.removeSavedPost(post).subscribe();

            const req = httpMock.expectOne(`${resourceUrl}1/${PostingType.POST}`);
            expect(req.request.method).toBe('DELETE');
        });

        it('should remove a answer when provided with AnswerPost model', () => {
            const answerPost = new AnswerPost();
            answerPost.id = 1;

            service.removeSavedPost(answerPost).subscribe();

            const req = httpMock.expectOne(`${resourceUrl}1/${PostingType.ANSWER}`);
            expect(req.request.method).toBe('DELETE');
        });
    });

    describe('Update saved post', () => {
        it('should update post status to PROGRESS if provided with Post model and PROGRESS', () => {
            const post = new Post();
            post.id = 1;

            service.changeSavedPostStatus(post, SavedPostStatus.PROGRESS).subscribe();

            const req = httpMock.expectOne(`${resourceUrl}1/${PostingType.POST}?status=${SavedPostStatus.PROGRESS}`);
            expect(req.request.method).toBe('PUT');
        });

        it('should update answer post status to COMPLETED if provided with AnswerPost model and COMPLETED', () => {
            const answerPost = new AnswerPost();
            answerPost.id = 1;

            service.changeSavedPostStatus(answerPost, SavedPostStatus.COMPLETED).subscribe();

            const req = httpMock.expectOne(`${resourceUrl}1/${PostingType.ANSWER}?status=${SavedPostStatus.COMPLETED}`);
            expect(req.request.method).toBe('PUT');
        });
    });

    describe('Fetch saved posts', () => {
        it('should fetch saved posts and convert dates accordingly', fakeAsync(() => {
            const courseId = 1;
            const status = SavedPostStatus.PROGRESS;
            const mockPosts = [
                {
                    id: 1,
                    creationDate: '2024-03-20T10:00:00Z',
                    updatedDate: '2024-03-20T11:00:00Z',
                    conversation: { id: 1, type: ConversationType.CHANNEL },
                },
            ];

            const fetchPost = service.fetchSavedPosts(courseId, status).subscribe((response) => {
                expect(response.body?.[0].conversation?.type).toBe(ConversationType.CHANNEL);
                expect(response.body?.[0].creationDate).toBeInstanceOf(Dayjs);
                expect(response.body?.[0].updatedDate).toBeInstanceOf(Dayjs);
            });

            const req = httpMock.expectOne(`${resourceUrl}1/${SavedPostStatus.PROGRESS}`);
            expect(req.request.method).toBe('GET');
            req.flush(mockPosts, { status: 200, statusText: 'OK' });
            tick();
            fetchPost.unsubscribe();
        }));
    });

    describe('Conversions of posts', () => {
        it('should convert posting to Post type', () => {
            const posting: Posting = {
                id: 1,
                postingType: PostingType.POST,
            } as Posting;

            const result = service.convertPostingToCorrespondingType(posting);
            expect(result).toBeInstanceOf(Post);
        });

        it('should convert posting to AnswerPost type', () => {
            const posting: Posting = {
                id: 1,
                postingType: PostingType.ANSWER,
            } as Posting;

            const result = service.convertPostingToCorrespondingType(posting);
            expect(result).toBeInstanceOf(AnswerPost);
        });
    });
});
