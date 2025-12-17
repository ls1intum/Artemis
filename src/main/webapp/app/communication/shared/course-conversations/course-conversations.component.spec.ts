import { BreakpointObserver, BreakpointState, Breakpoints } from '@angular/cdk/layout';
import { CourseConversationsComponent } from 'app/communication/shared/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { Conversation, ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { MockComponent, MockInstance, MockPipe, MockProvider } from 'ng-mocks';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { LoadingIndicatorContainerStubComponent } from 'test/helpers/stubs/shared/loading-indicator-container-stub.component';
import { ConversationHeaderComponent } from 'app/communication/course-conversations-components/layout/conversation-header/conversation-header.component';
import { CourseWideSearchComponent } from 'app/communication/course-conversations-components/course-wide-search/course-wide-search.component';
import { ConversationMessagesComponent } from 'app/communication/course-conversations-components/layout/conversation-messages/conversation-messages.component';
import { ConversationThreadSidebarComponent } from 'app/communication/course-conversations-components/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { BehaviorSubject, EMPTY, of } from 'rxjs';
import { NgbModal, NgbModalRef, NgbModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Params, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MetisService } from 'app/communication/service/metis.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { CourseConversationsCodeOfConductComponent } from 'app/communication/course-conversations-components/code-of-conduct/course-conversations-code-of-conduct.component';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { GroupChatCreateDialogComponent } from 'app/communication/course-conversations-components/group-chat-create-dialog/group-chat-create-dialog.component';
import { SidebarEventService } from 'app/shared/sidebar/service/sidebar-event.service';
import { SidebarAccordionComponent } from 'app/shared/sidebar/sidebar-accordion/sidebar-accordion.component';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { OneToOneChatCreateDialogComponent } from 'app/communication/course-conversations-components/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CourseSidebarService } from 'app/core/course/overview/services/course-sidebar.service';
import { ChannelsCreateDialogComponent } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channels-create-dialog.component';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Posting, PostingType, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { ElementRef, signal } from '@angular/core';
import {
    ChannelAction,
    ChannelsOverviewDialogComponent,
} from 'app/communication/course-conversations-components/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ConversationGlobalSearchComponent } from 'app/communication/shared/conversation-global-search/conversation-global-search.component';
import { AlertService } from 'app/shared/service/alert.service';
import { FaqService } from 'app/communication/faq/faq.service';
import { TranslateModule } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
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
        let acceptCodeOfConductSpy: jest.SpyInstance;
        let setActiveConversationSpy: jest.SpyInstance;
        let metisConversationService: MetisConversationService;
        let courseOverviewService: CourseOverviewService;
        let modalService: NgbModal;
        let courseSidebarService: CourseSidebarService;
        let activatedRoute: ActivatedRoute;
        let breakpoint$: BehaviorSubject<BreakpointState>;

        // Workaround for mocked components with viewChild: https://github.com/help-me-mom/ng-mocks/issues/8634
        MockInstance(CourseWideSearchComponent, 'content', signal(new ElementRef(document.createElement('div'))));
        MockInstance(CourseWideSearchComponent, 'messages', signal([new ElementRef(document.createElement('div'))]));
        // @ts-ignore
        MockInstance(ConversationGlobalSearchComponent, 'searchElement', signal([new ElementRef(document.createElement('div'))]));
        MockInstance(ConversationThreadSidebarComponent, 'threadContainer', signal(new ElementRef(document.createElement('div'))));
        const dummyTooltip = { close: jest.fn() } as unknown as NgbTooltip;
        MockInstance(ConversationThreadSidebarComponent, 'expandTooltip', signal(dummyTooltip));

        beforeEach(waitForAsync(() => {
            breakpoint$ = new BehaviorSubject<BreakpointState>({
                matches: false,
                breakpoints: { [Breakpoints.Handset]: false },
            });

            queryParamsSubject = new BehaviorSubject(convertToParamMap({}));

            TestBed.configureTestingModule({
                declarations: [
                    CourseConversationsComponent,
                    LoadingIndicatorContainerStubComponent,
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
                    MockPipe(HtmlForMarkdownPipe),
                ],
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
                    MockProvider(NgbModal),
                    MockProvider(MetisConversationService),
                    MockProvider(SidebarEventService),
                    MockProvider(ProfileService),
                    MockProvider(AlertService),
                    MockProvider(FaqService),
                    provideHttpClient(),
                    provideHttpClientTesting(),
                ],
                imports: [FormsModule, ReactiveFormsModule, FontAwesomeModule, NgbModule, TranslateModule.forRoot()],
            }).compileComponents();

            const metisService = new MockMetisService();

            TestBed.overrideComponent(CourseConversationsComponent, {
                set: {
                    providers: [{ provide: MetisService, useValue: metisService }],
                },
            });

            metisConversationService = TestBed.inject(MetisConversationService);
            courseOverviewService = TestBed.inject(CourseOverviewService);
            courseSidebarService = TestBed.inject(CourseSidebarService);
            activatedRoute = TestBed.inject(ActivatedRoute);

            Object.defineProperty(metisConversationService, 'isServiceSetup$', { get: () => new BehaviorSubject(true).asObservable() });
            Object.defineProperty(metisConversationService, 'conversationsOfUser$', { get: () => new BehaviorSubject([new GroupChatDTO()]).asObservable() });
            Object.defineProperty(metisConversationService, 'isLoading$', { get: () => new BehaviorSubject(false).asObservable() });
            Object.defineProperty(metisConversationService, 'isCodeOfConductAccepted$', { get: () => new BehaviorSubject(true).asObservable() });
            Object.defineProperty(metisConversationService, 'isCodeOfConductPresented$', { get: () => new BehaviorSubject(false).asObservable() });
            metisConversationService.checkIsCodeOfConductAccepted = jest.fn();
            metisConversationService.setActiveConversation = jest.fn();
            metisConversationService.setUpConversationService = jest.fn().mockReturnValue(EMPTY);
            metisConversationService.forceRefresh = jest.fn().mockReturnValue(EMPTY);
            metisConversationService.markAsRead = jest.fn();
            metisConversationService.acceptCodeOfConduct = jest.fn();
            metisConversationService.createGroupChat = jest.fn().mockReturnValue(EMPTY);
            metisConversationService.createOneToOneChat = jest.fn().mockReturnValue(EMPTY);
            metisConversationService.createChannel = jest.fn().mockReturnValue(EMPTY);
            metisConversationService.markAllChannelsAsRead = jest.fn().mockReturnValue(of());

            fixture = TestBed.createComponent(CourseConversationsComponent);
            component = fixture.componentInstance;

            postsSubject = new BehaviorSubject([]);
            jest.spyOn(metisConversationService, 'course', 'get').mockReturnValue(course);
            jest.spyOn(metisConversationService, 'activeConversation$', 'get').mockReturnValue(new BehaviorSubject(activeConversation).asObservable());
            setActiveConversationSpy = jest.spyOn(metisConversationService, 'setActiveConversation');
            acceptCodeOfConductSpy = jest.spyOn(metisConversationService, 'acceptCodeOfConduct');
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(postsSubject.asObservable());
            modalService = TestBed.inject(NgbModal);
            component.sidebarConversations = [];

            jest.spyOn(courseOverviewService, 'mapConversationsToSidebarCardElements').mockReturnValue([
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

            jest.spyOn(courseOverviewService, 'groupConversationsByChannelType').mockReturnValue({
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
        }));

        afterEach(() => {
            component.ngOnDestroy();
            jest.resetAllMocks();
        });

        it('should have service set up', () => {
            fixture.detectChanges();
            expect(component.isServiceSetUp).toBeTrue();
            expect(component.isLoading).toBeFalse();
            expect(component.conversationsOfUser).toHaveLength(1);
            expect(component.activeConversation).toEqual(activeConversation);
        });

        describe('Dialog Opening', () => {
            const expectedResults = [undefined, true];
            const mockModalRef: Partial<NgbModalRef> = {
                componentInstance: {
                    course: {},
                    initialize: jest.fn(),
                },
            };

            beforeEach(() => {
                // Set a mock course with id 1
                component.course.set({ id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING } as Course);
            });

            it('should open the group chat creation dialog', fakeAsync(() => {
                mockModalRef.result = Promise.resolve(undefined);
                const spy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
                component.openCreateGroupChatDialog();
                tick();
                expect(spy).toHaveBeenCalledExactlyOnceWith(GroupChatCreateDialogComponent, expect.anything());
            }));

            it('should open the one-to-one chat creation dialog', fakeAsync(() => {
                const spy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
                component.openCreateOneToOneChatDialog();
                tick();
                expect(spy).toHaveBeenCalledExactlyOnceWith(OneToOneChatCreateDialogComponent, expect.anything());
            }));

            it('should open the channel overview dialog', fakeAsync(() => {
                mockModalRef.result = Promise.resolve(expectedResults);
                const spy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);
                component.openChannelOverviewDialog();
                tick();
                expect(spy).toHaveBeenCalledExactlyOnceWith(ChannelsOverviewDialogComponent, expect.anything());
                expect(mockModalRef.componentInstance.initialize).toHaveBeenCalled();
            }));

            it('should open the create channel dialog when onCreateChannelPressed is called', fakeAsync(() => {
                fixture.detectChanges();
                mockModalRef.result = Promise.resolve(expectedResults);
                const spy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);

                component.openCreateChannelDialog();
                tick();

                expect(spy).toHaveBeenCalledExactlyOnceWith(ChannelsCreateDialogComponent, expect.anything());
                expect(mockModalRef.componentInstance.course).toEqual(course);
                expect(mockModalRef.componentInstance.initialize).toHaveBeenCalled();
            }));
        });

        it('should update thread in post', fakeAsync(() => {
            fixture.detectChanges();
            component.postInThread = { id: 1, content: 'loremIpsum' } as Post;
            fixture.changeDetectorRef.detectChanges();
            const updatedPost = { id: 1, content: 'updatedContent' } as Post;
            postsSubject.next([updatedPost]);
            tick();
            expect(component.postInThread).toEqual(updatedPost);
        }));

        it('should set active conversation depending on the query param', fakeAsync(() => {
            queryParamsSubject.next({ conversationId: '12' });
            // mock setActiveConversationById method
            fixture.changeDetectorRef.detectChanges();
            tick();
            expect(setActiveConversationSpy).toHaveBeenCalledWith(12);
        }));

        it('should call sidebar collapse if conversation changes', fakeAsync(() => {
            const closeSidebarOnMobileSpy = jest.spyOn(component, 'closeSidebarOnMobile');
            queryParamsSubject.next({ conversationId: '12' });
            fixture.changeDetectorRef.detectChanges();
            tick();
            expect(closeSidebarOnMobileSpy).toHaveBeenCalled();
        }));

        it('should call sidebar collapse if thread opens', fakeAsync(() => {
            const closeSidebarOnMobileSpy = jest.spyOn(component, 'closeSidebarOnMobile');
            queryParamsSubject.next({ messageId: '12' });
            fixture.changeDetectorRef.detectChanges();
            tick();
            expect(closeSidebarOnMobileSpy).toHaveBeenCalled();
        }));

        it('should set the query params when an active conversation is selected', () => {
            const activatedRoute = TestBed.inject(ActivatedRoute);
            const navigateSpy = jest.spyOn(router, 'navigate');
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

        it('should toggle isNavbarCollapsed when toggleCollapseState is called', () => {
            component.toggleSidebar();
            expect(component.isCollapsed).toBeTrue();

            component.toggleSidebar();
            expect(component.isCollapsed).toBeFalse();
        });

        it('should toggle isCollapsed when service emits corresponding event', () => {
            fixture.detectChanges();
            courseSidebarService.openSidebar();
            expect(component.isCollapsed).toBeTrue();

            courseSidebarService.closeSidebar();
            expect(component.isCollapsed).toBeFalse();

            courseSidebarService.toggleSidebar();
            expect(component.isCollapsed).toBeTrue();
        });

        it('should switch to mobile if breakpoint returns true and open sidebar', () => {
            const openSidebarSpy = jest.spyOn(courseSidebarService, 'openSidebar');
            expect(component.isMobile()).toBeFalse();
            breakpoint$.next({
                matches: true,
                breakpoints: { [Breakpoints.Handset]: true },
            });
            component.ngOnInit();
            expect(component.isMobile()).toBeTrue();
            expect(openSidebarSpy).toHaveBeenCalled();
        });

        it('should call close sidebar if conversation is selected', () => {
            const closeSidebarSpy = jest.spyOn(courseSidebarService, 'closeSidebar');
            breakpoint$.next({
                matches: true,
                breakpoints: { [Breakpoints.Handset]: true },
            });

            component.onConversationSelected(activeConversation?.id ?? 1);

            expect(closeSidebarSpy).toHaveBeenCalled();
        });

        it('should call close sidebar and search term is set and open sidebar otherwise if on mobile', () => {
            const openSidebarSpy = jest.spyOn(courseSidebarService, 'openSidebar');
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
            component.isCollapsed = false;
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).toBeNull();

            component.isCollapsed = true;
            fixture.changeDetectorRef.detectChanges();
            expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).not.toBeNull();
        });

        it('onConversationSelected should change active conversation', () => {
            const setActiveConversationSpy = jest.spyOn(metisConversationService, 'setActiveConversation').mockImplementation();
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

                component.createChannelFn = jest.fn().mockReturnValue(EMPTY);
                jest.spyOn(component, 'prepareSidebarData').mockImplementation();
            });

            it('should call createChannelFn with the provided channel', () => {
                component.performChannelAction(channelAction);
                expect(component.createChannelFn).toHaveBeenCalledWith(channel);
            });

            it('should call prepareSidebarData on successful completion', () => {
                component.createChannelFn = jest.fn().mockReturnValue({
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
                component.createChannelFn = jest.fn().mockReturnValue({
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
                const prepareSidebarDataSpy = jest.spyOn(component, 'prepareSidebarData');
                component.performChannelAction(channelAction);
                expect(component.createChannelFn).toBeUndefined();
                // Since createChannelFn is undefined, prepareSidebarData should not be called
                expect(prepareSidebarDataSpy).not.toHaveBeenCalled();
            });

            it('should correctly populate the recents group in accordionConversationGroups using existing mocks', fakeAsync(() => {
                (metisConversationService.forceRefresh as jest.Mock).mockReturnValue(of({}));

                component.prepareSidebarData();
                tick();
                const recentsGroup = component.accordionConversationGroups.recents;
                expect(recentsGroup).toBeDefined();
                expect(recentsGroup.entityData).toHaveLength(1);
                expect(recentsGroup.entityData[0].isCurrent).toBeTrue();
            }));
        });

        describe('query parameter handling', () => {
            it('should handle SavedPostStatus in conversationId', () => {
                const queryParams = {
                    conversationId: SavedPostStatus.ARCHIVED.toString().toLowerCase(),
                };
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['selectedSavedPostStatus']).toBe(SavedPostStatus.ARCHIVED);
                expect(setActiveConversationSpy).not.toHaveBeenCalled();
            });

            it('should handle numeric conversationId', () => {
                const queryParams = {
                    conversationId: '123',
                };
                activatedRoute.queryParams = of(queryParams);
                const spy = jest.spyOn(component, 'closeSidebarOnMobile');

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

                expect(component['focusPostId']).toBe(456);
            });

            it('should handle openThreadOnFocus parameter', () => {
                const queryParams = {
                    openThreadOnFocus: 'true',
                };
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['openThreadOnFocus']).toBe('true');
            });

            it('should handle messageId parameter', () => {
                const queryParams = {
                    messageId: '789',
                };
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['postInThread']).toEqual({ id: 789 });
            });

            it('should clear postInThread when no messageId is present', () => {
                const queryParams = {};
                activatedRoute.queryParams = of(queryParams);

                component.subscribeToQueryParameter();

                expect(component['postInThread']).toBeUndefined();
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

                expect(component['selectedSavedPostStatus']).toBe(SavedPostStatus.ARCHIVED);
                expect(component['focusPostId']).toBe(456);
                expect(component['openThreadOnFocus']).toBe('true');
                expect(component['postInThread']).toEqual({ id: 789 });
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

                expect(component['focusPostId']).toBe(123);
                expect(component['openThreadOnFocus']).toBeTrue();
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

                expect(component['focusPostId']).toBe(123);
                expect(component['openThreadOnFocus']).toBeFalse();
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
            const markAllChannelsAsRead = jest.spyOn(metisConversationService, 'markAllChannelsAsRead').mockReturnValue(of());
            const forceRefresh = jest.spyOn(metisConversationService, 'forceRefresh');
            forceRefresh.mockClear();
            component.markAllChannelAsRead();
            expect(markAllChannelsAsRead).toHaveBeenCalledOnce();
            expect(forceRefresh).toHaveBeenCalledTimes(2);
        });

        describe('conversation selection', () => {
            it('should handle numeric conversationId', () => {
                fixture.detectChanges();
                component.onConversationSelected(123);
                expect(component.selectedSavedPostStatus).toBeUndefined();
                expect(setActiveConversationSpy).toHaveBeenCalledWith(123);
            });

            it('should handle valid string conversationId as SavedPostStatus', () => {
                fixture.detectChanges();
                const validStatus = SavedPostStatus.ARCHIVED.toString().toLowerCase();
                component.onConversationSelected(validStatus);
                expect(component.selectedSavedPostStatus).toBe(SavedPostStatus.ARCHIVED);
                expect(component.postInThread).toBeUndefined();
                expect(setActiveConversationSpy).toHaveBeenCalledWith(undefined);
                expect(component.activeConversation).toBeUndefined();
            });

            it('should ignore invalid string conversationId', fakeAsync(() => {
                fixture.detectChanges();
                const invalidStatus = 'invalidStatus';
                setActiveConversationSpy.mockClear();
                component.onConversationSelected(invalidStatus);
                tick();
                expect(component.selectedSavedPostStatus).toBeUndefined();
                expect(setActiveConversationSpy).not.toHaveBeenCalled();
                discardPeriodicTasks();
            }));

            it('should toggle the value of showOnlyPinned', () => {
                expect(component.showOnlyPinned).toBeFalse();

                component.togglePinnedView();
                expect(component.showOnlyPinned).toBeTrue();

                component.togglePinnedView();
                expect(component.showOnlyPinned).toBeFalse();
            });

            it('should update pinnedCount when onPinnedCountChanged is called', () => {
                const newPinnedCount = 5;

                component.onPinnedCountChanged(newPinnedCount);
                expect(component.pinnedCount).toBe(newPinnedCount);
            });

            it('should set showOnlyPinned to false if pinnedCount becomes 0', () => {
                component.showOnlyPinned = true;
                component.onPinnedCountChanged(0);
                expect(component.showOnlyPinned).toBeFalse();
            });

            it('should not change showOnlyPinned if pinnedCount changes but is not 0', () => {
                component.showOnlyPinned = true;
                component.onPinnedCountChanged(5);
                expect(component.showOnlyPinned).toBeTrue();

                component.showOnlyPinned = false;
                component.onPinnedCountChanged(10);
                expect(component.showOnlyPinned).toBeFalse();
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

                expect(component.focusPostId).toBeUndefined();
                expect(component.openThreadOnFocus).toBeFalsy();
                expect(setActiveConversationSpy).not.toHaveBeenCalled();
            });

            it('should set openThreadOnFocus = true if postingType is ANSWER', () => {
                const post = {
                    id: 1,
                    postingType: PostingType.ANSWER,
                    post: { id: 2 } as Post,
                } as Posting;

                component.onTriggerNavigateToPost(post);
                expect(component.openThreadOnFocus).toBeTrue();
            });

            it('should set openThreadOnFocus = false if postingType is POST (question post)', () => {
                const post = {
                    id: 1,
                    postingType: PostingType.POST,
                    conversation: { id: 1 } as Conversation,
                } as Posting;

                component.onTriggerNavigateToPost(post);
                expect(component.openThreadOnFocus).toBeFalse();
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

                expect(component.focusPostId).toBe(10);
                expect(component.openThreadOnFocus).toBeFalse();
                expect(setActiveConversationSpy).toHaveBeenCalledWith(444);
            });
        });

        describe('Search Clear and Conversation Restoration', () => {
            beforeEach(() => {
                fixture.detectChanges();
            });

            it('should clear search config and restore previous conversation when X is clicked', () => {
                const previousConversation = { id: 42, type: 'channel' } as ConversationDTO;
                component.activeConversation = previousConversation;
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
                const updateQueryParamsSpy = jest.spyOn(component, 'updateQueryParameters');
                const courseWideSearchMock = { onSearch: jest.fn() };
                jest.spyOn(component, 'courseWideSearch').mockReturnValue(courseWideSearchMock as any);

                component.courseWideSearchConfig.searchTerm = 'test search';
                component.courseWideSearchConfig.selectedConversations = [{ id: 1 } as ConversationDTO];

                component.onClearSearchAndRestorePrevious();

                expect(component.courseWideSearchConfig.searchTerm).toBe('');
                expect(component.courseWideSearchConfig.selectedConversations).toEqual([]);
                expect(component.courseWideSearchConfig.selectedAuthors).toEqual([]);

                expect(setActiveConversationSpy).toHaveBeenCalledWith(undefined);
                expect(component.activeConversation).toBeUndefined();
                expect(component.selectedSavedPostStatus).toBeUndefined();
                expect(updateQueryParamsSpy).toHaveBeenCalled();
                expect(courseWideSearchMock.onSearch).toHaveBeenCalled();
            });

            it('should track last known conversation ID when active conversation changes', fakeAsync(() => {
                const newConversation = { id: 123, type: 'channel' } as ConversationDTO;
                jest.spyOn(metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(newConversation));

                component.ngOnInit();
                tick();

                expect(component.lastKnownConversationId).toBe(123);
            }));

            it('should save active conversation only once when search starts', () => {
                const conversation = { id: 50, type: 'channel' } as ConversationDTO;
                component.activeConversation = conversation;
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
                component.activeConversation = anotherConversation;

                component.onSelectionChange({
                    searchTerm: '',
                    selectedConversations: [anotherConversation],
                    selectedAuthors: [],
                });

                expect(component.previousConversationBeforeSearch).toEqual(conversation); // Still the first one
            });

            it('should not save conversation when no filters are active', () => {
                const conversation = { id: 70, type: 'channel' } as ConversationDTO;
                component.activeConversation = conversation;
                component.previousConversationBeforeSearch = undefined;

                component.onSelectionChange({
                    searchTerm: '',
                    selectedConversations: [],
                    selectedAuthors: [],
                });

                expect(component.previousConversationBeforeSearch).toBeUndefined();
            });

            it('should call closeSidebarOnMobile when clearing search', () => {
                const closeSidebarSpy = jest.spyOn(component, 'closeSidebarOnMobile');
                component.lastKnownConversationId = 1;

                component.onClearSearchAndRestorePrevious();

                expect(closeSidebarSpy).toHaveBeenCalled();
            });
        });
    });
});
