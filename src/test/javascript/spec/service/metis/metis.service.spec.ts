import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { MockPostService } from '../../helpers/mocks/service/mock-post.service';
import { MockAnswerPostService } from '../../helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { PostService } from 'app/shared/metis/post.service';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { AccountService } from 'app/core/auth/account.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../helpers/mocks/service/mock-reaction.service';
import { Reaction } from 'app/entities/metis/reaction.model';
import { CourseWideContext, DisplayPriority, MetisPostAction, MetisWebsocketChannelPrefix, PageType } from 'app/shared/metis/metis.util';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockProvider } from 'ng-mocks';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MetisPostDTO } from 'app/entities/metis/metis-post-dto.model';
import { of } from 'rxjs';
import {
    metisCourse,
    metisCoursePostsWithCourseWideContext,
    metisExercise,
    metisExercisePosts,
    metisLecture,
    metisLecturePosts,
    metisPostExerciseUser1,
    metisReactionUser2,
    metisResolvingAnswerPostUser1,
    metisUser1,
    metisUser2,
} from '../../helpers/sample/metis-sample-data';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';

describe('Metis Service', () => {
    let metisService: MetisService;
    let metisServiceUserStub: jest.SpyInstance;
    let metisServiceGetFilteredPostsSpy: jest.SpyInstance;
    let metisServiceCreateWebsocketSubscriptionSpy: jest.SpyInstance;
    let websocketServiceSubscribeSpy: jest.SpyInstance;
    let websocketServiceReceiveStub: jest.SpyInstance;
    let websocketService: JhiWebsocketService;
    let reactionService: ReactionService;
    let postService: PostService;
    let answerPostService: AnswerPostService;
    let post: Post;
    let answerPost: AnswerPost;
    let reaction: Reaction;
    let course: Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MockProvider(SessionStorageService),
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: PostService, useClass: MockPostService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        });
        metisService = TestBed.inject(MetisService);
        websocketService = TestBed.inject(JhiWebsocketService);
        reactionService = TestBed.inject(ReactionService);
        postService = TestBed.inject(PostService);
        answerPostService = TestBed.inject(AnswerPostService);
        metisServiceGetFilteredPostsSpy = jest.spyOn(metisService, 'getFilteredPosts');
        metisServiceCreateWebsocketSubscriptionSpy = jest.spyOn(metisService, 'createWebsocketSubscription');
        metisServiceUserStub = jest.spyOn(metisService, 'getUser');

        post = metisPostExerciseUser1;
        post.displayPriority = DisplayPriority.PINNED;
        answerPost = metisResolvingAnswerPostUser1;
        reaction = metisReactionUser2;
        course = metisCourse;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('Invoke post service methods', () => {
        it('should create a post', fakeAsync(() => {
            const postServiceSpy = jest.spyOn(postService, 'create');
            const createdPostSub = metisService.createPost(post).subscribe((createdPost) => {
                expect(createdPost).toEqual(post);
            });
            expect(postServiceSpy).toHaveBeenCalled();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            createdPostSub.unsubscribe();
        }));

        it('should delete a post', fakeAsync(() => {
            const postServiceSpy = jest.spyOn(postService, 'delete');
            metisService.deletePost(post);
            expect(postServiceSpy).toHaveBeenCalled();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
        }));

        it('should update a post', fakeAsync(() => {
            const postServiceSpy = jest.spyOn(postService, 'update');
            const updatedPostSub = metisService.updatePost(post).subscribe((updatedPost) => {
                expect(updatedPost).toEqual(post);
            });
            expect(postServiceSpy).toHaveBeenCalled();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            updatedPostSub.unsubscribe();
        }));

        it('should pin a post', fakeAsync(() => {
            const postServiceSpy = jest.spyOn(postService, 'updatePostDisplayPriority');
            const updatedPostSub = metisService.updatePostDisplayPriority(post.id!, DisplayPriority.PINNED).subscribe((updatedPost) => {
                expect(updatedPost).toEqual({ id: post.id, displayPriority: DisplayPriority.PINNED });
            });
            expect(postServiceSpy).toHaveBeenCalled();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            updatedPostSub.unsubscribe();
        }));

        it('should archive a post', fakeAsync(() => {
            const postServiceSpy = jest.spyOn(postService, 'updatePostDisplayPriority');
            const updatedPostSub = metisService.updatePostDisplayPriority(post.id!, DisplayPriority.ARCHIVED).subscribe((updatedPost) => {
                expect(updatedPost).toEqual({ id: post.id, displayPriority: DisplayPriority.ARCHIVED });
            });
            expect(postServiceSpy).toHaveBeenCalled();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            updatedPostSub.unsubscribe();
        }));

        it('should get correct list of posts when set', fakeAsync(() => {
            metisService.setPosts([post]);
            tick();
            const postsSub = metisService.posts.subscribe((posts) => {
                expect(posts).toEqual([post]);
            });
            tick();
            postsSub.unsubscribe();
        }));

        it('should update post tags', () => {
            const postServiceSpy = jest.spyOn(postService, 'getAllPostTagsByCourseId');
            metisService.updateCoursePostTags();
            expect(postServiceSpy).toHaveBeenCalled();
        });

        it('should get posts for lecture filter', () => {
            const postServiceSpy = jest.spyOn(postService, 'getPosts');
            metisService.getFilteredPosts({ lectureId: metisLecture.id }, false);
            expect(postServiceSpy).toHaveBeenCalledTimes(1);

            // don't change filter
            metisService.getFilteredPosts({ lectureId: metisLecture.id }, false);
            expect(postServiceSpy).toHaveBeenCalledTimes(1);

            // change filter
            metisService.getFilteredPosts({ lectureId: undefined, exerciseId: metisExercise.id }, false);
            expect(postServiceSpy).toHaveBeenCalledTimes(2);

            // change filter
            metisService.getFilteredPosts(
                {
                    lectureId: undefined,
                    exerciseId: undefined,
                    courseId: metisCourse.id,
                },
                false,
            );
            expect(postServiceSpy).toHaveBeenCalledTimes(3);
        });

        it('should get posts for exercise filter', () => {
            const postServiceSpy = jest.spyOn(postService, 'getPosts');
            metisService.getFilteredPosts({ exerciseId: metisExercise.id }, false);
            expect(postServiceSpy).toHaveBeenCalled();

            // don't change filter
            metisService.getFilteredPosts({ exerciseId: metisExercise.id }, false);
            expect(postServiceSpy).toHaveBeenCalledTimes(1);

            // change filter
            metisService.getFilteredPosts({ lectureId: metisLecture.id, exerciseId: undefined }, false);
            expect(postServiceSpy).toHaveBeenCalledTimes(2);

            // change filter
            metisService.getFilteredPosts(
                {
                    lectureId: undefined,
                    exerciseId: undefined,
                    courseWideContext: CourseWideContext.RANDOM,
                },
                false,
            );
            expect(postServiceSpy).toHaveBeenCalledTimes(3);
        });

        it('should get posts for course-context filter', () => {
            const postServiceSpy = jest.spyOn(postService, 'getPosts');
            metisService.getFilteredPosts({ courseWideContext: CourseWideContext.RANDOM });
            expect(postServiceSpy).toHaveBeenCalled();
        });

        it('should get posts for course', () => {
            const postServiceSpy = jest.spyOn(postService, 'getPosts');
            metisService.getFilteredPosts({ courseId: course.id });
            expect(postServiceSpy).toHaveBeenCalled();
        });

        it('should get similar posts within course', () => {
            const postServiceSpy = jest.spyOn(postService, 'computeSimilarityScoresWithCoursePosts');
            metisService.getSimilarPosts(post);
            expect(postServiceSpy).toHaveBeenCalled();
        });
    });

    describe('Invoke answer post service methods', () => {
        it('should create an answer post', fakeAsync(() => {
            const answerPostServiceSpy = jest.spyOn(answerPostService, 'create');
            const createdAnswerPostSub = metisService.createAnswerPost(answerPost).subscribe((createdAnswerPost) => {
                expect(createdAnswerPost).toEqual(answerPost);
            });
            expect(answerPostServiceSpy).toHaveBeenCalled();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            createdAnswerPostSub.unsubscribe();
        }));

        it('should delete an answer post', fakeAsync(() => {
            const answerPostServiceSpy = jest.spyOn(answerPostService, 'delete');
            metisService.deleteAnswerPost(answerPost);
            expect(answerPostServiceSpy).toHaveBeenCalled();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
        }));

        it('should update an answer post', fakeAsync(() => {
            const answerPostServiceSpy = jest.spyOn(answerPostService, 'update');
            const updatedAnswerPostSub = metisService.updateAnswerPost(answerPost).subscribe((updatedAnswerPost) => {
                expect(updatedAnswerPost).toEqual(answerPost);
            });
            expect(answerPostServiceSpy).toHaveBeenCalled();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            updatedAnswerPostSub.unsubscribe();
        }));
    });

    describe('Invoke reaction service methods', () => {
        it('should create a reaction', fakeAsync(() => {
            const reactionServiceSpy = jest.spyOn(reactionService, 'create');
            const createdReactionSub = metisService.createReaction(reaction).subscribe((createdReaction) => {
                expect(createdReaction).toEqual(reaction);
            });
            expect(reactionServiceSpy).toHaveBeenCalled();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            createdReactionSub.unsubscribe();
        }));

        it('should delete a reaction', fakeAsync(() => {
            const reactionServiceSpy = jest.spyOn(reactionService, 'delete');
            metisService.deleteReaction(reaction).subscribe(() => {
                expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            });
            tick();
            expect(reactionServiceSpy).toHaveBeenCalled();
        }));
    });

    it('should determine that metis user is author of post', () => {
        metisServiceUserStub.mockReturnValue(metisUser1);
        const metisUserIsAuthorOfPostingReturn = metisService.metisUserIsAuthorOfPosting(post);
        expect(metisUserIsAuthorOfPostingReturn).toBeTrue();
    });

    it('should determine that metis user is not author of post', () => {
        metisServiceUserStub.mockReturnValue(metisUser2);
        const metisUserIsAuthorOfPostingReturn = metisService.metisUserIsAuthorOfPosting(post);
        expect(metisUserIsAuthorOfPostingReturn).toBeFalse();
    });

    it('should set course information correctly and invoke an update of the post tags in this course', () => {
        const updateCoursePostTagsSpy = jest.spyOn(metisService, 'updateCoursePostTags');
        metisService.setCourse(course);
        const getCourseReturn = metisService.getCourse();
        expect(getCourseReturn).toEqual(course);
        expect(updateCoursePostTagsSpy).toHaveBeenCalled();
    });

    it('should set course when current course has different id', () => {
        metisService.setCourse(course);
        const newCourse = new Course();
        newCourse.id = 99;
        metisService.setCourse(newCourse);
        const getCourseReturn = metisService.getCourse();
        expect(getCourseReturn).toEqual(newCourse);
    });

    it('should create empty post for a course-wide context', () => {
        const emptyPost = metisService.createEmptyPostForContext(CourseWideContext.ORGANIZATION, undefined, undefined);
        expect(emptyPost.courseWideContext).toEqual(CourseWideContext.ORGANIZATION);
        expect(emptyPost.exercise).toBeUndefined();
        expect(emptyPost.lecture).toBeUndefined();
    });

    it('should create empty post for a exercise context', () => {
        const emptyPost = metisService.createEmptyPostForContext(undefined, metisExercise, undefined);
        expect(emptyPost.courseWideContext).toBeUndefined();
        expect(emptyPost.exercise).toEqual({
            id: metisExercise.id,
            title: metisExercise.title,
            type: metisExercise.type,
        });
        expect(emptyPost.lecture).toBeUndefined();
    });

    it('should create empty post for a lecture context', () => {
        const emptyPost = metisService.createEmptyPostForContext(undefined, undefined, metisLecture);
        expect(emptyPost.courseWideContext).toBeUndefined();
        expect(emptyPost.exercise).toBeUndefined();
        expect(emptyPost.lecture).toEqual({ ...metisLecture, attachments: undefined });
    });

    it('should determine the link components for a reference to a post with course-wide context', () => {
        metisService.setCourse(course);
        const referenceLinkComponents = metisService.getLinkForPost(metisCoursePostsWithCourseWideContext[0]);
        expect(referenceLinkComponents).toEqual(['/courses', metisCourse.id, 'discussion']);
    });

    it('should determine the link components for a reference to a post with exercise context', () => {
        metisService.setCourse(course);
        const referenceLinkComponents = metisService.getLinkForPost(metisExercisePosts[0]);
        expect(referenceLinkComponents).toEqual(['/courses', metisCourse.id, 'exercises', metisExercise.id]);
    });

    it('should determine the link components for a reference to a post with lecture context', () => {
        metisService.setCourse(course);
        const referenceLinkComponents = metisService.getLinkForPost(metisLecturePosts[0]);
        expect(referenceLinkComponents).toEqual(['/courses', metisCourse.id, 'lectures', metisLecture.id]);
    });

    it('should determine the router link required for referencing an exercise page within posting', () => {
        metisService.setCourse(course);
        const referenceRouterLink = metisService.getLinkForExercise(metisExercise.id!.toString());
        expect(referenceRouterLink).toBe(`/courses/${metisCourse.id}/exercises/${metisExercise.id!.toString()}`);
    });

    it('should determine the router link required for referencing a lecture page within posting', () => {
        metisService.setCourse(course);
        const referenceRouterLink = metisService.getLinkForLecture(metisLecture.id!.toString());
        expect(referenceRouterLink).toBe(`/courses/${metisCourse.id}/lectures/${metisLecture.id!.toString()}`);
    });

    it('should determine the query param for a reference to a post with course-wide context', () => {
        metisService.setCourse(course);
        const referenceLinkComponents = metisService.getQueryParamsForPost(metisCoursePostsWithCourseWideContext[0]);
        expect(referenceLinkComponents).toEqual({
            searchText: `#${metisCoursePostsWithCourseWideContext[0].id}`,
        });
    });

    it('should determine the query param for a reference to a post with exercise context', () => {
        metisService.setCourse(course);
        const referenceLinkComponents = metisService.getQueryParamsForPost(metisExercisePosts[0]);
        expect(referenceLinkComponents).toEqual({
            postId: metisExercisePosts[0].id,
        });
    });

    it('should determine the query param for a reference to a post with lecture context', () => {
        metisService.setCourse(course);
        const referenceLinkComponents = metisService.getQueryParamsForPost(metisLecturePosts[0]);
        expect(referenceLinkComponents).toEqual({
            postId: metisLecturePosts[0].id,
        });
    });

    it('should determine context information for a post with course-wide context', () => {
        metisService.setCourse(course);
        const contextInformation = metisService.getContextInformation(metisCoursePostsWithCourseWideContext[0]);
        expect(contextInformation.routerLinkComponents).toBeUndefined();
        expect(contextInformation.displayName).toBeDefined();
    });

    it('should determine context information for a post with exercise context', () => {
        metisService.setCourse(course);
        const contextInformation = metisService.getContextInformation(metisExercisePosts[0]);
        expect(contextInformation.routerLinkComponents).toEqual(['/courses', metisCourse.id, 'exercises', metisExercisePosts[0].exercise!.id]);
        expect(contextInformation.displayName).toEqual(metisExercisePosts[0].exercise!.title);
    });

    it('should determine context information for a post with lecture context', () => {
        metisService.setCourse(course);
        const contextInformation = metisService.getContextInformation(metisLecturePosts[0]);
        expect(contextInformation.routerLinkComponents).toEqual(['/courses', metisCourse.id, 'lectures', metisLecturePosts[0].lecture!.id]);
        expect(contextInformation.displayName).toEqual(metisLecturePosts[0].lecture!.title);
    });

    describe('Handle websocket related functionality', () => {
        beforeEach(() => {
            metisServiceCreateWebsocketSubscriptionSpy = jest.spyOn(metisService, 'createWebsocketSubscription');
            websocketServiceReceiveStub = jest.spyOn(websocketService, 'receive');
            websocketServiceSubscribeSpy = jest.spyOn(websocketService, 'subscribe');
            metisService.setCourse(metisCourse);
        });

        it('should create websocket subscription when posts with lecture context are initially retrieved from DB', fakeAsync(() => {
            const lecturePostWithTags = metisLecturePosts[0];
            lecturePostWithTags.tags = ['tag1', 'tag2'];
            websocketServiceReceiveStub.mockReturnValue(of({ post: lecturePostWithTags, action: MetisPostAction.CREATE } as MetisPostDTO));
            // setup subscription
            metisService.getFilteredPosts({ lectureId: metisLecture.id! });
            expect(metisServiceCreateWebsocketSubscriptionSpy).toHaveBeenCalledWith(MetisWebsocketChannelPrefix + `lectures/${metisLecture.id}`);
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({ lectureId: metisLecture.id! }, false);
        }));

        it('should create websocket subscription when posts with exercise context are initially retrieved from DB', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of({ post: metisExercisePosts[0], action: MetisPostAction.DELETE } as MetisPostDTO));
            metisService.setPageType(PageType.OVERVIEW);
            // setup subscription
            metisService.getFilteredPosts({ exerciseId: metisExercise.id!, page: 0, pageSize: ITEMS_PER_PAGE });
            expect(metisServiceCreateWebsocketSubscriptionSpy).toHaveBeenCalledWith(MetisWebsocketChannelPrefix + `exercises/${metisExercise.id}`);
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({ exerciseId: metisExercise.id!, page: 0, pageSize: ITEMS_PER_PAGE });
        }));

        it('should create websocket subscription when posts with course-wide context are initially retrieved from DB', fakeAsync(() => {
            const courseWidePostWithTags = metisCoursePostsWithCourseWideContext[0];
            courseWidePostWithTags.tags = ['tag1', 'tag2'];
            websocketServiceReceiveStub.mockReturnValue(of({ post: courseWidePostWithTags, action: MetisPostAction.UPDATE } as MetisPostDTO));
            // setup subscription
            metisService.getFilteredPosts({ courseWideContext: courseWidePostWithTags.courseWideContext });
            expect(metisServiceCreateWebsocketSubscriptionSpy).toHaveBeenCalledWith(MetisWebsocketChannelPrefix + `courses/${metisCourse.id}`);
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({ courseWideContext: courseWidePostWithTags.courseWideContext }, false);
        }));

        it('should not create new subscription if already exists', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of({ post: metisExercisePosts[0], action: MetisPostAction.DELETE } as MetisPostDTO));
            // setup subscription for the first time
            metisService.getFilteredPosts({ exerciseId: metisExercise.id! });
            expect(metisServiceCreateWebsocketSubscriptionSpy).toHaveBeenCalledWith(MetisWebsocketChannelPrefix + `exercises/${metisExercise.id}`);
            // trigger createWebsocketSubscription for the second time with the same context filter. i.e. same channel
            metisService.getFilteredPosts({ exerciseId: metisExercise.id! });
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({ exerciseId: metisExercise.id! }, false);
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
        }));
    });
});
