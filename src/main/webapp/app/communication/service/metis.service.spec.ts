import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockPostService } from 'test/helpers/mocks/service/mock-post.service';
import { MockAnswerPostService } from 'test/helpers/mocks/service/mock-answer-post.service';
import { MetisService } from 'app/communication/service/metis.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { PostService } from 'app/communication/service/post.service';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import { AccountService } from 'app/core/auth/account.service';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { ReactionService } from 'app/communication/service/reaction.service';
import { MockReactionService } from 'test/helpers/mocks/service/mock-reaction.service';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { DisplayPriority, MetisPostAction, PageType, PostContextFilter, UserRole } from 'app/communication/metis.util';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockProvider } from 'ng-mocks';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MetisPostDTO } from 'app/communication/shared/entities/metis-post-dto.model';
import { Subject, of, throwError } from 'rxjs';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import {
    conversationBetweenUser1User2,
    directMessageUser2,
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
} from 'test/helpers/sample/metis-sample-data';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { Conversation, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { SavedPostService } from 'app/communication/service/saved-post.service';
import { Posting, PostingType, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { ForwardedMessageService } from 'app/communication/service/forwarded-message.service';
import { MockForwardedMessageService } from 'test/helpers/mocks/service/mock-forwarded-message.service';
import { ForwardedMessage } from 'app/communication/shared/entities/forwarded-message.model';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';

describe('Metis Service', () => {
    let metisService: MetisService;
    let metisServiceUserStub: jest.SpyInstance;
    let metisServiceGetFilteredPostsSpy: jest.SpyInstance;
    let metisServiceCreateWebsocketSubscriptionSpy: jest.SpyInstance;
    let websocketServiceSubscribeSpy: jest.SpyInstance;
    let websocketService: WebsocketService;
    let reactionService: ReactionService;
    let postService: PostService;
    let forwardedMessageService: ForwardedMessageService;
    let answerPostService: AnswerPostService;
    let conversationService: ConversationService;
    let post: Post;
    let answerPost: AnswerPost;
    let reaction: Reaction;
    let course: Course;
    let savedPostService: SavedPostService;
    let setIsSavedAndStatusOfPostSpy: jest.SpyInstance;
    let originalPosts: Posting[];
    let targetConversation: Conversation;
    let newContent: string;
    let forwardedMessageCreateSpy: jest.SpyInstance;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(SessionStorageService),
                MockProvider(ConversationService),
                { provide: MetisService, useClass: MetisService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: PostService, useClass: MockPostService },
                { provide: ForwardedMessageService, useClass: MockForwardedMessageService },
                { provide: AnswerPostService, useClass: MockAnswerPostService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: WebsocketService, useClass: MockWebsocketService },
            ],
        });
        metisService = TestBed.inject(MetisService);
        websocketService = TestBed.inject(WebsocketService);
        reactionService = TestBed.inject(ReactionService);
        postService = TestBed.inject(PostService);
        forwardedMessageService = TestBed.inject(ForwardedMessageService);
        answerPostService = TestBed.inject(AnswerPostService);
        conversationService = TestBed.inject(ConversationService);
        savedPostService = TestBed.inject(SavedPostService);
        metisServiceGetFilteredPostsSpy = jest.spyOn(metisService, 'getFilteredPosts');
        metisServiceCreateWebsocketSubscriptionSpy = jest.spyOn(metisService, 'createWebsocketSubscription');
        metisServiceUserStub = jest.spyOn(metisService, 'getUser');
        // @ts-ignore method is private
        setIsSavedAndStatusOfPostSpy = jest.spyOn(metisService, 'setIsSavedAndStatusOfPost');
        httpMock = TestBed.inject(HttpTestingController);

        post = metisPostExerciseUser1;
        post.displayPriority = DisplayPriority.PINNED;
        answerPost = metisResolvingAnswerPostUser1;
        reaction = metisReactionUser2;
        course = metisCourse;
        originalPosts = [{ id: 111 }, { id: 222 }] as Posting[];
        targetConversation = { id: 999, type: ConversationType.CHANNEL } as Conversation;
        newContent = 'Forwarded content';

        forwardedMessageCreateSpy = jest.spyOn(forwardedMessageService, 'createForwardedMessage').mockImplementation((fm: ForwardedMessage) =>
            of(
                new HttpResponse({
                    body: { ...fm, id: Math.floor(Math.random() * 10000) } as ForwardedMessage,
                }),
            ),
        );
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

        it('should pin a post and add it to pinnedPosts$ when receiving a WebSocket update', fakeAsync(() => {
            let pinnedPostsResult: Post[] = [];
            const pinnedPostsSubscription = metisService.getPinnedPosts().subscribe((pinned) => {
                pinnedPostsResult = pinned;
            });

            const pinnedPostDTO: MetisPostDTO = {
                post: { id: 42, displayPriority: DisplayPriority.PINNED, authorRole: UserRole.USER, conversation: { id: 22 } as Conversation } as Post,
                action: MetisPostAction.UPDATE,
            };

            const pinnedFilter: PostContextFilter = { conversationIds: [22] };

            metisService['getFilteredPosts'](pinnedFilter);
            metisService['handleNewOrUpdatedMessage'](pinnedPostDTO);

            tick();

            expect(pinnedPostsResult).toHaveLength(1);
            expect(pinnedPostsResult[0].id).toBe(42);
            expect(pinnedPostsResult[0].displayPriority).toBe(DisplayPriority.PINNED);

            pinnedPostsSubscription.unsubscribe();
        }));

        it('should unpin a post and remove it from pinnedPosts$ when receiving a WebSocket update', fakeAsync(() => {
            const pinnedPost = { id: 42, displayPriority: DisplayPriority.PINNED } as Post;
            metisService['pinnedPosts$'].next([pinnedPost]);

            let pinnedPostsResult: Post[] = [];
            const pinnedPostsSubscription = metisService.getPinnedPosts().subscribe((pinned) => {
                pinnedPostsResult = pinned;
            });

            const unpinnedPostDTO: MetisPostDTO = {
                post: { id: 42, displayPriority: DisplayPriority.NONE, conversation: { id: 22 } as Conversation } as Post,
                action: MetisPostAction.UPDATE,
            };

            const pinnedFilter: PostContextFilter = { conversationIds: [22] };

            metisService['getFilteredPosts'](pinnedFilter);
            metisService['handleNewOrUpdatedMessage'](unpinnedPostDTO);
            tick();

            expect(pinnedPostsResult).toHaveLength(0);
            pinnedPostsSubscription.unsubscribe();
        }));

        it('should fetch pinned posts from server and update pinnedPosts$', fakeAsync(() => {
            const pinnedPostsMock: Post[] = [{ id: 100, displayPriority: DisplayPriority.PINNED } as Post, { id: 101, displayPriority: DisplayPriority.PINNED } as Post];
            const getPostsSpy = jest.spyOn(postService, 'getPosts').mockReturnValue(of(new HttpResponse({ body: pinnedPostsMock })));

            let pinnedPostsResult: Post[] = [];
            const subscription = metisService.getPinnedPosts().subscribe((pinned) => {
                pinnedPostsResult = pinned;
            });

            metisService.fetchAllPinnedPosts(post.conversation!.id!).subscribe((fetched) => {
                expect(fetched).toEqual(pinnedPostsMock);
            });

            tick();

            expect(pinnedPostsResult).toEqual(pinnedPostsMock);
            expect(getPostsSpy).toHaveBeenCalledOnce();

            subscription.unsubscribe();
        }));

        it('should ignore posts with invalid postContext when receiving WebSocket update', fakeAsync(() => {
            // Creating invalid postContext by not providing conversationId
            const post: Post = { id: 100 } as Post;
            const postDTO: MetisPostDTO = { post, action: MetisPostAction.CREATE } as MetisPostDTO;

            metisService['handleNewOrUpdatedMessage'](postDTO);
            tick();

            const cachedPostsStub = metisService.posts.subscribe((posts) => expect(posts).toEqual([]));

            tick();
            cachedPostsStub.unsubscribe();
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

        it('should get posts for course', () => {
            const postServiceSpy = jest.spyOn(postService, 'getPosts');
            metisService.getFilteredPosts({ courseId: course.id });
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

    it('should determine the router link required for referencing a faq', () => {
        metisService.setCourse(course);
        const link = metisService.getLinkForFaq();
        expect(link).toBe(`/courses/${metisCourse.id}/faq`);
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
        expect(contextInformation.routerLinkComponents).toEqual(['/courses', metisCourse.id, 'communication']);
        expect(contextInformation.displayName).not.toBeEmpty();
    });

    it('should return "Direct Message" when conversation is a one-to-one chat', () => {
        const dmConversation = { ...conversationBetweenUser1User2, type: ConversationType.ONE_TO_ONE };
        const directMessagePost: Post = {
            ...directMessageUser2,
            conversation: dmConversation,
        };
        metisService.setCourse(course);
        const contextInformation = metisService.getContextInformation(directMessagePost);

        expect(contextInformation.routerLinkComponents).toEqual(['/courses', course.id, 'communication']);
        expect(contextInformation.displayName).toBe('Direct Message');
        expect(contextInformation.queryParams).toEqual({ conversationId: dmConversation.id });
    });

    it('should return "Group Message" when conversation is a group chat', () => {
        const groupChatConversation = {
            id: 9999,
            type: ConversationType.GROUP_CHAT,
            conversationParticipants: [
                { id: 1, user: metisUser1, unreadMessagesCount: 0 },
                { id: 2, user: metisUser2, unreadMessagesCount: 0 },
                { id: 3, user: { id: 3, name: 'User3' }, unreadMessagesCount: 0 },
            ],
        };

        const groupChatPost: Post = {
            id: 8888,
            author: metisUser1,
            content: 'Hello Group Chat',
            conversation: groupChatConversation,
        } as Post;

        metisService.setCourse(course);
        const contextInformation = metisService.getContextInformation(groupChatPost);
        expect(contextInformation.routerLinkComponents).toEqual(['/courses', course.id, 'communication']);
        expect(contextInformation.displayName).toBe('Group Message');
        expect(contextInformation.queryParams).toEqual({ conversationId: groupChatConversation.id });
    });

    describe('Handle websocket related functionality', () => {
        beforeEach(() => {
            metisServiceCreateWebsocketSubscriptionSpy = jest.spyOn(metisService, 'createWebsocketSubscription');
            websocketServiceSubscribeSpy = jest.spyOn(websocketService, 'subscribe');
            metisService.setCourse(metisCourse);
        });

        it('should create websocket subscription when posts with lecture context are initially retrieved from DB', fakeAsync(() => {
            websocketServiceSubscribeSpy.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.CREATE } as MetisPostDTO));
            // setup subscription
            metisService.getFilteredPosts({ conversationIds: [metisPostInChannel.conversation!.id!] });
            metisServiceGetFilteredPostsSpy.mockReset();
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
        }));

        it('should create websocket subscription when posts with exercise context are initially retrieved from DB', fakeAsync(() => {
            websocketServiceSubscribeSpy.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.DELETE } as MetisPostDTO));
            metisService.setPageType(PageType.OVERVIEW);
            // setup subscription
            metisService.getFilteredPosts({ conversationIds: [metisPostInChannel.conversation!.id!], page: 0, pageSize: ITEMS_PER_PAGE });
            metisServiceGetFilteredPostsSpy.mockReset();
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
        }));

        it('should create websocket subscription when posts with course-wide context are initially retrieved from DB', fakeAsync(() => {
            websocketServiceSubscribeSpy.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.UPDATE } as MetisPostDTO));
            // setup subscription
            metisService.getFilteredPosts({ conversationIds: [metisPostInChannel.conversation!.id!] });
            metisServiceGetFilteredPostsSpy.mockReset();

            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
        }));

        it('should not create new subscription if already exists', fakeAsync(() => {
            websocketServiceSubscribeSpy.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.DELETE } as MetisPostDTO));
            // setup subscription for the first time
            metisService.getFilteredPosts({ conversationIds: [metisPostInChannel.conversation!.id!] });
            metisServiceGetFilteredPostsSpy.mockReset();
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(metisServiceGetFilteredPostsSpy).not.toHaveBeenCalled();
            metisService.getFilteredPosts({ conversationIds: [metisPostInChannel.conversation!.id!] });
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledExactlyOnceWith({ conversationIds: [metisPostInChannel.conversation!.id!] });
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
        }));

        it('subscribes to broadcast topic for course-wide channels', fakeAsync(() => {
            websocketServiceSubscribeSpy.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.CREATE } as MetisPostDTO));
            metisService.setPageType(PageType.OVERVIEW);
            // setup subscription
            metisService.getFilteredPosts({ conversationIds: [1], page: 0, pageSize: ITEMS_PER_PAGE }, true, {
                id: 1,
                type: ConversationType.CHANNEL,
                isCourseWide: true,
            } as ChannelDTO);
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({ conversationIds: [1], page: 0, pageSize: ITEMS_PER_PAGE }, true, {
                id: 1,
                type: ConversationType.CHANNEL,
                isCourseWide: true,
            } as ChannelDTO);
        }));

        it('subscribes to user specific topic for non-course-wide channels', fakeAsync(() => {
            websocketServiceSubscribeSpy.mockReturnValue(of({ post: metisPostInChannel, action: MetisPostAction.CREATE } as MetisPostDTO));
            metisService.setPageType(PageType.OVERVIEW);
            // setup subscription
            metisService.getFilteredPosts({ conversationIds: [1], page: 0, pageSize: ITEMS_PER_PAGE }, true, {
                id: 1,
                type: ConversationType.CHANNEL,
                isCourseWide: false,
            } as ChannelDTO);
            expect(metisServiceCreateWebsocketSubscriptionSpy).not.toHaveBeenCalled();
            expect(websocketServiceSubscribeSpy).toHaveBeenCalledOnce();
            // receive message on channel
            tick();
            expect(metisServiceGetFilteredPostsSpy).toHaveBeenCalledWith({ conversationIds: [1], page: 0, pageSize: ITEMS_PER_PAGE }, true, {
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
                websocketServiceSubscribeSpy.mockReturnValue(mockReceiveObservable.asObservable());
                metisService.setPageType(PageType.OVERVIEW);

                // set currentPostContextFilter appropriately
                metisService.getFilteredPosts({ plagiarismCaseId: 1 } as PostContextFilter);

                // Ensure subscribe to websocket was called
                expect(websocketServiceSubscribeSpy).toHaveBeenCalledTimes(2);

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
                websocketServiceSubscribeSpy.mockReturnValue(mockReceiveObservable.asObservable());
                metisService.setPageType(PageType.OVERVIEW);

                // set currentPostContextFilter appropriately
                metisService.getFilteredPosts({ conversationId: mockPostDTO.post.conversation?.id } as PostContextFilter);

                // Ensure subscribe to websocket was called
                expect(websocketService.subscribe).toHaveBeenCalledOnce();

                // Emulate receiving a message
                const getPostsSpy = jest.spyOn(postService, 'getPosts');
                const markAsReadSpy = jest.spyOn(conversationService, 'markAsRead');
                mockReceiveObservable.next(mockPostDTO);

                expect(markAsReadSpy).not.toHaveBeenCalled();

                // Ensure getPosts() was not called
                expect(getPostsSpy).not.toHaveBeenCalled();
            },
        );

        it('should update plagiarism posts received over WebSocket', () => {
            // Setup
            const post = { ...plagiarismPost };

            const mockPostDTO = {
                post: post,
                action: MetisPostAction.CREATE,
            };
            const mockReceiveObservable = new Subject();
            websocketServiceSubscribeSpy.mockReturnValue(mockReceiveObservable.asObservable());
            metisService.setPageType(PageType.PLAGIARISM_CASE_STUDENT);

            // set currentPostContextFilter appropriately
            metisService.getFilteredPosts({ plagiarismCaseId: mockPostDTO.post.plagiarismCase?.id, conversationIds: [22] } as PostContextFilter);

            // Ensure subscribe to websocket was not called
            expect(websocketService.subscribe).toHaveBeenCalled();

            // Emulate receiving a message
            mockReceiveObservable.next(mockPostDTO);
            expect(metisService['cachedPosts'].findIndex((post) => post.id === mockPostDTO.post.id)).toBeTruthy();
        });

        it('should update displayed conversation messages if new message does not match search text', fakeAsync(() => {
            // Setup
            const channel = 'someChannel';
            const mockPostDTO = {
                post: { ...metisPostInChannel, content: 'search Text' },
                action: MetisPostAction.CREATE,
            };
            const mockReceiveObservable = new Subject();
            websocketServiceSubscribeSpy.mockReturnValue(mockReceiveObservable.asObservable());
            metisService.setPageType(PageType.OVERVIEW);
            metisService.createWebsocketSubscription(channel);

            jest.spyOn(postService, 'getPosts').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [mockPostDTO.post],
                        headers: new HttpHeaders({
                            'X-Total-Count': 1,
                        }),
                    }),
                ),
            );

            // set currentPostContextFilter with search text
            metisService.getFilteredPosts({ conversationIds: [mockPostDTO.post.conversation?.id], searchText: 'Search text' } as PostContextFilter);

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
            metisService.getFilteredPosts({ conversationIds: [metisLectureChannelDTO.id] } as PostContextFilter, false, metisLectureChannelDTO);
            expect(metisService.getCurrentConversation()).toBe(metisLectureChannelDTO);
        });
    });

    it('should call ForwardedMessageService.getForwardedMessages with correct parameters when type is valid', fakeAsync(() => {
        metisService.setCourse(course);
        const forwardedMessageServiceSpy = jest.spyOn(forwardedMessageService, 'getForwardedMessages');
        const postIds = [1, 2, 3];

        metisService.getForwardedMessagesByIds(postIds, PostingType.POST);
        expect(forwardedMessageServiceSpy).toHaveBeenCalledWith(postIds, PostingType.POST);
        forwardedMessageServiceSpy.mockClear();

        metisService.getForwardedMessagesByIds(postIds, PostingType.ANSWER);
        expect(forwardedMessageServiceSpy).toHaveBeenCalledWith(postIds, PostingType.ANSWER);
        tick();
    }));

    it('should not call ForwardedMessageService.getForwardedMessages if IDs array is empty or undefined', fakeAsync(() => {
        const forwardedMessageServiceSpy = jest.spyOn(forwardedMessageService, 'getForwardedMessages');

        metisService.getForwardedMessagesByIds([], PostingType.POST);
        expect(forwardedMessageServiceSpy).not.toHaveBeenCalled();

        metisService.getForwardedMessagesByIds([], PostingType.ANSWER);
        expect(forwardedMessageServiceSpy).not.toHaveBeenCalled();

        metisService.getForwardedMessagesByIds(undefined as any, PostingType.POST);
        expect(forwardedMessageServiceSpy).not.toHaveBeenCalled();
        tick();
    }));

    it('should call PostService.getSourcePostsByIds with correct parameters', fakeAsync(() => {
        const postServiceSpy = jest.spyOn(postService, 'getSourcePostsByIds');
        const postIds = [4, 5, 6];

        metisService.getSourcePostsByIds(postIds);

        expect(postServiceSpy).toHaveBeenCalledWith(metisService['courseId'], postIds);
        tick();
    }));

    it('should return undefined if no source post is found with the given ids (404 error)', fakeAsync(() => {
        const postIds = [4, 5, 6];
        const postServiceSpy = jest.spyOn(postService, 'getSourcePostsByIds').mockReturnValue(throwError(() => ({ status: 404 })));
        let result: Post[] | undefined;
        metisService.getSourcePostsByIds(postIds).subscribe((res) => {
            result = res;
        });
        tick();
        expect(postServiceSpy).toHaveBeenCalledWith(metisService['courseId'], postIds);
        expect(result).toBeUndefined();
    }));

    it('should call AnswerPostService.getSourceAnswerPostsByIds with correct parameters', fakeAsync(() => {
        const postServiceSpy = jest.spyOn(answerPostService, 'getSourceAnswerPostsByIds');
        const answerPostIds = [7, 8, 9];

        metisService.getSourceAnswerPostsByIds(answerPostIds);

        expect(postServiceSpy).toHaveBeenCalledWith(metisService['courseId'], answerPostIds);
        tick();
    }));

    it('should return undefined if no source answer post is found with the given ids (404 error)', fakeAsync(() => {
        const postIds = [4, 5, 6];
        const answerPostServiceSpy = jest.spyOn(answerPostService, 'getSourceAnswerPostsByIds').mockReturnValue(throwError(() => ({ status: 404 })));
        let result: Post[] | undefined;
        metisService.getSourceAnswerPostsByIds(postIds).subscribe((res) => {
            result = res;
        });
        tick();
        expect(answerPostServiceSpy).toHaveBeenCalledWith(metisService['courseId'], postIds);
        expect(result).toBeUndefined();
    }));

    it('should not call getSourcePostsByIds if postId list is undefined', fakeAsync(() => {
        const postIds: number[] | undefined = undefined;
        metisService.getSourcePostsByIds(postIds as any);
        const postServiceSpy = jest.spyOn(postService, 'getSourcePostsByIds');

        expect(postServiceSpy).not.toHaveBeenCalled();
        tick();
    }));

    it('should not call getSourceAnswerPostsByIds if answerPostIds is undefined', () => {
        const answerPostIds: number[] | undefined = undefined;
        metisService.getSourceAnswerPostsByIds(answerPostIds as any);
        const answerPostServiceSpy = jest.spyOn(answerPostService, 'getSourceAnswerPostsByIds');
        expect(answerPostServiceSpy).not.toHaveBeenCalled();
    });

    it('should create forwarded messages and update cached posts', fakeAsync(() => {
        const originalPosts: Posting[] = [{ id: 1 }, { id: 2 }] as Posting[];
        metisService.setCourse(course);
        const targetConversation: Conversation = { id: 1, type: ConversationType.CHANNEL } as Conversation;
        const isAnswer = false;
        const newContent = 'Forwarded content';

        const createdPost: Post = { id: 100, content: newContent, conversation: targetConversation } as Post;
        jest.spyOn(postService, 'create').mockReturnValue(of(new HttpResponse({ body: createdPost })));

        let result: ForwardedMessage[] | undefined;
        metisService.createForwardedMessages(originalPosts, targetConversation, isAnswer, newContent).subscribe((res) => (result = res));
        tick();

        expect(postService.create).toHaveBeenCalledWith(expect.any(Number), expect.objectContaining({ content: newContent }));
        expect(forwardedMessageService.createForwardedMessage).toHaveBeenCalledTimes(originalPosts.length);
        expect(result?.length).toBe(originalPosts.length);
        expect(result?.every((fm) => fm.destinationPost?.id === createdPost.id)).toBeTrue();
    }));

    it('should throw an error if course ID is not set before forwarding', fakeAsync(() => {
        metisService.setCourse(undefined);
        let errorThrown: Error | undefined;
        metisService.createForwardedMessages(originalPosts, targetConversation, false, newContent).subscribe({
            error: (err) => {
                errorThrown = err;
            },
        });

        tick();
        expect(errorThrown).toBeDefined();
        expect(errorThrown?.message).toContain('Course ID is not set');
        expect(forwardedMessageCreateSpy).not.toHaveBeenCalled();
    }));

    it('should fail if any forwardedMessageService.createForwardedMessage fails', fakeAsync(() => {
        metisService.setCourse(metisCourse);
        forwardedMessageCreateSpy
            .mockReturnValueOnce(
                of(
                    new HttpResponse({
                        body: { id: 5678, sourcePostId: 111 } as unknown as ForwardedMessage,
                    }),
                ),
            )
            .mockReturnValueOnce(throwError(() => new Error('Some forwardedMessage creation error')));

        let errorThrown: Error | undefined;
        metisService.createForwardedMessages(originalPosts, targetConversation, false, newContent).subscribe({
            error: (err) => {
                errorThrown = err;
            },
        });

        tick();
        expect(errorThrown).toBeDefined();
        expect(errorThrown?.message).toBe('Some forwardedMessage creation error');
        expect(forwardedMessageCreateSpy).toHaveBeenCalledTimes(2);
    }));

    it('should NOT update local cache if target conversation differs from currentConversation', fakeAsync(() => {
        metisService.setCourse(metisCourse);
        metisService.getFilteredPosts({ conversationIds: [101] } as PostContextFilter, false, { id: 101 } as ChannelDTO);

        let result: ForwardedMessage[] | undefined;
        metisService.createForwardedMessages(originalPosts, targetConversation, false, newContent).subscribe((res) => (result = res));
        tick();

        expect(result).toHaveLength(originalPosts.length);
        expect(metisService['cachedPosts'].findIndex((p) => p.id === 123)).toBe(-1);
    }));

    it('should NOT add newly created post to cache if it already exists in cache', fakeAsync(() => {
        metisService.setCourse(metisCourse);
        metisService.getFilteredPosts({ conversationIds: [targetConversation.id] } as PostContextFilter, false, targetConversation);

        metisService['cachedPosts'] = [{ id: 123, content: 'cached content', conversation: targetConversation } as Post];

        let result: ForwardedMessage[] | undefined;
        metisService.createForwardedMessages(originalPosts, targetConversation, false, newContent).subscribe((res) => (result = res));
        tick();

        expect(result).toHaveLength(originalPosts.length);
        const matchesInCache = metisService['cachedPosts'].filter((p) => p.id === 123);
        expect(matchesInCache).toHaveLength(1);
    }));

    it('should create forwarded messages with sourceType=ANSWER if isAnswer=true', fakeAsync(() => {
        metisService.setCourse(course);
        const targetConversation: Conversation = { id: 2 } as Conversation;
        const originalAnswerPosts: Posting[] = [{ id: 10, content: 'originalAnswer1' } as Posting, { id: 11, content: 'originalAnswer2' } as Posting];

        const createdPost: Post = { id: 200, content: 'Forwarded answer container', hasForwardedMessages: true } as Post;
        jest.spyOn(postService, 'create').mockReturnValue(of(new HttpResponse({ body: createdPost })));

        const createFwSpy = jest.spyOn(forwardedMessageService, 'createForwardedMessage').mockReturnValue(of(new HttpResponse({ body: { id: 1234 } as ForwardedMessage })));

        let result: ForwardedMessage[] = [];
        metisService.createForwardedMessages(originalAnswerPosts, targetConversation, true, 'some content').subscribe((res) => (result = res));
        tick();

        expect(postService.create).toHaveBeenCalled();
        expect(createFwSpy).toHaveBeenCalledTimes(originalAnswerPosts.length);

        createFwSpy.mock.calls.forEach(([argForwardedMessage]) => {
            expect(argForwardedMessage.sourceType).toBe(PostingType.ANSWER.toString());
        });

        expect(result).toHaveLength(originalAnswerPosts.length);
    }));

    describe('Save post methods', () => {
        it('should save a post and update cached posts', () => {
            const savedPostServiceSpy = jest.spyOn(savedPostService, 'savePost');

            metisService.createPost(post).subscribe();
            metisService.savePost(post);

            expect(setIsSavedAndStatusOfPostSpy).toHaveBeenCalledWith(post, true, post.savedPostStatus);
            expect(savedPostServiceSpy).toHaveBeenCalledWith(post);
        });

        it('should remove a saved post and update cached posts', () => {
            const savedPostServiceSpy = jest.spyOn(savedPostService, 'removeSavedPost');

            metisService.createPost(post).subscribe();
            metisService.removeSavedPost(post);

            expect(setIsSavedAndStatusOfPostSpy).toHaveBeenCalledWith(post, false, post.savedPostStatus);
            expect(savedPostServiceSpy).toHaveBeenCalledWith(post);
        });

        it('should change the saved post status and update cached posts', () => {
            const status = SavedPostStatus.ARCHIVED;
            const savedPostServiceSpy = jest.spyOn(savedPostService, 'changeSavedPostStatus');

            metisService.createPost(post).subscribe();
            metisService.changeSavedPostStatus(post, status);

            expect(setIsSavedAndStatusOfPostSpy).toHaveBeenCalledWith(post, post.isSaved, status);
            expect(savedPostServiceSpy).toHaveBeenCalled();
        });

        it('should reset cached posts and update total number of posts', () => {
            const spyPostsNext = jest.spyOn(metisService['posts$'], 'next');
            const spyNumberOfPostsNext = jest.spyOn(metisService['totalNumberOfPosts$'], 'next');
            metisService.resetCachedPosts();

            expect(metisService['cachedPosts']).toEqual([]);
            expect(spyPostsNext).toHaveBeenCalledWith([]);
            expect(metisService['cachedTotalNumberOfPosts']).toBe(0);
            expect(spyNumberOfPostsNext).toHaveBeenCalledWith(0);
        });

        it('should remove pinned post from pinnedPosts$ when WebSocket DELETE action is received', () => {
            const pinnedPost = { id: 123, displayPriority: DisplayPriority.PINNED, conversation: { id: 22 } } as Post;
            const mockDeleteDTO: MetisPostDTO = {
                action: MetisPostAction.DELETE,
                post: { ...pinnedPost },
            };

            metisService['pinnedPosts$'].next([pinnedPost]);
            metisService['cachedPosts'].push({ ...pinnedPost });
            metisService['getFilteredPosts']({ conversationIds: [22] } as PostContextFilter);
            metisService['handleNewOrUpdatedMessage'](mockDeleteDTO);

            let pinnedPostsResult: Post[] = [];
            const pinnedSubscription = metisService.getPinnedPosts().subscribe((pinned) => {
                pinnedPostsResult = pinned;
            });

            expect(pinnedPostsResult).toHaveLength(0);
            pinnedSubscription.unsubscribe();
        });

        it('should remove a post from pinnedPosts$ if the given post ID exists in the pinned list', () => {
            const pinnedPost = { id: 999, displayPriority: DisplayPriority.PINNED } as Post;
            metisService['pinnedPosts$'].next([pinnedPost]);

            let pinnedPostsResult: Post[] = [];
            const pinnedSub = metisService.getPinnedPosts().subscribe((pinned) => {
                pinnedPostsResult = pinned;
            });

            metisService['removeFromPinnedPosts'](999);
            expect(pinnedPostsResult).toHaveLength(0);

            pinnedSub.unsubscribe();
        });

        it('should not modify pinnedPosts$ if the given post ID is not in the pinned list', () => {
            const pinnedPost = { id: 999, displayPriority: DisplayPriority.PINNED } as Post;
            metisService['pinnedPosts$'].next([pinnedPost]);

            let pinnedPostsResult: Post[] = [];
            const pinnedSub = metisService.getPinnedPosts().subscribe((pinned) => {
                pinnedPostsResult = pinned;
            });

            metisService['removeFromPinnedPosts'](123);

            expect(pinnedPostsResult).toHaveLength(1);
            expect(pinnedPostsResult[0]).toEqual(pinnedPost);

            pinnedSub.unsubscribe();
        });

        it('should update a pinned post when receiving a WebSocket update', fakeAsync(() => {
            const pinnedPost = {
                id: 42,
                displayPriority: DisplayPriority.PINNED,
                content: 'Old Content',
                authorRole: UserRole.USER,
                tags: [],
                conversation: { id: 22 },
            } as Post;

            metisService['cachedPosts'] = [pinnedPost];
            metisService['pinnedPosts$'].next([pinnedPost]);

            const updatedPost = {
                id: 42,
                displayPriority: DisplayPriority.PINNED,
                content: 'Updated Content',
                tags: ['newTag'],
                conversation: { id: 22 },
            } as Post;
            const updateDTO: MetisPostDTO = {
                post: updatedPost,
                action: MetisPostAction.UPDATE,
            };

            metisService['getFilteredPosts']({ conversationIds: [22] } as PostContextFilter);
            metisService['handleNewOrUpdatedMessage'](updateDTO);
            tick();

            let pinnedPostsResult: Post[] = [];
            metisService.getPinnedPosts().subscribe((pinned) => (pinnedPostsResult = pinned));

            expect(pinnedPostsResult).toHaveLength(1);
            expect(pinnedPostsResult[0].id).toBe(42);
            expect(pinnedPostsResult[0].content).toBe('Updated Content');
        }));
    });

    it('should properly set answer.post properties when receiving a post update via WebSocket', fakeAsync(() => {
        // Set up test data
        metisService.setCourse(course);

        // Create a post with answers in the cached posts
        const originalPost: Post = {
            id: 456,
            content: 'Original content',
            author: { id: 789, login: 'author' },
            conversation: { id: 123 },
            answers: [{ id: 100, content: 'Answer 1' } as AnswerPost, { id: 101, content: 'Answer 2' } as AnswerPost],
        } as Post;

        metisService['cachedPosts'] = [originalPost];
        metisService['currentPostContextFilter'] = { conversationIds: [123] } as PostContextFilter;

        // Create an updated post DTO (as would be received from WebSocket)
        const updatedPost: Post = {
            id: 456,
            content: 'Updated content',
            author: { id: 789, login: 'author' },
            conversation: { id: 123 },
            answers: [{ id: 100, content: 'Updated Answer 1', post: { id: 456 } } as AnswerPost, { id: 101, content: 'Updated Answer 2', post: { id: 456 } } as AnswerPost],
        } as Post;

        const updateDTO: MetisPostDTO = {
            action: MetisPostAction.UPDATE,
            post: updatedPost,
        };

        // Call the method that handles WebSocket updates
        metisService['handleNewOrUpdatedMessage'](updateDTO);
        tick();

        // Verify that the answer.post properties are set correctly for all answers
        const updatedCachedPost = metisService['cachedPosts'][0];
        expect(updatedCachedPost.answers?.length).toBe(2);

        updatedCachedPost.answers?.forEach((answer) => {
            expect(answer.post).toBeDefined();
            expect(answer.post?.id).toBe(456);
            expect(answer.post?.author).toEqual({ id: 789, login: 'author' });
            expect(answer.post?.conversation).toEqual({ id: 123 });
        });
    }));

    it('should make PUT request to enable communication with messaging', () => {
        metisService.enable(1, true).subscribe((resp) => expect(resp).toEqual(of()));
        httpMock.expectOne({ method: 'PUT', url: `api/communication/courses/1/enable?withMessaging=true` });
    });

    it('should make PUT request to enable communication without messaging', () => {
        metisService.enable(1, false).subscribe((resp) => expect(resp).toEqual(of()));
        httpMock.expectOne({ method: 'PUT', url: `api/communication/courses/1/enable?withMessaging=false` });
    });
});
