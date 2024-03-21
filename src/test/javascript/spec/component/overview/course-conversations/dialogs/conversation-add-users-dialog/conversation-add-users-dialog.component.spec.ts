import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO } from '../../helpers/conversationExampleModels';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AddUsersFormData } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';
import { initializeDialog } from '../dialog-test-helpers';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelIconComponent } from 'app/overview/course-conversations/other/channel-icon/channel-icon.component';
import { GroupChatIconComponent } from 'app/overview/course-conversations/other/group-chat-icon/group-chat-icon.component';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { By } from '@angular/platform-browser';
import { isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
@Component({
    selector: 'jhi-conversation-add-users-form',
    template: '',
})
class ConversationAddUsersFormStubComponent {
    @Output() formSubmitted: EventEmitter<AddUsersFormData> = new EventEmitter<AddUsersFormData>();
    @Input() courseId: number;
    @Input() maxSelectable?: number = undefined;

    @Input()
    activeConversation: ConversationDTO;
}
const examples: ConversationDTO[] = [generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];
examples.forEach((activeConversation) => {
    describe('ConversationAddUsersDialogComponent with ' + activeConversation.type, () => {
        let component: ConversationAddUsersDialogComponent;
        let fixture: ComponentFixture<ConversationAddUsersDialogComponent>;
        const course = { id: 1 } as Course;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                declarations: [
                    ConversationAddUsersDialogComponent,
                    ConversationAddUsersFormStubComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(ChannelIconComponent),
                    MockComponent(GroupChatIconComponent),
                ],
                providers: [
                    MockProvider(AlertService),
                    MockProvider(NgbActiveModal),
                    MockProvider(ChannelService),
                    MockProvider(ConversationService),
                    MockProvider(GroupChatService),
                ],
            }).compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ConversationAddUsersDialogComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            initializeDialog(component, fixture, { course, activeConversation });
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(component.isInitialized).toBeTrue();
        });

        it('should call the correct service depending on conversation type when form is submitted', () => {
            const addUsersFormData: AddUsersFormData = {
                selectedUsers: [{ id: 1, login: 'loginA' } as UserPublicInfoDTO],
                addAllStudents: false,
                addAllTutors: false,
                addAllInstructors: false,
            };
            const form: ConversationAddUsersFormStubComponent = fixture.debugElement.query(By.directive(ConversationAddUsersFormStubComponent)).componentInstance;

            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const activeModal = TestBed.inject(NgbActiveModal);
                jest.spyOn(activeModal, 'close');
                jest.spyOn(channelService, 'registerUsersToChannel').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                form.formSubmitted.emit(addUsersFormData);
                expect(channelService.registerUsersToChannel).toHaveBeenCalledOnce();
                expect(channelService.registerUsersToChannel).toHaveBeenCalledWith(
                    course.id,
                    activeConversation.id,
                    addUsersFormData.addAllStudents,
                    addUsersFormData.addAllTutors,
                    addUsersFormData.addAllInstructors,
                    addUsersFormData.selectedUsers?.map((user) => user.login),
                );
                expect(activeModal.close).toHaveBeenCalledOnce();
            }

            if (isGroupChatDTO(activeConversation)) {
                const groupChatService = TestBed.inject(GroupChatService);
                const activeModal = TestBed.inject(NgbActiveModal);
                jest.spyOn(activeModal, 'close');
                jest.spyOn(groupChatService, 'addUsersToGroupChat').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                form.formSubmitted.emit(addUsersFormData);
                expect(groupChatService.addUsersToGroupChat).toHaveBeenCalledOnce();
                expect(groupChatService.addUsersToGroupChat).toHaveBeenCalledWith(
                    course.id,
                    activeConversation.id,
                    addUsersFormData.selectedUsers?.map((user) => user.login),
                );
                expect(activeModal.close).toHaveBeenCalledOnce();
            }
        });
    });
});
