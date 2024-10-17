import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { Type } from '@angular/core';
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
import { ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { defaultFirstLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { NgbCollapseMocksModule } from '../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { SidebarEventService } from 'app/shared/sidebar/sidebar-event.service';
import { AccordionAddOptionsComponent } from 'app/shared/sidebar/accordion-add-options/accordion-add-options.component';
import { SidebarAccordionComponent } from 'app/shared/sidebar/sidebar-accordion/sidebar-accordion.component';
import { GroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { CourseSidebarService } from 'app/overview/course-sidebar.service';

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
        let sidebarEventService: SidebarEventService;
        let metisConversationService: MetisConversationService;
        let courseSidebarService: CourseSidebarService;

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
                    MockComponent(AccordionAddOptionsComponent),
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
                ],
                imports: [FormsModule, ReactiveFormsModule, FontAwesomeModule, NgbModule, NgbCollapseMocksModule, NgbTooltipMocksModule],
            }).compileComponents();

            const metisService = new MockMetisService();

            TestBed.overrideComponent(CourseConversationsComponent, {
                set: {
                    providers: [{ provide: MetisService, useValue: metisService }],
                },
            });

            sidebarEventService = TestBed.inject(SidebarEventService);
            metisConversationService = TestBed.inject(MetisConversationService);
            courseSidebarService = TestBed.inject(CourseSidebarService);

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
            jest.spyOn(sidebarEventService, 'emitSidebarAccordionPlusClickedEvent').mockImplementation((groupKey: string) => {
                component.onAccordionPlusButtonPressed(groupKey);
            });
            setActiveConversationSpy = jest.spyOn(metisConversationService, 'setActiveConversation');
            acceptCodeOfConductSpy = jest.spyOn(metisConversationService, 'acceptCodeOfConduct');
            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(postsSubject.asObservable());
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
            fixture.detectChanges();
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
            fixture.detectChanges();
            const setActiveConversationSpy = jest.spyOn(metisConversationService, 'setActiveConversation').mockImplementation();
            component.onConversationSelected(activeConversation?.id ?? 1);
            expect(setActiveConversationSpy).toHaveBeenCalled();
        });

        it('getChannelSubType method should return correct SubType', () => {
            fixture.detectChanges();
            expect(component.getChannelSubType('exerciseChannels')).toEqual(ChannelSubType.EXERCISE);
            expect(component.getChannelSubType('examChannels')).toEqual(ChannelSubType.EXAM);
            expect(component.getChannelSubType('generalChannels')).toEqual(ChannelSubType.GENERAL);
            expect(component.getChannelSubType('lectureChannels')).toEqual(ChannelSubType.LECTURE);
        });

        it('onAccordionPlusButtonPressed method should call openCreateGroupChatDialog when groupKey is groupChats', () => {
            fixture.detectChanges();
            const createGroupChatSpy = jest.spyOn(component, 'openCreateGroupChatDialog').mockImplementation();
            component.onAccordionPlusButtonPressed('groupChats');
            expect(createGroupChatSpy).toHaveBeenCalled();
        });

        it('onAccordionPlusButtonPressed method should call openCreateOneToOneChatDialog when groupKey is directMessages', () => {
            fixture.detectChanges();
            const createDirectMessageSpy = jest.spyOn(component, 'openCreateOneToOneChatDialog').mockImplementation();
            component.onAccordionPlusButtonPressed('directMessages');
            expect(createDirectMessageSpy).toHaveBeenCalled();
        });

        it('onAccordionPlusButtonPressed method should call openChannelOverviewDialog when groupKey is any channelType', () => {
            fixture.detectChanges();
            const openChannelOverviewDialogSpy = jest.spyOn(component, 'openChannelOverviewDialog').mockImplementation();
            component.onAccordionPlusButtonPressed('generalChannels');
            expect(openChannelOverviewDialogSpy).toHaveBeenCalledWith('generalChannels');
            component.onAccordionPlusButtonPressed('examChannels');
            expect(openChannelOverviewDialogSpy).toHaveBeenCalledWith('examChannels');
            component.onAccordionPlusButtonPressed('lectureChannels');
            expect(openChannelOverviewDialogSpy).toHaveBeenCalledWith('lectureChannels');
            component.onAccordionPlusButtonPressed('exerciseChannels');
            expect(openChannelOverviewDialogSpy).toHaveBeenCalledWith('exerciseChannels');
            component.onAccordionPlusButtonPressed('randomInputShouldAddedToGeneralChannels');
            expect(openChannelOverviewDialogSpy).toHaveBeenCalledWith('generalChannels');
        });

        it('should open create group chat dialog when button is pressed', fakeAsync(() => {
            const createGroupChatSpy = jest.spyOn(metisConversationService, 'createGroupChat').mockReturnValue(EMPTY);
            createConversationDialogTest([new UserPublicInfoDTO()], GroupChatCreateDialogComponent, 'groupChats');
            fixture.whenStable().then(() => {
                expect(createGroupChatSpy).toHaveBeenCalledOnce();
            });
        }));

        it('should open one to one chat dialog when button is pressed', fakeAsync(() => {
            const createOneToOneChatSpy = jest.spyOn(metisConversationService, 'createOneToOneChat').mockReturnValue(EMPTY);
            const chatPartner = new UserPublicInfoDTO();
            chatPartner.login = 'test';
            createConversationDialogTest(chatPartner, OneToOneChatCreateDialogComponent, 'directMessages');
            fixture.whenStable().then(() => {
                expect(createOneToOneChatSpy).toHaveBeenCalledOnce();
            });
        }));

        it('should open channel overview dialog when button is pressed', fakeAsync(() => {
            fixture.detectChanges();
            tick(301);
            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: {
                    course: undefined,
                    createChannelFn: undefined,
                    initialize: () => {},
                },
                result: Promise.resolve([new GroupChatDTO(), true]),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            component.onAccordionPlusButtonPressed('generalChannels');

            tick(301);
            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(ChannelsOverviewDialogComponent, defaultFirstLayerDialogOptions);
                expect(mockModalRef.componentInstance.course).toEqual(course);
            });
        }));

        function createConversationDialogTest(modalReturnValue: any, dialog: Type<AbstractDialogComponent>, channelType: string) {
            fixture.detectChanges();
            tick(301);
            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: {
                    course: undefined,
                    initialize: () => {},
                },
                result: Promise.resolve(modalReturnValue),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.detectChanges();

            sidebarEventService.emitSidebarAccordionPlusClickedEvent(channelType);
            tick(301);
            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(dialog, defaultFirstLayerDialogOptions);
                expect(mockModalRef.componentInstance.course).toEqual(course);
            });
        }
    });
});
