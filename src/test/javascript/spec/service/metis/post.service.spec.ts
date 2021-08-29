import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { PostService } from 'app/shared/metis/post.service';
import { CourseWideContext, DisplayPriority } from 'app/shared/metis/metis.util';

const expect = chai.expect;

describe('Post Service', () => {
    let injector: TestBed;
    let service: PostService;
    let httpMock: HttpTestingController;
    let post1: Post;
    let post2: Post;
    let post3: Post;
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

        post1 = new Post();
        post1.id = 0;
        post1.creationDate = undefined;
        post1.content = 'This is a test post';
        post1.title = 'title';
        post1.tags = ['tag1', 'tag2'];

        post2 = new Post();
        post2.id = 1;
        post2.creationDate = undefined;
        post2.content = 'This is a test post';
        post2.title = 'title';
        post2.tags = ['tag3', 'tag4'];

        post3 = new Post();
        post3.id = 1;
        post3.creationDate = undefined;
        post3.content = 'This is a test post';
        post3.title = 'title';
        post3.courseWideContext = CourseWideContext.RANDOM;

        courseDefault = new Course();
        courseDefault.id = 1;

        exerciseDefault = new TextExercise(courseDefault, undefined);
        exerciseDefault.id = 1;
        exerciseDefault.posts = [post1];

        lectureDefault = new Lecture();
        lectureDefault.id = 1;
        lectureDefault.posts = [post2];

        courseDefault.exercises = [exerciseDefault];
        courseDefault.lectures = [lectureDefault];

        posts = [post1, post2];
    });

    describe('Service methods', () => {
        it('should create a Post', fakeAsync(() => {
            const returnedFromService = { ...post1, id: 0 };
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
            const returnedFromService = { ...post1, content: 'This is another test post' };

            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should pin a Post', fakeAsync(() => {
            const newDisplayPriority = DisplayPriority.PINNED;
            const returnedFromService = { ...post1, displayPriority: newDisplayPriority };

            const expected = { ...returnedFromService };
            service
                .updatePostDisplayPriority(1, post1.id!, newDisplayPriority)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should archive a Post', fakeAsync(() => {
            const newDisplayPriority = DisplayPriority.ARCHIVED;
            const returnedFromService = { ...post1, displayPriority: newDisplayPriority };

            const expected = { ...returnedFromService };
            service
                .updatePostDisplayPriority(1, post1.id!, newDisplayPriority)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should delete a Post', fakeAsync(() => {
            service.delete(1, post1).subscribe((resp) => expect(resp.ok).to.be.true);

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));

        it('should return all student posts for a course', fakeAsync(() => {
            const returnedFromService = [...posts];

            const expected = [...posts];
            service
                .getPosts(courseDefault.id!, {})
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all student posts for a course wide context', fakeAsync(() => {
            const returnedFromService = [post3];

            const expected = [post3];
            service
                .getPosts(courseDefault.id!, { courseWideContext: CourseWideContext.RANDOM })
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all student posts for a lecture', fakeAsync(() => {
            const returnedFromService = [post2];

            const expected = [post2];
            service
                .getPosts(courseDefault.id!, { lectureId: lectureDefault.id! })
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all student posts for an exercise', fakeAsync(() => {
            const returnedFromService = [post1];

            const expected = [post1];
            service
                .getPosts(courseDefault.id!, { exerciseId: exerciseDefault.id! })
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
