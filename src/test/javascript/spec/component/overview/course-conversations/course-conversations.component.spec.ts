import { CourseConversationsComponent } from 'app/communication/shared/course-conversations.component';
import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { Conversation, ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { OneToOneChatDTO } from '../../../../../../main/webapp/app/entities/metis/conversation/one-to-one-chat.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from './helpers/conversationExampleModels';
import { MockComponent, MockInstance, MockPipe, MockProvider } from 'ng-mocks';
import { MetisConversationService } from 'app/communication/metis-conversation.service';
import { LoadingIndicatorContainerStubComponent } from '../../../helpers/stubs/loading-indicator-container-stub.component';
import { ConversationHeaderComponent } from 'app/communication/course-conversations/layout/conversation-header/conversation-header.component';
import { CourseWideSearchComponent } from 'app/communication/course-conversations/course-wide-search/course-wide-search.component';
import { ConversationMessagesComponent } from 'app/communication/course-conversations/layout/conversation-messages/conversation-messages.component';
import { ConversationThreadSidebarComponent } from 'app/communication/course-conversations/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { Course } from 'app/entities/course.model';
import { BehaviorSubject, EMPTY, of } from 'rxjs';
import { NgbModal, NgbModalRef, NgbModule, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, convertToParamMap, Params, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MetisService } from 'app/communication/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { CourseConversationsCodeOfConductComponent } from 'app/communication/course-conversations/code-of-conduct/course-conversations-code-of-conduct.component';
import { MockMetisService } from '../../../helpers/mocks/service/mock-metis-service.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { getElement } from '../../../helpers/utils/general.utils';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { CourseOverviewService } from 'app/course/overview/course-overview.service';
import { GroupChatCreateDialogComponent } from 'app/communication/course-conversations/group-chat-create-dialog/group-chat-create-dialog.component';
import { SidebarEventService } from 'app/shared/sidebar/sidebar-event.service';
import { SidebarAccordionComponent } from 'app/shared/sidebar/sidebar-accordion/sidebar-accordion.component';
import { GroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { OneToOneChatCreateDialogComponent } from 'app/communication/course-conversations/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { ChannelAction, ChannelsOverviewDialogComponent } from 'app/course/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { CourseSidebarService } from 'app/course/overview/course-sidebar.service';
import { ChannelsCreateDialogComponent } from 'app/communication/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';
import { Posting, PostingType, SavedPostStatus, SavedPostStatusMap } from 'app/entities/metis/posting.model';
import { ElementRef, signal } from '@angular/core';

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
        const course = { id: 1 } as Course;
        let queryParamsSubject: BehaviorSubject<Params>;
        const router = new MockRouter();
        let postsSubject: BehaviorSubject<Post[]>;
        let acceptCodeOfConductSpy: jest.SpyInstance;
        let setActiveConversationSpy: jest.SpyInstance;
        let metisConversationService: MetisConversationService;
        let courseOverviewService: CourseOverviewService;
        let modalService: NgbModal;
        let courseSidebarService: CourseSidebarService;
        let layoutService: LayoutService;
        let activatedRoute: ActivatedRoute;

        const MockLayoutService = {
            activeBreakpoints: [],
            breakpointObserver: undefined,
            breakpointService: {
                getBreakpoints: jest.fn().mockReturnValue(['(min-width: 600px)']),
                getBreakpointName: jest.fn().mockReturnValue(CustomBreakpointNames.medium),
            },
            subscribeToLayoutChanges: () => {
                return EMPTY;
            },
            isBreakpointActive: jest.fn().mockReturnValue(CustomBreakpointNames.medium),
        };

        // Workaround for mocked components with viewChild: https://github.com/help-me-mom/ng-mocks/issues/8634
        MockInstance(CourseWideSearchComponent, 'content', signal(new ElementRef(document.createElement('div'))));
        MockInstance(CourseWideSearchComponent, 'messages', signal([new ElementRef(document.createElement('div'))]));
        MockInstance(ConversationThreadSidebarComponent, 'threadContainer', signal(new ElementRef(document.createElement('div'))));
        const dummyTooltip = { close: jest.fn() } as unknown as NgbTooltip;
        MockInstance(ConversationThreadSidebarComponent, 'expandTooltip', signal(dummyTooltip));

        beforeEach(waitForAsync(() => {
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
                    MockComponent(SidebarAccordionComponent),
                    MockPipe(ArtemisTranslatePipe),
                    MockPipe(HtmlForMarkdownPipe),
                ],
                providers: [
                    { provide: Router, useValue: router },
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            parent: {
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
                    { provide: LayoutService, useValue: MockLayoutService },
                ],
                imports: [FormsModule, ReactiveFormsModule, FontAwesomeModule, NgbModule],
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
            layoutService = TestBed.inject(LayoutService);
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
                component.course = { id: 1 } as Course;
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
            fixture.detectChanges();
            const updatedPost = { id: 1, content: 'updatedContent' } as Post;
            postsSubject.next([updatedPost]);
            tick();
            expect(component.postInThread).toEqual(updatedPost);
        }));

        it('should set active conversation depending on the query param', fakeAsync(() => {
            queryParamsSubject.next({ conversationId: '12' });
            // mock setActiveConversationById method
            fixture.detectChanges();
            tick();
            expect(setActiveConversationSpy).toHaveBeenCalledWith(12);
        }));

        it('should call sidebar collapse if conversation changes', fakeAsync(() => {
            const closeSidebarOnMobileSpy = jest.spyOn(component, 'closeSidebarOnMobile');
            queryParamsSubject.next({ conversationId: '12' });
            fixture.detectChanges();
            tick();
            expect(closeSidebarOnMobileSpy).toHaveBeenCalled();
        }));

        it('should call sidebar collapse if thread opens', fakeAsync(() => {
            const closeSidebarOnMobileSpy = jest.spyOn(component, 'closeSidebarOnMobile');
            queryParamsSubject.next({ messageId: '12' });
            fixture.detectChanges();
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

        it('should initialize the course-wide search text correctly', () => {
            fixture.detectChanges();
            const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
            expect(searchInput.textContent).toBe('');
        });

        it('should update the course-wide search text correctly given an input', () => {
            fixture.detectChanges();
            const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
            searchInput.value = 'test input';
            searchInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            expect(component.courseWideSearchTerm).toBe('test input');
        });

        it('should set search text in the courseWideSearchConfig correctly', () => {
            fixture.detectChanges();
            component.initializeCourseWideSearchConfig();
            component.courseWideSearchTerm = 'test';
            component.onSearch();
            fixture.detectChanges();
            expect(component.courseWideSearchConfig.searchTerm).toBe(component.courseWideSearchTerm);
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
            expect(component.isMobile).toBeFalse();
            layoutService.isBreakpointActive = jest.fn().mockReturnValue(true);
            component.ngOnInit();
            expect(component.isMobile).toBeTrue();
            expect(openSidebarSpy).toHaveBeenCalled();
        });

        it('should call close sidebar if coversation is selected', () => {
            const closeSidebarSpy = jest.spyOn(courseSidebarService, 'closeSidebar');
            component.isMobile = true;

            component.onConversationSelected(activeConversation?.id ?? 1);

            expect(closeSidebarSpy).toHaveBeenCalled();
        });

        it('should call close sidebar and search term is set and open sidebar otherwise if on mobile', () => {
            const openSidebarSpy = jest.spyOn(courseSidebarService, 'openSidebar');
            fixture.detectChanges();
            component.isMobile = true;
            component.courseWideSearchTerm = '';
            component.onSearch();
            expect(openSidebarSpy).toHaveBeenCalled();
        });

        it('should display sidebar when conversation is provided', () => {
            fixture.detectChanges();
            // Wait for any async operations to complete here if necessary
            fixture.detectChanges(); // Trigger change detection again if async operations might change the state
            expect(fixture.nativeElement.querySelector('jhi-sidebar')).not.toBeNull();
        });

        it('should toggle sidebar visibility based on isCollapsed property', () => {
            component.isCollapsed = false;
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.sidebar-collapsed')).toBeNull();

            component.isCollapsed = true;
            fixture.detectChanges();
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
                    conversationId: SavedPostStatusMap.ARCHIVED.toString(),
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
                    conversationId: SavedPostStatusMap.ARCHIVED.toString(),
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
            const markAllChannelsAsRead = jest.spyOn(metisConversationService, 'markAllChannelsAsRead').mockReturnValue(of());
            const forceRefresh = jest.spyOn(metisConversationService, 'forceRefresh');
            component.markAllChannelAsRead();
            expect(markAllChannelsAsRead).toHaveBeenCalledOnce();
            expect(forceRefresh).toHaveBeenCalledTimes(2);
        });

        describe('conversation selection', () => {
            it('should handle numeric conversationId', () => {
                component.onConversationSelected(123);
                expect(component.selectedSavedPostStatus).toBeNull();
                expect(setActiveConversationSpy).toHaveBeenCalledWith(123);
            });

            it('should handle valid string conversationId as SavedPostStatus', () => {
                const validStatus = SavedPostStatusMap.ARCHIVED.toString();
                component.onConversationSelected(validStatus);
                expect(component.selectedSavedPostStatus).toBe(SavedPostStatus.ARCHIVED);
                expect(component.postInThread).toBeUndefined();
                expect(setActiveConversationSpy).toHaveBeenCalledWith(undefined);
                expect(component.activeConversation).toBeUndefined();
            });

            it('should ignore invalid string conversationId', () => {
                const invalidStatus = 'invalidStatus';
                component.onConversationSelected(invalidStatus);
                expect(component.selectedSavedPostStatus).toBeNull();
                expect(metisConversationService.setActiveConversation).not.toHaveBeenCalled();
            });

            it('should toggle the value of showOnlyPinned', () => {
                expect(component.showOnlyPinned).toBe(false);

                component.togglePinnedView();
                expect(component.showOnlyPinned).toBe(true);

                component.togglePinnedView();
                expect(component.showOnlyPinned).toBe(false);
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
    });
});
