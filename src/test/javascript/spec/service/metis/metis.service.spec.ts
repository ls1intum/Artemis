import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Post } from 'app/entities/metis/post.model';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
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
import { DisplayPriority, MetisPostAction, PageType, PostContextFilter } from 'app/shared/metis/metis.util';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockProvider } from 'ng-mocks';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MetisPostDTO } from 'app/entities/metis/metis-post-dto.model';
import { Subject, of } from 'rxjs';
import {
    metisChannel,
    metisCourse,
    metisExam,
    metisExercise,
    metisLecture,
    metisLectureChannelDTO,
    metisPostExerciseUser1,
    metisPostInChannel,
    metisReactionUser2,
    metisResolvingAnswerPostUser1,
    metisUser1,
    metisUser2,
    plagiarismPost,
} from '../../helpers/sample/metis-sample-data';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../helpers/mocks/service/mock-notification.service';

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
    let conversationService: ConversationService;
    let post: Post;
    let answerPost: AnswerPost;
    let reaction: Reaction;
    let course: Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MockProvider(SessionStorageService),
                MockProvider(ConversationService),
                { provide: NotificationService, useClass: MockNotificationService },
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
        conversationService = TestBed.inject(ConversationService);
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
            const cachedPostsSub = metisService.posts.subscribe((posts) => expect(posts).toEqual([post]));

            expect(postServiceSpy).toHaveBeenCalledOnce();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            createdPostSub.unsubscribe();
            cachedPostsSub.unsubscribe();
        }));

        it('should delete a post', fakeAsync(() => {
            const postServiceSpy = jest.spyOn(postService, 'delete');
            const createdPostSub = metisService.createPost(post).subscribe();

            metisService.deletePost(post);
            const cachedPostsSub = metisService.posts.subscribe((posts) => expect(posts).toEqual([]));

            expect(postServiceSpy).toHaveBeenCalledOnce();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            createdPostSub.unsubscribe();
            cachedPostsSub.unsubscribe();
        }));

        it('should update a post', fakeAsync(() => {
            const postServiceSpy = jest.spyOn(postService, 'update');
            const createdPostSub = metisService.createPost(post).subscribe();
            post.content = 'new content for update';

            const updatedPostSub = metisService.updatePost(post).subscribe((updatedPost) => {
                expect(updatedPost).toEqual(post);
            });
            const cachedPostsSub = metisService.posts.subscribe((posts) => expect(posts).toEqual([post]));

            expect(postServiceSpy).toHaveBeenCalledOnce();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            updatedPostSub.unsubscribe();
            createdPostSub.unsubscribe();
            cachedPostsSub.unsubscribe();
        }));

        it('should pin a post', fakeAsync(() => {
            const postServiceSpy = jest.spyOn(postService, 'updatePostDisplayPriority');
            const updatedPostSub = metisService.updatePostDisplayPriority(post.id!, DisplayPriority.PINNED).subscribe((updatedPost) => {
                expect(updatedPost).toEqual({ id: post.id, displayPriority: DisplayPriority.PINNED });
            });
            expect(postServiceSpy).toHaveBeenCalledOnce();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            updatedPostSub.unsubscribe();
        }));

        it('should archive a post', fakeAsync(() => {
            const postServiceSpy = jest.spyOn(postService, 'updatePostDisplayPriority');
            const updatedPostSub = metisService.updatePostDisplayPriority(post.id!, DisplayPriority.ARCHIVED).subscribe((updatedPost) => {
                expect(updatedPost).toEqual({ id: post.id, displayPriority: DisplayPriority.ARCHIVED });
            });
            expect(postServiceSpy).toHaveBeenCalledOnce();
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
            expect(postServiceSpy).toHaveBeenCalledOnce();
        });

        it('should get posts for course', () => {
            const postServiceSpy = jest.spyOn(postService, 'getPosts');
            metisService.getFilteredPosts({ courseId: course.id });
            expect(postServiceSpy).toHaveBeenCalledOnce();
        });

        it('should get similar posts within course', () => {
            const postServiceSpy = jest.spyOn(postService, 'computeSimilarityScoresWithCoursePosts');
            metisService.getSimilarPosts(post);
            expect(postServiceSpy).toHaveBeenCalledOnce();
        });
    });

    describe('Invoke answer post service methods', () => {
        it('should create an answer post', fakeAsync(() => {
            const answerPostServiceSpy = jest.spyOn(answerPostService, 'create');
            const createdPostSub = metisService.createPost(post).subscribe();
            answerPost = { ...answerPost, post };

            const createdAnswerPostSub = metisService.createAnswerPost(answerPost).subscribe((createdAnswerPost) => {
                expect(createdAnswerPost).toEqual(answerPost);
            });
            const cachedPostsSub = metisService.posts.subscribe((posts) => expect(posts).toEqual([{ ...post, answers: [answerPost] }]));

            expect(answerPostServiceSpy).toHaveBeenCalledOnce();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            createdAnswerPostSub.unsubscribe();
            createdPostSub.unsubscribe();
            cachedPostsSub.unsubscribe();
        }));

        it('should delete an answer post', fakeAsync(() => {
            const answerPostServiceSpy = jest.spyOn(answerPostService, 'delete');
            const createdPostSub = metisService.createPost(post).subscribe();
            answerPost = { ...answerPost, post };
            const createdAnswerPostSub = metisService.createAnswerPost(answerPost).subscribe();

            metisService.deleteAnswerPost(answerPost);
            const cachedPostsSub = metisService.posts.subscribe((posts) => expect(posts).toEqual([{ ...post, answers: [] }]));

            expect(answerPostServiceSpy).toHaveBeenCalledOnce();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            createdAnswerPostSub.unsubscribe();
            createdPostSub.unsubscribe();
            cachedPostsSub.unsubscribe();
        }));

        it('should update an answer post', fakeAsync(() => {
            const answerPostServiceSpy = jest.spyOn(answerPostService, 'update');
            const createdPostSub = metisService.createPost(post).subscribe();
            answerPost = { ...answerPost, post };
            const createdAnswerPostSub = metisService.createAnswerPost(answerPost).subscribe();

            const updatedAnswerPostSub = metisService.updateAnswerPost(answerPost).subscribe((updatedAnswerPost) => {
                expect(updatedAnswerPost).toEqual(answerPost);
            });
            const cachedPostsSub = metisService.posts.subscribe((posts) => expect(posts).toEqual([{ ...post, answers: [answerPost] }]));

            expect(answerPostServiceSpy).toHaveBeenCalledOnce();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            updatedAnswerPostSub.unsubscribe();
            createdAnswerPostSub.unsubscribe();
            createdPostSub.unsubscribe();
            cachedPostsSub.unsubscribe();
        }));
    });

    describe('Invoke reaction service methods', () => {
        it('should create a reaction', fakeAsync(() => {
            const reactionServiceSpy = jest.spyOn(reactionService, 'create');
            const createdPostSub = metisService.createPost(post).subscribe();
            reaction = { ...reaction, post };

            const createdReactionSub = metisService.createReaction(reaction).subscribe((createdReaction) => {
                expect(createdReaction).toEqual(reaction);
            });
            const cachedPostsSub = metisService.posts.subscribe((posts) => expect(posts).toEqual([{ ...post, reactions: [reaction] }]));

            expect(reactionServiceSpy).toHaveBeenCalledOnce();
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            createdReactionSub.unsubscribe();
            createdPostSub.unsubscribe();
            cachedPostsSub.unsubscribe();
        }));

        it('should delete a reaction', fakeAsync(() => {
            const reactionServiceSpy = jest.spyOn(reactionService, 'delete');
            post = { ...post, reactions: [reaction] };
            reaction.post = post;
            const createdPostSub = metisService.createPost(post).subscribe();

            metisService.deleteReaction(reaction).subscribe(() => {
                expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            });
            const cachedPostsSub = metisService.posts.subscribe((posts) => expect(posts).toEqual([{ ...post, reactions: [] }]));

            tick();
            expect(reactionServiceSpy).toHaveBeenCalledOnce();
            createdPostSub.unsubscribe();
            cachedPostsSub.unsubscribe();
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

    it('should not fetch course post tags if communication is not enabled', () => {
        const updateCoursePostTagsSpy = jest.spyOn(metisService, 'updateCoursePostTags');
        course.courseInformationSharingConfiguration = CourseInformationSharingConfiguration.MESSAGING_ONLY;
        metisService.setCourse(course);
        const getCourseReturn = metisService.getCourse();
        expect(getCourseReturn).toEqual(course);
        expect(updateCoursePostTagsSpy).not.toHaveBeenCalled();
    });

    it('should set course when current course has different id', () => {
        metisService.setCourse(course);
        const newCourse = new Course();
        newCourse.id = 99;
        metisService.setCourse(newCourse);
        const getCourseReturn = metisService.getCourse();
        expect(getCourseReturn).toEqual(newCourse);
    });

    it('should determine the link components for a reference to a post with course-wide context', () => {
        metisService.setCourse(course);
        const referenceLinkComponents = metisService.getLinkForPost();
        expect(referenceLinkComponents).toEqual(['/courses', metisCourse.id, 'discussion']);
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

    it('should determine the router link required for referencing a exam page', () => {
        metisService.setCourse(course);
        const referenceRouterLink = metisService.getLinkForExam(metisExam.id!.toString());
        expect(referenceRouterLink).toBe(`/courses/${metisCourse.id}/exams/${metisExam.id!.toString()}`);
    });

    it('should determine the router link required for navigation based on the channel subtype', () => {
        metisService.setCourse(course);
        const channelDTO = new ChannelDTO();
        channelDTO.subTypeReferenceId = 1;

        channelDTO.subType = ChannelSubType.EXERCISE;
        const exerciseRouterLink = metisService.getLinkForChannelSubType(channelDTO);

        channelDTO.subType = ChannelSubType.LECTURE;
        const lectureRouterLink = metisService.getLinkForChannelSubType(channelDTO);

        channelDTO.subType = ChannelSubType.EXAM;
        const examRouterLink = metisService.getLinkForChannelSubType(channelDTO);

        channelDTO.subType = ChannelSubType.GENERAL;
        const generalRouterLink = metisService.getLinkForChannelSubType(channelDTO);

        expect(exerciseRouterLink).toBe(`/courses/${metisCourse.id}/exercises/1`);
        expect(lectureRouterLink).toBe(`/courses/${metisCourse.id}/lectures/1`);
        expect(examRouterLink).toBe(`/courses/${metisCourse.id}/exams/1`);
        expect(generalRouterLink).toBeUndefined();
    });

    it('should determine the query param for a reference to a post in a conversation', () => {
        metisService.setCourse(course);
        const referenceLinkComponents = metisService.getQueryParamsForPost(metisPostInChannel);
        expect(referenceLinkComponents).toEqual({
            searchText: `#${metisPostInChannel.id}`,
        });
    });

    it('should determine the query param for a reference to conversation message', () => {
        metisService.setCourse(course);
        const referenceLinkComponents = metisService.getQueryParamsForPost({ id: 1 } as Post);
        expect(referenceLinkComponents).toBeEmpty();
    });

    it('should determine context information for a conversation message', () => {
        metisService.setCourse(course);
        const contextInformation = metisService.getContextInformation(metisPostInChannel);
        expect(contextInformation.routerLinkComponents).toEqual(['/courses', metisCourse.id, 'messages']);
        expect(contextInformation.displayName).not.toBeEmpty();
    });

    describe('Handle websocket related functionality', () => {
        beforeEach(() => {
            metisServiceCreateWebsocketSubscriptionSpy = jest.spyOn(metisService, 'createWebsocketSubscription');
            websocketServiceReceiveStub = jest.spyOn(websocketService, 'receive');
            websocketServiceSubscribeSpy = jest.spyOn(websocketService, 'subscribe');
            metisService.setCourse(metisCourse);
        });

        it('should create websocket subscription when posts with lecture context are initially retrieved from DB', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.CREATE } as MetisPostDTO));
            // setup subscription
            metisService.getFilteredPosts({ courseWideChannelIds: [metisPostInChannel.conversation!.id!] });
            metisServiceGetFilteredPostsSpy.mockReset();
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).not.toHaveBeenCalled();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
        }));

        it('should create websocket subscription when posts with exercise context are initially retrieved from DB', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.DELETE } as MetisPostDTO));
            metisService.setPageType(PageType.OVERVIEW);
            // setup subscription
            metisService.getFilteredPosts({ courseWideChannelIds: [metisPostInChannel.conversation!.id!], page: 0, pageSize: ITEMS_PER_PAGE });
            metisServiceGetFilteredPostsSpy.mockReset();
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).not.toHaveBeenCalled();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
        }));

        it('should create websocket subscription when posts with course-wide context are initially retrieved from DB', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.UPDATE } as MetisPostDTO));
            // setup subscription
            metisService.getFilteredPosts({ courseWideChannelIds: [metisPostInChannel.conversation!.id!] });
            metisServiceGetFilteredPostsSpy.mockReset();

            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).not.toHaveBeenCalled();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
        }));

        it('should not create new subscription if already exists', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.DELETE } as MetisPostDTO));
            // setup subscription for the first time
            metisService.getFilteredPosts({ courseWideChannelIds: [metisPostInChannel.conversation!.id!] });
            metisServiceGetFilteredPostsSpy.mockReset();
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            metisService.getFilteredPosts({ courseWideChannelIds: [metisPostInChannel.conversation!.id!] });
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledExactlyOnceWith({ courseWideChannelIds: [metisPostInChannel.conversation!.id!] });
            expect(websocketServiceSubscribeSpy).not.toHaveBeenCalled();
        }));

        it('subscribes to broadcast topic for course-wide channels', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.CREATE } as MetisPostDTO));
            metisService.setPageType(PageType.OVERVIEW);
            // setup subscription
            metisService.getFilteredPosts({ conversationId: 1, page: 0, pageSize: ITEMS_PER_PAGE }, true, {
                id: 1,
                type: ConversationType.CHANNEL,
                isCourseWide: true,
            } as ChannelDTO);
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).not.toHaveBeenCalled();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({ conversationId: 1, page: 0, pageSize: ITEMS_PER_PAGE }, true, {
                id: 1,
                type: ConversationType.CHANNEL,
                isCourseWide: true,
            } as ChannelDTO);
        }));

        it('subscribes to user specific topic for non-course-wide channels', fakeAsync(() => {
            websocketServiceReceiveStub.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.CREATE } as MetisPostDTO));
            metisService.setPageType(PageType.OVERVIEW);
            // setup subscription
            metisService.getFilteredPosts({ conversationId: 1, page: 0, pageSize: ITEMS_PER_PAGE }, true, {
                id: 1,
                type: ConversationType.CHANNEL,
                isCourseWide: false,
            } as ChannelDTO);
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).not.toHaveBeenCalled();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({ conversationId: 1, page: 0, pageSize: ITEMS_PER_PAGE }, true, {
                id: 1,
                type: ConversationType.CHANNEL,
                isCourseWide: false,
            } as ChannelDTO);
        }));

        it.each([MetisPostAction.CREATE, MetisPostAction.UPDATE, MetisPostAction.DELETE])(
            'should not call postService.getPosts() for new or updated messages received over WebSocket',
            (action: MetisPostAction) => {
                // Setup
                const mockPostDTO = {
                    post: metisPostInChannel,
                    action,
                };
                const mockReceiveObservable = new Subject();
                websocketServiceReceiveStub.mockReturnValue(mockReceiveObservable.asObservable());
                metisService.setPageType(PageType.OVERVIEW);

                // set currentPostContextFilter appropriately
                metisService.getFilteredPosts({ plagiarismCaseId: 1 } as PostContextFilter);

                // Ensure subscribe to websocket was called
                expect(websocketService.subscribe).toHaveBeenCalledExactlyOnceWith('/topic/metis/plagiarismCase/1');

                // Emulate receiving a message
                const getPostsSpy = jest.spyOn(postService, 'getPosts');
                const markAsReadSpy = jest.spyOn(conversationService, 'markAsRead');
                mockReceiveObservable.next(mockPostDTO);

                expect(markAsReadSpy).not.toHaveBeenCalled();

                // Ensure getPosts() was not called
                expect(getPostsSpy).not.toHaveBeenCalled();
            },
        );

        it.each([MetisPostAction.CREATE, MetisPostAction.UPDATE, MetisPostAction.DELETE])(
            'should not call postService.getPosts() for new or updated plagiarism posts received over WebSocket',
            (action: MetisPostAction) => {
                // Setup
                const mockPostDTO = {
                    post: metisPostInChannel,
                    action,
                };
                const mockReceiveObservable = new Subject();
                websocketServiceReceiveStub.mockReturnValue(mockReceiveObservable.asObservable());
                metisService.setPageType(PageType.OVERVIEW);

                // set currentPostContextFilter appropriately
                metisService.getFilteredPosts({ conversationId: mockPostDTO.post.conversation?.id } as PostContextFilter);

                // Ensure subscribe to websocket was not called
                expect(websocketService.subscribe).not.toHaveBeenCalled();

                // Emulate receiving a message
                const getPostsSpy = jest.spyOn(postService, 'getPosts');
                const markAsReadSpy = jest.spyOn(conversationService, 'markAsRead');
                mockReceiveObservable.next(mockPostDTO);

                expect(markAsReadSpy).not.toHaveBeenCalled();

                // Ensure getPosts() was not called
                expect(getPostsSpy).not.toHaveBeenCalled();
            },
        );

        it('should update messages received over WebSocket in private channels', () => {
            // Setup
            const mockPostDTO = {
                post: { ...metisPostInChannel, conversation: { ...metisChannel, isCourseWide: false } },
                action: MetisPostAction.CREATE,
            };
            const mockReceiveObservable = new Subject();
            websocketServiceReceiveStub.mockReturnValue(mockReceiveObservable.asObservable());
            metisService.setPageType(PageType.OVERVIEW);

            // set currentPostContextFilter appropriately
            metisService.getFilteredPosts({ conversationId: metisChannel.id } as PostContextFilter);
            const markAsReadSpy = jest.spyOn(conversationService, 'markAsRead').mockReturnValue(of());

            metisService['handleNewOrUpdatedMessage'](mockPostDTO);

            // Ensure subscribe to websocket was not called
            expect(websocketService.subscribe).not.toHaveBeenCalled();

            // Emulate receiving a message
            mockReceiveObservable.next(mockPostDTO);

            expect(markAsReadSpy).toHaveBeenCalled();
        });

        it('should update plagiarism posts received over WebSocket', () => {
            // Setup
            const mockPostDTO = {
                post: plagiarismPost,
                action: MetisPostAction.CREATE,
            };
            const mockReceiveObservable = new Subject();
            websocketServiceReceiveStub.mockReturnValue(mockReceiveObservable.asObservable());
            metisService.setPageType(PageType.PLAGIARISM_CASE_STUDENT);

            // set currentPostContextFilter appropriately
            metisService.getFilteredPosts({ plagiarismCaseId: mockPostDTO.post.plagiarismCase?.id } as PostContextFilter);

            // Ensure subscribe to websocket was not called
            expect(websocketService.subscribe).toHaveBeenCalled();

            // Emulate receiving a message
            mockReceiveObservable.next(mockPostDTO);
            expect(metisService['cachedPosts']).toContain(plagiarismPost);
        });

        it('should update displayed conversation messages if new message does not match search text', fakeAsync(() => {
            // Setup
            const channel = 'someChannel';
            const mockPostDTO = {
                post: { ...metisPostInChannel, content: 'search Text' },
                action: MetisPostAction.CREATE,
            };
            const mockReceiveObservable = new Subject();
            websocketServiceReceiveStub.mockReturnValue(mockReceiveObservable.asObservable());
            metisService.setPageType(PageType.OVERVIEW);
            metisService.createWebsocketSubscription(channel);

            jest.spyOn(postService, 'getPosts').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [],
                        headers: new HttpHeaders({
                            'X-Total-Count': 0,
                        }),
                    }),
                ),
            );

            // set currentPostContextFilter with search text
            metisService.getFilteredPosts({ conversationId: mockPostDTO.post.conversation?.id, searchText: 'Search text' } as PostContextFilter);

            jest.spyOn(conversationService, 'markAsRead').mockReturnValue(of());
            // Emulate receiving a message matching the search text
            mockReceiveObservable.next(mockPostDTO);
            // Emulate receiving a message not matching the search text
            mockReceiveObservable.next({
                post: { ...metisPostInChannel, content: 'other Text' },
                action: MetisPostAction.CREATE,
            });

            metisService.posts.subscribe((posts) => {
                expect(posts[0]).toBe(mockPostDTO.post);
            });
            tick();
        }));

        it('should return current conversation', () => {
            metisService.getFilteredPosts({ conversationId: metisLectureChannelDTO.id } as PostContextFilter, false, metisLectureChannelDTO);
            expect(metisService.getCurrentConversation()).toBe(metisLectureChannelDTO);
        });
    });
});
