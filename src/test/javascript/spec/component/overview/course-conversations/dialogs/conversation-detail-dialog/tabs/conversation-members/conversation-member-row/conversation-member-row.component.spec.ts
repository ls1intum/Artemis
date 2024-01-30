import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationMemberRowComponent } from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/tabs/conversation-members/conversation-member-row/conversation-member-row.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { NgbModal, NgbModalRef, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../../../../helpers/conversationExampleModels';
import { Course } from 'app/entities/course.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { User } from 'app/core/user/user.model';
import { GenericConfirmationDialogComponent } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { ChannelDTO, isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { By } from '@angular/platform-browser';
import { NgbDropdownMocksModule } from '../../../../../../../../helpers/mocks/directive/ngbDropdownMocks.module';

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
    generateExampleChannelDTO({}),
    generateExampleChannelDTO({ isCourseWide: true }),
];

examples.forEach((activeConversation) => {
    describe('ConversationMemberRowComponent with ' + activeConversation.type, () => {
        let component: ConversationMemberRowComponent;
        let fixture: ComponentFixture<ConversationMemberRowComponent>;
        const course = { id: 1 } as Course;
        let conversationMember: ConversationUserDTO;
        let conversationCreator: ConversationUserDTO;
        let loggedInUser: User;
        const canGrantChannelModeratorRole = jest.fn();
        const canRevokeChannelModeratorRole = jest.fn();
        const canRemoveUsersFromConversation = jest.fn();

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [NgbTooltipModule, NgbDropdownMocksModule],
                declarations: [ConversationMemberRowComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
                providers: [
                    MockProvider(AccountService),
                    MockProvider(NgbModal),
                    MockProvider(TranslateService),
                    MockProvider(ChannelService),
                    MockProvider(GroupChatService),
                    MockProvider(AlertService),
                ],
            }).compileComponents();
        }));

        beforeEach(() => {
            loggedInUser = { ...currentUserTemplate };
            conversationMember = { ...memberTemplate };
            conversationCreator = { ...creatorTemplate };
            activeConversation.creator = conversationCreator;

            const accountService = TestBed.inject(AccountService);
            jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(loggedInUser));
            canRemoveUsersFromConversation.mockReturnValue(!(activeConversation instanceof ChannelDTO && activeConversation.isCourseWide));
            canGrantChannelModeratorRole.mockReturnValue(true);
            canRevokeChannelModeratorRole.mockReturnValue(true);

            fixture = TestBed.createComponent(ConversationMemberRowComponent);
            component = fixture.componentInstance;
            component.activeConversation = activeConversation;
            component.course = course;
            component.conversationMember = conversationMember;
            component.canRevokeChannelModeratorRole = canRevokeChannelModeratorRole;
            component.canGrantChannelModeratorRole = canGrantChannelModeratorRole;
            component.canRemoveUsersFromConversation = canRemoveUsersFromConversation;
        });

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should create', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            expect(component).toBeTruthy();
            expect(component.canBeRemovedFromConversation).toEqual(canRemoveUsersFromConversation());

            if (isChannelDTO(activeConversation)) {
                expect(component.canBeGrantedChannelModeratorRole).toBeFalse(); // is already moderator
                expect(component.canBeRevokedChannelModeratorRole).toBeTrue();
            }
        }));

        it('should show remove user button if the user has the permissions in group chat', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isGroupChatDTO(activeConversation)) {
                expect(component.canBeRemovedFromConversation).toBeTrue();
                checkRemoveMemberButton(true);
            }
        }));

        it('should show remove user button if the user has the permissions in channel', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDTO(activeConversation)) {
                expect(component.canBeRemovedFromConversation).toEqual(canRemoveUsersFromConversation());
                checkRemoveMemberButton(component.canBeRemovedFromConversation);
            }
        }));

        it('should hide remove user button if the user does not have the permissions', fakeAsync(() => {
            canRemoveUsersFromConversation.mockReturnValue(false);
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDTO(activeConversation) || isGroupChatDTO(activeConversation)) {
                expect(component.canBeRemovedFromConversation).toBeFalse();
                checkRemoveMemberButton(false);
            }
        }));

        it('should show revoke moderator button if user is already moderator', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDTO(activeConversation)) {
                expect(component.canBeGrantedChannelModeratorRole).toBeFalse(); // is already moderator
                expect(component.canBeRevokedChannelModeratorRole).toBeTrue();
                checkRevokeModeratorButton(true);
                checkGrantModeratorButton(false);
            }
        }));

        it('should show grant moderator button if user is not yet moderator', fakeAsync(() => {
            if (isChannelDTO(activeConversation)) {
                component.conversationMember.isChannelModerator = false;
                fixture.detectChanges();
                tick();
                fixture.detectChanges();

                expect(component.canBeGrantedChannelModeratorRole).toBeTrue();
                expect(component.canBeRevokedChannelModeratorRole).toBeFalse();
                checkRevokeModeratorButton(false);
                checkGrantModeratorButton(true);
            }
        }));

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

        it('should open the grant channel moderator role dialog', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const changesPerformedSpy = jest.spyOn(component.changePerformed, 'emit');
                const grantChannelModeratorRoleSpy = jest
                    .spyOn(channelService, 'grantChannelModeratorRole')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openGrantChannelModeratorRoleDialog.bind(component));
                expect(grantChannelModeratorRoleSpy).toHaveBeenCalledOnce();
                expect(grantChannelModeratorRoleSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        }));

        it('should open the revoke channel moderator role dialog', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const changesPerformedSpy = jest.spyOn(component.changePerformed, 'emit');
                const revokeChannelModeratorRoleSpy = jest
                    .spyOn(channelService, 'revokeChannelModeratorRole')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openRevokeChannelModeratorRoleDialog.bind(component));
                expect(revokeChannelModeratorRoleSpy).toHaveBeenCalledOnce();
                expect(revokeChannelModeratorRoleSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        }));

        it('should open the remove from private channel dialog', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDTO(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const changesPerformedSpy = jest.spyOn(component.changePerformed, 'emit');
                const deregisterUsersFromChannelSpy = jest
                    .spyOn(channelService, 'deregisterUsersFromChannel')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openRemoveFromChannelDialog.bind(component));
                expect(deregisterUsersFromChannelSpy).toHaveBeenCalledOnce();
                expect(deregisterUsersFromChannelSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        }));

        it('should open the remove from group chat dialog', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isGroupChatDTO(activeConversation)) {
                const groupChatService = TestBed.inject(GroupChatService);
                const changesPerformedSpy = jest.spyOn(component.changePerformed, 'emit');
                const removeUsersFromGroupChatSpy = jest
                    .spyOn(groupChatService, 'removeUsersFromGroupChat')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openRemoveFromGroupChatDialog.bind(component));
                expect(removeUsersFromGroupChatSpy).toHaveBeenCalledOnce();
                expect(removeUsersFromGroupChatSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        }));
        function genericConfirmationDialogTest(method: (event: MouseEvent) => void) {
            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: {
                    translationParameters: undefined,
                    translationKeys: undefined,
                    canBeUndone: undefined,
                    initialize: () => {},
                },
                result: Promise.resolve(),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.detectChanges();

            const fakeClickEvent = new MouseEvent('click');
            method(fakeClickEvent);
            tick();

            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(GenericConfirmationDialogComponent, defaultSecondLayerDialogOptions);
            });
        }
    });
});
