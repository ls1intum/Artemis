import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import * as sinon from 'sinon';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { MockPostService } from '../../helpers/mocks/service/mock-post.service';
import { MockAnswerPostService } from '../../helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ArtemisTestModule } from '../../test.module';
import { PostService } from 'app/shared/metis/post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { AccountService } from 'app/core/auth/account.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../helpers/mocks/service/mock-reaction.service';
import { Reaction } from 'app/entities/metis/reaction.model';
import { CourseWideContext, DisplayPriority } from 'app/shared/metis/metis.util';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import {
    metisAnswerPostUser1,
    metisCourse,
    metisExercise,
    metisLecture,
    metisPostExerciseUser1,
    metisReactionUser2,
    metisUser1,
    metisUser2,
} from '../../helpers/sample/metis-sample-data';

chai.use(sinonChai);
const expect = chai.expect;

describe('Metis Service', () => {
    let injector: TestBed;
    let metisService: MetisService;
    let metisServiceUserStub: SinonStub;
    let metisServiceGetFilteredPostsSpy: SinonSpy;
    let reactionService: MockReactionService;
    let postService: MockPostService;
    let answerPostService: MockAnswerPostService;
    let accountService: MockAccountService;
    let accountServiceIsAtLeastTutorStub: SinonStub;
    let post: Post;
    let answerPost: AnswerPost;
    let reaction: Reaction;
    let course: Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, ArtemisTestModule],
            providers: [
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        injector = getTestBed();
        metisService = injector.get(MetisService);
        reactionService = injector.get(ReactionService);
        postService = injector.get(PostService);
        answerPostService = injector.get(AnswerPostService);
        accountService = injector.get(AccountService);
        metisServiceGetFilteredPostsSpy = spy(metisService, 'getFilteredPosts');
        metisServiceUserStub = stub(metisService, 'getUser');
        accountServiceIsAtLeastTutorStub = stub(accountService, 'isAtLeastTutorInCourse');

        post = metisPostExerciseUser1;
        post.displayPriority = DisplayPriority.PINNED;
        answerPost = metisAnswerPostUser1;
        reaction = metisReactionUser2;
        course = metisCourse;
    });

    afterEach(() => {
        sinon.restore();
    });

    describe('Invoke post service methods', () => {
        it('should create a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'create');
            const createdPostSub = metisService.createPost(post).subscribe((createdPost) => {
                expect(createdPost).to.be.deep.equal(post);
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
            createdPostSub.unsubscribe();
        }));

        it('should delete a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'delete');
            metisService.deletePost(post);
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
        }));

        it('should update a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'update');
            const updatedPostSub = metisService.updatePost(post).subscribe((updatedPost) => {
                expect(updatedPost).to.be.deep.equal(post);
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
            updatedPostSub.unsubscribe();
        }));

        it('should pin a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'updatePostDisplayPriority');
            const updatedPostSub = metisService.updatePostDisplayPriority(post.id!, DisplayPriority.PINNED).subscribe((updatedPost) => {
                expect(updatedPost).to.be.deep.equal({ id: post.id, displayPriority: DisplayPriority.PINNED });
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
            updatedPostSub.unsubscribe();
        }));

        it('should archive a post', fakeAsync(() => {
            const postServiceSpy = spy(postService, 'updatePostDisplayPriority');
            const updatedPostSub = metisService.updatePostDisplayPriority(post.id!, DisplayPriority.ARCHIVED).subscribe((updatedPost) => {
                expect(updatedPost).to.be.deep.equal({ id: post.id, displayPriority: DisplayPriority.ARCHIVED });
            });
            expect(postServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
            updatedPostSub.unsubscribe();
        }));

        it('should get correct list of posts when set', fakeAsync(() => {
            metisService.setPosts([post]);
            tick();
            const postsSub = metisService.posts.subscribe((posts) => {
                expect(posts).to.be.deep.equal([post]);
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
            const postServiceSpy = spy(postService, 'getPosts');
            metisService.getFilteredPosts({ lectureId: metisLecture.id }, false);
            expect(postServiceSpy).to.have.been.calledOnce;

            // don't change filter
            metisService.getFilteredPosts({ lectureId: metisLecture.id }, false);
            expect(postServiceSpy).to.have.been.calledOnce;

            // change filter
            metisService.getFilteredPosts({ lectureId: undefined, exerciseId: metisExercise.id }, false);
            expect(postServiceSpy).to.have.been.calledTwice;

            // change filter
            metisService.getFilteredPosts({ lectureId: undefined, exerciseId: undefined, courseId: metisCourse.id }, false);
            expect(postServiceSpy).to.have.been.calledThrice;
        });

        it('should get posts for exercise filter', () => {
            const postServiceSpy = spy(postService, 'getPosts');
            metisService.getFilteredPosts({ exerciseId: metisExercise.id }, false);
            expect(postServiceSpy).to.have.been.called;

            // don't change filter
            metisService.getFilteredPosts({ exerciseId: metisExercise.id }, false);
            expect(postServiceSpy).to.have.been.calledOnce;

            // change filter
            metisService.getFilteredPosts({ lectureId: metisLecture.id, exerciseId: undefined }, false);
            expect(postServiceSpy).to.have.been.calledTwice;

            // change filter
            metisService.getFilteredPosts({ lectureId: undefined, exerciseId: undefined, courseWideContext: CourseWideContext.RANDOM }, false);
            expect(postServiceSpy).to.have.been.calledThrice;
        });

        it('should get posts for course-context filter', () => {
            const postServiceSpy = spy(postService, 'getPosts');
            metisService.getFilteredPosts({ courseWideContext: CourseWideContext.RANDOM });
            expect(postServiceSpy).to.have.been.called;
        });

        it('should get posts for course', () => {
            const postServiceSpy = spy(postService, 'getPosts');
            metisService.getFilteredPosts({ courseId: course.id });
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
            expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
            createdAnswerPostSub.unsubscribe();
        }));

        it('should delete an answer post', fakeAsync(() => {
            const answerPostServiceSpy = spy(answerPostService, 'delete');
            metisService.deleteAnswerPost(answerPost);
            expect(answerPostServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
        }));

        it('should create a post', fakeAsync(() => {
            const answerPostServiceSpy = spy(answerPostService, 'update');
            const updatedAnswerPostSub = metisService.updateAnswerPost(answerPost).subscribe((updatedAnswerPost) => {
                expect(updatedAnswerPost).to.be.deep.equal(answerPost);
            });
            expect(answerPostServiceSpy).to.have.been.called;
            tick();
            expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
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
            expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
            createdReactionSub.unsubscribe();
        }));

        it('should delete a reaction', fakeAsync(() => {
            const reactionServiceSpy = spy(reactionService, 'delete');
            metisService.deleteReaction(reaction).subscribe(() => {
                expect(metisServiceGetFilteredPostsSpy).to.have.been.called;
            });
            tick();
            expect(reactionServiceSpy).to.have.been.called;
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
        metisServiceUserStub.returns(metisUser1);
        const metisUserIsAuthorOfPostingReturn = metisService.metisUserIsAuthorOfPosting(post);
        expect(metisUserIsAuthorOfPostingReturn).to.be.true;
    });

    it('should determine that metis user is not author of post', () => {
        metisServiceUserStub.returns(metisUser2);
        const metisUserIsAuthorOfPostingReturn = metisService.metisUserIsAuthorOfPosting(post);
        expect(metisUserIsAuthorOfPostingReturn).to.be.false;
    });

    it('should set course information correctly and invoke an update of the post tags in this course', () => {
        const updateCoursePostTagsSpy = spy(metisService, 'updateCoursePostTags');
        metisService.setCourse(course);
        const getCourseReturn = metisService.getCourse();
        expect(getCourseReturn).to.be.equal(course);
        expect(updateCoursePostTagsSpy).to.have.been.called;
    });

    it('should set course when current course has different id', () => {
        metisService.setCourse(course);
        const newCourse = new Course();
        newCourse.id = 99;
        metisService.setCourse(newCourse);
        const getCourseReturn = metisService.getCourse();
        expect(getCourseReturn).to.be.equal(newCourse);
    });
});
