import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { SinonStub, spy, stub } from 'sinon';
import * as sinon from 'sinon';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { CourseWideContext, Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { MockPostService } from '../../helpers/mocks/service/mock-post.service';
import { MockAnswerPostService } from '../../helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ArtemisTestModule } from '../../test.module';
import { PostService } from 'app/shared/metis/post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { AccountService } from 'app/core/auth/account.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { User } from 'app/core/user/user.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('Metis Service', () => {
    let injector: TestBed;
    let metisService: MetisService;
    let metisServiceUserStub: SinonStub;
    let postService: MockPostService;
    let answerPostService: MockAnswerPostService;
    let accountService: MockAccountService;
    let accountServiceIsAtLeastTutorStub: SinonStub;
    let post1: Post;
    let post2: Post;
    let post3: Post;
    let user1: User;
    let user2: User;
    let answerPost: AnswerPost;
    let courseDefault: Course;
    let exerciseDefault: TextExercise;
    let lectureDefault: Lecture;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, ArtemisTestModule],
            providers: [
                { provide: MetisService, useClass: MetisService },
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        injector = getTestBed();
        metisService = injector.get(MetisService);
        postService = injector.get(PostService);
        answerPostService = injector.get(AnswerPostService);
        accountService = injector.get(AccountService);

        user1 = { id: 1, name: 'usersame1', login: 'login1' } as User;
        user2 = { id: 2, name: 'usersame2', login: 'login2' } as User;

        post1 = new Post();
        post1.id = 1;
        post1.content = 'This is a test post';
        post1.title = 'title';
        post1.tags = ['tag1', 'tag2'];
        post1.votes = 5;
        post1.author = user1;

        post2 = new Post();
        post2.id = 2;
        post2.content = 'This is a test post';
        post2.title = 'title';
        post2.tags = ['tag1', 'tag2'];
        post2.votes = 2;
        post2.author = user2;

        post3 = new Post();
        post3.id = 3;
        post3.content = 'This is a test post';
        post3.title = 'title';
        post3.tags = ['tag1', 'tag2'];
        post3.votes = 1;
        post3.author = user2;
        post3.courseWideContext = CourseWideContext.RANDOM;

        const posts: Post[] = [post1, post2, post3];

        answerPost = new AnswerPost();
        answerPost.id = 1;
        answerPost.creationDate = undefined;
        answerPost.content = 'This is a test answer post';

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
        courseDefault.posts = posts;

        metisServiceUserStub = stub(metisService, 'getUser');
        accountServiceIsAtLeastTutorStub = stub(accountService, 'isAtLeastTutorInCourse');

        afterEach(() => {
            sinon.restore();
        });
    });

    describe('Invoke post service methods', () => {
        it('should create a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'create');
            const createdPostSub = metisService.createPost(post1).subscribe((createdPost) => {
                expect(createdPost).to.be.deep.equal(post1);
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            createdPostSub.unsubscribe();
        }));
        it('should delete a post', () => {
            const postServiceSpy = spy(postService, 'delete');
            metisService.deletePost(post1);
            expect(postServiceSpy).to.have.been.called;
        });
        it('should update a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'update');
            const updatedPostSub = metisService.updatePost(post1).subscribe((updatedPost) => {
                expect(updatedPost).to.be.deep.equal(post1);
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            updatedPostSub.unsubscribe();
        }));
        it('should get correct list of posts when set', fakeAsync(() => {
            metisService.setPosts([post1]);
            tick();
            const postsSub = metisService.posts.subscribe((posts) => {
                expect(posts).to.be.deep.equal([post1]);
            });
            tick();
            postsSub.unsubscribe();
        }));
        it('should update post tags', () => {
            const postServiceSpy = spy(postService, 'getAllPostTagsByCourseId');
            metisService.updateCoursePostTags();
            expect(postServiceSpy).to.have.been.called;
        });
        it('should get posts for lecture filter', () => {
            const postServiceSpy = spy(postService, 'getAllPostsByLectureId');
            metisService.getPostsForFilter({ lecture: lectureDefault });
            expect(postServiceSpy).to.have.been.called;
        });
        it('should get posts for exercise filter', () => {
            const postServiceSpy = spy(postService, 'getAllPostsByExerciseId');
            metisService.getPostsForFilter({ exercise: exerciseDefault });
            expect(postServiceSpy).to.have.been.called;
        });
        it('should get posts for course-context filter', () => {
            const postServiceSpy = spy(postService, 'getAllPostsByCourseId');
            metisService.getPostsForFilter({ courseWideContext: CourseWideContext.RANDOM });
            expect(postServiceSpy).to.have.been.called;
        });
        it('should get posts for course', () => {
            const postServiceSpy = spy(postService, 'getAllPostsByCourseId');
            metisService.getPostsForFilter({});
            expect(postServiceSpy).to.have.been.called;
        });
        it('should update votes for a post', () => {
            const postServiceSpy = spy(postService, 'updateVotes');
            metisService.updatePostVotes(post1, 6);
            expect(postServiceSpy).to.have.been.called;
        });
    });

    describe('Invoke answer post service methods', () => {
        it('should create an answer post', fakeAsync(() => {
            const answerPostServiceSpy = spy(answerPostService, 'create');
            const createdAnswerPostSub = metisService.createAnswerPost(answerPost).subscribe((createdAnswerPost) => {
                expect(createdAnswerPost).to.be.deep.equal(answerPost);
            });
            expect(answerPostServiceSpy).to.have.been.called;
            tick();
            createdAnswerPostSub.unsubscribe();
        }));
        it('should delete an answer post', () => {
            const answerPostServiceSpy = spy(answerPostService, 'delete');
            metisService.deleteAnswerPost(answerPost);
            expect(answerPostServiceSpy).to.have.been.called;
        });
        it('should create a post', fakeAsync(() => {
            const answerPostServiceSpy = spy(answerPostService, 'update');
            const updatedAnswerPostSub = metisService.updateAnswerPost(answerPost).subscribe((updatedAnswerPost) => {
                expect(updatedAnswerPost).to.be.deep.equal(answerPost);
            });
            expect(answerPostServiceSpy).to.have.been.called;
            tick();
            updatedAnswerPostSub.unsubscribe();
        }));
    });

    it('should determine that metis user is at least tutor in course', () => {
        accountServiceIsAtLeastTutorStub.returns(true);
        const metisUserIsAtLeastTutorInCourseReturn = metisService.metisUserIsAtLeastTutorInCourse();
        expect(metisUserIsAtLeastTutorInCourseReturn).to.be.true;
    });

    it('should determine that metis user is not at least tutor in course', () => {
        accountServiceIsAtLeastTutorStub.returns(false);
        const metisUserIsAtLeastTutorInCourseReturn = metisService.metisUserIsAtLeastTutorInCourse();
        expect(metisUserIsAtLeastTutorInCourseReturn).to.be.false;
    });

    it('should determine that metis user is at least tutor in course', () => {
        accountServiceIsAtLeastTutorStub.returns(true);
        const metisUserIsAtLeastTutorInCourseReturn = metisService.metisUserIsAtLeastTutorInCourse();
        expect(metisUserIsAtLeastTutorInCourseReturn).to.be.true;
    });

    it('should determine that metis user is author of post', () => {
        metisServiceUserStub.returns(user1);
        const metisUserIsAuthorOfPostingReturn = metisService.metisUserIsAuthorOfPosting(post1);
        expect(metisUserIsAuthorOfPostingReturn).to.be.true;
    });

    it('should determine that metis user is not author of post', () => {
        metisServiceUserStub.returns(user2);
        const metisUserIsAuthorOfPostingReturn = metisService.metisUserIsAuthorOfPosting(post1);
        expect(metisUserIsAuthorOfPostingReturn).to.be.false;
    });

    it('should set course information correctly and invoke an update of the post tags in this course', () => {
        const updateCoursePostTagsSpy = spy(metisService, 'updateCoursePostTags');
        metisService.setCourse(courseDefault);
        const getCourseReturn = metisService.getCourse();
        expect(getCourseReturn).to.be.equal(courseDefault);
        expect(updateCoursePostTagsSpy).to.have.been.called;
    });
});
