import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Type } from '@angular/core';
import { AccordionAddOptionsComponent } from 'app/shared/sidebar/accordion-add-options/accordion-add-options.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY } from 'rxjs';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { GroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { defaultFirstLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { NgbCollapseMocksModule } from '../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { AccountService } from 'app/core/auth/account.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { Course, CourseInformationSharingConfiguration } from 'app/entities/course.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ChannelSubType } from 'app/entities/metis/conversation/channel.model';

describe('AccordionAddOptionsComponent', () => {
    let component: AccordionAddOptionsComponent;
    let fixture: ComponentFixture<AccordionAddOptionsComponent>;
    let metisConversationService: MetisConversationService;
    const canCreateChannel = jest.fn();
    const course: Course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NgbCollapseMocksModule, NgbTooltipMocksModule],
            declarations: [AccordionAddOptionsComponent, MockComponent(FaIconComponent)],
            providers: [
                MockProvider(NgbModal),
                MockProvider(MetisConversationService),
                MockProvider(NotificationService),
                MockProvider(AccountService),
                MockProvider(MetisService),
                MockProvider(CourseStorageService),
                MockProvider(ConversationService, {
                    getConversationName: (conversation: ConversationDTO) => {
                        return conversation.id + '';
                    },
                }),
            ],
        }).compileComponents();

        metisConversationService = TestBed.inject(MetisConversationService);
        Object.defineProperty(metisConversationService, 'course', { get: () => course });
        Object.defineProperty(metisConversationService, 'forceRefresh', { value: () => EMPTY });
        Object.defineProperty(metisConversationService, 'setActiveConversation', { value: () => {} });
        canCreateChannel.mockReturnValue(true);
        fixture = TestBed.createComponent(AccordionAddOptionsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();

        component.sidebarType = 'conversation';
        component.groupKey = 'groupChats';
        component.courseId = 1;
        component.course = course;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should return correct channelSubType when subtype is exercise', fakeAsync(() => {
        component.groupKey = 'exerciseChannels';
        fixture.detectChanges();
        expect(component.getChannelSubType()).toEqual(ChannelSubType.EXERCISE);
    }));

    it('should return correct channelSubType when subtype is lecture', () => {
        component.groupKey = 'lectureChannels';
        fixture.detectChanges();
        expect(component.getChannelSubType()).toEqual(ChannelSubType.LECTURE);
    });

    it('should return correct channelSubType when groupKey is general', () => {
        component.groupKey = 'generalChannels';
        fixture.detectChanges();
        expect(component.getChannelSubType()).toEqual(ChannelSubType.GENERAL);
    });

    it('should return correct channelSubType when groupKey is exercise', () => {
        component.groupKey = 'examChannels';
        fixture.detectChanges();
        expect(component.getChannelSubType()).toEqual(ChannelSubType.EXAM);
    });

    it('should not show create channel button if user is missing the permission', fakeAsync(() => {
        fixture.detectChanges();
        tick(301);
        canCreateChannel.mockReturnValue(false);
        fixture.detectChanges();
        expect(fixture.debugElement.nativeElement.querySelector('#createChannel')).toBeFalsy();
    }));

    it('should open create group chat dialog when button is pressed', fakeAsync(() => {
        component.groupKey = 'groupChats';
        fixture.detectChanges();
        const createGroupChatSpy = jest.fn().mockReturnValue(EMPTY);
        Object.defineProperty(metisConversationService, 'createGroupChat', { value: createGroupChatSpy });
        createConversationDialogTest([new UserPublicInfoDTO()], GroupChatCreateDialogComponent, 'createGroupChat');
        fixture.detectChanges();
        fixture.whenStable().then(() => {
            expect(createGroupChatSpy).toHaveBeenCalledOnce();
        });
    }));

    it('should open one to one chat dialog when button is pressed', fakeAsync(() => {
        component.groupKey = 'directMessages';
        const createOneToOneChatSpy = jest.fn().mockReturnValue(EMPTY);
        Object.defineProperty(metisConversationService, 'createOneToOneChat', { value: createOneToOneChatSpy });
        const chatPartner = new UserPublicInfoDTO();
        chatPartner.login = 'test';
        createConversationDialogTest(chatPartner, OneToOneChatCreateDialogComponent, 'createDirectMessage');
        fixture.whenStable().then(() => {
            expect(createOneToOneChatSpy).toHaveBeenCalledOnce();
        });
    }));

    it('should open channel overview dialog when button is pressed', fakeAsync(() => {
        component.groupKey = 'generalChannels';
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
});
