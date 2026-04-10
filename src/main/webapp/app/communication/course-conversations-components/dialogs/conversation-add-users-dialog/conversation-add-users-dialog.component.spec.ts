import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { By } from '@angular/platform-browser';
import { ChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { GroupChatDTO, isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { Subject, of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ConversationAddUsersDialogComponent } from 'app/communication/course-conversations-components/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { AddUsersFormData } from 'app/communication/course-conversations-components/dialogs/conversation-add-users-dialog/add-users-form/conversation-add-users-form.component';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';

const examples: ConversationDTO[] = [generateExampleGroupChatDTO({} as GroupChatDTO), generateExampleChannelDTO({} as ChannelDTO)];

examples.forEach((activeConversation) => {
    describe('ConversationAddUsersDialogComponent with ' + activeConversation.type, () => {
        setupTestBed({ zoneless: true });

        let component: ConversationAddUsersDialogComponent;
        let fixture: ComponentFixture<ConversationAddUsersDialogComponent>;
        const course = { id: 1 } as Course;

        beforeEach(async () => {
            TestBed.configureTestingModule({
                imports: [ConversationAddUsersDialogComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ChannelIconComponent)],
                providers: [
                    MockProvider(AlertService),
                    { provide: DynamicDialogRef, useValue: { close: vi.fn(), destroy: vi.fn(), onClose: new Subject() } },
                    { provide: DynamicDialogConfig, useValue: { data: {} } },
                    MockProvider(ChannelService),
                    MockProvider(ConversationService),
                    MockProvider(GroupChatService),
                    { provide: TranslateService, useClass: MockTranslateService },
                    SessionStorageService,
                    provideHttpClient(),
                    provideHttpClientTesting(),
                ],
            });
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(ConversationAddUsersDialogComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            initializeDialog(component, fixture, { course, activeConversation });
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
            expect(component.isInitialized).toBe(true);
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
                const dialogRef = TestBed.inject(DynamicDialogRef);
                vi.spyOn(dialogRef, 'close');
                vi.spyOn(channelService, 'registerUsersToChannel').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
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
                expect(dialogRef.close).toHaveBeenCalledExactlyOnceWith(true);
            }

            if (isGroupChatDTO(activeConversation)) {
                const groupChatService = TestBed.inject(GroupChatService);
                const dialogRef = TestBed.inject(DynamicDialogRef);
                vi.spyOn(dialogRef, 'close');
                vi.spyOn(groupChatService, 'addUsersToGroupChat').mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                form.formSubmitted.emit(addUsersFormData);
                expect(groupChatService.addUsersToGroupChat).toHaveBeenCalledOnce();
                expect(groupChatService.addUsersToGroupChat).toHaveBeenCalledWith(
                    course.id,
                    activeConversation.id,
                    addUsersFormData.selectedUsers?.map((user) => user.login),
                );
                expect(dialogRef.close).toHaveBeenCalledExactlyOnceWith(true);
            }
        });
    });
});
