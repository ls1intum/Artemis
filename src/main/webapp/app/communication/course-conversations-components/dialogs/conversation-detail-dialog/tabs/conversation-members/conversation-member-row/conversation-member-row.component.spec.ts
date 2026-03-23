import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { User } from 'app/core/user/user.model';
import { GenericConfirmationDialogComponent } from 'app/communication/course-conversations-components/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { ChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { By } from '@angular/platform-browser';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faUser, faUserCheck, faUserGraduate } from '@fortawesome/free-solid-svg-icons';
import { ConversationMemberRowComponent } from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/tabs/conversation-members/conversation-member-row/conversation-member-row.component';

const memberTemplate = {
    id: 1,
    login: 'login',
    firstName: 'Kaddl',
    lastName: 'Garching',
    isChannelModerator: true,
    isStudent: false,
    isEditor: false,
    isTeachingAssistant: false,
    isInstructor: true,
} as ConversationUserDTO;
const creatorTemplate = { id: 2, login: 'login2', firstName: 'Kaddl2', lastName: 'Garching2' } as ConversationUserDTO;
const currentUserTemplate = { id: 3, login: 'login3', firstName: 'Kaddl3', lastName: 'Garching3' } as User;

const examples: ConversationDTO[] = [
    generateOneToOneChatDTO({}),
    generateExampleGroupChatDTO({}),
    generateExampleChannelDTO({} as ChannelDTO),
    generateExampleChannelDTO({ isCourseWide: true } as ChannelDTO),
];

examples.forEach((activeConversation) => {
    describe('ConversationMemberRowComponent with ' + activeConversation.type, () => {
        setupTestBed({ zoneless: true });

        let component: ConversationMemberRowComponent;
        let fixture: ComponentFixture<ConversationMemberRowComponent>;
        const course = { id: 1 } as Course;
        let conversationMember: ConversationUserDTO;
        let conversationCreator: ConversationUserDTO;
        let loggedInUser: User;
        const canGrantChannelModeratorRole = vi.fn();
        const canRevokeChannelModeratorRole = vi.fn();
        const canRemoveUsersFromConversation = vi.fn();
        let translateService: TranslateService;

        beforeEach(async () => {
            vi.useFakeTimers();
            TestBed.configureTestingModule({
                imports: [
                    ConversationMemberRowComponent,
                    NgbTooltipModule,
                    FaIconComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(ProfilePictureComponent),
                    MockDirective(TranslateDirective),
                ],
                providers: [
                    MockProvider(AccountService),
                    MockProvider(DialogService),
                    MockProvider(TranslateService),
                    MockProvider(ChannelService),
                    MockProvider(GroupChatService),
                    MockProvider(AlertService),
                ],
            }).overrideComponent(ConversationMemberRowComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe, ProfilePictureComponent] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe), MockComponent(ProfilePictureComponent)] },
            });
        });

        beforeEach(() => {
            loggedInUser = { ...currentUserTemplate };
            conversationMember = { ...memberTemplate };
            conversationCreator = { ...creatorTemplate };
            activeConversation.creator = conversationCreator;

            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(loggedInUser));
            canRemoveUsersFromConversation.mockReturnValue(!(activeConversation instanceof ChannelDTO && activeConversation.isCourseWide));
            canGrantChannelModeratorRole.mockReturnValue(true);
            canRevokeChannelModeratorRole.mockReturnValue(true);

            fixture = TestBed.createComponent(ConversationMemberRowComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('activeConversation', activeConversation);
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('conversationMember', conversationMember);
            component.canRevokeChannelModeratorRole = canRevokeChannelModeratorRole;
            component.canGrantChannelModeratorRole = canGrantChannelModeratorRole;
            component.canRemoveUsersFromConversation = canRemoveUsersFromConversation;
            translateService = TestBed.inject(TranslateService) as TranslateService;
            vi.spyOn(translateService, 'instant').mockImplementation((key: string) => key);
        });

        afterEach(() => {
            vi.useRealTimers();
            vi.restoreAllMocks();
        });

        it('should create', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);
            expect(component).toBeTruthy();
            expect(component.canBeRemovedFromConversation).toEqual(canRemoveUsersFromConversation());

            if (isChannelDTO(activeConversation)) {
                expect(component.canBeGrantedChannelModeratorRole).toBe(false); // is already moderator
                expect(component.canBeRevokedChannelModeratorRole).toBe(true);
            }
        });

        it('should show remove user button if the user has the permissions in group chat', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);
            fixture.changeDetectorRef.detectChanges();
            if (isGroupChatDTO(activeConversation)) {
                expect(component.canBeRemovedFromConversation).toBe(true);
                checkRemoveMemberButton(true);
            }
        });

        it('should show remove user button if the user has the permissions in channel', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);
            fixture.changeDetectorRef.detectChanges();
            if (isChannelDTO(activeConversation)) {
                expect(component.canBeRemovedFromConversation).toEqual(canRemoveUsersFromConversation());
                checkRemoveMemberButton(component.canBeRemovedFromConversation);
            }
        });

        it('should hide remove user button if the user does not have the permissions', async () => {
            canRemoveUsersFromConversation.mockReturnValue(false);
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);
            fixture.changeDetectorRef.detectChanges();
            if (isChannelDTO(activeConversation) || isGroupChatDTO(activeConversation)) {
                expect(component.canBeRemovedFromConversation).toBe(false);
                checkRemoveMemberButton(false);
            }
        });

        it('should show revoke moderator button if user is already moderator', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);
            fixture.changeDetectorRef.detectChanges();
            if (isChannelDTO(activeConversation)) {
                expect(component.canBeGrantedChannelModeratorRole).toBe(false); // is already moderator
                expect(component.canBeRevokedChannelModeratorRole).toBe(true);
                checkRevokeModeratorButton(true);
                checkGrantModeratorButton(false);
            }
        });

        it('should show grant moderator button if user is not yet moderator', async () => {
            if (isChannelDTO(activeConversation)) {
                const updatedMember = { ...conversationMember };
                (updatedMember as ChannelDTO).isChannelModerator = false;
                fixture.componentRef.setInput('conversationMember', updatedMember);
                fixture.detectChanges();
                await fixture.whenStable();
                vi.advanceTimersByTime(0);
                fixture.changeDetectorRef.detectChanges();

                expect(component.canBeGrantedChannelModeratorRole).toBe(true);
                expect(component.canBeRevokedChannelModeratorRole).toBe(false);
                checkRevokeModeratorButton(false);
                checkGrantModeratorButton(true);
            }
        });

        function checkGrantModeratorButton(shouldExist: boolean) {
            const grantModeratorRoleButton = fixture.debugElement.query(By.css('.grant-moderator'));
            if (shouldExist) {
                expect(grantModeratorRoleButton).toBeTruthy();
            } else {
                expect(grantModeratorRoleButton).toBeFalsy();
            }
        }

        function checkRevokeModeratorButton(shouldExist: boolean) {
            const revokeModeratorRoleButton = fixture.debugElement.query(By.css('.revoke-moderator'));
            if (shouldExist) {
                expect(revokeModeratorRoleButton).toBeTruthy();
            } else {
                expect(revokeModeratorRoleButton).toBeFalsy();
            }
        }

        function checkRemoveMemberButton(shouldExist: boolean) {
            const removeMemberButton = fixture.debugElement.query(By.css('.remove-member'));
            if (shouldExist) {
                expect(removeMemberButton).toBeTruthy();
            } else {
                expect(removeMemberButton).toBeFalsy();
            }
        }

        it('should open the grant channel moderator role dialog', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const changesPerformedSpy = vi.spyOn(component.changePerformed, 'emit');
                const grantChannelModeratorRoleSpy = vi
                    .spyOn(channelService, 'grantChannelModeratorRole')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openGrantChannelModeratorRoleDialog.bind(component));
                expect(grantChannelModeratorRoleSpy).toHaveBeenCalledOnce();
                expect(grantChannelModeratorRoleSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        });

        it('should open the revoke channel moderator role dialog', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const changesPerformedSpy = vi.spyOn(component.changePerformed, 'emit');
                const revokeChannelModeratorRoleSpy = vi
                    .spyOn(channelService, 'revokeChannelModeratorRole')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openRevokeChannelModeratorRoleDialog.bind(component));
                expect(revokeChannelModeratorRoleSpy).toHaveBeenCalledOnce();
                expect(revokeChannelModeratorRoleSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        });

        it('should open the remove from private channel dialog', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const changesPerformedSpy = vi.spyOn(component.changePerformed, 'emit');
                const deregisterUsersFromChannelSpy = vi
                    .spyOn(channelService, 'deregisterUsersFromChannel')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openRemoveFromChannelDialog.bind(component));
                expect(deregisterUsersFromChannelSpy).toHaveBeenCalledOnce();
                expect(deregisterUsersFromChannelSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        });

        it('should open the remove from group chat dialog', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);
            if (isGroupChatDTO(activeConversation)) {
                const groupChatService = TestBed.inject(GroupChatService);
                const changesPerformedSpy = vi.spyOn(component.changePerformed, 'emit');
                const removeUsersFromGroupChatSpy = vi
                    .spyOn(groupChatService, 'removeUsersFromGroupChat')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openRemoveFromGroupChatDialog.bind(component));
                expect(removeUsersFromGroupChatSpy).toHaveBeenCalledOnce();
                expect(removeUsersFromGroupChatSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        });

        it('should emit userId when another user clicks the name', async () => {
            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);

            component.idOfLoggedInUser = loggedInUser.id!;
            fixture.changeDetectorRef.detectChanges();
            const userNameClickedSpy = vi.spyOn(component.onUserNameClicked, 'emit');
            component.userNameClicked();

            expect(userNameClickedSpy).toHaveBeenCalledWith(conversationMember.id);
        });

        it('should set isCurrentUser to true if conversation member is the logged-in user', async () => {
            loggedInUser.id = conversationMember.id!;

            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);

            expect(component.isCurrentUser).toBe(true);
        });

        it('should set isCurrentUser to false if conversation member is NOT the logged-in user', async () => {
            loggedInUser.id = 999;

            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);

            expect(component.isCurrentUser).toBe(false);
        });

        it('should prevent removal action if the user is the current user', async () => {
            loggedInUser.id = conversationMember.id!;

            fixture.detectChanges();
            await fixture.whenStable();
            vi.advanceTimersByTime(0);

            expect(component.canBeRemovedFromConversation).toBe(false);
        });

        it.each`
            role                           | isInstructor | isEditor | isTeachingAssistant | expectedIcon      | expectedTooltip
            ${'instructor'}                | ${true}      | ${false} | ${false}            | ${faUserGraduate} | ${'artemisApp.metis.userAuthorityTooltips.instructor'}
            ${'editor (tutor)'}            | ${false}     | ${true}  | ${false}            | ${faUserCheck}    | ${'artemisApp.metis.userAuthorityTooltips.tutor'}
            ${'teachingAssistant (tutor)'} | ${false}     | ${false} | ${true}             | ${faUserCheck}    | ${'artemisApp.metis.userAuthorityTooltips.tutor'}
            ${'regular student (default)'} | ${false}     | ${false} | ${false}            | ${faUser}         | ${'artemisApp.metis.userAuthorityTooltips.student'}
        `('should set correct icon and tooltip when role = $role', ({ isInstructor, isEditor, isTeachingAssistant, expectedIcon, expectedTooltip }) => {
            const updatedMember: ConversationUserDTO = {
                id: 123,
                isInstructor,
                isEditor,
                isTeachingAssistant,
            } as ConversationUserDTO;

            fixture.componentRef.setInput('conversationMember', updatedMember);
            fixture.changeDetectorRef.detectChanges();
            component.setUserAuthorityIconAndTooltip();
            expect(component.userIcon).toBe(expectedIcon);
            expect(component.userTooltip).toBe(expectedTooltip);
        });

        function genericConfirmationDialogTest(method: (event: MouseEvent) => void) {
            const dialogService = TestBed.inject(DialogService);
            const mockOnClose = new Subject<any>();
            const mockDialogRef = {
                onClose: mockOnClose.asObservable(),
                close: vi.fn(),
            };
            const openDialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef as unknown as DynamicDialogRef);

            const fakeClickEvent = new MouseEvent('click');
            method(fakeClickEvent);
            mockOnClose.next(true);
            mockOnClose.complete();
            vi.advanceTimersByTime(0);

            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(openDialogSpy).toHaveBeenCalledWith(GenericConfirmationDialogComponent, expect.anything());
        }
    });
});
