import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelService } from 'app/communication/conversations/channel.service';
import { ConversationService } from 'app/communication/conversations/conversation.service';
import { GroupChatService } from 'app/communication/conversations/group-chat.service';
import { ConversationDTO } from 'app/communication/entities/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO } from '../../helpers/conversationExampleModels';
import { initializeDialog } from '../dialog-test-helpers';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelIconComponent } from 'app/communication/course-conversations/other/channel-icon/channel-icon.component';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { By } from '@angular/platform-browser';
import { ChannelDTO, isChannelDTO } from 'app/communication/entities/conversation/channel.model';
import { GroupChatDTO, isGroupChatDTO } from 'app/communication/entities/conversation/group-chat.model';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockSyncStorage } from '../../../../../helpers/mocks/service/mock-sync-storage.service';
import { SessionStorageService } from 'ngx-webstorage';
import { ConversationAddUsersDialogComponent } from 'app/communication/course-conversations/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { AddUsersFormData } from 'app/communication/course-conversations/dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';

const examples: ConversationDTO[] = [generateExampleGroupChatDTO({} as GroupChatDTO), generateExampleChannelDTO({} as ChannelDTO)];

examples.forEach((activeConversation) => {
    describe('ConversationAddUsersDialogComponent with ' + activeConversation.type, () => {
        let component: ConversationAddUsersDialogComponent;
        let fixture: ComponentFixture<ConversationAddUsersDialogComponent>;
        const course = { id: 1 } as Course;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                declarations: [ConversationAddUsersDialogComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ChannelIconComponent)],
                providers: [
                    MockProvider(AlertService),
                    MockProvider(NgbActiveModal),
                    MockProvider(ChannelService),
                    MockProvider(ConversationService),
                    MockProvider(GroupChatService),
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    provideHttpClient(),
                    provideHttpClientTesting(),
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
            const formComponentDebug = fixture.debugElement.query(By.css('jhi-conversation-add-users-form'));
            const form = formComponentDebug.componentInstance;

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
