import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { MockProvider } from 'ng-mocks';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { provideLocationMocks } from '@angular/common/testing';
import { Router, provideRouter } from '@angular/router';
import { EMPTY, Subject, forkJoin, of, switchMap, take, throwError } from 'rxjs';
import { ConversationDTO } from '../shared/entities/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationWebsocketDTO } from 'app/communication/shared/entities/conversation/conversation-websocket-dto.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { CommunicationCrudAction } from 'app/communication/communication.util';
import dayjs from 'dayjs/esm';
import { PostBroadcastDTO } from 'app/communication/shared/entities/post-broadcast-dto.model';
import { Post } from 'app/communication/shared/entities/post.model';

describe('CourseConversationsService', () => {
    let courseConversationsService: CourseConversationsService;
    let conversationService: ConversationService;
    let groupChatService: GroupChatService;
    let oneToOneChatService: OneToOneChatService;
    let channelService: ChannelService;
    let websocketService: WebsocketService;
    let alertService: AlertService;

    const course = { id: 1 } as Course;
    let groupChat: GroupChatDTO;
    let oneToOneChat: OneToOneChatDTO;
    let channel: ChannelDTO;
    let receiveMockSubject: Subject<ConversationWebsocketDTO>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideRouter([{ path: 'courses/:courseId/:tab', children: [] }]),
                provideLocationMocks(),
                { provide: CourseConversationsService, useClass: CourseConversationsService },
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
        courseConversationsService = TestBed.inject(CourseConversationsService);
        groupChatService = TestBed.inject(GroupChatService);
        oneToOneChatService = TestBed.inject(OneToOneChatService);
        channelService = TestBed.inject(ChannelService);
        websocketService = TestBed.inject(WebsocketService);
        conversationService = TestBed.inject(ConversationService);
        alertService = TestBed.inject(AlertService);

        vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel] })));
        vi.spyOn(conversationService, 'convertServerDates').mockImplementation((conversation) => conversation);

        receiveMockSubject = new Subject<ConversationWebsocketDTO>();
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(receiveMockSubject.asObservable());
    });

    afterEach(() => {
        courseConversationsService.ngOnDestroy();
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(courseConversationsService).toBeTruthy();
    });

    it('allows message reads before the communication cache is initialized, as in exercise discussions', () => {
        const getConversations = vi.spyOn(conversationService, 'getConversationsOfUser');
        getConversations.mockClear();
        const request = courseConversationsService.messagesRequested(channel.id!);
        expect(() => courseConversationsService.messagesLoaded(channel.id!, request)).not.toThrow();
        expect(getConversations).not.toHaveBeenCalled();
        courseConversationsService.setUpConversationService(course).subscribe();
        expect(courseConversationsService['conversationsLoaded']).toBe(true);
        expect(getConversations).toHaveBeenCalledOnce();
    });

    it('should set up the service correctly', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    expect(courseConversationsService.course).toEqual(course);
                    forkJoin([courseConversationsService.isLoading$, courseConversationsService.activeConversation$, courseConversationsService.conversationsOfUser$]).subscribe({
                        next: ([isLoading, activeConversation, conversations]) => {
                            expect(isLoading).toBe(false);
                            expect(activeConversation).toBeUndefined();
                            expect(conversations).toEqual([groupChat, oneToOneChat, channel]);
                            done({});
                        },
                    });
                    courseConversationsService._isLoading$.complete();
                    courseConversationsService._activeConversation$.complete();
                    courseConversationsService._conversationsOfUser$.complete();
                },
            });
        });
    });

    it('should set active conversation', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    courseConversationsService.setActiveConversation(groupChat);
                    courseConversationsService.activeConversation$.subscribe((activeConversation) => {
                        expect(activeConversation).toEqual(groupChat);
                        done({});
                    });
                },
            });
        });
    });

    it('should set active conversation by id', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    courseConversationsService.setActiveConversation(groupChat.id);
                    courseConversationsService.activeConversation$.subscribe((activeConversation) => {
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
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    courseConversationsService.setActiveConversation(groupChat);
                    courseConversationsService.hasUnreadMessages$.pipe().subscribe((hasUnreadMessages) => {
                        expect(hasUnreadMessages).toBe(true);
                        done({});
                    });
                },
            });
        });
    });

    it("should show alert if channel doesn't exist", () => {
        groupChat.unreadMessagesCount = 1;
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const addAlertSpy = vi.spyOn(alertService, 'addAlert');

                    courseConversationsService.setActiveConversation(4);
                    expect(addAlertSpy).toHaveBeenCalledOnce();
                    done({});
                },
            });
        });
    });

    it('should load the conversations again and open a course-wide channel that was created after they were loaded', () => {
        // Nobody is told about a new course-wide channel, so the cache of a user who already has the page open does not know it.
        // The notification about it links to it, and following that link must not claim that the user is not a member.
        const newChannel = generateExampleChannelDTO({ id: 10, isCourseWide: true } as ChannelDTO);
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const addAlertSpy = vi.spyOn(alertService, 'addAlert');
                    vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newChannel] })));

                    courseConversationsService.setActiveConversation(newChannel.id);

                    expect(addAlertSpy).not.toHaveBeenCalled();
                    courseConversationsService.activeConversation$.pipe(take(1)).subscribe((activeConversation) => {
                        expect(activeConversation?.id).toBe(newChannel.id);
                        done({});
                    });
                },
            });
        });
    });

    it('should ask the server only once before warning that the user is not a member', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const addAlertSpy = vi.spyOn(alertService, 'addAlert');
                    const getConversationsOfUserSpy = vi.spyOn(conversationService, 'getConversationsOfUser');
                    getConversationsOfUserSpy.mockClear();

                    courseConversationsService.setActiveConversation(4);

                    expect(getConversationsOfUserSpy).toHaveBeenCalledOnce();
                    expect(addAlertSpy).toHaveBeenCalledOnce();
                    done({});
                },
            });
        });
    });

    it('should get conversations of users again if force refresh is called', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    courseConversationsService.setActiveConversation(groupChat);
                    const getConversationsOfUserSpy = vi.spyOn(conversationService, 'getConversationsOfUser');
                    getConversationsOfUserSpy.mockClear();
                    courseConversationsService.forceRefresh().subscribe({
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

    it.each([
        [undefined, undefined, 1, 1],
        [true, true, 1, 1],
        [false, true, 0, 1],
        [true, false, 1, 0],
        [false, false, 0, 0],
    ] as const)('publishes one accepted refresh with active/list flags %s/%s', (notifyActive, notifyList, activeEmissions, listEmissions) => {
        courseConversationsService.setUpConversationService(course).subscribe();
        courseConversationsService.setActiveConversation(channel.id);
        const list = vi.fn();
        const active = vi.fn();
        const listSubscription = courseConversationsService.conversationsOfUser$.subscribe(list);
        const activeSubscription = courseConversationsService.activeConversation$.subscribe(active);
        list.mockClear();
        active.mockClear();
        const refreshed = [groupChat, oneToOneChat, channel].map((row) => ({ ...row, unreadMessagesCount: 7, hasUnreadMessage: true }));
        const get = vi
            .spyOn(conversationService, 'getConversationsOfUser')
            .mockReturnValue(of(new HttpResponse({ body: refreshed })))
            .mockClear();

        courseConversationsService.forceRefresh(notifyActive, notifyList).subscribe();

        expect(get).toHaveBeenCalledExactlyOnceWith(course.id);
        expect(list).toHaveBeenCalledTimes(listEmissions);
        if (listEmissions) expect(list).toHaveBeenCalledWith(refreshed);
        expect(active).toHaveBeenCalledTimes(activeEmissions);
        if (activeEmissions) expect(active).toHaveBeenCalledWith(channel);
        expect(courseConversationsService['conversationsOfUser']).toEqual(refreshed);
        expect(channel.unreadMessagesCount).toBe(7);
        expect(courseConversationsService['activeConversation']).toBe(channel);
        listSubscription.unsubscribe();
        activeSubscription.unsubscribe();
    });

    it('should keep the open conversation when a conversation that is not cached is requested', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    courseConversationsService.setActiveConversation(groupChat);

                    courseConversationsService.setActiveConversation(9999);

                    // closing the open conversation would empty the view and strip its id from the URL, so a reload
                    // could not restore it either
                    courseConversationsService.activeConversation$.pipe(take(1)).subscribe((activeConversation) => {
                        expect(activeConversation?.id).toBe(groupChat.id);
                        done({});
                    });
                },
            });
        });
    });

    it('should open a conversation that was requested before the conversations were loaded', () => {
        // The reload case. Opening the page with ?conversationId= asks for a conversation while the request that
        // fetches them is still in flight, because the route emits its query parameters straight away. Dropping that
        // request left the page on an empty view, because it is the only request that is ever made.
        return new Promise((done) => {
            courseConversationsService.setActiveConversation(groupChat.id);

            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    courseConversationsService.activeConversation$.pipe(take(1)).subscribe((activeConversation) => {
                        expect(activeConversation?.id).toBe(groupChat.id);
                        done({});
                    });
                },
            });
        });
    });

    it('should not warn about membership for a conversation requested before the conversations were loaded', () => {
        // Whether the user is a member cannot be known while the list is still on its way, so claiming they are not is
        // both wrong and, to the user, the only visible symptom of the race.
        const addAlertSpy = vi.spyOn(alertService, 'addAlert');

        courseConversationsService.setActiveConversation(4);

        expect(addAlertSpy).not.toHaveBeenCalled();
    });

    it('should keep a conversation the user opened rather than the one the url asked for', () => {
        // Both requests are made while the conversations are still in flight, which is the only window in which one can
        // be pending: the url asks for one on load, and the user picks another before the answer arrives. The later
        // request is the more recent intent and has to be the one that opens.
        const conversationsResponse = new Subject<HttpResponse<ConversationDTO[]>>();
        vi.spyOn(conversationService, 'getConversationsOfUser')
            .mockReturnValueOnce(conversationsResponse.asObservable())
            .mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel] })));

        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    courseConversationsService.activeConversation$.pipe(take(1)).subscribe((activeConversation) => {
                        expect(activeConversation?.id).toBe(groupChat.id);
                        done({});
                    });
                },
            });

            // Still loading: neither of these can be resolved against the cache yet.
            courseConversationsService.setActiveConversation(9999);
            courseConversationsService.setActiveConversation(groupChat.id);

            conversationsResponse.next(new HttpResponse({ body: [groupChat, oneToOneChat, channel] }));
            conversationsResponse.complete();
        });
    });

    it('should not open a refreshed channel after the user selected another conversation meanwhile', () => {
        const newChannel = generateExampleChannelDTO({ id: 10, isCourseWide: true } as ChannelDTO);
        const refreshResponse = new Subject<HttpResponse<ConversationDTO[]>>();
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(refreshResponse.asObservable());
                    courseConversationsService.setActiveConversation(newChannel.id);

                    // the user picks a cached conversation while the refresh for the channel is still running
                    courseConversationsService.setActiveConversation(groupChat);
                    refreshResponse.next(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newChannel] }));
                    refreshResponse.complete();

                    courseConversationsService.activeConversation$.pipe(take(1)).subscribe((activeConversation) => {
                        expect(activeConversation?.id).toBe(groupChat.id);
                        done({});
                    });
                },
            });
        });
    });

    it('should only open the latest selection when refreshes for two uncached channels finish out of order', () => {
        const olderChannel = generateExampleChannelDTO({ id: 10, isCourseWide: true } as ChannelDTO);
        const newerChannel = generateExampleChannelDTO({ id: 11, isCourseWide: true } as ChannelDTO);
        const olderRefresh = new Subject<HttpResponse<ConversationDTO[]>>();
        const newerRefresh = new Subject<HttpResponse<ConversationDTO[]>>();
        const allConversations = new HttpResponse({ body: [groupChat, oneToOneChat, channel, olderChannel, newerChannel] });
        courseConversationsService.setUpConversationService(course).subscribe();
        const get = vi
            .spyOn(conversationService, 'getConversationsOfUser')
            .mockReturnValue(of(allConversations))
            .mockReturnValueOnce(olderRefresh.asObservable())
            .mockReturnValueOnce(newerRefresh.asObservable());
        get.mockClear();
        courseConversationsService.setActiveConversation(olderChannel.id);
        courseConversationsService.setActiveConversation(newerChannel.id);

        newerRefresh.next(allConversations);
        newerRefresh.complete();
        olderRefresh.next(allConversations);
        olderRefresh.complete();

        let selected: number | undefined;
        courseConversationsService.activeConversation$.pipe(take(1)).subscribe((conversation) => (selected = conversation?.id));
        expect(selected).toBe(newerChannel.id);
        expect(get).toHaveBeenCalledTimes(3); // The pre-intent response is rejected and gets a current server snapshot.
    });

    it('should still clear the active conversation when it is cleared on purpose', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    courseConversationsService.setActiveConversation(groupChat);

                    courseConversationsService.setActiveConversation(undefined);

                    courseConversationsService.activeConversation$.pipe(take(1)).subscribe((activeConversation) => {
                        expect(activeConversation).toBeUndefined();
                        done({});
                    });
                },
            });
        });
    });

    it('should keep the cached conversations and the active conversation when a force refresh fails', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    courseConversationsService.setActiveConversation(groupChat);
                    vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

                    courseConversationsService.forceRefresh().subscribe({
                        // the failure is passed on, so that subscribers cannot mistake it for an up to date list
                        error: () => {
                            // A failed refresh must not be mistaken for "the user has no conversations": dropping the cache
                            // would clear the active conversation and strip its id from the URL, so a reload could not restore it
                            forkJoin({
                                conversations: courseConversationsService.conversationsOfUser$.pipe(take(1)),
                                activeConversation: courseConversationsService.activeConversation$.pipe(take(1)),
                            }).subscribe(({ conversations, activeConversation }) => {
                                expect(conversations).toEqual([groupChat, oneToOneChat, channel]);
                                expect(activeConversation?.id).toBe(groupChat.id);
                                done({});
                            });
                        },
                    });
                },
            });
        });
    });

    it('should not report the service as set up when the conversations cannot be loaded', () => {
        return new Promise((done) => {
            vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const isServiceSetUpValues: boolean[] = [];
            courseConversationsService.isServiceSetup$.subscribe((isSetUp) => isServiceSetUpValues.push(isSetUp));

            courseConversationsService.setUpConversationService(course).subscribe({
                // the failure is passed on, so the caller does not record the service as instantiated
                error: () => {
                    // announcing the service as ready with an empty cache would make every conversation opened from the
                    // URL look like one the user is not a member of
                    expect(isServiceSetUpValues).toEqual([false]);
                    done({});
                },
            });
        });
    });

    it('should set active conversation to newly created channel', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const newChannel = generateExampleChannelDTO({ id: 99 } as ChannelDTO);
                    const createChannelSpy = vi.spyOn(channelService, 'create').mockReturnValue(of(new HttpResponse({ body: newChannel })));
                    const getConversationSpy = vi
                        .spyOn(conversationService, 'getConversationsOfUser')
                        .mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newChannel] })));
                    createChannelSpy.mockClear();
                    courseConversationsService.createChannel(newChannel).subscribe({
                        complete: () => {
                            expect(createChannelSpy).toHaveBeenCalledOnce();
                            expect(createChannelSpy).toHaveBeenCalledWith(course.id, newChannel);
                            courseConversationsService.activeConversation$.subscribe((activeConversation) => {
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
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const newGroupChat = generateExampleGroupChatDTO({ id: 99 });
                    const createGroupChatSpy = vi.spyOn(groupChatService, 'create').mockReturnValue(of(new HttpResponse({ body: newGroupChat })));
                    const getConversationSpy = vi
                        .spyOn(conversationService, 'getConversationsOfUser')
                        .mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newGroupChat] })));
                    createGroupChatSpy.mockClear();
                    courseConversationsService.createGroupChat(['login']).subscribe({
                        complete: () => {
                            expect(createGroupChatSpy).toHaveBeenCalledOnce();
                            expect(createGroupChatSpy).toHaveBeenCalledWith(course.id, ['login']);
                            courseConversationsService.activeConversation$.subscribe((activeConversation) => {
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
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const newOneToOneChat = generateOneToOneChatDTO({ id: 99 });
                    const createOneToOneChatSpy = vi.spyOn(oneToOneChatService, 'create').mockReturnValue(of(new HttpResponse({ body: newOneToOneChat })));
                    const getConversationSpy = vi
                        .spyOn(conversationService, 'getConversationsOfUser')
                        .mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newOneToOneChat] })));
                    createOneToOneChatSpy.mockClear();
                    courseConversationsService.createOneToOneChat('login').subscribe({
                        complete: () => {
                            expect(createOneToOneChatSpy).toHaveBeenCalledOnce();
                            expect(createOneToOneChatSpy).toHaveBeenCalledWith(course.id, 'login');
                            courseConversationsService.activeConversation$.subscribe((activeConversation) => {
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
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const newOneToOneChat = generateOneToOneChatDTO({ id: 99 });
                    const createOneToOneChatSpy = vi.spyOn(oneToOneChatService, 'createWithId').mockReturnValue(of(new HttpResponse({ body: newOneToOneChat })));
                    const getConversationSpy = vi
                        .spyOn(conversationService, 'getConversationsOfUser')
                        .mockReturnValue(of(new HttpResponse({ body: [groupChat, oneToOneChat, channel, newOneToOneChat] })));
                    createOneToOneChatSpy.mockClear();
                    courseConversationsService.createOneToOneChatWithId(1).subscribe({
                        complete: () => {
                            expect(createOneToOneChatSpy).toHaveBeenCalledOnce();
                            expect(createOneToOneChatSpy).toHaveBeenCalledWith(course.id, 1);
                            courseConversationsService.activeConversation$.subscribe((activeConversation) => {
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
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const websocketDTO = new ConversationWebsocketDTO();
                    websocketDTO.action = CommunicationCrudAction.CREATE;
                    websocketDTO.conversation = generateExampleChannelDTO({ id: 99 } as ChannelDTO);

                    receiveMockSubject.next(websocketDTO);
                    courseConversationsService.conversationsOfUser$.subscribe((conversationsOfUser) => {
                        expect(conversationsOfUser).toEqual([groupChat, oneToOneChat, channel, websocketDTO.conversation]);
                        done({});
                    });
                },
            });
        });
    });

    it('should update conversation in conversations of user on conversation update received', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const websocketDTO = new ConversationWebsocketDTO();
                    websocketDTO.action = CommunicationCrudAction.UPDATE;
                    websocketDTO.conversation = { ...channel, name: 'newtitle' } as ChannelDTO;

                    receiveMockSubject.next(websocketDTO);
                    courseConversationsService.conversationsOfUser$.subscribe((conversationsOfUser) => {
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
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const websocketDTO = new ConversationWebsocketDTO();
                    websocketDTO.action = CommunicationCrudAction.NEW_MESSAGE;
                    // 1 of january 2022
                    const lastMessageDate = dayjs('2022-01-01T00:00:00.000Z');
                    websocketDTO.conversation = { ...channel, lastMessageDate } as ChannelDTO;

                    receiveMockSubject.next(websocketDTO);
                    courseConversationsService.conversationsOfUser$.subscribe((conversationsOfUser) => {
                        // find updated conversation in cache
                        const updatedConversation = conversationsOfUser.find((conversation) => conversation.id === channel.id);
                        expect(updatedConversation!.lastMessageDate!.isSame(lastMessageDate)).toBe(true);
                        done({});
                    });
                },
            });
        });
    });

    describe('bounded unread reconciliation', () => {
        beforeEach(() => vi.useFakeTimers());

        it.each(['channel', 'group', 'direct'] as const)('opens a read %s with accepted metadata on rapid navigation before the batching window', (kind) => {
            const conversation = kind === 'channel' ? channel : kind === 'group' ? groupChat : oneToOneChat;
            const other = conversation === channel ? groupChat : channel;
            const oldDate = dayjs('2026-01-01T10:00:00Z');
            const readDate = dayjs('2026-01-01T12:00:00Z');
            conversation.lastReadDate = oldDate;
            conversation.unreadMessagesCount = 2;
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setConversationViewOpen(true);
            const openings: { id: number | undefined; date: string | undefined }[] = [];
            const active = courseConversationsService.activeConversation$.subscribe((row) => {
                if (row && openings.at(-1)?.id !== row.id) openings.push({ id: row.id, date: row.lastReadDate?.toISOString() });
            });
            courseConversationsService.setActiveConversation(conversation.id);
            courseConversationsService.messagesLoaded(conversation.id!, courseConversationsService.messagesRequested(conversation.id!));
            const response = new Subject<HttpResponse<ConversationDTO[]>>();
            const get = vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(response).mockClear();
            courseConversationsService.setActiveConversation(other.id);
            expect(courseConversationsService['activeConversation']?.id).toBe(conversation.id);
            expect(get).toHaveBeenCalledTimes(1);
            response.next(
                new HttpResponse({
                    body: [conversation, other].map((row) => ({
                        ...row,
                        lastReadDate: row.id === conversation.id ? readDate : oldDate,
                        unreadMessagesCount: 0,
                        hasUnreadMessage: false,
                    })),
                }),
            );
            response.complete();
            expect(courseConversationsService['activeConversation']?.id).toBe(other.id);
            courseConversationsService.setActiveConversation(conversation.id);
            expect(courseConversationsService['activeConversation']?.lastReadDate?.isSame(readDate)).toBe(true);
            expect(openings[0]).toEqual({ id: conversation.id, date: oldDate.toISOString() });
            expect(openings.at(-1)).toEqual({ id: conversation.id, date: readDate.toISOString() });
            vi.advanceTimersByTime(1000);
            expect(get).toHaveBeenCalledTimes(1);
            active.unsubscribe();
        });

        it('waits through cancelled view entry and publishes readiness only after recovered metadata is applied', () => {
            channel.lastReadDate = dayjs('2026-01-01T10:00:00Z');
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            courseConversationsService.messagesLoaded(channel.id!, courseConversationsService.messagesRequested(channel.id!));
            const pending = Array.from({ length: 3 }, () => new Subject<HttpResponse<ConversationDTO[]>>());
            const readDate = dayjs('2026-01-01T12:00:00Z');
            const get = vi
                .spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(pending[0])
                .mockReturnValueOnce(pending[1])
                .mockReturnValueOnce(pending[2])
                .mockReturnValue(of(new HttpResponse({ body: [{ ...channel, lastReadDate: readDate, unreadMessagesCount: 0, hasUnreadMessage: false }] })))
                .mockClear();
            const ready: boolean[] = [];
            const observedDates: string[] = [];
            const completed = vi.fn();
            courseConversationsService.prepareConversationView().subscribe({
                next: (value) => {
                    ready.push(value);
                    observedDates.push(channel.lastReadDate!.toISOString());
                },
                complete: completed,
            });
            for (const response of pending) {
                courseConversationsService.handleNewMessage(channel.id, dayjs());
                response.next(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 99 }] }));
                response.complete();
            }
            expect(ready).toEqual([]);
            expect(completed).not.toHaveBeenCalled();
            expect(get).toHaveBeenCalledTimes(3);
            vi.advanceTimersByTime(1000);
            expect(ready).toEqual([true]);
            expect(observedDates).toEqual([readDate.toISOString()]);
            expect(completed).toHaveBeenCalledTimes(1);
            expect(get).toHaveBeenCalledTimes(4);
            expect(courseConversationsService['isConversationViewOpen']).toBe(false);
        });

        it('reports failed view entry and can become ready after a later accepted retry', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            courseConversationsService.messagesLoaded(channel.id!, courseConversationsService.messagesRequested(channel.id!));
            const readDate = dayjs('2026-01-01T12:00:00Z');
            const get = vi
                .spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })))
                .mockReturnValue(of(new HttpResponse({ body: [{ ...channel, lastReadDate: readDate, unreadMessagesCount: 0, hasUnreadMessage: false }] })))
                .mockClear();
            const ready: boolean[] = [];
            const completed = vi.fn();
            courseConversationsService.prepareConversationView().subscribe({ next: (value) => ready.push(value), complete: completed });
            expect(ready).toEqual([false]);
            expect(completed).not.toHaveBeenCalled();
            courseConversationsService.forceRefresh(false).subscribe();
            expect(ready).toEqual([false, true]);
            expect(completed).toHaveBeenCalledTimes(1);
            expect(channel.lastReadDate?.isSame(readDate)).toBe(true);
            vi.advanceTimersByTime(1000);
            expect(get).toHaveBeenCalledTimes(2);
        });

        it('cancels a discarded view entry while preserving its pending read reconciliation', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            courseConversationsService.messagesLoaded(channel.id!, courseConversationsService.messagesRequested(channel.id!));
            const pending = new Subject<HttpResponse<ConversationDTO[]>>();
            const get = vi
                .spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(pending)
                .mockReturnValue(of(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 0, hasUnreadMessage: false }] })))
                .mockClear();
            const ready = vi.fn();
            const subscription = courseConversationsService.prepareConversationView().subscribe(ready);
            subscription.unsubscribe();
            pending.next(new HttpResponse({ body: [] }));
            pending.complete();
            expect(courseConversationsService['conversationsOfUser']).toHaveLength(3);
            vi.advanceTimersByTime(1000);
            expect(get).toHaveBeenCalledTimes(2);
            expect(ready).not.toHaveBeenCalled();
            expect(courseConversationsService['activeConversation']?.id).toBe(channel.id);
        });

        it('does not rearm readiness reconciliation when the conversation service is disabled', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.messagesLoaded(channel.id!, courseConversationsService.messagesRequested(channel.id!));
            const response = new Subject<HttpResponse<ConversationDTO[]>>();
            const get = vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(response).mockClear();
            const view = courseConversationsService.isServiceSetup$.pipe(switchMap((setup) => (setup ? courseConversationsService.prepareConversationView() : EMPTY))).subscribe();
            expect(get).toHaveBeenCalledTimes(1);
            courseConversationsService.disableConversationService();
            vi.advanceTimersByTime(5000);
            expect(get).toHaveBeenCalledTimes(1);
            expect(response.observed).toBe(false);
            view.unsubscribe();
        });

        it('batches sequential read completions without postponing the fixed window', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            courseConversationsService.setConversationViewOpen(true);
            const get = vi.spyOn(conversationService, 'getConversationsOfUser').mockClear();
            const mark = vi.spyOn(conversationService, 'markAsRead').mockReturnValue(of(new HttpResponse<void>()));
            for (let index = 0; index < 10; index++) courseConversationsService.handleNewMessage(channel.id, dayjs());
            expect(mark).toHaveBeenCalledTimes(10);
            expect(get).not.toHaveBeenCalled();
            vi.advanceTimersByTime(900);
            courseConversationsService.handleNewMessage(channel.id, dayjs());
            vi.advanceTimersByTime(100);
            expect(get).toHaveBeenCalledTimes(1);
            vi.advanceTimersByTime(5000);
            expect(get).toHaveBeenCalledTimes(1);
        });

        it('bounds invalidated foreground requests and later reconciles without publishing stale state', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            const responses = Array.from({ length: 4 }, () => new Subject<HttpResponse<ConversationDTO[]>>());
            let index = 0;
            const get = vi.spyOn(conversationService, 'getConversationsOfUser').mockImplementation(() => responses[index++]);
            get.mockClear();
            const failed = vi.fn();
            const completed = vi.fn();
            const alert = vi.spyOn(alertService, 'addAlert');
            const counts: number[] = [];
            const subscription = courseConversationsService.conversationsOfUser$.subscribe((rows) =>
                counts.push(rows.find((row) => row.id === channel.id)?.unreadMessagesCount ?? 0),
            );
            courseConversationsService.forceRefresh(false).subscribe({ error: failed, complete: completed });
            for (let attempt = 0; attempt < 3; attempt++) {
                courseConversationsService.handleNewMessage(channel.id, dayjs());
                responses[attempt].next(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 99 }] }));
                responses[attempt].complete();
            }
            expect(get).toHaveBeenCalledTimes(3);
            expect(failed).toHaveBeenCalledTimes(1);
            expect(completed).not.toHaveBeenCalled();
            expect(alert).not.toHaveBeenCalled();
            expect(counts).not.toContain(99);
            expect(courseConversationsService['activeConversation']).toBe(channel);
            expect(channel.unreadMessagesCount).toBe(3);
            expect(courseConversationsService['isLoading']).toBe(false);
            vi.advanceTimersByTime(1000);
            expect(get).toHaveBeenCalledTimes(4);
            responses[3].next(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 3, hasUnreadMessage: true }] }));
            responses[3].complete();
            vi.advanceTimersByTime(5000);
            expect(get).toHaveBeenCalledTimes(4);
            expect(counts.at(-1)).toBe(3);
            subscription.unsubscribe();
        });

        it('keeps one slow recovery in flight and delays its invalidated retry', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            const first = new Subject<HttpResponse<ConversationDTO[]>>();
            const get = vi
                .spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(first)
                .mockReturnValue(of(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 0 }] })));
            get.mockClear();
            const read = () => courseConversationsService.messagesLoaded(channel.id!, courseConversationsService.messagesRequested(channel.id!));
            read();
            vi.advanceTimersByTime(1000);
            read();
            vi.advanceTimersByTime(2000);
            expect(get).toHaveBeenCalledTimes(1);
            first.next(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 99 }] }));
            first.complete();
            expect(get).toHaveBeenCalledTimes(1);
            expect(courseConversationsService['conversationsOfUser']).toHaveLength(3);
            vi.advanceTimersByTime(999);
            expect(get).toHaveBeenCalledTimes(1);
            vi.advanceTimersByTime(1);
            expect(get).toHaveBeenCalledTimes(2);
            expect(courseConversationsService['conversationsOfUser'][0].unreadMessagesCount).toBe(0);
            vi.advanceTimersByTime(5000);
            expect(get).toHaveBeenCalledTimes(2);
        });

        it('does not reject a startup selection using metadata captured before that selection', () => {
            const old = new Subject<HttpResponse<ConversationDTO[]>>();
            const created = generateExampleChannelDTO({ id: 99 } as ChannelDTO);
            vi.spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(old)
                .mockReturnValue(of(new HttpResponse({ body: [channel, created] })));
            const alert = vi.spyOn(alertService, 'addAlert');
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(created.id);
            old.next(new HttpResponse({ body: [channel] }));
            old.complete();
            expect(alert).not.toHaveBeenCalled();
            expect(courseConversationsService['activeConversation']?.id).toBe(created.id);
        });

        it('lets initial setup finish during arrivals and reconciles after the cache is available', () => {
            const first = new Subject<HttpResponse<ConversationDTO[]>>();
            const get = vi
                .spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(first)
                .mockReturnValue(of(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 1 }] })));
            get.mockClear();
            const complete = vi.fn();
            courseConversationsService.setUpConversationService(course).subscribe({ complete });
            for (let index = 0; index < 10; index++) courseConversationsService.handleNewMessage(channel.id, dayjs());
            first.next(new HttpResponse({ body: [channel] }));
            first.complete();
            expect(complete).toHaveBeenCalledTimes(1);
            expect(get).toHaveBeenCalledTimes(1);
            vi.advanceTimersByTime(1000);
            expect(get).toHaveBeenCalledTimes(2);
            expect(courseConversationsService['conversationsOfUser'][0].unreadMessagesCount).toBe(1);
        });

        it('ignores arrivals outside this course when validating a loaded snapshot', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            const response = new Subject<HttpResponse<ConversationDTO[]>>();
            const get = vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(response).mockClear();
            const complete = vi.fn();
            courseConversationsService.forceRefresh().subscribe({ complete });
            courseConversationsService.handleNewMessage(99999, dayjs());
            response.next(new HttpResponse({ body: [channel] }));
            response.complete();
            expect(complete).toHaveBeenCalledTimes(1);
            expect(get).toHaveBeenCalledTimes(1);
        });

        it.each([false, true])('cancels queued and active recovery on destroy (in flight=%s)', (inFlight) => {
            courseConversationsService.setUpConversationService(course).subscribe();
            const response = new Subject<HttpResponse<ConversationDTO[]>>();
            const get = vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(response).mockClear();
            const read = () => courseConversationsService.messagesLoaded(channel.id!, courseConversationsService.messagesRequested(channel.id!));
            read();
            if (inFlight) {
                vi.advanceTimersByTime(1000);
                read();
            }
            courseConversationsService.ngOnDestroy();
            response.next(new HttpResponse({ body: [] }));
            response.complete();
            vi.advanceTimersByTime(5000);
            expect(get).toHaveBeenCalledTimes(inFlight ? 1 : 0);
            expect(courseConversationsService['conversationsOfUser']).toHaveLength(3);
        });

        it.each(['selection', 'creation'] as const)('resumes the latest pending %s after cancelled foreground work', (kind) => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            const created = generateExampleGroupChatDTO({ id: 99 });
            vi.spyOn(groupChatService, 'create').mockReturnValue(of(new HttpResponse({ body: created })));
            const responses = Array.from({ length: 3 }, () => new Subject<HttpResponse<ConversationDTO[]>>());
            const get = vi
                .spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(responses[0])
                .mockReturnValueOnce(responses[1])
                .mockReturnValueOnce(responses[2])
                .mockReturnValue(of(new HttpResponse({ body: [channel, created] })));
            get.mockClear();
            let displayed: number | undefined;
            const active = courseConversationsService.activeConversation$.subscribe((conversation) => (displayed = conversation?.id));
            if (kind === 'creation') courseConversationsService.createGroupChat(['partner']).subscribe({ error: () => {} });
            else courseConversationsService.setActiveConversation(created.id);
            for (const response of responses) {
                courseConversationsService.handleNewMessage(channel.id, dayjs());
                response.next(new HttpResponse({ body: [channel, created] }));
                response.complete();
            }
            expect(displayed).toBe(channel.id);
            expect(courseConversationsService['activeConversation']?.id).toBe(channel.id);
            vi.advanceTimersByTime(1000);
            expect(get).toHaveBeenCalledTimes(4);
            expect(displayed).toBe(created.id);
            expect(courseConversationsService['activeConversation']?.id).toBe(created.id);
            active.unsubscribe();
        });

        it.each(['selection', 'creation'] as const)('does not resolve a pending %s from a list requested before it', (kind) => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            const created = generateExampleGroupChatDTO({ id: 99 });
            const old = new Subject<HttpResponse<ConversationDTO[]>>();
            const fresh = new Subject<HttpResponse<ConversationDTO[]>>();
            vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValueOnce(old).mockReturnValueOnce(fresh);
            vi.spyOn(groupChatService, 'create').mockReturnValue(of(new HttpResponse({ body: created })));
            const alert = vi.spyOn(alertService, 'addAlert');
            courseConversationsService.messagesLoaded(channel.id!, courseConversationsService.messagesRequested(channel.id!));
            vi.advanceTimersByTime(1000);
            if (kind === 'creation') courseConversationsService.createGroupChat(['partner']).subscribe({ error: () => {} });
            else courseConversationsService.setActiveConversation(created.id);
            old.next(new HttpResponse({ body: [channel] }));
            old.complete();
            expect(alert).not.toHaveBeenCalled();
            expect(courseConversationsService['activeConversation']?.id).toBe(channel.id);
            fresh.next(new HttpResponse({ body: [channel, created] }));
            fresh.complete();
            expect(courseConversationsService['activeConversation']?.id).toBe(created.id);
            expect(alert).not.toHaveBeenCalled();
        });

        it('does not resume a cancelled selection after a newer cached selection', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            const responses = Array.from({ length: 3 }, () => new Subject<HttpResponse<ConversationDTO[]>>());
            const created = generateExampleGroupChatDTO({ id: 99 });
            vi.spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(responses[0])
                .mockReturnValueOnce(responses[1])
                .mockReturnValueOnce(responses[2])
                .mockReturnValue(of(new HttpResponse({ body: [channel, groupChat, created] })));
            courseConversationsService.setActiveConversation(created.id);
            for (const response of responses) {
                courseConversationsService.handleNewMessage(channel.id, dayjs());
                response.next(new HttpResponse({ body: [channel] }));
                response.complete();
            }
            courseConversationsService.setActiveConversation(groupChat);
            vi.advanceTimersByTime(1000);
            expect(courseConversationsService['activeConversation']?.id).toBe(groupChat.id);
        });

        it('never treats exhausted selection refreshes as proof of missing membership', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            const responses = Array.from({ length: 4 }, () => new Subject<HttpResponse<ConversationDTO[]>>());
            let index = 0;
            const get = vi
                .spyOn(conversationService, 'getConversationsOfUser')
                .mockImplementation(() => responses[index++])
                .mockClear();
            const alert = vi.spyOn(alertService, 'addAlert');
            courseConversationsService.setActiveConversation(99);
            for (let attempt = 0; attempt < 3; attempt++) {
                courseConversationsService.handleNewMessage(channel.id, dayjs());
                responses[attempt].next(new HttpResponse({ body: [channel] }));
                responses[attempt].complete();
            }
            expect(get).toHaveBeenCalledTimes(3);
            expect(alert).not.toHaveBeenCalled();
            expect(courseConversationsService['activeConversation']).toBe(channel);
        });
    });

    describe('a new message event for a conversation', () => {
        beforeEach(() => vi.useFakeTimers());
        const eventFor = (conversation: ConversationDTO) => {
            const websocketDTO = new ConversationWebsocketDTO();
            websocketDTO.action = CommunicationCrudAction.NEW_MESSAGE;
            websocketDTO.conversation = conversation;
            return websocketDTO;
        };

        const cachedUnreadCount = (conversationId: number | undefined) =>
            courseConversationsService['conversationsOfUser'].find((conversation) => conversation.id === conversationId)?.unreadMessagesCount ?? 0;

        it('should raise the unread count of a conversation that is not open', () => {
            return new Promise((done) => {
                courseConversationsService.setUpConversationService(course).subscribe({
                    complete: () => {
                        courseConversationsService.setActiveConversation(groupChat);
                        const markAsRead = vi.spyOn(conversationService, 'markAsRead').mockReturnValue(of(new HttpResponse<void>()));

                        receiveMockSubject.next(eventFor(channel));

                        expect(cachedUnreadCount(channel.id)).toBe(1);
                        expect(markAsRead).not.toHaveBeenCalled();
                        done({});
                    },
                });
            });
        });

        it('should not raise the unread count of the open conversation but mark it as read on the server', () => {
            return new Promise((done) => {
                courseConversationsService.setUpConversationService(course).subscribe({
                    complete: () => {
                        courseConversationsService.setConversationViewOpen(true);
                        courseConversationsService.setActiveConversation(channel);
                        const markAsRead = vi.spyOn(conversationService, 'markAsRead').mockReturnValue(of(new HttpResponse<void>()));

                        // e.g. a reply in a thread of the user, which the server counted for them like a message
                        receiveMockSubject.next(eventFor(channel));

                        expect(cachedUnreadCount(channel.id)).toBe(0);
                        expect(markAsRead).toHaveBeenCalledWith(course.id, channel.id);
                        done({});
                    },
                });
            });
        });

        it('should raise the unread count of the selected conversation while the user is on another tab of the course', () => {
            return new Promise((done) => {
                courseConversationsService.setUpConversationService(course).subscribe({
                    complete: () => {
                        // the service outlives the conversation view and keeps the selection for the way back to it
                        courseConversationsService.setConversationViewOpen(true);
                        courseConversationsService.setActiveConversation(channel);
                        courseConversationsService.setConversationViewOpen(false);
                        const markAsRead = vi.spyOn(conversationService, 'markAsRead').mockReturnValue(of(new HttpResponse<void>()));

                        receiveMockSubject.next(eventFor(channel));

                        expect(cachedUnreadCount(channel.id)).toBe(1);
                        expect(markAsRead).not.toHaveBeenCalled();
                        done({});
                    },
                });
            });
        });

        it('should keep a conversation that the user marked as unread unread', () => {
            return new Promise((done) => {
                courseConversationsService.setUpConversationService(course).subscribe({
                    complete: () => {
                        courseConversationsService.setConversationViewOpen(true);
                        courseConversationsService.setActiveConversation(channel);
                        courseConversationsService.updateConversationUnreadState(channel.id, dayjs(), 1);
                        expect(courseConversationsService.isConversationMarkedAsUnread(channel.id)).toBe(true);
                        expect(courseConversationsService.isConversationMarkedAsUnread(groupChat.id)).toBe(false);
                        expect(courseConversationsService.isConversationMarkedAsUnread(undefined)).toBe(false);
                        const markAsRead = vi.spyOn(conversationService, 'markAsRead').mockReturnValue(of(new HttpResponse<void>()));

                        receiveMockSubject.next(eventFor(channel));

                        expect(cachedUnreadCount(channel.id)).toBe(2);
                        expect(markAsRead).not.toHaveBeenCalled();
                        done({});
                    },
                });
            });
        });

        it.each(['group', 'direct-login', 'direct-id', 'channel'] as const)('reads live arrivals after creating a %s from a manually unread conversation', (kind) => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setConversationViewOpen(true);
            courseConversationsService.setActiveConversation(channel);
            const unreadDate = dayjs('2026-01-01T10:00:00Z');
            courseConversationsService.updateConversationUnreadState(channel.id, unreadDate, 2);
            const created =
                kind === 'group'
                    ? generateExampleGroupChatDTO({ id: 99 })
                    : kind === 'channel'
                      ? generateExampleChannelDTO({ id: 99 } as ChannelDTO)
                      : generateOneToOneChatDTO({ id: 99 });
            vi.spyOn(groupChatService, 'create').mockReturnValue(of(new HttpResponse({ body: created as GroupChatDTO })));
            vi.spyOn(oneToOneChatService, 'create').mockReturnValue(of(new HttpResponse({ body: created as OneToOneChatDTO })));
            vi.spyOn(oneToOneChatService, 'createWithId').mockReturnValue(of(new HttpResponse({ body: created as OneToOneChatDTO })));
            vi.spyOn(channelService, 'create').mockReturnValue(of(new HttpResponse({ body: created as ChannelDTO })));
            vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(of(new HttpResponse({ body: [channel, created] })));
            const markAsRead = vi.spyOn(conversationService, 'markAsRead').mockReturnValue(of(new HttpResponse<void>()));
            const result =
                kind === 'group'
                    ? courseConversationsService.createGroupChat(['partner'])
                    : kind === 'channel'
                      ? courseConversationsService.createChannel(created as ChannelDTO)
                      : kind === 'direct-id'
                        ? courseConversationsService.createOneToOneChatWithId(7)
                        : courseConversationsService.createOneToOneChat('partner');
            result.subscribe();

            courseConversationsService.setActiveConversation(created.id);
            expect(courseConversationsService.isConversationMarkedAsUnread(created.id)).toBe(false);
            expect(courseConversationsService['activeConversation']?.id).toBe(created.id);
            courseConversationsService.messagesLoaded(created.id!, courseConversationsService.messagesRequested(created.id!));
            receiveMockSubject.next(eventFor(created));
            expect(markAsRead).toHaveBeenCalledExactlyOnceWith(course.id, created.id);
            expect(cachedUnreadCount(created.id)).toBe(0);
            expect(cachedUnreadCount(channel.id)).toBe(2);
            expect(channel.lastReadDate?.isSame(unreadDate)).toBe(true);
            expect(courseConversationsService['hasUnreadMessages']).toBe(true);
        });

        it.each(['group', 'direct-login', 'direct-id'] as const)('preserves a manual unread mark when %s creation returns the current conversation', (kind) => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setConversationViewOpen(true);
            const current = kind === 'group' ? groupChat : oneToOneChat;
            courseConversationsService.setActiveConversation(current);
            courseConversationsService.updateConversationUnreadState(current.id, dayjs(), 2);
            const response = new HttpResponse({ body: { ...current } });
            vi.spyOn(groupChatService, 'create').mockReturnValue(of(response as HttpResponse<GroupChatDTO>));
            vi.spyOn(oneToOneChatService, 'create').mockReturnValue(of(response as HttpResponse<OneToOneChatDTO>));
            vi.spyOn(oneToOneChatService, 'createWithId').mockReturnValue(of(response as HttpResponse<OneToOneChatDTO>));
            const markAsRead = vi.spyOn(conversationService, 'markAsRead');
            const result =
                kind === 'group'
                    ? courseConversationsService.createGroupChat(['partner'])
                    : kind === 'direct-id'
                      ? courseConversationsService.createOneToOneChatWithId(7)
                      : courseConversationsService.createOneToOneChat('partner');
            result.subscribe();
            courseConversationsService.setActiveConversation(current.id);
            expect(courseConversationsService['activeConversation']).toBe(current);
            expect(courseConversationsService.isConversationMarkedAsUnread(current.id)).toBe(true);
            receiveMockSubject.next(eventFor(current));
            expect(markAsRead).not.toHaveBeenCalled();
            expect(cachedUnreadCount(current.id)).toBe(3);
        });

        it.each(['channel', 'group', 'direct'] as const)('resumes automatic reads after reopening a manually unread %s', (kind) => {
            courseConversationsService.setUpConversationService(course).subscribe();
            const conversation = kind === 'channel' ? channel : kind === 'group' ? groupChat : oneToOneChat;
            courseConversationsService.setActiveConversation(conversation);
            courseConversationsService.setConversationViewOpen(true);
            courseConversationsService.updateConversationUnreadState(conversation.id, dayjs(), 2);
            const markAsRead = vi.spyOn(conversationService, 'markAsRead').mockReturnValue(of(new HttpResponse<void>()));

            // A thread query update within the current view must retain the explicit unread mark.
            courseConversationsService.setActiveConversation(conversation.id);
            courseConversationsService.setConversationViewOpen(true);
            receiveMockSubject.next(eventFor(conversation));
            expect(courseConversationsService.isConversationMarkedAsUnread(conversation.id)).toBe(true);
            expect(cachedUnreadCount(conversation.id)).toBe(3);
            expect(markAsRead).not.toHaveBeenCalled();

            // Exercises destroys the view; Communication reopens the retained conversation and loads its messages.
            courseConversationsService.setConversationViewOpen(false);
            expect(courseConversationsService.isConversationMarkedAsUnread(conversation.id)).toBe(true);
            courseConversationsService.setConversationViewOpen(true);
            courseConversationsService.setActiveConversation(conversation.id);
            const readDate = dayjs();
            vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(
                of(new HttpResponse({ body: [{ ...conversation, unreadMessagesCount: 0, hasUnreadMessage: false, lastReadDate: readDate }] })),
            );
            courseConversationsService.messagesLoaded(conversation.id!, courseConversationsService.messagesRequested(conversation.id!));
            vi.advanceTimersByTime(1000);
            expect(cachedUnreadCount(conversation.id)).toBe(0);
            expect(courseConversationsService.isConversationMarkedAsUnread(conversation.id)).toBe(false);

            receiveMockSubject.next(eventFor(conversation));
            expect(markAsRead).toHaveBeenCalledExactlyOnceWith(course.id, conversation.id);
            expect(cachedUnreadCount(conversation.id)).toBe(0);
            expect(courseConversationsService['hasUnreadMessages']).toBe(false);
        });

        it.each([
            ['another course tab', false, false, 1],
            ['an explicitly unread conversation', true, true, 3],
        ] as const)('preserves unread replies on navigation from %s', async (_context, viewOpen, markedUnread, expectedCount) => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            courseConversationsService.setConversationViewOpen(viewOpen);
            if (markedUnread) courseConversationsService.updateConversationUnreadState(channel.id, dayjs(), 2);
            receiveMockSubject.next(eventFor(channel));
            expect(cachedUnreadCount(channel.id)).toBe(expectedCount);

            await TestBed.inject(Router).navigateByUrl('/courses/1/lectures');

            expect(cachedUnreadCount(channel.id)).toBe(expectedCount);
            expect(courseConversationsService['activeConversation']?.hasUnreadMessage).toBe(true);
            expect(courseConversationsService['hasUnreadMessages']).toBe(true);
        });

        it('does not treat navigation as a confirmed read', async () => {
            channel.lastReadDate = dayjs('2026-01-01T10:00:00Z');
            channel.unreadMessagesCount = 2;
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            courseConversationsService.setConversationViewOpen(true);
            await TestBed.inject(Router).navigateByUrl('/courses/1/communication?conversationId=3');
            expect(cachedUnreadCount(channel.id)).toBe(2);
            expect(channel.lastReadDate?.toISOString()).toBe('2026-01-01T10:00:00.000Z');
            courseConversationsService.setActiveConversation(groupChat);
            expect(cachedUnreadCount(channel.id)).toBe(2);
            expect(courseConversationsService['hasUnreadMessages']).toBe(true);
        });

        // The fake server snapshots DTOs when a GET starts, independently of when responses arrive.
        // This tests the public cache against server state, without a client request/contribution ledger.
        const kinds = ['channel', 'group', 'direct'] as const;
        const scenarios = kinds.flatMap((kind) =>
            [true, false].flatMap((firstSucceeds) =>
                [true, false].flatMap((secondSucceeds) =>
                    [true, false].flatMap((reverseResponses) => [true, false].map((navigate) => ({ kind, firstSucceeds, secondSucceeds, reverseResponses, navigate }))),
                ),
            ),
        );
        it.each(scenarios)(
            'uses authoritative counts for overlapping reads: $kind / first=$firstSucceeds second=$secondSucceeds reversed=$reverseResponses navigate=$navigate',
            ({ kind, firstSucceeds, secondSucceeds, reverseResponses, navigate }) => {
                courseConversationsService.setUpConversationService(course).subscribe();
                const conversation = kind === 'channel' ? channel : kind === 'group' ? groupChat : oneToOneChat;
                const other = conversation === channel ? groupChat : channel;
                courseConversationsService.setActiveConversation(conversation);
                courseConversationsService.setConversationViewOpen(true);
                let serverCount = 0;
                const serverReadDate = dayjs('2026-01-01T12:00:00Z');
                const snapshot = () =>
                    [groupChat, oneToOneChat, channel].map((row) => ({
                        ...row,
                        unreadMessagesCount: row.id === conversation.id ? serverCount : 0,
                        hasUnreadMessage: row.id === conversation.id && serverCount > 0,
                        lastReadDate: serverReadDate,
                    }));
                vi.spyOn(conversationService, 'getConversationsOfUser').mockImplementation(() => of(new HttpResponse({ body: snapshot() })));
                const responses = [new Subject<HttpResponse<void>>(), new Subject<HttpResponse<void>>()];
                vi.spyOn(conversationService, 'markAsRead').mockReturnValueOnce(responses[0]).mockReturnValueOnce(responses[1]);
                serverCount++;
                receiveMockSubject.next(eventFor(conversation));
                if (firstSucceeds) serverCount = 0;
                serverCount++;
                receiveMockSubject.next(eventFor(conversation));
                if (secondSucceeds) serverCount = 0;
                if (navigate) courseConversationsService.setActiveConversation(other);
                const expected = serverCount;
                for (const index of reverseResponses ? [1, 0] : [0, 1]) {
                    if (index === 0 ? firstSucceeds : secondSucceeds) {
                        responses[index].next(new HttpResponse<void>());
                        responses[index].complete();
                    } else {
                        responses[index].error(new Error('read rejected'));
                    }
                    vi.advanceTimersByTime(1000);
                    expect(cachedUnreadCount(conversation.id)).toBe(expected);
                    const cached = courseConversationsService['conversationsOfUser'].find((row) => row.id === conversation.id)!;
                    expect(cached.hasUnreadMessage).toBe(expected > 0);
                    expect(cached.lastReadDate).toBe(serverReadDate);
                    expect(courseConversationsService['hasUnreadMessages']).toBe(expected > 0);
                    expect(courseConversationsService['activeConversation']?.id).toBe(navigate ? other.id : conversation.id);
                }
            },
        );

        it.each(kinds.flatMap((kind) => ['success', 'failure', 'messages'].flatMap((outcome) => [false, true].map((navigate) => ({ kind, outcome, navigate })))))(
            'preserves a manual unread mark against an older $outcome for $kind (navigate=$navigate)',
            ({ kind, outcome, navigate }) => {
                courseConversationsService.setUpConversationService(course).subscribe();
                const conversation = kind === 'channel' ? channel : kind === 'group' ? groupChat : oneToOneChat;
                courseConversationsService.setActiveConversation(conversation);
                courseConversationsService.setConversationViewOpen(true);
                const pending = new Subject<HttpResponse<void>>();
                vi.spyOn(conversationService, 'markAsRead').mockReturnValue(pending);
                const request = courseConversationsService.messagesRequested(conversation.id!);
                receiveMockSubject.next(eventFor(conversation));
                const manualDate = dayjs('2026-01-01T10:00:00Z');
                courseConversationsService.updateConversationUnreadState(conversation.id, manualDate, 3);
                if (navigate) courseConversationsService.setActiveConversation(conversation === channel ? groupChat : channel);
                const getConversations = vi.spyOn(conversationService, 'getConversationsOfUser');
                getConversations.mockClear();
                if (outcome === 'success') {
                    pending.next(new HttpResponse<void>());
                    pending.complete();
                } else if (outcome === 'failure') pending.error(new Error('rejected'));
                else courseConversationsService.messagesLoaded(conversation.id!, request);
                expect(cachedUnreadCount(conversation.id)).toBe(3);
                expect(conversation.lastReadDate?.isSame(manualDate)).toBe(true);
                expect(conversation.hasUnreadMessage).toBe(true);
                expect(courseConversationsService['hasUnreadMessages']).toBe(true);
                vi.advanceTimersByTime(1000);
                expect(getConversations).not.toHaveBeenCalled();
            },
        );

        it.each(['success', 'failure', 'messages', 'manual', 'arrival', 'read-start', 'bulk-success', 'bulk-failure'] as const)(
            'rejects a stale list after %s changes the unread baseline',
            (mutation) => {
                courseConversationsService.setUpConversationService(course).subscribe();
                courseConversationsService.setActiveConversation(channel);
                courseConversationsService.setConversationViewOpen(true);
                const pending = new Subject<HttpResponse<void>>();
                vi.spyOn(conversationService, 'markAsRead').mockReturnValue(pending);
                receiveMockSubject.next(eventFor(channel));
                const request = courseConversationsService.messagesRequested(channel.id!);
                const stale = new Subject<HttpResponse<ConversationDTO[]>>();
                const expected = mutation === 'manual' ? 3 : mutation === 'failure' || mutation === 'arrival' || mutation === 'bulk-failure' || mutation === 'read-start' ? 2 : 0;
                const confirmedDate = dayjs('2026-01-01T12:00:00Z');
                const fresh = () => [groupChat, oneToOneChat, { ...channel, unreadMessagesCount: expected, hasUnreadMessage: expected > 0, lastReadDate: confirmedDate }];
                const getConversations = vi
                    .spyOn(conversationService, 'getConversationsOfUser')
                    .mockReturnValueOnce(stale)
                    .mockImplementation(() => of(new HttpResponse({ body: fresh() })));
                const publishedCounts: number[] = [];
                const counts = courseConversationsService.conversationsOfUser$.subscribe((rows) =>
                    publishedCounts.push(rows.find((row) => row.id === channel.id)?.unreadMessagesCount ?? 0),
                );
                const activeNotifications = vi.spyOn(courseConversationsService['_activeConversation$'], 'next');
                courseConversationsService.forceRefresh(false).subscribe();
                if (mutation === 'success') {
                    pending.next(new HttpResponse<void>());
                    pending.complete();
                } else if (mutation === 'failure') pending.error(new Error('read failed'));
                else if (mutation === 'messages') courseConversationsService.messagesLoaded(channel.id!, request);
                else if (mutation === 'manual') courseConversationsService.updateConversationUnreadState(channel.id, confirmedDate, expected);
                else if (mutation === 'arrival') {
                    courseConversationsService.setConversationViewOpen(false);
                    receiveMockSubject.next(eventFor(channel));
                } else if (mutation === 'read-start') courseConversationsService['readNewMessage'](channel.id);
                else {
                    vi.spyOn(conversationService, 'markAllChannelsAsRead').mockReturnValue(
                        mutation === 'bulk-success' ? of(undefined) : throwError(() => new HttpErrorResponse({ status: 500 })),
                    );
                    courseConversationsService.markAllChannelsAsRead(course).subscribe();
                }
                publishedCounts.length = 0;
                stale.next(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 7, hasUnreadMessage: true }] }));
                stale.complete();
                expect(cachedUnreadCount(channel.id)).toBe(expected);
                expect(publishedCounts).not.toContain(7);
                expect(courseConversationsService['activeConversation']?.lastReadDate?.isSame(confirmedDate)).toBe(true);
                expect(courseConversationsService['hasUnreadMessages']).toBe(expected > 0);
                expect(getConversations.mock.calls.length).toBeGreaterThan(1);
                expect(activeNotifications).not.toHaveBeenCalled();
                counts.unsubscribe();
            },
        );

        it('keeps the visible DTO connected after ordinary and silent refreshes, then a manual unread mark', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            const viewConversation = channel;
            vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(
                of(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 0, hasUnreadMessage: false, name: 'updated-channel' }] })),
            );
            courseConversationsService.forceRefresh().subscribe();
            courseConversationsService.messagesLoaded(channel.id!, courseConversationsService.messagesRequested(channel.id!));
            expect(courseConversationsService['activeConversation']).toBe(viewConversation);
            expect(viewConversation.name).toBe('updated-channel');
            courseConversationsService.setActiveConversation(channel.id);
            const manualDate = dayjs('2026-01-01T10:00:00Z');
            courseConversationsService.updateConversationUnreadState(channel.id, manualDate, 3);
            expect(viewConversation.lastReadDate).toBe(manualDate);
            expect(viewConversation.unreadMessagesCount).toBe(3);
            courseConversationsService.setActiveConversation(channel.id);
            expect(courseConversationsService.isConversationMarkedAsUnread(channel.id)).toBe(true);
            const arrivalDate = dayjs('2026-01-01T12:00:00Z');
            receiveMockSubject.next(eventFor({ ...channel, lastMessageDate: arrivalDate }));
            expect(cachedUnreadCount(channel.id)).toBe(4);
            expect(viewConversation.unreadMessagesCount).toBe(4);
            expect(viewConversation.lastMessageDate?.isSame(arrivalDate)).toBe(true);
        });

        it('coalesces read completions and repeats an invalidated recovery snapshot', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            courseConversationsService.setConversationViewOpen(true);
            const first = new Subject<HttpResponse<void>>();
            const second = new Subject<HttpResponse<void>>();
            vi.spyOn(conversationService, 'markAsRead').mockReturnValueOnce(first).mockReturnValueOnce(second);
            receiveMockSubject.next(eventFor(channel));
            receiveMockSubject.next(eventFor(channel));
            const recovery = new Subject<HttpResponse<ConversationDTO[]>>();
            const getConversations = vi
                .spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(recovery)
                .mockReturnValue(of(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 0, hasUnreadMessage: false, name: 'updated-channel' }] })));
            getConversations.mockClear();
            first.error(new Error('first failed'));
            vi.advanceTimersByTime(1000);
            second.next(new HttpResponse<void>());
            second.complete();
            expect(getConversations).toHaveBeenCalledTimes(1);
            recovery.next(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 1, hasUnreadMessage: true }] }));
            recovery.complete();
            expect(getConversations).toHaveBeenCalledTimes(1);
            vi.advanceTimersByTime(1000);
            expect(getConversations).toHaveBeenCalledTimes(2);
            expect(cachedUnreadCount(channel.id)).toBe(0);
            expect(courseConversationsService['hasUnreadMessages']).toBe(false);
        });

        it('retains cache and selection if recovery fails, then allows another recovery', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            const getConversations = vi
                .spyOn(conversationService, 'getConversationsOfUser')
                .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })))
                .mockReturnValue(of(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 2, hasUnreadMessage: true }] })));
            getConversations.mockClear();
            const request = courseConversationsService.messagesRequested(channel.id!);
            courseConversationsService.messagesLoaded(channel.id!, request);
            vi.advanceTimersByTime(1000);
            expect(courseConversationsService['activeConversation']?.id).toBe(channel.id);
            expect(courseConversationsService['conversationsOfUser']).toHaveLength(3);
            expect(courseConversationsService['isLoading']).toBe(false);
            courseConversationsService.messagesLoaded(channel.id!, request);
            vi.advanceTimersByTime(1000);
            expect(getConversations).toHaveBeenCalledTimes(2);
            expect(cachedUnreadCount(channel.id)).toBe(2);
        });

        it('does not send automatic reads for another conversation, a closed view or a manual unread mark', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(channel);
            const mark = vi.spyOn(conversationService, 'markAsRead');
            courseConversationsService['readNewMessage'](channel.id);
            courseConversationsService.setConversationViewOpen(true);
            courseConversationsService['readNewMessage'](groupChat.id);
            courseConversationsService['readNewMessage'](undefined);
            courseConversationsService.updateConversationUnreadState(channel.id, dayjs(), 3);
            courseConversationsService['readNewMessage'](channel.id);
            expect(mark).not.toHaveBeenCalled();
            expect(cachedUnreadCount(channel.id)).toBe(3);
        });
    });

    it('should remove conversation in conversations of user on conversation delete received', () => {
        return new Promise((done) => {
            courseConversationsService.setUpConversationService(course).subscribe({
                complete: () => {
                    const websocketDTO = new ConversationWebsocketDTO();
                    websocketDTO.action = CommunicationCrudAction.DELETE;
                    websocketDTO.conversation = { ...channel } as ChannelDTO;

                    receiveMockSubject.next(websocketDTO);
                    courseConversationsService.conversationsOfUser$.subscribe((conversationsOfUser) => {
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
        vi.spyOn(conversationService, 'checkForUnreadMessages').mockReturnValue(of(new HttpResponse<boolean>({ body: unreadMessages })));
        let numberOfSubscriptions = 0;

        courseConversationsService.hasUnreadMessages$.pipe().subscribe((hasUnreadMessages: boolean) => {
            expect(hasUnreadMessages).toBe(true);
            numberOfSubscriptions++;
        });

        courseConversationsService.checkForUnreadMessages(course);

        expect(numberOfSubscriptions).toBe(unreadMessages ? 1 : 0);
    });

    it('should set code of conduct', () => {
        courseConversationsService.setCodeOfConduct();
        courseConversationsService.isCodeOfConductPresented$.subscribe((isCodeOfConductPresented: boolean) => {
            expect(isCodeOfConductPresented).toBe(true);
        });
    });

    it('should check and accept code of conduct', () => {
        const checkStub = vi.spyOn(conversationService, 'checkIsCodeOfConductAccepted').mockReturnValue(of(new HttpResponse<boolean>({ body: false })));
        courseConversationsService.checkIsCodeOfConductAccepted(course);
        let lastValue: boolean | undefined;
        const sub = courseConversationsService.isCodeOfConductAccepted$.subscribe((isCodeOfConductAccepted: boolean) => {
            lastValue = isCodeOfConductAccepted;
        });
        expect(lastValue).toBe(false);
        sub.unsubscribe();
        expect(checkStub).toHaveBeenCalledOnce();

        const acceptStub = vi.spyOn(conversationService, 'acceptCodeOfConduct').mockReturnValue(of(new HttpResponse<void>({})));
        courseConversationsService.acceptCodeOfConduct(course);
        let acceptedValue: boolean | undefined;
        const sub2 = courseConversationsService.isCodeOfConductAccepted$.subscribe((isCodeOfConductAccepted: boolean) => {
            acceptedValue = isCodeOfConductAccepted;
        });
        expect(acceptedValue).toBe(true);
        sub2.unsubscribe();
        expect(acceptStub).toHaveBeenCalledOnce();
    });

    it('rejects a stale course badge response after messages have been read', () => {
        courseConversationsService.setUpConversationService(course).subscribe();
        const stale = new Subject<HttpResponse<boolean>>();
        const check = vi
            .spyOn(conversationService, 'checkForUnreadMessages')
            .mockReturnValueOnce(stale)
            .mockReturnValue(of(new HttpResponse({ body: false })));
        courseConversationsService.checkForUnreadMessages(course);
        courseConversationsService.messagesLoaded(channel.id!, courseConversationsService.messagesRequested(channel.id!));
        stale.next(new HttpResponse({ body: true }));
        stale.complete();
        expect(check).toHaveBeenCalledTimes(2);
        expect(courseConversationsService['hasUnreadMessages']).toBe(false);
    });

    it('should handle new message', () => {
        const postDTO: PostBroadcastDTO = {
            post: { author: { id: 456 }, content: 'Content', conversation: { id: 1 } } as Post,
            action: CommunicationCrudAction.CREATE,
        };
        courseConversationsService.setUpConversationService(course).subscribe();
        courseConversationsService.handleNewMessage(postDTO.post.conversation?.id, postDTO.post.conversation?.lastMessageDate);
        expect(courseConversationsService['conversationsOfUser'][0].unreadMessagesCount).toBe(1);
    });

    it('should call refresh after marking all channels as read', () => {
        const markAllChannelAsReadSpy = vi.spyOn(conversationService, 'markAllChannelsAsRead').mockReturnValue(of());
        courseConversationsService.markAllChannelsAsRead(course);
        expect(markAllChannelAsReadSpy).toHaveBeenCalledOnce();
    });

    describe('updateConversationUnreadState', () => {
        it('should update last read date and unread messages count of the conversation', () => {
            groupChat.unreadMessagesCount = 0;
            groupChat.hasUnreadMessage = false;
            (courseConversationsService as any).activeConversation = groupChat;
            (courseConversationsService as any).conversationsOfUser = [groupChat];

            const nextSpy = vi.spyOn((courseConversationsService as any)._conversationsOfUser$, 'next');
            const conversationId = groupChat.id;
            const lastReadDate = dayjs();
            const unreadMessagesCount = 4;
            (courseConversationsService as any).updateConversationUnreadState(conversationId, lastReadDate, unreadMessagesCount);

            expect((courseConversationsService as any).activeConversation.unreadMessagesCount).toBe(unreadMessagesCount);
            expect((courseConversationsService as any).activeConversation.hasUnreadMessage).toBe(true);
            expect((courseConversationsService as any).activeConversation.lastReadDate).toBeDefined();

            expect((courseConversationsService as any).conversationsOfUser[0].unreadMessagesCount).toBe(unreadMessagesCount);
            expect((courseConversationsService as any).conversationsOfUser[0].hasUnreadMessage).toBe(true);
            expect((courseConversationsService as any).conversationsOfUser[0].lastReadDate).toBeDefined();

            expect((courseConversationsService as any).isMarkedAsUnread).toBe(true);
            expect(nextSpy).toHaveBeenCalledWith((courseConversationsService as any).conversationsOfUser);
        });

        it('should not update conversationsOfUser if conversation is not found in the array', () => {
            const nonExistentConversation = { ...groupChat, id: 999 };
            (courseConversationsService as any).conversationsOfUser = [groupChat];

            const nextSpy = vi.spyOn((courseConversationsService as any)._conversationsOfUser$, 'next');

            (courseConversationsService as any).updateConversationUnreadState(nonExistentConversation.id, dayjs(), 5);

            expect(nextSpy).not.toHaveBeenCalled();
        });

        it('should not update active conversation if conversationId does not match', () => {
            groupChat.unreadMessagesCount = 0;
            groupChat.hasUnreadMessage = false;
            (courseConversationsService as any).activeConversation = groupChat;
            (courseConversationsService as any).conversationsOfUser = [groupChat];
            const nonExistentConversation = { ...oneToOneChat, id: 999 };

            (courseConversationsService as any).updateConversationUnreadState(nonExistentConversation.id, dayjs(), 5);

            expect((courseConversationsService as any).activeConversation.unreadMessagesCount).toBe(0);
            expect((courseConversationsService as any).activeConversation.hasUnreadMessage).toBe(false);
        });
    });

    it('should return correct params object with conversationId', () => {
        const conversationId = 42;
        const result = CourseConversationsService.getQueryParamsForConversation(conversationId);
        expect(result).toEqual({ conversationId: 42 });
    });

    it('should return correct route components for given courseId', () => {
        const courseId = 123;
        const result = CourseConversationsService.getLinkForConversation(courseId);
        expect(result).toEqual(['/courses', 123, 'communication']);
    });

    describe('markAllChannelsAsRead', () => {
        it('keeps unread counts until the bulk read is confirmed, then applies server state', () => {
            vi.useFakeTimers();
            channel.unreadMessagesCount = 3;
            courseConversationsService.setUpConversationService(course).subscribe();
            const pending = new Subject<void>();
            vi.spyOn(conversationService, 'markAllChannelsAsRead').mockReturnValue(pending);
            vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(
                of(new HttpResponse({ body: [{ ...channel, unreadMessagesCount: 0, hasUnreadMessage: false, name: 'updated-channel' }] })),
            );
            courseConversationsService.markAllChannelsAsRead(course).subscribe();
            expect(courseConversationsService['conversationsOfUser'].find((row) => row.id === channel.id)?.unreadMessagesCount).toBe(3);
            pending.next();
            pending.complete();
            vi.advanceTimersByTime(1000);
            expect(courseConversationsService['conversationsOfUser'][0].unreadMessagesCount).toBe(0);
            expect(courseConversationsService['hasUnreadMessages']).toBe(false);
        });

        it('reloads unread counts for channels, groups and direct chats when the bulk read fails', () => {
            courseConversationsService.setUpConversationService(course).subscribe();
            courseConversationsService.setActiveConversation(groupChat);
            const restored = [
                { ...groupChat, unreadMessagesCount: 3, hasUnreadMessage: true },
                { ...oneToOneChat, unreadMessagesCount: 2, hasUnreadMessage: true },
                { ...channel, unreadMessagesCount: 1, hasUnreadMessage: true },
            ];
            vi.spyOn(conversationService, 'getConversationsOfUser').mockReturnValue(of(new HttpResponse({ body: restored })));
            const failedRead = new Subject<void>();
            vi.spyOn(conversationService, 'markAllChannelsAsRead').mockReturnValue(failedRead);
            const completed = vi.fn();
            const errorAlert = vi.spyOn(alertService, 'error');
            courseConversationsService.markAllChannelsAsRead(course).subscribe({ complete: completed });
            expect(completed).not.toHaveBeenCalled();

            failedRead.error(new HttpErrorResponse({ status: 400 }));

            expect(courseConversationsService['conversationsOfUser'].map((conversation) => conversation.unreadMessagesCount)).toEqual([3, 2, 1]);
            expect(courseConversationsService['conversationsOfUser'].every((conversation) => conversation.hasUnreadMessage)).toBe(true);
            expect(courseConversationsService['activeConversation']?.unreadMessagesCount).toBe(3);
            expect(courseConversationsService['hasUnreadMessages']).toBe(true);
            expect(completed).toHaveBeenCalledOnce();
            expect(errorAlert).toHaveBeenCalledExactlyOnceWith('error.http.400');
        });

        it('should return Observable without calling service when course has no id', () => {
            const courseWithoutId = {} as Course;

            const markAllChannelsAsReadSpy = vi.spyOn(conversationService, 'markAllChannelsAsRead');

            const result = courseConversationsService.markAllChannelsAsRead(courseWithoutId);

            result.subscribe({
                complete: () => {
                    expect(markAllChannelsAsReadSpy).not.toHaveBeenCalled();
                },
            });
        });

        it('should handle error when service call fails', () => {
            const errorResponse = new HttpErrorResponse({ status: 500 });

            // @ts-ignore
            vi.spyOn(conversationService, 'markAllChannelsAsRead').mockReturnValue(of(errorResponse));

            const errorSpy = vi.spyOn(alertService, 'error');

            courseConversationsService.markAllChannelsAsRead(course);

            expect(errorSpy).not.toHaveBeenCalled();
        });
    });

    it('should set hasUnreadMessages to true only if there are unread messages in non-muted conversations', () => {
        // All unread in muted conversations
        (courseConversationsService as any).conversationsOfUser = [
            { unreadMessagesCount: 2, isMuted: true },
            { unreadMessagesCount: 0, isMuted: false },
        ];
        (courseConversationsService as any).hasUnreadMessagesCheck();
        // hasUnreadMessages starts as false and check finds no unread non-muted, so value stays false (no emission)
        expect((courseConversationsService as any).hasUnreadMessages).toBe(false);

        // Unread in non-muted conversation
        (courseConversationsService as any).conversationsOfUser = [
            { unreadMessagesCount: 2, isMuted: false },
            { unreadMessagesCount: 0, isMuted: true },
        ];
        (courseConversationsService as any).hasUnreadMessagesCheck();
        let hasUnreadValue: boolean | undefined;
        const sub = courseConversationsService.hasUnreadMessages$.subscribe((hasUnread) => {
            hasUnreadValue = hasUnread;
        });
        expect(hasUnreadValue).toBe(true);
        sub.unsubscribe();
    });
});
