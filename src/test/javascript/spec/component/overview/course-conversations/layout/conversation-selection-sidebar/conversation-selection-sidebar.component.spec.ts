import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ConversationSelectionSidebarComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-selection-sidebar.component';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Type } from '@angular/core';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdownMocksModule } from '../../../../../helpers/mocks/directive/ngbDropdownMocks.module';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { AccountService } from 'app/core/auth/account.service';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../helpers/conversationExampleModels';
import { BehaviorSubject, EMPTY } from 'rxjs';
import { By } from '@angular/platform-browser';
import { ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { defaultFirstLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { GroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ConversationSidebarSectionComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-sidebar-section/conversation-sidebar-section.component';
import { MockLocalStorageService } from '../../../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { NgbCollapseMocksModule } from '../../../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { ConversationSidebarEntryComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-sidebar-section/conversation-sidebar-entry/conversation-sidebar-entry.component';
import { TranslateService } from '@ngx-translate/core';
import { GroupChatIconComponent } from 'app/overview/course-conversations/other/group-chat-icon/group-chat-icon.component';
import { ChannelIconComponent } from 'app/overview/course-conversations/other/channel-icon/channel-icon.component';
import { NgbTooltipMocksModule } from '../../../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { MetisService } from 'app/shared/metis/metis.service';
import { CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { NotificationService } from 'app/shared/notification/notification.service';

const examples: (ConversationDTO | undefined)[] = [
    undefined,
    generateOneToOneChatDTO({}),
    generateExampleGroupChatDTO({}),
    generateExampleChannelDTO({}),
    generateExampleChannelDTO({ subType: ChannelSubType.EXERCISE }),
    generateExampleChannelDTO({ subType: ChannelSubType.LECTURE }),
    generateExampleChannelDTO({ subType: ChannelSubType.EXAM }),
];

examples.forEach((activeConversation) => {
    describe(
        'ConversationSelectionSidebarComponent with ' +
            (activeConversation instanceof ChannelDTO ? activeConversation.subType + ' ' : '') +
            (activeConversation?.type || 'no active conversation'),
        () => {
            let component: ConversationSelectionSidebarComponent;
            let fixture: ComponentFixture<ConversationSelectionSidebarComponent>;
            let metisConversationService: MetisConversationService;
            const course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING } as any;
            const canCreateChannel = jest.fn();
            let allConversations: ConversationDTO[] = [];

            const visibleGroupChat = generateExampleGroupChatDTO({ id: 3 });
            const favoriteGroupChat = generateExampleGroupChatDTO({ id: 4, isFavorite: true });
            const mutedGroupChat = generateExampleGroupChatDTO({ id: 101, isMuted: true });
            const hiddenGroupChat = generateExampleGroupChatDTO({ id: 2, isHidden: true });

            const visibleChannel = generateExampleChannelDTO({ id: 5 });
            const favoriteChannel = generateExampleChannelDTO({ id: 7, isFavorite: true });
            const mutedChannel = generateExampleChannelDTO({ id: 102, isMuted: true });
            const hiddenChannel = generateExampleChannelDTO({ id: 6, isHidden: true });

            const visibleExerciseChannel = generateExampleChannelDTO({ id: 8, subType: ChannelSubType.EXERCISE });
            const favoriteExerciseChannel = generateExampleChannelDTO({ id: 10, isFavorite: true, subType: ChannelSubType.EXERCISE });
            const mutedExerciseChannel = generateExampleChannelDTO({ id: 103, isMuted: true, subType: ChannelSubType.EXERCISE });
            const hiddenExerciseChannel = generateExampleChannelDTO({ id: 9, isHidden: true, subType: ChannelSubType.EXERCISE });

            const visibleLectureChannel = generateExampleChannelDTO({ id: 11, subType: ChannelSubType.LECTURE });
            const favoriteLectureChannel = generateExampleChannelDTO({ id: 13, isFavorite: true, subType: ChannelSubType.LECTURE });
            const mutedLectureChannel = generateExampleChannelDTO({ id: 104, isMuted: true, subType: ChannelSubType.LECTURE });
            const hiddenLectureChannel = generateExampleChannelDTO({ id: 12, isHidden: true, subType: ChannelSubType.LECTURE });

            const visibleExamChannel = generateExampleChannelDTO({ id: 14, subType: ChannelSubType.EXAM });
            const hiddenExamChannel = generateExampleChannelDTO({ id: 15, isHidden: true, subType: ChannelSubType.EXAM });
            const mutedExamChannel = generateExampleChannelDTO({ id: 105, isMuted: true, subType: ChannelSubType.EXAM });
            const favoriteExamChannel = generateExampleChannelDTO({ id: 16, isFavorite: true, subType: ChannelSubType.EXAM });

            const visibleOneToOneChat = generateOneToOneChatDTO({ id: 17 });
            const favoriteOneToOneChat = generateOneToOneChatDTO({ id: 19, isFavorite: true });
            const mutedOneToOneChat = generateOneToOneChatDTO({ id: 106, isMuted: true });
            const hiddenOneToOneChat = generateOneToOneChatDTO({ id: 18, isHidden: true });

            beforeEach(waitForAsync(() => {
                TestBed.configureTestingModule({
                    imports: [NgbDropdownMocksModule, NgbCollapseMocksModule, NgbTooltipMocksModule],
                    declarations: [
                        ConversationSelectionSidebarComponent,
                        ConversationSidebarSectionComponent,
                        ConversationSidebarEntryComponent,
                        MockComponent(FaIconComponent),
                        MockPipe(ArtemisTranslatePipe),
                        MockComponent(GroupChatIconComponent),
                        MockComponent(ChannelIconComponent),
                    ],
                    providers: [
                        MockProvider(TranslateService),
                        MockProvider(NgbModal),
                        MockProvider(MetisConversationService),
                        MockProvider(NotificationService),
                        MockProvider(AccountService),
                        MockProvider(MetisService),
                        { provide: LocalStorageService, useClass: MockLocalStorageService },
                        MockProvider(ConversationService, {
                            getConversationName: (conversation: ConversationDTO) => {
                                return conversation.id + '';
                            },
                        }),
                    ],
                }).compileComponents();
            }));

            beforeEach(() => {
                allConversations = [
                    visibleChannel,
                    favoriteChannel,
                    mutedChannel,
                    hiddenChannel,

                    visibleExerciseChannel,
                    favoriteExerciseChannel,
                    mutedExerciseChannel,
                    hiddenExerciseChannel,

                    visibleLectureChannel,
                    favoriteLectureChannel,
                    mutedLectureChannel,
                    hiddenLectureChannel,

                    visibleExamChannel,
                    favoriteExamChannel,
                    mutedExamChannel,
                    hiddenExamChannel,

                    visibleGroupChat,
                    favoriteGroupChat,
                    mutedGroupChat,
                    hiddenGroupChat,

                    visibleOneToOneChat,
                    favoriteOneToOneChat,
                    mutedOneToOneChat,
                    hiddenOneToOneChat,
                ];

                canCreateChannel.mockReturnValue(true);
                metisConversationService = TestBed.inject(MetisConversationService);
                Object.defineProperty(metisConversationService, 'course', { get: () => course });
                Object.defineProperty(metisConversationService, 'activeConversation$', { get: () => new BehaviorSubject(activeConversation).asObservable() });
                Object.defineProperty(metisConversationService, 'conversationsOfUser$', {
                    get: () => new BehaviorSubject(allConversations).asObservable(),
                });
                Object.defineProperty(metisConversationService, 'forceRefresh', { value: () => EMPTY });
                Object.defineProperty(metisConversationService, 'setActiveConversation', { value: () => {} });

                fixture = TestBed.createComponent(ConversationSelectionSidebarComponent);
                component = fixture.componentInstance;
                component.canCreateChannel = canCreateChannel;
            });

            it('should create', fakeAsync(() => {
                fixture.detectChanges();
                tick(301);
                expect(component).toBeTruthy();
            }));

            it('should set properties correctly', fakeAsync(() => {
                fixture.detectChanges();
                tick(301);
                expect(component.course).toEqual(course);
                expect(component.activeConversation).toEqual(activeConversation);
                allConversations.forEach((conversation) => {
                    expect(component.allConversations).toContain(conversation);
                });

                expect(component.starredConversations).toContain(favoriteChannel);
                expect(component.starredConversations).toContain(favoriteGroupChat);
                expect(component.starredConversations).toContain(favoriteOneToOneChat);
                expect(component.starredConversations).toHaveLength(6);
                expect(component.starredConversations).toEqual(component.displayedStarredConversations);

                expect(component.channelConversations).toContain(visibleChannel);
                expect(component.channelConversations).toContain(mutedChannel);
                expect(component.channelConversations).toContain(hiddenChannel);
                expect(component.channelConversations).toHaveLength(12);
                expect(component.channelConversations).toEqual(component.displayedChannelConversations);

                expect(component.groupChats).toContain(visibleGroupChat);
                expect(component.groupChats).toContain(mutedGroupChat);
                expect(component.groupChats).toContain(hiddenGroupChat);
                expect(component.groupChats).toHaveLength(3);
                expect(component.groupChats).toEqual(component.displayedGroupChats);

                expect(component.oneToOneChats).toContain(visibleOneToOneChat);
                expect(component.oneToOneChats).toContain(mutedOneToOneChat);
                expect(component.oneToOneChats).toContain(hiddenOneToOneChat);
                expect(component.oneToOneChats).toHaveLength(3);
                expect(component.oneToOneChats).toEqual(component.displayedOneToOneChats);
            }));

            it('should filter conversations correctly when search term is entered', fakeAsync(() => {
                fixture.detectChanges();
                tick(301);
                const inputField = fixture.debugElement.query(By.css('input'));
                inputField.nativeElement.value = visibleGroupChat.id + '';
                inputField.nativeElement.dispatchEvent(new Event('input'));
                tick(301);
                expect(component.searchTerm).toEqual(visibleGroupChat.id + '');
                expect(component.displayedStarredConversations).toHaveLength(1);
                expect(component.displayedChannelConversations).toHaveLength(1);
                expect(component.displayedGroupChats).toHaveLength(1);
                expect(component.displayedGroupChats).toContain(visibleGroupChat);
                expect(component.displayedOneToOneChats).toHaveLength(0);
            }));

            it('should not show create channel button if user is missing the permission', fakeAsync(() => {
                fixture.detectChanges();
                tick(301);
                canCreateChannel.mockReturnValue(false);
                fixture.detectChanges();
                expect(fixture.debugElement.nativeElement.querySelector('#createChannel')).toBeFalsy();
            }));

            it('should open create channel dialog when button is pressed', fakeAsync(() => {
                const createChannelSpy = jest.fn().mockReturnValue(EMPTY);
                Object.defineProperty(metisConversationService, 'createChannel', { value: createChannelSpy });
                createConversationDialogTest(new ChannelDTO(), ChannelsCreateDialogComponent, 'createChannel');
                fixture.whenStable().then(() => {
                    expect(createChannelSpy).toHaveBeenCalledOnce();
                });
            }));

            it('should open create group chat dialog when button is pressed', fakeAsync(() => {
                const createGroupChatSpy = jest.fn().mockReturnValue(EMPTY);
                Object.defineProperty(metisConversationService, 'createGroupChat', { value: createGroupChatSpy });
                createConversationDialogTest([new UserPublicInfoDTO()], GroupChatCreateDialogComponent, 'createGroupChat');
                fixture.whenStable().then(() => {
                    expect(createGroupChatSpy).toHaveBeenCalledOnce();
                });
            }));

            it('should open one to one chat dialog when button is pressed', fakeAsync(() => {
                const createOneToOneChatSpy = jest.fn().mockReturnValue(EMPTY);
                Object.defineProperty(metisConversationService, 'createOneToOneChat', { value: createOneToOneChatSpy });
                const chatPartner = new UserPublicInfoDTO();
                chatPartner.login = 'test';
                createConversationDialogTest(chatPartner, OneToOneChatCreateDialogComponent, 'createOneToOne');
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
                fixture.detectChanges();

                const dialogOpenButton = fixture.debugElement.nativeElement.querySelector('#channelOverview');
                dialogOpenButton.click();
                tick(301);
                fixture.whenStable().then(() => {
                    expect(openDialogSpy).toHaveBeenCalledOnce();
                    expect(openDialogSpy).toHaveBeenCalledWith(ChannelsOverviewDialogComponent, defaultFirstLayerDialogOptions);
                    expect(mockModalRef.componentInstance.course).toEqual(course);
                });
            }));

            it('should refresh when settings are changed', fakeAsync(() => {
                fixture.detectChanges();
                tick(301);

                const forceRefreshMock = jest.spyOn(metisConversationService, 'forceRefresh').mockReturnValue(EMPTY);
                component.onSettingsDidChange();
                expect(forceRefreshMock).toHaveBeenCalledOnce();
            }));

            it('should run conversations update when favorite status is changed', fakeAsync(() => {
                fixture.detectChanges();
                tick(301);
                const onConversationsUpdateSpy = jest.spyOn(component, 'onConversationsUpdate');
                component.updateConversations();
                tick(301);
                expect(onConversationsUpdateSpy).toHaveBeenCalledOnce();
                expect(onConversationsUpdateSpy).toHaveBeenCalledWith(component.allConversations);
            }));

            it('should run conversations update when hidden status is changed', fakeAsync(() => {
                fixture.detectChanges();
                tick(301);
                const onConversationsUpdateSpy = jest.spyOn(component, 'onConversationsUpdate');
                component.updateConversations();
                tick(301);
                expect(onConversationsUpdateSpy).toHaveBeenCalledOnce();
                expect(onConversationsUpdateSpy).toHaveBeenCalledWith(component.allConversations);
            }));

            it('should run conversations update when muted status is changed', fakeAsync(() => {
                fixture.detectChanges();
                tick(301);
                const onConversationsUpdateSpy = jest.spyOn(component, 'onConversationsUpdate');
                component.updateConversations();
                tick(301);
                expect(onConversationsUpdateSpy).toHaveBeenCalledOnce();
                expect(onConversationsUpdateSpy).toHaveBeenCalledWith(component.allConversations);
            }));

            it('should open code of conduct', () => {
                const metisSpy = jest.spyOn(metisConversationService, 'setCodeOfConduct');
                component.openCodeOfConduct();
                expect(metisSpy).toHaveBeenCalledOnce();
            });

            it('should hide buttons if messaging disabled', fakeAsync(() => {
                fixture.detectChanges();
                tick(301);
                component.course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_ONLY } as any;
                component.isMessagingEnabled = false;
                fixture.detectChanges();
                tick(301);

                const channelButton = fixture.debugElement.query(By.css('#channelButton'));
                const exerciseChannelButton = fixture.debugElement.query(By.css('#exerciseChannelButton'));
                const lectureChannelButton = fixture.debugElement.query(By.css('#lectureChannelButton'));
                const examChannelButton = fixture.debugElement.query(By.css('#examChannelButton'));
                const groupChatButton = fixture.debugElement.query(By.css('#createGroupChat'));
                const oneToOneChatButton = fixture.debugElement.query(By.css('#createOneToOne'));

                expect(channelButton).toBeNull();
                expect(exerciseChannelButton).toBeNull();
                expect(lectureChannelButton).toBeNull();
                expect(examChannelButton).toBeNull();
                expect(groupChatButton).toBeNull();
                expect(oneToOneChatButton).toBeNull();
            }));

            function createConversationDialogTest(modalReturnValue: any, dialog: Type<AbstractDialogComponent>, buttonId: string) {
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

                const dialogOpenButton = fixture.debugElement.nativeElement.querySelector('#' + buttonId);
                dialogOpenButton.click();
                tick(301);
                fixture.whenStable().then(() => {
                    expect(openDialogSpy).toHaveBeenCalledOnce();
                    expect(openDialogSpy).toHaveBeenCalledWith(dialog, defaultFirstLayerDialogOptions);
                    expect(mockModalRef.componentInstance.course).toEqual(course);
                });
            }
        },
    );
});
