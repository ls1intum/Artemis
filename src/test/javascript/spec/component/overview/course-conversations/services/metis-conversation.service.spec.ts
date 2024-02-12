import { TestBed, fakeAsync } from '@angular/core/testing';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { MockProvider } from 'ng-mocks';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { OneToOneChatService } from 'app/shared/metis/conversations/one-to-one-chat.service';
import { AlertService } from 'app/core/util/alert.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Course } from 'app/entities/course.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpResponse } from '@angular/common/http';
import { Subject, forkJoin, of } from 'rxjs';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../helpers/conversationExampleModels';
import { GroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationWebsocketDTO } from 'app/entities/metis/conversation/conversation-websocket-dto.model';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';
import { MetisPostAction } from 'app/shared/metis/metis.util';
import dayjs from 'dayjs/esm';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../../../helpers/mocks/service/mock-notification.service';
import { MetisPostDTO } from 'app/entities/metis/metis-post-dto.model';
import { Post } from 'app/entities/metis/post.model';

describe('MetisConversationService', () => {
    let metisConversationService: MetisConversationService;
    let conversationService: ConversationService;
    let groupChatService: GroupChatService;
    let oneToOneChatService: OneToOneChatService;
    let channelService: ChannelService;
    let websocketService: JhiWebsocketService;
    let courseManagementService: CourseManagementService;
    let alertService: AlertService;
    let notificationService: NotificationService;
    let newOrUpdatedMessageSubject: Subject<MetisPostDTO>;

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
                MockProvider(JhiWebsocketService),
                MockProvider(AlertService),
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        groupChat = generateExampleGroupChatDTO({ id: 1 });
        oneToOneChat = generateOneToOneChatDTO({ id: 2 });
        channel = generateExampleChannelDTO({ id: 3 });

        notificationService = TestBed.inject(NotificationService);
        newOrUpdatedMessageSubject = new Subject<MetisPostDTO>();
        jest.spyOn(notificationService, 'newOrUpdatedMessage', 'get').mockReturnValue(newOrUpdatedMessageSubject);

        metisConversationService = TestBed.inject(MetisConversationService);
        groupChatService = TestBed.inject(GroupChatService);
        oneToOneChatService = TestBed.inject(OneToOneChatService);
        channelService = TestBed.inject(ChannelService);
        websocketService = TestBed.inject(JhiWebsocketService);
        courseManagementService = TestBed.inject(CourseManagementService);
        conversationService = TestBed.inject(ConversationService);
        alertService = TestBed.inject(AlertService);

        jest.spyOn(courseManagementService, 'findOneForDashboard').mockReturnValue(of(new HttpResponse<Course>({ body: course })));
        jest.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel] })));
        jest.spyOn(conversationService, 'convertServerDates').mockImplementation((conversation) => conversation);

        receiveMockSubject = new Subject<ConversationWebsocketDTO>();

        jest.spyOn(websocketService, 'receive').mockReturnValue(receiveMockSubject.asObservable());
        jest.spyOn(websocketService, 'subscribe');
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
                    const newChannel = generateExampleChannelDTO({ id: 99 });
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

    it('should add new conversation to conversations of user on conversation create received', () => {
        return new Promise((done) => {
            metisConversationService.setUpConversationService(course).subscribe({
                complete: () => {
                    const websocketDTO = new ConversationWebsocketDTO();
                    websocketDTO.action = MetisPostAction.CREATE;
                    websocketDTO.conversation = generateExampleChannelDTO({ id: 99 });

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
            notification: { title: 'title' },
        };
        metisConversationService['conversationsOfUser'] = [{ id: 1, unreadMessageCount: 0 } as ChannelDTO];

        newOrUpdatedMessageSubject.next(postDTO);
        expect(metisConversationService['conversationsOfUser'][0].unreadMessagesCount).toBe(1);
    }));

    it('should mark messages as read', () => {
        metisConversationService['conversationsOfUser'] = [{ id: 1, unreadMessageCount: 1 } as ChannelDTO, { id: 2, unreadMessageCount: 1 } as ChannelDTO];
        metisConversationService.markAsRead(2);
        expect(metisConversationService['conversationsOfUser'][1].unreadMessagesCount).toBe(0);
    });
});
