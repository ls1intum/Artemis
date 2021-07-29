import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { PostService } from 'app/shared/metis/post.service';

const expect = chai.expect;

describe('Post Service', () => {
    let injector: TestBed;
    let service: PostService;
    let httpMock: HttpTestingController;
    let elemDefault: Post;
    let elem2: Post;
    let courseDefault: Course;
    let exerciseDefault: TextExercise;
    let lectureDefault: Lecture;
    let posts: Post[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(PostService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new Post();
        elemDefault.id = 0;
        elemDefault.creationDate = undefined;
        elemDefault.content = 'This is a test post';
        elemDefault.title = 'title';
        elemDefault.tags = ['tag1', 'tag2'];

        elem2 = new Post();
        elem2.id = 1;
        elem2.creationDate = undefined;
        elem2.content = 'This is a test post';
        elem2.title = 'title';
        elem2.tags = ['tag3', 'tag4'];

        courseDefault = new Course();
        courseDefault.id = 1;

        exerciseDefault = new TextExercise(courseDefault, undefined);
        exerciseDefault.id = 1;
        exerciseDefault.posts = [elemDefault];

        lectureDefault = new Lecture();
        lectureDefault.id = 1;
        lectureDefault.posts = [elem2];

        courseDefault.exercises = [exerciseDefault];
        courseDefault.lectures = [lectureDefault];

        posts = [elemDefault, elem2];
    });

    describe('Service methods', () => {
        it('should create a Post', fakeAsync(() => {
            const returnedFromService = { ...elemDefault, id: 0 };
            const expected = { ...returnedFromService };
            service
                .create(1, new Post())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a Post', fakeAsync(() => {
            const returnedFromService = { ...elemDefault, content: 'This is another test post' };

            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should delete a Post', fakeAsync(() => {
            service.delete(1, elemDefault).subscribe((resp) => expect(resp.ok).to.be.true);

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));

        it('should update the votes of a Post', fakeAsync(() => {
            const returnedFromService = { ...elemDefault, votes: 42 };

            const expected = { ...returnedFromService };
            service
                .updateVotes(1, expected.id!, 0)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all student posts for a course', fakeAsync(() => {
            const returnedFromService = [...posts];

            const expected = [...posts];
            service
                .getAllPostsByCourseId(courseDefault.id!)
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all student posts for a lecture', fakeAsync(() => {
            const returnedFromService = [elem2];

            const expected = [elem2];
            service
                .getAllPostsByLectureId(courseDefault.id!, lectureDefault.id!)
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all student posts for an exercise', fakeAsync(() => {
            const returnedFromService = [elemDefault];

            const expected = [elemDefault];
            service
                .getAllPostsByExerciseId(courseDefault.id!, exerciseDefault.id!)
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all post tags for a course', fakeAsync(() => {
            const returnedFromService = ['tag1', 'tag2', 'tag3', 'tag4'];

            const expected = ['tag1', 'tag2', 'tag3', 'tag4'];
            service
                .getAllPostTagsByCourseId(courseDefault.id!)
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
