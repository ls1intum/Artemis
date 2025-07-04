import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { Post } from 'app/communication/shared/entities/post.model';
import { PostService } from 'app/communication/service/post.service';
import { DisplayPriority, PostSortCriterion, SortDirection } from 'app/communication/metis.util';
import { metisCourse, metisCoursePosts, metisPostExerciseUser1, metisPostToCreateUser1 } from 'test/helpers/sample/metis-sample-data';
import { provideHttpClient } from '@angular/common/http';

describe('Post Service', () => {
    let service: PostService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(PostService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a Post', fakeAsync(() => {
            const returnedFromService = { ...metisPostToCreateUser1 };
            const expected = { ...returnedFromService };
            service
                .create(1, new Post())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a Post', fakeAsync(() => {
            const returnedFromService = { ...metisPostExerciseUser1, content: 'This is another test post' };
            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should pin a Post', fakeAsync(() => {
            const newDisplayPriority = DisplayPriority.PINNED;
            const returnedFromService = { ...metisPostExerciseUser1, displayPriority: newDisplayPriority };
            const expected = { ...returnedFromService };
            service
                .updatePostDisplayPriority(1, metisPostExerciseUser1.id!, newDisplayPriority)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should archive a Post', fakeAsync(() => {
            const newDisplayPriority = DisplayPriority.ARCHIVED;
            const returnedFromService = { ...metisPostExerciseUser1, displayPriority: newDisplayPriority };
            const expected = { ...returnedFromService };
            service
                .updatePostDisplayPriority(1, metisPostExerciseUser1.id!, newDisplayPriority)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should delete a Post', fakeAsync(() => {
            service.delete(1, metisPostExerciseUser1).subscribe((resp) => expect(resp.ok).toBeTruthy());
            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));

        it('should return all student posts for a course', fakeAsync(() => {
            const returnedFromService = metisCoursePosts;
            const expected = metisCoursePosts;
            service
                .getPosts(metisCourse.id!, {})
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should use /posts endpoints if plagiarismCaseId is provided in the postContextFilter', fakeAsync(() => {
            const plagiarismCaseId = 123;
            const expectedUrl = `api/plagiarism/courses/${metisCourse.id}/posts?plagiarismCaseId=${plagiarismCaseId}`;
            const mockResponse: Post[] = [];

            service
                .getPosts(metisCourse.id!, { plagiarismCaseId })
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(mockResponse));
            const req = httpMock.expectOne({ method: 'GET', url: expectedUrl });

            req.flush(mockResponse);
            tick();
        }));

        it('should use /messages endpoints if conversation ids are provided', fakeAsync(() => {
            const conversationIds = [123];
            const expectedUrl = `api/communication/courses/${metisCourse.id}/messages?conversationIds=${conversationIds}`;
            const mockResponse: Post[] = [];

            service
                .getPosts(metisCourse.id!, { conversationIds })
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(mockResponse));
            const req = httpMock.expectOne({ method: 'GET', url: expectedUrl });

            req.flush(mockResponse);
            tick();
        }));

        it('should get source posts by IDs', fakeAsync(() => {
            const postIds = [1, 2, 3];
            const returnedFromService = metisCoursePosts.slice(0, 3);
            const expected = returnedFromService;
            const expectedUrl = `api/communication/courses/${metisCourse.id}/messages-source-posts?postIds=${postIds.join(',')}`;

            service
                .getSourcePostsByIds(metisCourse.id!, postIds)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toEqual(expected));

            const req = httpMock.expectOne(
                (request) =>
                    request.method === 'GET' && request.url === `api/communication/courses/${metisCourse.id}/messages-source-posts` && request.urlWithParams === expectedUrl,
            );

            expect(req.request.urlWithParams).toBe(expectedUrl);
            req.flush(returnedFromService);
            tick();
        }));

        it('should send postSortCriterion and sortingOrder in params if set', fakeAsync(() => {
            const postSortCriterion = PostSortCriterion.CREATION_DATE;
            const sortingOrder = SortDirection.DESCENDING;
            const expectedUrl = `api/plagiarism/courses/${metisCourse.id}/posts?postSortCriterion=${postSortCriterion}&sortingOrder=${sortingOrder}&plagiarismCaseId=123`;

            service.getPosts(metisCourse.id!, { postSortCriterion, sortingOrder, plagiarismCaseId: 123 }).pipe(take(1)).subscribe();

            const req = httpMock.expectOne(
                (request) => request.method === 'GET' && request.url === `api/plagiarism/courses/${metisCourse.id}/posts` && request.urlWithParams === expectedUrl,
            );

            expect(req.request.urlWithParams).toBe(expectedUrl);
            req.flush([]);
            tick();
        }));

        it('should send searchText if set', fakeAsync(() => {
            const searchText = 'foo';
            const plagiarismCaseId = 123;

            service.getPosts(metisCourse.id!, { searchText, plagiarismCaseId }).pipe(take(1)).subscribe();

            const req = httpMock.expectOne((request) => {
                const url = request.url;
                const params = new URLSearchParams(request.urlWithParams.split('?')[1]);
                return (
                    request.method === 'GET' &&
                    url === `api/plagiarism/courses/${metisCourse.id}/posts` &&
                    params.get('searchText') === searchText &&
                    params.get('plagiarismCaseId') === `${plagiarismCaseId}`
                );
            });

            req.flush([]);
            tick();
        }));

        it('should send authorIds if set', fakeAsync(() => {
            const authorIds = [42];
            service.getPosts(metisCourse.id!, { authorIds }).pipe(take(1)).subscribe();

            const req = httpMock.expectOne((r) => r.urlWithParams.includes(`authorIds=${authorIds}`));
            req.flush([]);
            tick();
        }));

        it('should send filterToCourseWide if set', fakeAsync(() => {
            const filterToCourseWide = true;
            service.getPosts(metisCourse.id!, { filterToCourseWide }).pipe(take(1)).subscribe();

            const req = httpMock.expectOne((r) => r.urlWithParams.includes(`filterToCourseWide=true`));
            req.flush([]);
            tick();
        }));

        it('should send filterToUnresolved if set', fakeAsync(() => {
            const filterToUnresolved = true;
            service.getPosts(metisCourse.id!, { filterToUnresolved }).pipe(take(1)).subscribe();

            const req = httpMock.expectOne((r) => r.urlWithParams.includes(`filterToUnresolved=true`));
            req.flush([]);
            tick();
        }));

        it('should send filterToAnsweredOrReacted if set', fakeAsync(() => {
            const filterToAnsweredOrReacted = true;
            service.getPosts(metisCourse.id!, { filterToAnsweredOrReacted }).pipe(take(1)).subscribe();

            const req = httpMock.expectOne((r) => r.urlWithParams.includes(`filterToAnsweredOrReacted=true`));
            req.flush([]);
            tick();
        }));

        it('should send pagingEnabled, page and size if pagingEnabled is true', fakeAsync(() => {
            const pagingEnabled = true;
            const page = 2;
            const pageSize = 25;
            service.getPosts(metisCourse.id!, { pagingEnabled, page, pageSize }).pipe(take(1)).subscribe();

            const req = httpMock.expectOne((r) => r.urlWithParams.includes(`pagingEnabled=true`) && r.urlWithParams.includes(`page=2`) && r.urlWithParams.includes(`size=25`));
            req.flush([]);
            tick();
        }));

        it('should send pinnedOnly if set', fakeAsync(() => {
            const pinnedOnly = true;
            service.getPosts(metisCourse.id!, { pinnedOnly }).pipe(take(1)).subscribe();

            const req = httpMock.expectOne((r) => r.urlWithParams.includes(`pinnedOnly=true`));
            req.flush([]);
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
