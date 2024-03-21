import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post.service';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { metisCourse, metisCoursePosts, metisPostExerciseUser1, metisPostToCreateUser1, metisTags } from '../../helpers/sample/metis-sample-data';

describe('Post Service', () => {
    let service: PostService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
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

        it('should return all similar posts in a course', fakeAsync(() => {
            const returnedFromService = metisCoursePosts.slice(0, 4);
            const expected = returnedFromService;
            service
                .computeSimilarityScoresWithCoursePosts(metisPostExerciseUser1, metisCourse.id!)
                .pipe(take(2))
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

        it('should return all post tags for a course', fakeAsync(() => {
            const returnedFromService = metisTags;
            const expected = returnedFromService;
            service
                .getAllPostTagsByCourseId(metisCourse.id!)
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should use /posts endpoints if plagiarismCaseId is provided in the postContextFilter', fakeAsync(() => {
            const plagiarismCaseId = 123;
            const expectedUrl = `${service.resourceUrl}${metisCourse.id}/posts?plagiarismCaseId=${plagiarismCaseId}`;
            const mockResponse: Post[] = [];

            service
                .getPosts(metisCourse.id!, { plagiarismCaseId })
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(mockResponse));
            const req = httpMock.expectOne({ method: 'GET', url: expectedUrl });

            req.flush(mockResponse);
            tick();
        }));

        it('should use /messages endpoints if course-wide channel ids are provided', fakeAsync(() => {
            const courseWideChannelIds = [123];
            const expectedUrl = `${service.resourceUrl}${metisCourse.id}/messages?courseWideChannelIds=${courseWideChannelIds}`;
            const mockResponse: Post[] = [];

            service
                .getPosts(metisCourse.id!, { courseWideChannelIds })
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(mockResponse));
            const req = httpMock.expectOne({ method: 'GET', url: expectedUrl });

            req.flush(mockResponse);
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
