import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import * as sinon from 'sinon';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { CourseWideContext, DisplayPriority, Post } from 'app/entities/metis/post.model';
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
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../helpers/mocks/service/mock-reaction.service';
import { Reaction } from 'app/entities/metis/reaction.model';
import { VOTE_EMOJI_ID } from 'app/shared/metis/metis.util';

chai.use(sinonChai);
const expect = chai.expect;

describe('Metis Service', () => {
    let injector: TestBed;
    let metisService: MetisService;
    let metisServiceUserStub: SinonStub;
    let metisServiceGetPostsForFilterSpy: SinonSpy;
    let reactionService: MockReactionService;
    let postService: MockPostService;
    let answerPostService: MockAnswerPostService;
    let accountService: MockAccountService;
    let accountServiceIsAtLeastTutorStub: SinonStub;
    let reactionWithVoteEmoji: Reaction;
    let post1: Post;
    let post2: Post;
    let post3: Post;
    let post4: Post;
    let user1: User;
    let user2: User;
    let answerPost: AnswerPost;
    let reaction: Reaction;
    let courseDefault: Course;
    let exerciseDefault: TextExercise;
    let lectureDefault: Lecture;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, ArtemisTestModule],
            providers: [
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        injector = getTestBed();
        metisService = injector.get(MetisService);
        reactionService = injector.get(ReactionService);
        postService = injector.get(PostService);
        answerPostService = injector.get(AnswerPostService);
        accountService = injector.get(AccountService);
        metisServiceGetPostsForFilterSpy = spy(metisService, 'getPostsForFilter');

        user1 = { id: 1, name: 'usersame1', login: 'login1' } as User;
        user2 = { id: 2, name: 'usersame2', login: 'login2' } as User;

        reactionWithVoteEmoji = new Reaction();
        reactionWithVoteEmoji.emojiId = VOTE_EMOJI_ID;
        reactionWithVoteEmoji.user = user1;

        post1 = new Post();
        post1.id = 1;
        post1.content = 'This is a test post';
        post1.title = 'title';
        post1.tags = ['tag1', 'tag2'];
        post1.author = user1;
        post1.creationDate = moment();
        post1.displayPriority = DisplayPriority.PINNED;

        post2 = new Post();
        post2.id = 2;
        post2.content = 'This is a test post';
        post2.title = 'title';
        post2.tags = ['tag1', 'tag2'];
        post2.author = user2;
        post2.creationDate = moment().subtract(1, 'day');
        post2.displayPriority = DisplayPriority.NONE;

        post3 = new Post();
        post3.id = 3;
        post3.content = 'This is a test post';
        post3.title = 'title';
        post3.tags = ['tag1', 'tag2'];
        post3.author = user2;
        post3.courseWideContext = CourseWideContext.RANDOM;
        post3.creationDate = moment().subtract(2, 'day');
        post3.reactions = [reactionWithVoteEmoji];
        post3.displayPriority = DisplayPriority.NONE;

        post4 = new Post();
        post4.id = 4;
        post4.content = 'This is a test post';
        post4.title = 'title';
        post4.tags = ['tag1', 'tag2'];
        post4.author = user2;
        post4.courseWideContext = CourseWideContext.RANDOM;
        post4.creationDate = moment().subtract(2, 'minute');
        post4.reactions = [reactionWithVoteEmoji];
        post4.displayPriority = DisplayPriority.ARCHIVED;

        const posts: Post[] = [post1, post2, post3, post4];

        answerPost = new AnswerPost();
        answerPost.id = 1;
        answerPost.creationDate = undefined;
        answerPost.content = 'This is a test answer post';

        reaction = new Reaction();
        reaction.emojiId = 'smile';
        reaction.user = user1;
        reaction.post = post1;

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
    });

    afterEach(() => {
        sinon.restore();
    });

    describe('Invoke post service methods', () => {
        it('should create a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'create');
            const createdPostSub = metisService.createPost(post1).subscribe((createdPost) => {
                expect(createdPost).to.be.deep.equal(post1);
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
            createdPostSub.unsubscribe();
        }));

        it('should delete a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'delete');
            metisService.deletePost(post1);
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
        }));

        it('should update a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'update');
            const updatedPostSub = metisService.updatePost(post1).subscribe((updatedPost) => {
                expect(updatedPost).to.be.deep.equal(post1);
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
            updatedPostSub.unsubscribe();
        }));

        it('should pin a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'updatePostDisplayPriority');
            post1.displayPriority = DisplayPriority.PINNED;
            const updatedPostSub = metisService.updatePostDisplayPriority(post1).subscribe((updatedPost) => {
                expect(updatedPost).to.be.deep.equal({ id: post1.id, displayPriority: DisplayPriority.PINNED });
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
            updatedPostSub.unsubscribe();
        }));

        it('should archive a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'updatePostDisplayPriority');
            post1.displayPriority = DisplayPriority.ARCHIVED;
            const updatedPostSub = metisService.updatePostDisplayPriority(post1).subscribe((updatedPost) => {
                expect(updatedPost).to.be.deep.equal({ id: post1.id, displayPriority: DisplayPriority.ARCHIVED });
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
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
    });

    describe('Invoke answer post service methods', () => {
        it('should create an answer post', fakeAsync(() => {
            const answerPostServiceSpy = spy(answerPostService, 'create');
            const createdAnswerPostSub = metisService.createAnswerPost(answerPost).subscribe((createdAnswerPost) => {
                expect(createdAnswerPost).to.be.deep.equal(answerPost);
            });
            expect(answerPostServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
            createdAnswerPostSub.unsubscribe();
        }));

        it('should delete an answer post', fakeAsync(() => {
            const answerPostServiceSpy = spy(answerPostService, 'delete');
            metisService.deleteAnswerPost(answerPost);
            expect(answerPostServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
        }));

        it('should create a post', fakeAsync(() => {
            const answerPostServiceSpy = spy(answerPostService, 'update');
            const updatedAnswerPostSub = metisService.updateAnswerPost(answerPost).subscribe((updatedAnswerPost) => {
                expect(updatedAnswerPost).to.be.deep.equal(answerPost);
            });
            expect(answerPostServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
            updatedAnswerPostSub.unsubscribe();
        }));
    });

    describe('Invoke reaction service methods', () => {
        it('should create a reaction', fakeAsync(() => {
            const reactionServiceSpy = spy(reactionService, 'create');
            const createdReactionSub = metisService.createReaction(reaction).subscribe((createdReaction) => {
                expect(createdReaction).to.be.deep.equal(reaction);
            });
            expect(reactionServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
            createdReactionSub.unsubscribe();
        }));

        it('should delete a reaction', fakeAsync(() => {
            const reactionServiceSpy = spy(reactionService, 'delete');
            metisService.deleteReaction(reaction).subscribe(() => {
                expect(metisServiceGetPostsForFilterSpy).to.have.been.called;
            });
            tick();
            expect(reactionServiceSpy).to.have.been.called;
        }));
    });

    it('should sort posts', () => {
        // first is the pinned post, second the post with highest voteEmoji count, third is newest post that is not pinned and has no votes,
        // fourth is the archived post (even if it has votes or is new)
        const sortedPosts: Post[] = [post1, post3, post2, post4];
        expect(MetisService.sortPosts([post1, post3, post2, post4])).to.be.deep.equal(sortedPosts);
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
