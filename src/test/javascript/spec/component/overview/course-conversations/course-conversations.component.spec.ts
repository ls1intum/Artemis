import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from './helpers/conversationExampleModels';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { LoadingIndicatorContainerStubComponent } from '../../../helpers/stubs/loading-indicator-container-stub.component';
import { ConversationHeaderComponent } from 'app/overview/course-conversations/layout/conversation-header/conversation-header.component';
import { CourseWideSearchComponent } from 'app/overview/course-conversations/course-wide-search/course-wide-search.component';
import { ConversationMessagesComponent } from 'app/overview/course-conversations/layout/conversation-messages/conversation-messages.component';
import { ConversationThreadSidebarComponent } from 'app/overview/course-conversations/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { Course } from 'app/entities/course.model';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Params, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { CourseConversationsCodeOfConductComponent } from 'app/overview/course-conversations/code-of-conduct/course-conversations-code-of-conduct.component';
import { MockMetisService } from '../../../helpers/mocks/service/mock-metis-service.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { getElement } from '../../../helpers/utils/general.utils';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { NgbCollapseMocksModule } from '../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { SidebarEventService } from 'app/shared/sidebar/sidebar-event.service';
import { SidebarAccordionComponent } from 'app/shared/sidebar/sidebar-accordion/sidebar-accordion.component';
import { GroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { ChannelAction, ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { CourseSidebarService } from 'app/overview/course-sidebar.service';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';

const examples: (ConversationDTO | undefined)[] = [undefined, generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];

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
        let modalService: NgbModal;
        let courseSidebarService: CourseSidebarService;
        let layoutService: LayoutService;

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
                imports: [FormsModule, ReactiveFormsModule, FontAwesomeModule, NgbModule, NgbCollapseMocksModule, NgbTooltipMocksModule],
            }).compileComponents();

            const metisService = new MockMetisService();

            TestBed.overrideComponent(CourseConversationsComponent, {
                set: {
                    providers: [{ provide: MetisService, useValue: metisService }],
                },
            });

            metisConversationService = TestBed.inject(MetisConversationService);
            courseSidebarService = TestBed.inject(CourseSidebarService);
            layoutService = TestBed.inject(LayoutService);

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
                const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation();

                component.createChannelFn = jest.fn().mockReturnValue({
                    pipe: () => ({
                        subscribe: ({ error }: any) => {
                            error('Test Error');
                        },
                    }),
                });

                component.performChannelAction(channelAction);
                expect(consoleErrorSpy).toHaveBeenCalledWith('Error creating channel:', 'Test Error');

                consoleErrorSpy.mockRestore();
            });

            it('should not call createChannelFn or prepareSidebarData if createChannelFn is undefined', () => {
                component.createChannelFn = undefined;
                const prepareSidebarDataSpy = jest.spyOn(component, 'prepareSidebarData');
                component.performChannelAction(channelAction);
                expect(component.createChannelFn).toBeUndefined();
                // Since createChannelFn is undefined, prepareSidebarData should not be called
                expect(prepareSidebarDataSpy).not.toHaveBeenCalled();
            });
        });
    });
});
