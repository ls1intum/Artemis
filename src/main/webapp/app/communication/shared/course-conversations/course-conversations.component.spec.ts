import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { BreakpointObserver, BreakpointState, Breakpoints } from '@angular/cdk/layout';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Conversation, ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { MockComponent, MockDirective, MockInstance, MockPipe, MockProvider } from 'ng-mocks';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { SortDirection } from 'app/communication/communication.util';
import { LoadingIndicatorContainerStubComponent } from 'test/helpers/stubs/shared/loading-indicator-container-stub.component';
import { LoadingIndicatorContainerComponent } from 'app/shared-ui/loading-indicator-container/loading-indicator-container.component';
import { ConversationHeaderComponent } from 'app/communication/course-conversations-components/layout/conversation-header/conversation-header.component';
import { CourseWideSearchComponent } from 'app/communication/course-conversations-components/course-wide-search/course-wide-search.component';
import { ConversationMessagesComponent } from 'app/communication/course-conversations-components/layout/conversation-messages/conversation-messages.component';
import { ConversationThreadSidebarComponent } from 'app/communication/course-conversations-components/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { BehaviorSubject, EMPTY, Subject, of, throwError } from 'rxjs';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ActivatedRoute, Params, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { CommunicationService } from 'app/communication/service/communication.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseConversationsCodeOfConductComponent } from 'app/communication/course-conversations-components/code-of-conduct/course-conversations-code-of-conduct.component';
import { MockCommunicationService } from 'test/helpers/mocks/service/mock-communication.service';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { GroupChatCreateDialogComponent } from 'app/communication/course-conversations-components/group-chat-create-dialog/group-chat-create-dialog.component';
import { SidebarEventService } from 'app/course/sidebar/service/sidebar-event.service';
import { SidebarAccordionComponent } from 'app/course/sidebar/sidebar-accordion/sidebar-accordion.component';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { OneToOneChatCreateDialogComponent } from 'app/communication/course-conversations-components/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CourseSidebarService } from 'app/course/overview/services/course-sidebar.service';
import { ChannelsCreateDialogComponent } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channels-create-dialog.component';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Posting, PostingType, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { ElementRef, computed, signal } from '@angular/core';
import {
    ChannelAction,
    ChannelsOverviewDialogComponent,
} from 'app/communication/course-conversations-components/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ConversationGlobalSearchComponent } from 'app/communication/shared/conversation-global-search/conversation-global-search.component';
import { AlertService } from 'app/foundation/service/alert.service';
import { FaqService } from 'app/communication/faq/faq.service';
import { provideTranslateService } from '@ngx-translate/core';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

const examples: (ConversationDTO | undefined)[] = [
    undefined,
    generateOneToOneChatDTO({} as OneToOneChatDTO),
    generateExampleGroupChatDTO({} as GroupChatDTO),
    generateExampleChannelDTO({} as ChannelDTO),
];

examples.forEach((activeConversation) => {
    describe('CourseConversationComponent with ' + (activeConversation?.type || 'no active conversation'), () => {
        let component: CourseConversationsComponent;
        let fixture: ComponentFixture<CourseConversationsComponent>;
        const course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING } as Course;
        let queryParamsSubject: BehaviorSubject<Params>;
        const router = new MockRouter();
        let postsSubject: BehaviorSubject<Post[]>;
        let acceptCodeOfConductSpy: ReturnType<typeof vi.spyOn>;
        let setActiveConversationSpy: ReturnType<typeof vi.spyOn>;
        let courseConversationsService: CourseConversationsService;
        let courseOverviewService: CourseOverviewService;
        let dialogService: DialogService;
        let courseSidebarService: CourseSidebarService;
        let activatedRoute: ActivatedRoute;
        let breakpoint$: BehaviorSubject<BreakpointState>;
        let activeConversation$: BehaviorSubject<ConversationDTO | undefined>;

        // Workaround for mocked components with viewChild: https://github.com/help-me-mom/ng-mocks/issues/8634
        MockInstance(CourseWideSearchComponent, 'content', signal(new ElementRef(document.createElement('div'))));
        MockInstance(CourseWideSearchComponent, 'messages', signal([new ElementRef(document.createElement('div'))]));
        // @ts-ignore
        MockInstance(ConversationGlobalSearchComponent, 'searchElement', signal([new ElementRef(document.createElement('div'))]));
        MockInstance(ConversationThreadSidebarComponent, 'threadContainer', signal(new ElementRef(document.createElement('div'))));
        const dummyTooltip = { close: vi.fn() } as unknown as NgbTooltip;
        MockInstance(ConversationThreadSidebarComponent, 'expandTooltip', signal(dummyTooltip));

        beforeEach(() => {
            vi.useFakeTimers();
        });

        beforeEach(async () => {
            breakpoint$ = new BehaviorSubject<BreakpointState>({
                matches: false,
                breakpoints: { [Breakpoints.Handset]: false },
            });

            queryParamsSubject = new BehaviorSubject<Params>({});

            TestBed.configureTestingModule({
                imports: [
                    CourseConversationsComponent,
                    MockComponent(ConversationHeaderComponent),
                    MockComponent(DocumentationButtonComponent),
                    MockComponent(ConversationMessagesComponent),
                    MockComponent(ConversationThreadSidebarComponent),
                    MockComponent(CourseConversationsCodeOfConductComponent),
                    MockComponent(ButtonComponent),
                    MockComponent(SidebarComponent),
                    MockComponent(CourseWideSearchComponent),
                    MockComponent(ConversationGlobalSearchComponent),
                    MockComponent(SidebarAccordionComponent),
                    MockPipe(ArtemisTranslatePipe),
                    MockDirective(MarkdownDirective),
                    FormsModule,
                    ReactiveFormsModule,
                    FontAwesomeModule,
                    NgbTooltip,
                ],
                declarations: [LoadingIndicatorContainerStubComponent],
                providers: [
                    {
                        provide: BreakpointObserver,
                        useValue: {
                            observe: () => breakpoint$.asObservable(),
                            isMatched: (query: string | string[]) => false,
                        },
                    },
                    { provide: Router, useValue: router },
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            parent: {
                                snapshot: {
                                    data: { course },
                                },
                                parent: {
                                    paramMap: new BehaviorSubject(
                                        convertToParamMap({
                                            courseId: 1,
                                        }),
                                    ),
                                },
                            },
                            queryParams: queryParamsSubject,
                        },
                    },
                    MockProvider(CourseOverviewService),
                    MockProvider(DialogService),
                    MockProvider(CourseConversationsService),
                    MockProvider(SidebarEventService),
                    MockProvider(ProfileService),
                    MockProvider(AlertService),
                    MockProvider(FaqService, {
                        findAllByCourseIdAndState: () => of(new HttpResponse({ body: [] })),
                    }),
                    provideHttpClient(),
                    provideHttpClientTesting(),
                    provideTranslateService(),
                ],
            });

            const communicationService = new MockCommunicationService();

            TestBed.overrideComponent(CourseConversationsComponent, {
                set: {
                    providers: [{ provide: CommunicationService, useValue: communicationService }],
                },
            });

            TestBed.overrideComponent(CourseConversationsComponent, {
                remove: {
                    imports: [
                        LoadingIndicatorContainerComponent,
                        SidebarComponent,
                        ConversationHeaderComponent,
                        ConversationMessagesComponent,
                        CourseWideSearchComponent,
                        ConversationThreadSidebarComponent,
                        ConversationGlobalSearchComponent,
                        CourseConversationsCodeOfConductComponent,
                    ],
                },
                add: {
                    imports: [
                        MockComponent(LoadingIndicatorContainerComponent),
                        MockComponent(SidebarComponent),
                        MockComponent(ConversationHeaderComponent),
                        MockComponent(ConversationMessagesComponent),
                        MockComponent(CourseWideSearchComponent),
                        MockComponent(ConversationThreadSidebarComponent),
                        MockComponent(ConversationGlobalSearchComponent),
                        MockComponent(CourseConversationsCodeOfConductComponent),
                    ],
                },
            });

            courseConversationsService = TestBed.inject(CourseConversationsService);
            courseOverviewService = TestBed.inject(CourseOverviewService);
            courseSidebarService = TestBed.inject(CourseSidebarService);
            activatedRoute = TestBed.inject(ActivatedRoute);

            Object.defineProperty(courseConversationsService, 'isServiceSetup$', { get: () => new BehaviorSubject(true).asObservable() });
            Object.defineProperty(courseConversationsService, 'conversationsOfUser$', { get: () => new BehaviorSubject([new GroupChatDTO()]).asObservable() });
            Object.defineProperty(courseConversationsService, 'isLoading$', { get: () => new BehaviorSubject(false).asObservable() });
            Object.defineProperty(courseConversationsService, 'isCodeOfConductAccepted$', { get: () => new BehaviorSubject(true).asObservable() });
            Object.defineProperty(courseConversationsService, 'isCodeOfConductPresented$', { get: () => new BehaviorSubject(false).asObservable() });
            courseConversationsService.checkIsCodeOfConductAccepted = vi.fn();
            courseConversationsService.setActiveConversation = vi.fn();
            courseConversationsService.setUpConversationService = vi.fn().mockReturnValue(EMPTY);
            courseConversationsService.forceRefresh = vi.fn().mockReturnValue(EMPTY);
            courseConversationsService.acceptCodeOfConduct = vi.fn();
            courseConversationsService.createGroupChat = vi.fn().mockReturnValue(EMPTY);
            courseConversationsService.createOneToOneChat = vi.fn().mockReturnValue(EMPTY);
            courseConversationsService.createChannel = vi.fn().mockReturnValue(EMPTY);
            courseConversationsService.markAllChannelsAsRead = vi.fn().mockReturnValue(of());

            fixture = TestBed.createComponent(CourseConversationsComponent);
            component = fixture.componentInstance;

            postsSubject = new BehaviorSubject<Post[]>([]);
            vi.spyOn(courseConversationsService, 'course', 'get').mockReturnValue(course);
            activeConversation$ = new BehaviorSubject<ConversationDTO | undefined>(activeConversation);
            vi.spyOn(courseConversationsService, 'activeConversation$', 'get').mockReturnValue(activeConversation$.asObservable());
            setActiveConversationSpy = vi.spyOn(courseConversationsService, 'setActiveConversation');
            acceptCodeOfConductSpy = vi.spyOn(courseConversationsService, 'acceptCodeOfConduct');
            vi.spyOn(communicationService, 'posts', 'get').mockReturnValue(postsSubject.asObservable());
            dialogService = TestBed.inject(DialogService);
            component.sidebarConversations.set([]);

            vi.spyOn(courseOverviewService, 'mapConversationsToSidebarCardElements').mockReturnValue([
                {
                    id: 1,
                    title: 'Test Channel 1',
                    isCurrent: true,
                    conversation: { id: 1 },
                    size: 'S',
                },
                {
                    id: 2,
                    title: 'Test Channel 2',
                    isCurrent: false,
                    conversation: { id: 2 },
                    size: 'S',
                },
            ]);

            vi.spyOn(courseOverviewService, 'groupConversationsByChannelType').mockReturnValue({
                recents: {
                    entityData: [
                        {
                            id: 1,
                            title: 'Test Channel 1',
                            isCurrent: true,
                            conversation: { id: 1 },
                            size: 'S',
                        },
                    ],
                },
                generalChannels: { entityData: [] },
            });
        });

        afterEach(() => {
            component.ngOnDestroy();
            vi.useRealTimers();
            vi.resetAllMocks();
        });

        it('should refresh an already open thread after its forwarded sources finish loading', () => {
            activeConversation$.next({ id: 5, type: ConversationType.CHANNEL });
            fixture.detectChanges();
            const openedPost = new Post();
            openedPost.id = 42;
            openedPost.conversation = { id: 5 };
            component.postInThread.set(openedPost);
            fixture.detectChanges();
            const sidebar = fixture.debugElement.query(By.directive(ConversationThreadSidebarComponent));
            const forwardedContent = computed(() => sidebar.componentInstance.activePost()?.forwardedPosts?.[0]?.content);
            expect(forwardedContent()).toBeUndefined();

            // Forwarded sources arrive after the user opened the bare wrapper in the thread.
            openedPost.forwardedPosts = [{ id: 43, content: 'Loaded forwarded message' }];
            fixture.debugElement.query(By.directive(ConversationMessagesComponent)).triggerEventHandler('forwardedMessagesLoaded', [openedPost]);
            fixture.detectChanges();

            expect(sidebar.componentInstance.activePost()).not.toBe(openedPost);
            expect(forwardedContent()).toBe('Loaded forwarded message');
        });

        it('should have service set up', () => {
            fixture.detectChanges();
            expect(component.isServiceSetUp()).toBe(true);
            expect(component.isLoading()).toBe(false);
            expect(component.conversationsOfUser()).toHaveLength(1);
            expect(component.activeConversation()).toEqual(activeConversation);
        });

        it('should open and close a thread without retaining a pending source', () => {
            const opened = { id: 42 } as Post;
            const setOpenPost = vi.spyOn(component['selectionState'], 'setOpenPostId');
            component['pendingThreadPostId'] = 99;

            component.openThread(opened);
            expect(component.postInThread()).toBe(opened);
            expect(setOpenPost).toHaveBeenCalledWith(42);
            expect(component['pendingThreadPostId']).toBeUndefined();

            component.closeThread();
            expect(component.postInThread()).toBeUndefined();
            expect(component['pendingThreadPostId']).toBeUndefined();
        });

        it.each(['post', 'reply'] as const)('should scroll to and temporarily highlight an existing original %s', (kind) => {
            const host = document.createElement('div');
            host.id = 'item-987654';
            const target = kind === 'post' ? document.createElement('div') : host;
            if (kind === 'post') {
                target.className = 'post';
                host.appendChild(target);
            }
            const scroll = vi.fn();
            target.scrollIntoView = scroll;
            document.body.appendChild(host);
            try {
                if (kind === 'post') component['scrollToAndHighlightPost'](987654);
                else component['scrollToAndHighlightReply'](987654);

                expect(scroll).toHaveBeenCalledExactlyOnceWith({ behavior: 'smooth', block: 'center' });
                expect(target.classList.contains('highlight-' + kind)).toBe(true);
                vi.advanceTimersByTime(2000);
                expect(target.classList.contains('highlight-' + kind)).toBe(false);
            } finally {
                host.remove();
            }
        });

        it.each(['post', 'reply'] as const)('should wait for a lazily rendered original %s and disconnect its observer', async (kind) => {
            if (kind === 'post') component['scrollToAndHighlightPost'](987655);
            else component['scrollToAndHighlightReply'](987655);
            const observer = kind === 'post' ? component['highlightPostObserver']! : component['highlightReplyObserver']!;
            const disconnect = vi.spyOn(observer, 'disconnect');
            const host = document.createElement('div');
            host.id = 'item-987655';
            const target = kind === 'post' ? document.createElement('div') : host;
            if (kind === 'post') {
                target.className = 'post';
                host.appendChild(target);
            }
            const scroll = vi.fn();
            target.scrollIntoView = scroll;
            document.body.appendChild(host);
            try {
                // Deliver the real DOM mutation observer microtask.
                await Promise.resolve();
                expect(scroll).toHaveBeenCalledExactlyOnceWith({ behavior: 'smooth', block: 'center' });
                expect(target.classList.contains('highlight-' + kind)).toBe(true);
                expect(disconnect).toHaveBeenCalled();
                vi.advanceTimersByTime(2000);
                expect(target.classList.contains('highlight-' + kind)).toBe(false);
            } finally {
                host.remove();
            }
        });

        it('should stop waiting for an unavailable original reply after five seconds', () => {
            component['focusReplyId'] = 987656;
            component['scrollToAndHighlightReply'](987656);
            const disconnect = vi.spyOn(component['highlightReplyObserver']!, 'disconnect');

            vi.advanceTimersByTime(5000);

            expect(disconnect).toHaveBeenCalled();
            expect(component['focusReplyId']).toBeUndefined();
        });

        it('should store the highlight observers and disconnect them in ngOnDestroy', () => {
            // Trigger both highlight helpers for elements that do not exist yet, so each creates and stores a MutationObserver.
            component['scrollToAndHighlightPost'](999999);
            component['scrollToAndHighlightReply'](999998);

            const postObserver = component['highlightPostObserver'];
            const replyObserver = component['highlightReplyObserver'];
            expect(postObserver).toBeDefined();
            expect(replyObserver).toBeDefined();

            const postDisconnectSpy = vi.spyOn(postObserver!, 'disconnect');
            const replyDisconnectSpy = vi.spyOn(replyObserver!, 'disconnect');

            component.ngOnDestroy();

            expect(postDisconnectSpy).toHaveBeenCalled();
            expect(replyDisconnectSpy).toHaveBeenCalled();
        });

        describe('Dialog Opening', () => {
            const mockOnClose = new Subject<any>();
            const mockDialogRef = { onClose: mockOnClose.asObservable(), close: vi.fn() } as unknown as DynamicDialogRef;

            beforeEach(() => {
                // Set a mock course with id 1
                component.course.set({ id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING } as Course);
            });

            it('should open the group chat creation dialog', () => {
                const spy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
                component.openCreateGroupChatDialog();
                expect(spy).toHaveBeenCalledExactlyOnceWith(GroupChatCreateDialogComponent, expect.anything());
            });

            it('should open the one-to-one chat creation dialog', () => {
                const spy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
                component.openCreateOneToOneChatDialog();
                expect(spy).toHaveBeenCalledExactlyOnceWith(OneToOneChatCreateDialogComponent, expect.anything());
            });

            it('should open the channel overview dialog', () => {
                const spy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
                component.openChannelOverviewDialog();
                expect(spy).toHaveBeenCalledExactlyOnceWith(ChannelsOverviewDialogComponent, expect.anything());
            });

            it('should open the create channel dialog when onCreateChannelPressed is called', () => {
                fixture.detectChanges();
                const spy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

                component.openCreateChannelDialog();

                expect(spy).toHaveBeenCalledExactlyOnceWith(ChannelsCreateDialogComponent, expect.anything());
            });

            for (const kind of ['group', 'direct'] as const) {
                it.each(['complete', 'error', 'cancel'] as const)(`preserves sidebar read state when ${kind} chat creation ends with %s`, (outcome) => {
                    fixture.detectChanges();
                    const previousSidebar = component.sidebarData();
                    const previousCards = component.sidebarConversations();
                    const replacement = [{ id: 44, title: 'Study group', isCurrent: false, conversation: { id: 44, unreadMessagesCount: 2 }, size: 'S' as const }];
                    vi.mocked(courseOverviewService.mapConversationsToSidebarCardElements).mockReturnValue(replacement);
                    const create = kind === 'group' ? vi.mocked(courseConversationsService.createGroupChat) : vi.mocked(courseConversationsService.createOneToOneChat);
                    create.mockReturnValue(outcome === 'error' ? throwError(() => new Error('conversation reload failed')) : EMPTY);
                    const ref = new DynamicDialogRef();
                    vi.spyOn(dialogService, 'open').mockReturnValue(ref);
                    if (kind === 'group') component.openCreateGroupChatDialog();
                    else component.openCreateOneToOneChatDialog();

                    const partners = [
                        { id: 41, login: 'student-a' },
                        { id: 42, login: 'student-b' },
                    ];
                    ref.close(outcome === 'cancel' ? undefined : kind === 'group' ? partners : partners[0]);

                    if (outcome === 'cancel') {
                        expect(create).not.toHaveBeenCalled();
                    } else {
                        expect(create).toHaveBeenCalledExactlyOnceWith(kind === 'group' ? ['student-a', 'student-b'] : 'student-a');
                    }
                    if (outcome === 'complete') {
                        expect(component.sidebarData()).not.toBe(previousSidebar);
                        expect(component.sidebarConversations()).toEqual(replacement);
                        expect(component.sidebarConversations()[0].conversation?.unreadMessagesCount).toBe(2);
                    } else {
                        expect(component.sidebarData()).toBe(previousSidebar);
                        expect(component.sidebarConversations()).toBe(previousCards);
                    }
                });
            }

            it.each([
                ['modified', true, false],
                ['unmodified', false, false],
                ['failed reload', true, true],
            ] as const)('opens the overview selection only after a valid %s result and retains unread cards on failure', (_context, modified, failed) => {
                fixture.detectChanges();
                const previousSidebar = component.sidebarData();
                const previousCards = component.sidebarConversations();
                const selected = generateExampleChannelDTO({ id: 77, unreadMessagesCount: 2 } as ChannelDTO);
                const ref = new DynamicDialogRef();
                vi.spyOn(dialogService, 'open').mockReturnValue(ref);
                vi.mocked(courseConversationsService.forceRefresh).mockReturnValue(failed ? throwError(() => new Error('reload failed')) : EMPTY);
                setActiveConversationSpy.mockClear();
                component.openChannelOverviewDialog();

                ref.close([selected, modified]);

                if (failed) {
                    expect(setActiveConversationSpy).not.toHaveBeenCalled();
                    expect(component.sidebarData()).toBe(previousSidebar);
                    expect(component.sidebarConversations()).toBe(previousCards);
                } else {
                    expect(setActiveConversationSpy).toHaveBeenCalledExactlyOnceWith(selected);
                    expect(component.sidebarData()).not.toBe(previousSidebar);
                }
                if (modified) expect(courseConversationsService.forceRefresh).toHaveBeenCalledWith(false, true);
            });
        });

        it('should update thread in post', () => {
            fixture.detectChanges();
            component.postInThread.set({ id: 1, content: 'loremIpsum' } as Post);
            fixture.changeDetectorRef.detectChanges();
            const updatedPost = { id: 1, content: 'updatedContent' } as Post;
            postsSubject.next([updatedPost]);
            vi.advanceTimersByTime(0);
            expect(component.postInThread()).toEqual(updatedPost);
        });

        it('should set active conversation depending on the query param', () => {
            queryParamsSubject.next({ conversationId: '12' });
            // mock setActiveConversationById method
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(0);
            expect(setActiveConversationSpy).toHaveBeenCalledWith(12);
        });

        it('should call sidebar collapse if conversation changes', () => {
            const closeSidebarOnMobileSpy = vi.spyOn(component, 'closeSidebarOnMobile');
            queryParamsSubject.next({ conversationId: '12' });
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(0);
            expect(closeSidebarOnMobileSpy).toHaveBeenCalled();
        });

        it('should call sidebar collapse if thread opens', () => {
            const closeSidebarOnMobileSpy = vi.spyOn(component, 'closeSidebarOnMobile');
            queryParamsSubject.next({ messageId: '12' });
            fixture.changeDetectorRef.detectChanges();
            vi.advanceTimersByTime(0);
            expect(closeSidebarOnMobileSpy).toHaveBeenCalled();
        });

        it('should set the query params when an active conversation is selected', () => {
            const activatedRoute = TestBed.inject(ActivatedRoute);
            const navigateSpy = vi.spyOn(router, 'navigate');
            fixture.detectChanges();
            expect(navigateSpy).toHaveBeenCalledWith([], {
                relativeTo: activatedRoute,
                queryParams: { conversationId: activeConversation?.id },
                replaceUrl: true,
            });
        });

        it('should accept code of conduct', () => {
            fixture.detectChanges();
            component.acceptCodeOfConduct();
            expect(acceptCodeOfConductSpy).toHaveBeenCalledOnce();
        });

        it('should set search text in the courseWideSearchConfig correctly', () => {
            fixture.detectChanges();
            component.initializeCourseWideSearchConfig();
            component.onSearch({
                searchTerm: 'test',
                selectedAuthors: [],
                selectedConversations: [],
            });
            fixture.changeDetectorRef.detectChanges();
            expect(component.courseWideSearchConfig.searchTerm).toBe('test');
        });

        it('should tell the service for as long as the conversation view is open', () => {
            const setConversationViewOpen = vi.spyOn(courseConversationsService, 'setConversationViewOpen');

            fixture.detectChanges();
            expect(setConversationViewOpen).toHaveBeenCalledWith(true);

            fixture.destroy();
            expect(setConversationViewOpen).toHaveBeenLastCalledWith(false);
        });

        it('should show the newest messages of the course first when no search is active', () => {
            fixture.detectChanges();
            component.initializeCourseWideSearchConfig();
            expect(component.courseWideSearchConfig.sortingOrder).toBe(SortDirection.DESCENDING);
        });

        it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
            component.toggleSidebar();
            expect(component.isCollapsed()).toBe(true);

            component.toggleSidebar();
            expect(component.isCollapsed()).toBe(false);
        });

        it('should toggle isCollapsed when service emits corresponding event', () => {
            fixture.detectChanges();
            courseSidebarService.openSidebar();
            expect(component.isCollapsed()).toBe(true);

            courseSidebarService.closeSidebar();
            expect(component.isCollapsed()).toBe(false);

            courseSidebarService.toggleSidebar();
            expect(component.isCollapsed()).toBe(true);
        });

        it('should reload sidebar when reloadSidebar$ event is emitted', () => {
            const prepareSidebarDataSpy = vi.spyOn(component, 'prepareSidebarData');
            fixture.detectChanges(); // ngOnInit is called here

            courseSidebarService.reloadSidebar();

            expect(prepareSidebarDataSpy).toHaveBeenCalled();
        });

        it('should switch to mobile if breakpoint returns true and open sidebar', () => {
            const openSidebarSpy = vi.spyOn(courseSidebarService, 'openSidebar');
            expect(component.isMobile()).toBe(false);
            breakpoint$.next({
                matches: true,
                breakpoints: { [Breakpoints.Handset]: true },
            });
            component.ngOnInit();
            expect(component.isMobile()).toBe(true);
            expect(openSidebarSpy).toHaveBeenCalled();
        });

        it('should call close sidebar if conversation is selected', () => {
            const closeSidebarSpy = vi.spyOn(courseSidebarService, 'closeSidebar');
            breakpoint$.next({
                matches: true,
                breakpoints: { [Breakpoints.Handset]: true },
            });

            component.onConversationSelected(activeConversation?.id ?? 1);

            expect(closeSidebarSpy).toHaveBeenCalled();
        });

        it('should call close sidebar and search term is set and open sidebar otherwise if on mobile', () => {
            const openSidebarSpy = vi.spyOn(courseSidebarService, 'openSidebar');
            fixture.detectChanges();
            breakpoint$.next({
                matches: true,
                breakpoints: { [Breakpoints.Handset]: true },
            });
            fixture.changeDetectorRef.detectChanges();
            component.onSearch({
                searchTerm: '',
                selectedAuthors: [],
                selectedConversations: [],
            });
            expect(openSidebarSpy).toHaveBeenCalled();
        });

        it('should display sidebar when conversation is provided', async () => {
            fixture.detectChanges();
            // Wait for any async operations to complete here if necessary
            await fixture.whenStable(); // Trigger change detection again if async operations might change the state
            expect(fixture.nativeElement.querySelector('jhi-sidebar')).not.toBeNull();
        });

        it('should toggle sidebar visibility based on isCollapsed property', () => {
            component.isCollapsed.set(false);
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).toBeNull();

            component.isCollapsed.set(true);
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).not.toBeNull();
        });

        it('onConversationSelected should change active conversation', () => {
            const setActiveConversationSpy = vi.spyOn(courseConversationsService, 'setActiveConversation').mockImplementation(() => {});
            component.onConversationSelected(activeConversation?.id ?? 1);
            expect(setActiveConversationSpy).toHaveBeenCalled();
        });

        describe('performChannelAction', () => {
            let channelAction: ChannelAction;
            let channel: ChannelDTO;

            beforeEach(() => {
                channel = new ChannelDTO();
                channel.id = 123;
                channel.name = 'Test Channel';

                channelAction = {
                    action: 'create',
                    channel,
                };

                component.createChannelFn = undefined;
                component.ngOnInit();

                component.createChannelFn = vi.fn().mockReturnValue(EMPTY);
                vi.spyOn(component, 'prepareSidebarData').mockImplementation(() => {});
            });

            it('should call createChannelFn with the provided channel', () => {
                component.performChannelAction(channelAction);
                expect(component.createChannelFn).toHaveBeenCalledWith(channel);
            });

            it('should call prepareSidebarData on successful completion', () => {
                component.createChannelFn = vi.fn().mockReturnValue({
                    pipe: () => ({
                        subscribe: ({ complete }: any) => {
                            complete();
                        },
                    }),
                });

                component.performChannelAction(channelAction);
                expect(component.prepareSidebarData).toHaveBeenCalled();
            });

            it('should log an error if createChannelFn throws an error', () => {
                component.createChannelFn = vi.fn().mockReturnValue({
                    pipe: () => ({
                        subscribe: ({ error }: any) => {
                            error('Test Error');
                        },
                    }),
                });

                component.performChannelAction(channelAction);
            });

            it('should not call createChannelFn or prepareSidebarData if createChannelFn is undefined', () => {
                component.createChannelFn = undefined;
                const prepareSidebarDataSpy = vi.spyOn(component, 'prepareSidebarData');
                component.performChannelAction(channelAction);
                expect(component.createChannelFn).toBeUndefined();
                // Since createChannelFn is undefined, prepareSidebarData should not be called
                expect(prepareSidebarDataSpy).not.toHaveBeenCalled();
            });

            it('should correctly populate the recents group in accordionConversationGroups using existing mocks', () => {
                (courseConversationsService.forceRefresh as ReturnType<typeof vi.fn>).mockReturnValue(of({}));

                component.prepareSidebarData();
                const recentsGroup = component.accordionConversationGroups().recents;
                expect(recentsGroup).toBeDefined();
                expect(recentsGroup.entityData).toHaveLength(1);
                expect(recentsGroup.entityData[0].isCurrent).toBe(true);
            });
        });

        describe('empty direct messages in the sidebar', () => {
            const emptyChat = { id: 101, type: ConversationType.ONE_TO_ONE } as ConversationDTO;
            const usedChat = { id: 102, type: ConversationType.ONE_TO_ONE, lastMessageDate: dayjs() } as ConversationDTO;
            const emptyChannel = { id: 103, type: ConversationType.CHANNEL } as ConversationDTO;
            const emptyGroupChat = { id: 104, type: ConversationType.GROUP_CHAT } as ConversationDTO;

            beforeEach(() => {
                (courseConversationsService.forceRefresh as ReturnType<typeof vi.fn>).mockReturnValue(of({}));
                component.conversationsOfUser.set([emptyChat, usedChat, emptyChannel, emptyGroupChat]);
            });

            it('should leave an empty direct message out of the sidebar when it is not open', () => {
                component.activeConversation.set(undefined);

                component.prepareSidebarData();

                expect(vi.mocked(courseOverviewService.groupConversationsByChannelType).mock.lastCall?.[1]).toEqual([usedChat, emptyChannel, emptyGroupChat]);
                expect(courseOverviewService.mapConversationsToSidebarCardElements).toHaveBeenLastCalledWith([usedChat, emptyChannel, emptyGroupChat]);
            });

            it('should keep an empty direct message in the sidebar while it is open', () => {
                component.activeConversation.set(emptyChat);

                component.prepareSidebarData();

                expect(vi.mocked(courseOverviewService.groupConversationsByChannelType).mock.lastCall?.[1]).toEqual([emptyChat, usedChat, emptyChannel, emptyGroupChat]);
            });

            it('should reload the sidebar when the user leaves an empty direct message', () => {
                fixture.detectChanges();
                component.conversationsOfUser.set([emptyChat, usedChat]);
                activeConversation$.next(emptyChat);
                const prepareSidebarDataSpy = vi.spyOn(component, 'prepareSidebarData');

                activeConversation$.next(usedChat);

                expect(prepareSidebarDataSpy).toHaveBeenCalledOnce();
            });

            it('should not reload the sidebar when the user leaves a direct message that has messages', () => {
                fixture.detectChanges();
                component.conversationsOfUser.set([emptyChat, usedChat]);
                activeConversation$.next(usedChat);
                const prepareSidebarDataSpy = vi.spyOn(component, 'prepareSidebarData');

                activeConversation$.next(emptyChat);

                expect(prepareSidebarDataSpy).not.toHaveBeenCalled();
            });
        });

        describe('an empty direct message that receives its first message', () => {
            let emptyChat: ConversationDTO;
            let usedChat: ConversationDTO;
            let conversationsOfUser$: BehaviorSubject<ConversationDTO[]>;

            beforeEach(() => {
                emptyChat = { id: 101, type: ConversationType.ONE_TO_ONE } as ConversationDTO;
                usedChat = { id: 102, type: ConversationType.ONE_TO_ONE, lastMessageDate: dayjs() } as ConversationDTO;
                conversationsOfUser$ = new BehaviorSubject<ConversationDTO[]>([emptyChat, usedChat]);
                Object.defineProperty(courseConversationsService, 'conversationsOfUser$', { get: () => conversationsOfUser$.asObservable() });
                (courseConversationsService.forceRefresh as ReturnType<typeof vi.fn>).mockReturnValue(of({}));
                vi.mocked(courseOverviewService.mapConversationsToSidebarCardElements).mockImplementation((conversations) =>
                    conversations.map((conversation) => ({ id: conversation.id!, title: 'chat', size: 'S', conversation })),
                );
                fixture.detectChanges();
                component.prepareSidebarData();
                (courseConversationsService.forceRefresh as ReturnType<typeof vi.fn>).mockClear();
                vi.mocked(courseOverviewService.groupConversationsByChannelType).mockClear();
            });

            it('should add the chat to the sidebar when the other participant sends the first message', () => {
                expect(component.sidebarConversations().map((card) => card.id)).toEqual([usedChat.id]);

                // the service updates the cached chat in place when the message event arrives
                emptyChat.lastMessageDate = dayjs();
                conversationsOfUser$.next([emptyChat, usedChat]);

                expect(component.sidebarConversations().map((card) => card.id)).toEqual([emptyChat.id, usedChat.id]);
                expect(vi.mocked(courseOverviewService.groupConversationsByChannelType).mock.lastCall?.[1]).toContain(emptyChat);
                expect(courseConversationsService.forceRefresh).not.toHaveBeenCalled();
            });

            it('should keep the chat out of the sidebar while it is still empty', () => {
                conversationsOfUser$.next([emptyChat, usedChat]);

                expect(courseOverviewService.groupConversationsByChannelType).not.toHaveBeenCalled();
                expect(component.sidebarConversations().map((card) => card.id)).toEqual([usedChat.id]);
            });
        });

        describe('the unread count of a conversation changes while the sidebar is shown', () => {
            let channel: ConversationDTO;
            let conversationsOfUser$: BehaviorSubject<ConversationDTO[]>;

            beforeEach(() => {
                channel = { id: 301, type: ConversationType.CHANNEL, unreadMessagesCount: 0 } as ConversationDTO;
                conversationsOfUser$ = new BehaviorSubject<ConversationDTO[]>([channel]);
                Object.defineProperty(courseConversationsService, 'conversationsOfUser$', { get: () => conversationsOfUser$.asObservable() });
                (courseConversationsService.forceRefresh as ReturnType<typeof vi.fn>).mockReturnValue(of({}));
                vi.mocked(courseOverviewService.mapConversationsToSidebarCardElements).mockImplementation((conversations) =>
                    conversations.map((conversation) => ({ id: conversation.id!, title: 'channel', size: 'S', conversation })),
                );
                fixture.detectChanges();
                component.prepareSidebarData();
                (courseConversationsService.forceRefresh as ReturnType<typeof vi.fn>).mockClear();
                vi.mocked(courseOverviewService.groupConversationsByChannelType).mockClear();
            });

            it('should rebuild the sidebar so that a message or reply that arrived is shown', () => {
                // the service raises the count of the cached channel in place, so the sidebar cards, which read it from that
                // object, would not notice it without being rebuilt
                channel.unreadMessagesCount = 1;
                conversationsOfUser$.next([channel]);

                expect(courseOverviewService.groupConversationsByChannelType).toHaveBeenCalledOnce();
                expect(courseConversationsService.forceRefresh).not.toHaveBeenCalled();
            });

            it('should rebuild the sidebar when the conversation was marked as unread without a count', () => {
                channel.hasUnreadMessage = true;
                conversationsOfUser$.next([channel]);

                expect(courseOverviewService.groupConversationsByChannelType).toHaveBeenCalledOnce();
            });

            it('should leave the sidebar alone when no unread count changed', () => {
                conversationsOfUser$.next([channel]);

                expect(courseOverviewService.groupConversationsByChannelType).not.toHaveBeenCalled();
            });
        });

        describe('opening a conversation that the sidebar does not list yet', () => {
            const newChannel = { id: 201, type: ConversationType.CHANNEL } as ConversationDTO;
            const knownChannel = { id: 202, type: ConversationType.CHANNEL } as ConversationDTO;

            beforeEach(() => {
                fixture.detectChanges();
                (courseConversationsService.forceRefresh as ReturnType<typeof vi.fn>).mockClear();
                component.conversationsOfUser.set([newChannel, knownChannel]);
                component.sidebarConversations.set([{ id: knownChannel.id!, title: 'known', size: 'S', conversation: knownChannel }]);
                vi.mocked(courseOverviewService.groupConversationsByChannelType).mockClear();
            });

            it('should rebuild the sidebar without asking the server again', () => {
                activeConversation$.next(newChannel);

                expect(courseOverviewService.groupConversationsByChannelType).toHaveBeenCalledOnce();
                expect(vi.mocked(courseOverviewService.groupConversationsByChannelType).mock.lastCall?.[1]).toContain(newChannel);
                expect(courseConversationsService.forceRefresh).not.toHaveBeenCalled();
            });

            it('should leave the sidebar alone when the conversation is already listed', () => {
                activeConversation$.next(knownChannel);

                expect(courseOverviewService.groupConversationsByChannelType).not.toHaveBeenCalled();
            });
        });

        describe('query parameter handling', () => {
            it('should handle SavedPostStatus in conversationId', () => {
                const queryParams = {
                    conversationId: SavedPostStatus.ARCHIVED.toString().toLowerCase(),
                };
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['selectedSavedPostStatus']()).toBe(SavedPostStatus.ARCHIVED);
                expect(setActiveConversationSpy).not.toHaveBeenCalled();
            });

            it('should handle numeric conversationId', () => {
                const queryParams = {
                    conversationId: '123',
                };
                activatedRoute.queryParams = of(queryParams);
                const spy = vi.spyOn(component, 'closeSidebarOnMobile');

                component.subscribeToQueryParameter();

                expect(setActiveConversationSpy).toHaveBeenCalledWith(123);
                expect(spy).toHaveBeenCalled();
            });

            it('should handle focusPostId parameter', () => {
                const queryParams = {
                    focusPostId: '456',
                };
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['focusPostId']()).toBe(456);
            });

            it('should handle openThreadOnFocus parameter', () => {
                const queryParams = {
                    openThreadOnFocus: 'true',
                };
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['openThreadOnFocus']()).toBe('true');
            });

            it('should handle messageId parameter', () => {
                const queryParams = {
                    messageId: '789',
                };
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['postInThread']()).toEqual({ id: 789, conversation: { id: undefined } });
            });

            it('should clear postInThread when no messageId is present', () => {
                const queryParams = {};
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['postInThread']()).toBeUndefined();
            });

            it('should handle multiple query parameters together', () => {
                const queryParams = {
                    conversationId: SavedPostStatus.ARCHIVED.toString().toLowerCase(),
                    focusPostId: '456',
                    openThreadOnFocus: 'true',
                    messageId: '789',
                };
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['selectedSavedPostStatus']()).toBe(SavedPostStatus.ARCHIVED);
                expect(component['focusPostId']()).toBe(456);
                expect(component['openThreadOnFocus']()).toBe('true');
                expect(component['postInThread']()).toEqual({ id: 789, conversation: { id: undefined } });
            });
        });

        describe('navigate to post functionality', () => {
            it('should handle answer post navigation correctly', () => {
                const answerPost: Posting = {
                    referencePostId: 123,
                    postingType: PostingType.ANSWER,
                    conversation: {
                        id: 456,
                    },
                };

                component.onNavigateToPost(answerPost);

                expect(component['focusPostId']()).toBe(123);
                expect(component['openThreadOnFocus']()).toBe(true);
                expect(setActiveConversationSpy).toHaveBeenCalledWith(456);
            });

            it('should handle question post navigation correctly', () => {
                const questionPost: Posting = {
                    referencePostId: 123,
                    postingType: PostingType.POST,
                    conversation: {
                        id: 456,
                    },
                };

                component.onNavigateToPost(questionPost);

                expect(component['focusPostId']()).toBe(123);
                expect(component['openThreadOnFocus']()).toBe(false);
                expect(setActiveConversationSpy).toHaveBeenCalledWith(456);
            });

            it('should not process navigation when referencePostId is undefined', () => {
                const invalidPost: Posting = {
                    postingType: PostingType.POST,
                    conversation: {
                        id: 456,
                    },
                };

                component.onNavigateToPost(invalidPost);

                expect(setActiveConversationSpy).not.toHaveBeenCalled();
            });

            it('should not process navigation when conversation id is undefined', () => {
                const invalidPost: Posting = {
                    referencePostId: 123,
                    postingType: PostingType.POST,
                    conversation: {},
                };

                component.onNavigateToPost(invalidPost);

                expect(setActiveConversationSpy).not.toHaveBeenCalled();
            });
        });

        it('should mark all channels as read', () => {
            fixture.detectChanges();
            const markAllChannelsAsRead = vi.spyOn(courseConversationsService, 'markAllChannelsAsRead').mockReturnValue(of());
            const forceRefresh = vi.spyOn(courseConversationsService, 'forceRefresh');
            forceRefresh.mockClear();
            component.markAllChannelAsRead();
            expect(markAllChannelsAsRead).toHaveBeenCalledOnce();
            expect(forceRefresh).toHaveBeenCalledTimes(2);
        });

        describe('conversation selection', () => {
            it('should handle numeric conversationId', () => {
                fixture.detectChanges();
                component.onConversationSelected(123);
                expect(component.selectedSavedPostStatus()).toBeUndefined();
                expect(setActiveConversationSpy).toHaveBeenCalledWith(123);
            });

            it('should handle valid string conversationId as SavedPostStatus', () => {
                fixture.detectChanges();
                const validStatus = SavedPostStatus.ARCHIVED.toString().toLowerCase();
                component.onConversationSelected(validStatus);
                expect(component.selectedSavedPostStatus()).toBe(SavedPostStatus.ARCHIVED);
                expect(component.postInThread()).toBeUndefined();
                expect(setActiveConversationSpy).toHaveBeenCalledWith(undefined);
                expect(component.activeConversation()).toBeUndefined();
            });

            it('should ignore invalid string conversationId', () => {
                fixture.detectChanges();
                const invalidStatus = 'invalidStatus';
                setActiveConversationSpy.mockClear();
                component.onConversationSelected(invalidStatus);
                vi.advanceTimersByTime(0);
                expect(component.selectedSavedPostStatus()).toBeUndefined();
                expect(setActiveConversationSpy).not.toHaveBeenCalled();
                vi.clearAllTimers();
            });

            it('should toggle the value of showOnlyPinned', () => {
                expect(component.showOnlyPinned()).toBe(false);

                component.togglePinnedView();
                expect(component.showOnlyPinned()).toBe(true);

                component.togglePinnedView();
                expect(component.showOnlyPinned()).toBe(false);
            });

            it('should update pinnedCount when onPinnedCountChanged is called', () => {
                const newPinnedCount = 5;

                component.onPinnedCountChanged(newPinnedCount);
                expect(component.pinnedCount()).toBe(newPinnedCount);
            });

            it('should set showOnlyPinned to false if pinnedCount becomes 0', () => {
                component.showOnlyPinned.set(true);
                component.onPinnedCountChanged(0);
                expect(component.showOnlyPinned()).toBe(false);
            });

            it('should not change showOnlyPinned if pinnedCount changes but is not 0', () => {
                component.showOnlyPinned.set(true);
                component.onPinnedCountChanged(5);
                expect(component.showOnlyPinned()).toBe(true);

                component.showOnlyPinned.set(false);
                component.onPinnedCountChanged(10);
                expect(component.showOnlyPinned()).toBe(false);
            });
        });

        describe('CourseConversationsComponent onTriggerNavigateToPost Tests', () => {
            let component: CourseConversationsComponent;

            beforeEach(() => {
                fixture = TestBed.createComponent(CourseConversationsComponent);
                component = fixture.componentInstance;
            });

            it('should do nothing if post.id is undefined', () => {
                const post = {} as Posting;

                component.onTriggerNavigateToPost(post);

                expect(component.focusPostId()).toBeUndefined();
                expect(component.openThreadOnFocus()).toBeFalsy();
                expect(setActiveConversationSpy).not.toHaveBeenCalled();
            });

            it('should set openThreadOnFocus = true if postingType is ANSWER', () => {
                const post = {
                    id: 1,
                    postingType: PostingType.ANSWER,
                    post: { id: 2 } as Post,
                } as Posting;

                component.onTriggerNavigateToPost(post);
                expect(component.openThreadOnFocus()).toBe(true);
            });

            it('should set openThreadOnFocus = false if postingType is POST (question post)', () => {
                const post = {
                    id: 1,
                    postingType: PostingType.POST,
                    conversation: { id: 1 } as Conversation,
                } as Posting;

                component.onTriggerNavigateToPost(post);
                expect(component.openThreadOnFocus()).toBe(false);
            });

            it('should call setActiveConversation if conversation.id is defined', () => {
                const post = {
                    id: 1,
                    conversation: { id: 999 },
                } as Posting;

                component.onTriggerNavigateToPost(post);

                expect(setActiveConversationSpy).toHaveBeenCalledWith(999);
            });

            it('should NOT call setActiveConversation if post.id is undefined', () => {
                const post = {
                    id: undefined,
                    conversation: {},
                } as Posting;

                component.onTriggerNavigateToPost(post);

                expect(setActiveConversationSpy).not.toHaveBeenCalled();
            });

            it('should combine logic: set focusPostId and only set conversation if ID is present', () => {
                const post = {
                    id: 10,
                    referencePostId: 888,
                    postingType: PostingType.ANSWER,
                    conversation: {
                        id: 444,
                    },
                } as Posting;

                component.onTriggerNavigateToPost(post);

                expect(component.focusPostId()).toBe(10);
                expect(component.openThreadOnFocus()).toBe(false);
                expect(setActiveConversationSpy).toHaveBeenCalledWith(444);
            });
        });

        describe('Search Clear and Conversation Restoration', () => {
            beforeEach(() => {
                fixture.detectChanges();
            });

            it('should clear search config and restore previous conversation when X is clicked', () => {
                const previousConversation = { id: 42, type: 'channel' } as ConversationDTO;
                component.activeConversation.set(previousConversation);
                component.lastKnownConversationId = 42;

                component.courseWideSearchConfig.searchTerm = 'test search';
                component.courseWideSearchConfig.selectedConversations = [previousConversation];
                component.courseWideSearchConfig.selectedAuthors = [{ id: 1 } as any];

                component.onSelectionChange({
                    searchTerm: '',
                    selectedConversations: [previousConversation],
                    selectedAuthors: [],
                });

                expect(component.previousConversationBeforeSearch).toEqual(previousConversation);

                component.onClearSearchAndRestorePrevious();

                expect(component.courseWideSearchConfig.searchTerm).toBe('');
                expect(component.courseWideSearchConfig.selectedConversations).toEqual([]);
                expect(component.courseWideSearchConfig.selectedAuthors).toEqual([]);

                expect(setActiveConversationSpy).toHaveBeenCalledWith(42);
                expect(component.previousConversationBeforeSearch).toBeUndefined();
            });

            it('should restore last known conversation when no previous conversation before search', () => {
                component.previousConversationBeforeSearch = undefined;
                component.lastKnownConversationId = 99;

                component.courseWideSearchConfig.searchTerm = 'test';
                component.courseWideSearchConfig.selectedConversations = [{ id: 1 } as ConversationDTO];

                component.onClearSearchAndRestorePrevious();

                expect(component.courseWideSearchConfig.searchTerm).toBe('');
                expect(component.courseWideSearchConfig.selectedConversations).toEqual([]);
                expect(component.courseWideSearchConfig.selectedAuthors).toEqual([]);

                expect(setActiveConversationSpy).toHaveBeenCalledWith(99);
            });

            it('should trigger All messages search when no conversation to restore', () => {
                component.previousConversationBeforeSearch = undefined;
                component.lastKnownConversationId = undefined;
                const updateQueryParamsSpy = vi.spyOn(component, 'updateQueryParameters');
                const courseWideSearchMock = { onSearch: vi.fn() };
                vi.spyOn(component, 'courseWideSearch').mockReturnValue(courseWideSearchMock as any);

                component.courseWideSearchConfig.searchTerm = 'test search';
                component.courseWideSearchConfig.selectedConversations = [{ id: 1 } as ConversationDTO];

                component.onClearSearchAndRestorePrevious();

                expect(component.courseWideSearchConfig.searchTerm).toBe('');
                expect(component.courseWideSearchConfig.selectedConversations).toEqual([]);
                expect(component.courseWideSearchConfig.selectedAuthors).toEqual([]);

                expect(setActiveConversationSpy).toHaveBeenCalledWith(undefined);
                expect(component.activeConversation()).toBeUndefined();
                expect(component.selectedSavedPostStatus()).toBeUndefined();
                expect(updateQueryParamsSpy).toHaveBeenCalled();
                expect(courseWideSearchMock.onSearch).toHaveBeenCalled();
            });

            it('should track last known conversation ID when active conversation changes', () => {
                const newConversation = { id: 123, type: 'channel' } as ConversationDTO;
                vi.spyOn(courseConversationsService, 'activeConversation$', 'get').mockReturnValue(of(newConversation));

                component.ngOnInit();
                vi.advanceTimersByTime(0);

                expect(component.lastKnownConversationId).toBe(123);
            });

            it('should save active conversation only once when search starts', () => {
                const conversation = { id: 50, type: 'channel' } as ConversationDTO;
                component.activeConversation.set(conversation);
                component.previousConversationBeforeSearch = undefined;

                // First selection - should save
                component.onSelectionChange({
                    searchTerm: '',
                    selectedConversations: [conversation],
                    selectedAuthors: [],
                });

                expect(component.previousConversationBeforeSearch).toEqual(conversation);

                // Second selection - should NOT overwrite
                const anotherConversation = { id: 60, type: 'channel' } as ConversationDTO;
                component.activeConversation.set(anotherConversation);

                component.onSelectionChange({
                    searchTerm: '',
                    selectedConversations: [anotherConversation],
                    selectedAuthors: [],
                });

                expect(component.previousConversationBeforeSearch).toEqual(conversation); // Still the first one
            });

            it('should not save conversation when no filters are active', () => {
                const conversation = { id: 70, type: 'channel' } as ConversationDTO;
                component.activeConversation.set(conversation);
                component.previousConversationBeforeSearch = undefined;

                component.onSelectionChange({
                    searchTerm: '',
                    selectedConversations: [],
                    selectedAuthors: [],
                });

                expect(component.previousConversationBeforeSearch).toBeUndefined();
            });

            it('should call closeSidebarOnMobile when clearing search', () => {
                const closeSidebarSpy = vi.spyOn(component, 'closeSidebarOnMobile');
                component.lastKnownConversationId = 1;

                component.onClearSearchAndRestorePrevious();

                expect(closeSidebarSpy).toHaveBeenCalled();
            });
        });

        it('should update the page title signal via setPageTitle', () => {
            expect(component.pageTitle()).toBe('');

            component.setPageTitle('overview.communication');

            expect(component.pageTitle()).toBe('overview.communication');
        });
    });
});
