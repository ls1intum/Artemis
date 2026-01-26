import { TestBed, fakeAsync } from '@angular/core/testing';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { MockProvider } from 'ng-mocks';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';
import { AlertService } from 'app/shared/service/alert.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, forkJoin, of } from 'rxjs';
import { ConversationDTO } from '../shared/entities/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationWebsocketDTO } from 'app/communication/shared/entities/conversation/conversation-websocket-dto.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MetisPostAction } from 'app/communication/metis.util';
import dayjs from 'dayjs/esm';
import { MetisPostDTO } from 'app/communication/shared/entities/metis-post-dto.model';
import { Post } from 'app/communication/shared/entities/post.model';

describe('MetisConversationService', () => {
    let metisConversationService: MetisConversationService;
    let conversationService: ConversationService;
    let groupChatService: GroupChatService;
    let oneToOneChatService: OneToOneChatService;
    let channelService: ChannelService;
    let websocketService: WebsocketService;
    let courseManagementService: CourseManagementService;
    let alertService: AlertService;

    const course = { id: 1 } as Course;
    let groupChat: GroupChatDTO;
    let oneToOneChat: OneToOneChatDTO;
    let channel: ChannelDTO;
    let receiveMockSubject: Subject<ConversationWebsocketDTO>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: MetisConversationService, useClass: MetisConversationService },
                MockProvider(CourseManagementService),
                MockProvider(GroupChatService),
                MockProvider(ChannelService),
                MockProvider(OneToOneChatService),
                MockProvider(ConversationService),
                MockProvider(WebsocketService),
                MockProvider(AlertService),
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        groupChat = generateExampleGroupChatDTO({ id: 1 });
        oneToOneChat = generateOneToOneChatDTO({ id: 2 });
        channel = generateExampleChannelDTO({ id: 3 } as ChannelDTO);
        metisConversationService = TestBed.inject(MetisConversationService);
        groupChatService = TestBed.inject(GroupChatService);
        oneToOneChatService = TestBed.inject(OneToOneChatService);
        channelService = TestBed.inject(ChannelService);
        websocketService = TestBed.inject(WebsocketService);
        courseManagementService = TestBed.inject(CourseManagementService);
        conversationService = TestBed.inject(ConversationService);
        alertService = TestBed.inject(AlertService);

        jest.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of(new HttpResponse<Course>({ body: course })));
        jest.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel] })));
        jest.spyOn(conversationService, 'convertServerDates').mockImplementation((conversation) => conversation);

        receiveMockSubject = new Subject<ConversationWebsocketDTO>();
        jest.spyOn(websocketService, 'subscribe').mockReturnValue(receiveMockSubject.asObservable());
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(metisConversationService).toBeTruthy();
    });

    it('should set up the service correctly', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    expect(metisConversationService.course).toEqual(course);
                    forkJoin([metisConversationService.isLoading$, metisConversationService.activeConversation$, metisConversationService.conversationsOfUser$]).subscribe({
                        next: ([isLoading, activeConversation, conversations]) => {
                            expect(isLoading).toBeFalse();
                            expect(activeConversation).toBeUndefined();
                            expect(conversations).toEqual([groupChat, oneToOneChat, channel]);
                            done({});
                        },
                    });
                    metisConversationService._isLoading$.complete();
                    metisConversationService._activeConversation$.complete();
                    metisConversationService._conversationsOfUser$.complete();
                },
            });
        });
    });

    it('should set active conversation', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    metisConversationService.setActiveConversation(groupChat);
                    metisConversationService.activeConversation$.subscribe((activeConversation) => {
                        expect(activeConversation).toEqual(groupChat);
                        done({});
                    });
                },
            });
        });
    });

    it('should set active conversation by id', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    metisConversationService.setActiveConversation(groupChat.id);
                    metisConversationService.activeConversation$.subscribe((activeConversation) => {
                        expect(activeConversation).toEqual(groupChat);
                        done({});
                    });
                },
            });
        });
    });

    it('should set has unread messages to true', () => {
        groupChat.unreadMessagesCount = 1;
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    metisConversationService.setActiveConversation(groupChat);
                    metisConversationService.hasUnreadMessages$.pipe().subscribe((hasUnreadMessages) => {
                        expect(hasUnreadMessages).toBeTrue();
                        done({});
                    });
                },
            });
        });
    });

    it("should show alert if channel doesn't exist", () => {
        groupChat.unreadMessagesCount = 1;
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const addAlertSpy = jest.spyOn(alertService, 'addAlert');

                    metisConversationService.setActiveConversation(4);
                    expect(addAlertSpy).toHaveBeenCalledOnce();
                    done({});
                },
            });
        });
    });

    it('should get conversations of users again if force refresh is called', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    metisConversationService.setActiveConversation(groupChat);
                    const getConversationsOfUserSpy = jest.spyOn(conversationService, 'getConversationsOfUser');
                    getConversationsOfUserSpy.mockClear();
                    metisConversationService.forceRefresh().subscribe({
                        complete: () => {
                            expect(getConversationsOfUserSpy).toHaveBeenCalledOnce();
                            expect(getConversationsOfUserSpy).toHaveBeenCalledWith(1);
                            done({});
                        },
                    });
                },
            });
        });
    });

    it('should set active conversation to newly created channel', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const newChannel = generateExampleChannelDTO({ id: 99 } as ChannelDTO);
                    const createChannelSpy = jest.spyOn(channelService, 'create').mockReturnValue(of(new HttpResponse({ body: newChannel })));
                    const getConversationSpy = jest
                        .spyOn(conversationService, 'getConversationsOfUser')
                        .mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newChannel] })));
                    createChannelSpy.mockClear();
                    metisConversationService.createChannel(newChannel).subscribe({
                        complete: () => {
                            expect(createChannelSpy).toHaveBeenCalledOnce();
                            expect(createChannelSpy).toHaveBeenCalledWith(course.id, newChannel);
                            metisConversationService.activeConversation$.subscribe((activeConversation) => {
                                expect(activeConversation).toBe(newChannel);
                                expect(getConversationSpy).toHaveBeenCalledTimes(2);
                                done({});
                            });
                        },
                    });
                },
            });
        });
    });

    it('should set active conversation to newly created groupChat', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const newGroupChat = generateExampleGroupChatDTO({ id: 99 });
                    const createGroupChatSpy = jest.spyOn(groupChatService, 'create').mockReturnValue(of(new HttpResponse({ body: newGroupChat })));
                    const getConversationSpy = jest
                        .spyOn(conversationService, 'getConversationsOfUser')
                        .mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newGroupChat] })));
                    createGroupChatSpy.mockClear();
                    metisConversationService.createGroupChat(['login']).subscribe({
                        complete: () => {
                            expect(createGroupChatSpy).toHaveBeenCalledOnce();
                            expect(createGroupChatSpy).toHaveBeenCalledWith(course.id, ['login']);
                            metisConversationService.activeConversation$.subscribe((activeConversation) => {
                                expect(activeConversation).toBe(newGroupChat);
                                expect(getConversationSpy).toHaveBeenCalledTimes(2);
                                done({});
                            });
                        },
                    });
                },
            });
        });
    });

    it('should set active conversation to newly created one to one chat', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const newOneToOneChat = generateOneToOneChatDTO({ id: 99 });
                    const createOneToOneChatSpy = jest.spyOn(oneToOneChatService, 'create').mockReturnValue(of(new HttpResponse({ body: newOneToOneChat })));
                    const getConversationSpy = jest
                        .spyOn(conversationService, 'getConversationsOfUser')
                        .mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newOneToOneChat] })));
                    createOneToOneChatSpy.mockClear();
                    metisConversationService.createOneToOneChat('login').subscribe({
                        complete: () => {
                            expect(createOneToOneChatSpy).toHaveBeenCalledOnce();
                            expect(createOneToOneChatSpy).toHaveBeenCalledWith(course.id, 'login');
                            metisConversationService.activeConversation$.subscribe((activeConversation) => {
                                expect(activeConversation).toBe(newOneToOneChat);
                                expect(getConversationSpy).toHaveBeenCalledTimes(2);
                                done({});
                            });
                        },
                    });
                },
            });
        });
    });

    it('should set active conversation to newly created one to one chat when calling with id', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const newOneToOneChat = generateOneToOneChatDTO({ id: 99 });
                    const createOneToOneChatSpy = jest.spyOn(oneToOneChatService, 'createWithId').mockReturnValue(of(new HttpResponse({ body: newOneToOneChat })));
                    const getConversationSpy = jest
                        .spyOn(conversationService, 'getConversationsOfUser')
                        .mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newOneToOneChat] })));
                    createOneToOneChatSpy.mockClear();
                    metisConversationService.createOneToOneChatWithId(1).subscribe({
                        complete: () => {
                            expect(createOneToOneChatSpy).toHaveBeenCalledOnce();
                            expect(createOneToOneChatSpy).toHaveBeenCalledWith(course.id, 1);
                            metisConversationService.activeConversation$.subscribe((activeConversation) => {
                                expect(activeConversation).toBe(newOneToOneChat);
                                expect(getConversationSpy).toHaveBeenCalledTimes(2);
                                done({});
                            });
                        },
                    });
                },
            });
        });
    });

    it('should add new conversation to conversations of user on conversation create received', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const websocketDTO = new ConversationWebsocketDTO();
                    websocketDTO.action = MetisPostAction.CREATE;
                    websocketDTO.conversation = generateExampleChannelDTO({ id: 99 } as ChannelDTO);

                    receiveMockSubject.next(websocketDTO);
                    metisConversationService.conversationsOfUser$.subscribe((conversationsOfUser) => {
                        expect(conversationsOfUser).toEqual([groupChat, oneToOneChat, channel, websocketDTO.conversation]);
                        done({});
                    });
                },
            });
        });
    });

    it('should update conversation in conversations of user on conversation update received', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const websocketDTO = new ConversationWebsocketDTO();
                    websocketDTO.action = MetisPostAction.UPDATE;
                    websocketDTO.conversation = { ...channel, name: 'newtitle' } as ChannelDTO;

                    receiveMockSubject.next(websocketDTO);
                    metisConversationService.conversationsOfUser$.subscribe((conversationsOfUser) => {
                        expect(conversationsOfUser).toContainEqual(websocketDTO.conversation);
                        expect(conversationsOfUser).not.toContainEqual(channel);
                        expect(conversationsOfUser).toContainEqual(groupChat);
                        expect(conversationsOfUser).toContainEqual(oneToOneChat);
                        expect(conversationsOfUser).toHaveLength(3);
                        done({});
                    });
                },
            });
        });
    });

    it('should update conversation last message date in conversations of user on conversation new message received', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const websocketDTO = new ConversationWebsocketDTO();
                    websocketDTO.action = MetisPostAction.NEW_MESSAGE;
                    // 1 of january 2022
                    const lastMessageDate = dayjs('2022-01-01T00:00:00.000Z');
                    websocketDTO.conversation = { ...channel, lastMessageDate } as ChannelDTO;

                    receiveMockSubject.next(websocketDTO);
                    metisConversationService.conversationsOfUser$.subscribe((conversationsOfUser) => {
                        // find updated conversation in cache
                        const updatedConversation = conversationsOfUser.find((conversation) => conversation.id === channel.id);
                        expect(updatedConversation!.lastMessageDate!.isSame(lastMessageDate)).toBeTrue();
                        done({});
                    });
                },
            });
        });
    });

    it('should remove conversation in conversations of user on conversation delete received', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const websocketDTO = new ConversationWebsocketDTO();
                    websocketDTO.action = MetisPostAction.DELETE;
                    websocketDTO.conversation = { ...channel } as ChannelDTO;

                    receiveMockSubject.next(websocketDTO);
                    metisConversationService.conversationsOfUser$.subscribe((conversationsOfUser) => {
                        expect(conversationsOfUser).not.toContainEqual(channel);
                        expect(conversationsOfUser).toContainEqual(groupChat);
                        expect(conversationsOfUser).toContainEqual(oneToOneChat);
                        expect(conversationsOfUser).toHaveLength(2);
                        done({});
                    });
                },
            });
        });
    });

    it.each([true, false])('should update subscription for unread messages', (unreadMessages: boolean) => {
        jest.spyOn(conversationService, 'checkForUnreadMessages').mockReturnValue(of(new HttpResponse<boolean>({ body: unreadMessages })));
        let numberOfSubscriptions = 0;

        metisConversationService.hasUnreadMessages$.pipe().subscribe((hasUnreadMessages: boolean) => {
            expect(hasUnreadMessages).toBeTrue();
            numberOfSubscriptions++;
        });

        metisConversationService.checkForUnreadMessages(course);

        expect(numberOfSubscriptions).toBe(unreadMessages ? 1 : 0);
    });

    it('should set code of conduct', () => {
        metisConversationService.setCodeOfConduct();
        metisConversationService.isCodeOfConductPresented$.subscribe((isCodeOfConductPresented: boolean) => {
            expect(isCodeOfConductPresented).toBeTrue();
        });
    });

    it('should check and accept code of conduct', () => {
        const checkStub = jest.spyOn(conversationService, 'checkIsCodeOfConductAccepted').mockReturnValue(of(new HttpResponse<boolean>({ body: false })));
        metisConversationService.checkIsCodeOfConductAccepted(course);
        metisConversationService.isCodeOfConductAccepted$.subscribe((isCodeOfConductAccepted: boolean) => {
            expect(isCodeOfConductAccepted).toBeFalse();
        });
        expect(checkStub).toHaveBeenCalledOnce();

        const acceptStub = jest.spyOn(conversationService, 'acceptCodeOfConduct').mockReturnValue(of(new HttpResponse<void>({})));
        metisConversationService.acceptCodeOfConduct(course);
        metisConversationService.isCodeOfConductAccepted$.subscribe((isCodeOfConductAccepted: boolean) => {
            expect(isCodeOfConductAccepted).toBeTrue();
        });
        expect(acceptStub).toHaveBeenCalledOnce();
    });

    it('should handle new message', fakeAsync(() => {
        const postDTO: MetisPostDTO = {
            post: { author: { id: 456 }, content: 'Content', conversation: { id: 1 } } as Post,
            action: MetisPostAction.CREATE,
        };
        metisConversationService['conversationsOfUser'] = [{ id: 1, unreadMessageCount: 0 } as ConversationDTO];
        metisConversationService.handleNewMessage(postDTO.post.conversation?.id, postDTO.post.conversation?.lastMessageDate);
        expect(metisConversationService['conversationsOfUser'][0].unreadMessagesCount).toBe(1);
    }));

    it('should mark messages as read', () => {
        metisConversationService['conversationsOfUser'] = [{ id: 1, unreadMessageCount: 1 } as ConversationDTO, { id: 2, unreadMessageCount: 1 } as ConversationDTO];
        metisConversationService.markAsRead(2);
        expect(metisConversationService['conversationsOfUser'][1].unreadMessagesCount).toBe(0);
    });

    it('should call refresh after marking all channels as read', () => {
        const markAllChannelAsReadSpy = jest.spyOn(conversationService, 'markAllChannelsAsRead').mockReturnValue(of());
        metisConversationService.markAllChannelsAsRead(course);
        expect(markAllChannelAsReadSpy).toHaveBeenCalledOnce();
    });

    describe('updateLastReadDateAndNumberOfUnreadMessages', () => {
        it('should update last read date and unread messages count for active conversation', () => {
            (metisConversationService as any).activeConversation = groupChat;
            (metisConversationService as any).conversationsOfUser = [groupChat];
            groupChat.unreadMessagesCount = 5;
            groupChat.hasUnreadMessage = true;

            const nextSpy = jest.spyOn((metisConversationService as any)._conversationsOfUser$, 'next');

            (metisConversationService as any).updateLastReadDateAndNumberOfUnreadMessages();

            expect((metisConversationService as any).activeConversation.unreadMessagesCount).toBe(0);
            expect((metisConversationService as any).activeConversation.hasUnreadMessage).toBeFalse();
            expect((metisConversationService as any).activeConversation.lastReadDate).toBeDefined();

            expect((metisConversationService as any).conversationsOfUser[0].unreadMessagesCount).toBe(0);
            expect((metisConversationService as any).conversationsOfUser[0].hasUnreadMessage).toBeFalse();
            expect((metisConversationService as any).conversationsOfUser[0].lastReadDate).toBeDefined();

            expect(nextSpy).toHaveBeenCalledWith((metisConversationService as any).conversationsOfUser);
        });

        it('should not update anything if there is no active conversation', () => {
            (metisConversationService as any).activeConversation = undefined;

            const nextSpy = jest.spyOn((metisConversationService as any)._conversationsOfUser$, 'next');

            (metisConversationService as any).updateLastReadDateAndNumberOfUnreadMessages();

            expect(nextSpy).not.toHaveBeenCalled();
        });

        it('should not update conversationsOfUser if active conversation is not found in the array', () => {
            const nonExistentConversation = { ...groupChat, id: 999 };
            (metisConversationService as any).activeConversation = nonExistentConversation;

            const nextSpy = jest.spyOn((metisConversationService as any)._conversationsOfUser$, 'next');

            (metisConversationService as any).updateLastReadDateAndNumberOfUnreadMessages();

            expect(nextSpy).not.toHaveBeenCalled();

            expect((metisConversationService as any).activeConversation.unreadMessagesCount).toBe(0);
            expect((metisConversationService as any).activeConversation.hasUnreadMessage).toBeFalse();
        });
    });

    it('should return correct params object with conversationId', () => {
        const conversationId = 42;
        const result = MetisConversationService.getQueryParamsForConversation(conversationId);
        expect(result).toEqual({ conversationId: 42 });
    });

    it('should return correct route components for given courseId', () => {
        const courseId = 123;
        const result = MetisConversationService.getLinkForConversation(courseId);
        expect(result).toEqual(['/courses', 123, 'communication']);
    });

    describe('markAllChannelsAsRead', () => {
        it('should update all conversations and call service', () => {
            (metisConversationService as any).conversationsOfUser = [
                { ...groupChat, unreadMessagesCount: 3, hasUnreadMessage: true },
                { ...oneToOneChat, unreadMessagesCount: 2, hasUnreadMessage: true },
                { ...channel, unreadMessagesCount: 1, hasUnreadMessage: true },
            ];

            // @ts-ignore
            const markAllChannelsAsReadSpy = jest.spyOn(conversationService, 'markAllChannelsAsRead').mockReturnValue(of({}));

            const nextSpy = jest.spyOn((metisConversationService as any)._conversationsOfUser$, 'next');

            metisConversationService.markAllChannelsAsRead(course);

            // @ts-ignore
            (metisConversationService as any).conversationsOfUser.forEach((conversation) => {
                expect(conversation.unreadMessagesCount).toBe(0);
                expect(conversation.hasUnreadMessage).toBeFalse();
            });

            expect(nextSpy).toHaveBeenCalledWith((metisConversationService as any).conversationsOfUser);

            expect(markAllChannelsAsReadSpy).toHaveBeenCalledWith(course.id);
        });

        it('should return Observable without calling service when course has no id', () => {
            const courseWithoutId = {} as Course;

            const markAllChannelsAsReadSpy = jest.spyOn(conversationService, 'markAllChannelsAsRead');

            const result = metisConversationService.markAllChannelsAsRead(courseWithoutId);

            result.subscribe({
                complete: () => {
                    expect(markAllChannelsAsReadSpy).not.toHaveBeenCalled();
                },
            });
        });

        it('should handle error when service call fails', () => {
            const errorResponse = new HttpErrorResponse({ status: 500 });

            // @ts-ignore
            jest.spyOn(conversationService, 'markAllChannelsAsRead').mockReturnValue(of(errorResponse));

            const errorSpy = jest.spyOn(alertService, 'error');

            metisConversationService.markAllChannelsAsRead(course);

            expect(errorSpy).not.toHaveBeenCalled();
        });
    });

    it('should set hasUnreadMessages to true only if there are unread messages in non-muted conversations', () => {
        // All unread in muted conversations
        (metisConversationService as any).conversationsOfUser = [
            { unreadMessagesCount: 2, isMuted: true },
            { unreadMessagesCount: 0, isMuted: false },
        ];
        (metisConversationService as any).hasUnreadMessagesCheck();
        metisConversationService.hasUnreadMessages$.subscribe((hasUnread) => {
            expect(hasUnread).toBeFalse();
        });

        // Unread in non-muted conversation
        (metisConversationService as any).conversationsOfUser = [
            { unreadMessagesCount: 2, isMuted: false },
            { unreadMessagesCount: 0, isMuted: true },
        ];
        (metisConversationService as any).hasUnreadMessagesCheck();
        metisConversationService.hasUnreadMessages$.subscribe((hasUnread) => {
            expect(hasUnread).toBeTrue();
        });
    });
});
