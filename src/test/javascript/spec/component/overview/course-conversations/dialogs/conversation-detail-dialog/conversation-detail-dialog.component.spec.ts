import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelIconComponent } from 'app/overview/course-conversations/other/channel-icon/channel-icon.component';
import { GroupChatIconComponent } from 'app/overview/course-conversations/other/group-chat-icon/group-chat-icon.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../helpers/conversationExampleModels';
import { initializeDialog } from '../dialog-test-helpers';
import { isOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { By } from '@angular/platform-browser';

@Component({
    selector: 'jhi-conversation-members',
    template: '',
})
class ConversationMembersStubComponent {
    @Input()
    course: Course;
    @Input()
    public activeConversation: ConversationDTO;
    @Output() changesPerformed = new EventEmitter<void>();
}

@Component({
    selector: 'jhi-conversation-settings',
    template: '',
})
class ConversationSettingsStubComponent {
    @Input()
    activeConversation: ConversationDTO;

    @Input()
    course: Course;

    @Output()
    channelArchivalChange: EventEmitter<void> = new EventEmitter<void>();

    @Output()
    channelDeleted: EventEmitter<void> = new EventEmitter<void>();

    @Output()
    conversationLeave: EventEmitter<void> = new EventEmitter<void>();
}

@Component({
    selector: 'jhi-conversation-info',
    template: '',
})
class ConversationInfoStubComponent {
    @Input()
    activeConversation: ConversationDTO;

    @Input()
    course: Course;

    @Output()
    changesPerformed = new EventEmitter<void>();
}

const examples: ConversationDTO[] = [generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];

examples.forEach((activeConversation) => {
    describe('ConversationDetailDialogComponent with ' + activeConversation.type, () => {
        let component: ConversationDetailDialogComponent;
        let fixture: ComponentFixture<ConversationDetailDialogComponent>;
        const course = { id: 1 } as Course;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                declarations: [
                    ConversationDetailDialogComponent,
                    ConversationMembersStubComponent,
                    ConversationSettingsStubComponent,
                    ConversationInfoStubComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(ChannelIconComponent),
                    MockComponent(GroupChatIconComponent),
                ],
                providers: [MockProvider(NgbActiveModal), MockProvider(ConversationService)],
            }).compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ConversationDetailDialogComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            initializeDialog(component, fixture, { course, activeConversation, selectedTab: ConversationDetailTabs.INFO });
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(component.isInitialized).toBeTrue();
        });

        it('should not show the settings tab for one-to-one chats', () => {
            if (isOneToOneChatDTO(activeConversation)) {
                expect(fixture.nativeElement.querySelector('.settings-tab')).toBeFalsy();
            } else {
                expect(fixture.nativeElement.querySelector('.settings-tab')).toBeTruthy();
            }
        });

        it('should react correctly to events from members tab', () => {
            component.selectedTab = ConversationDetailTabs.MEMBERS;
            fixture.detectChanges();
            const membersComponent = fixture.debugElement.query(By.directive(ConversationMembersStubComponent)).componentInstance;
            expect(membersComponent).toBeTruthy();
            expect(component.changesWerePerformed).toBeFalse();
            membersComponent.changesPerformed.emit();
            expect(component.changesWerePerformed).toBeTrue();
        });

        it('should react correctly to events from info tab', () => {
            component.selectedTab = ConversationDetailTabs.INFO;
            fixture.detectChanges();
            const infoComponent = fixture.debugElement.query(By.directive(ConversationInfoStubComponent)).componentInstance;
            expect(infoComponent).toBeTruthy();
            expect(component.changesWerePerformed).toBeFalse();
            infoComponent.changesPerformed.emit();
            expect(component.changesWerePerformed).toBeTrue();
        });

        it('should react correctly to events from settings tab', () => {
            if (!isOneToOneChatDTO(activeConversation)) {
                component.selectedTab = ConversationDetailTabs.SETTINGS;
                fixture.detectChanges();
                const settingsComponent = fixture.debugElement.query(By.directive(ConversationSettingsStubComponent)).componentInstance;
                expect(settingsComponent).toBeTruthy();

                const activeModal = TestBed.inject(NgbActiveModal);
                const closeSpy = jest.spyOn(activeModal, 'close');

                settingsComponent.channelArchivalChange.emit();
                expect(closeSpy).toHaveBeenCalledOnce();

                closeSpy.mockClear();
                settingsComponent.channelDeleted.emit();
                expect(closeSpy).toHaveBeenCalledOnce();

                closeSpy.mockClear();
                settingsComponent.conversationLeave.emit();
                expect(closeSpy).toHaveBeenCalledOnce();
            }
        });
    });
});
