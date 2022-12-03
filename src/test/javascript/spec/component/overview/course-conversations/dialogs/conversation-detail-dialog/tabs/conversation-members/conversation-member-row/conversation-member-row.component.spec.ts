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
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../../../../helpers/conversationExampleModels';
import { Course } from 'app/entities/course.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { User } from 'app/core/user/user.model';
import { GenericConfirmationDialogComponent } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { By } from '@angular/platform-browser';
import { NgbDropdownMocksModule } from '../../../../../../../../helpers/mocks/directive/ngbDropdownMocks.module';
const memberTemplate = {
    id: 1,
    login: 'login',
    firstName: 'Kaddl',
    lastName: 'Garching',
    isChannelAdmin: true,
    isStudent: false,
    isEditor: false,
    isTeachingAssistant: false,
    isInstructor: true,
} as ConversationUserDTO;
const creatorTemplate = { id: 2, login: 'login2', firstName: 'Kaddl2', lastName: 'Garching2' } as ConversationUserDTO;
const currentUserTemplate = { id: 3, login: 'login3', firstName: 'Kaddl3', lastName: 'Garching3' } as User;

const examples: ConversationDto[] = [generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];

examples.forEach((activeConversation) => {
    describe('ConversationMemberRowComponent with ' + activeConversation.type, () => {
        let component: ConversationMemberRowComponent;
        let fixture: ComponentFixture<ConversationMemberRowComponent>;
        const course = { id: 1 } as Course;
        let conversationMember: ConversationUserDTO;
        let conversationCreator: ConversationUserDTO;
        let loggedInUser: User;
        const canGrantChannelAdminRights = jest.fn();
        const canRevokeChannelAdminRights = jest.fn();
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
            canRemoveUsersFromConversation.mockReturnValue(true);
            canGrantChannelAdminRights.mockReturnValue(true);
            canRevokeChannelAdminRights.mockReturnValue(true);

            fixture = TestBed.createComponent(ConversationMemberRowComponent);
            component = fixture.componentInstance;
            component.activeConversation = activeConversation;
            component.course = course;
            component.conversationMember = conversationMember;
            component.canRevokeChannelAdminRights = canRevokeChannelAdminRights;
            component.canGrantChannelAdminRights = canGrantChannelAdminRights;
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
            expect(component.canBeRemovedFromConversation).toBeTrue();

            if (isChannelDto(activeConversation)) {
                expect(component.canBeGrantedChannelAdminRights).toBeFalse(); // is already admin
                expect(component.canBeRevokedChannelAdminRights).toBeTrue();
            }
        }));

        it('should show remove user button if the user has the permissions', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDto(activeConversation) || isGroupChatDto(activeConversation)) {
                expect(component.canBeRemovedFromConversation).toBeTrue();
                checkRemoveMemberButton(true);
            }
        }));

        it('should hide remove user button if the user does not have the permissions', fakeAsync(() => {
            canRemoveUsersFromConversation.mockReturnValue(false);
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDto(activeConversation) || isGroupChatDto(activeConversation)) {
                expect(component.canBeRemovedFromConversation).toBeFalse();
                checkRemoveMemberButton(false);
            }
        }));

        it('should show revoke admin button if user is already admin', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDto(activeConversation)) {
                expect(component.canBeGrantedChannelAdminRights).toBeFalse(); // is already admin
                expect(component.canBeRevokedChannelAdminRights).toBeTrue();
                checkRevokeAdminButton(true);
                checkGrantAdminButton(false);
            }
        }));

        it('should show grant admin button if user is not yet admin', fakeAsync(() => {
            if (isChannelDto(activeConversation)) {
                component.conversationMember.isChannelAdmin = false;
                fixture.detectChanges();
                tick();
                fixture.detectChanges();

                expect(component.canBeGrantedChannelAdminRights).toBeTrue();
                expect(component.canBeRevokedChannelAdminRights).toBeFalse();
                checkRevokeAdminButton(false);
                checkGrantAdminButton(true);
            }
        }));

        function checkGrantAdminButton(shouldExist: boolean) {
            const grantAdminRightsButton = fixture.debugElement.query(By.css('.grant-admin'));
            if (shouldExist) {
                expect(grantAdminRightsButton).toBeTruthy();
            } else {
                expect(grantAdminRightsButton).toBeFalsy();
            }
        }

        function checkRevokeAdminButton(shouldExist: boolean) {
            const revokeAdminRightsButton = fixture.debugElement.query(By.css('.revoke-admin'));
            if (shouldExist) {
                expect(revokeAdminRightsButton).toBeTruthy();
            } else {
                expect(revokeAdminRightsButton).toBeFalsy();
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

        it('should open the grant channel admin rights dialog', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDto(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const changesPerformedSpy = jest.spyOn(component.changePerformed, 'emit');
                const grantChannelAdminRightsSpy = jest
                    .spyOn(channelService, 'grantChannelAdminRights')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openGrantChannelAdminRightsDialog.bind(component));
                expect(grantChannelAdminRightsSpy).toHaveBeenCalledOnce();
                expect(grantChannelAdminRightsSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        }));

        it('should open the revoke channel admin rights dialog', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDto(activeConversation)) {
                const channelService = TestBed.inject(ChannelService);
                const changesPerformedSpy = jest.spyOn(component.changePerformed, 'emit');
                const revokeChannelAdminRightsSpy = jest
                    .spyOn(channelService, 'revokeChannelAdminRights')
                    .mockReturnValue(of(new HttpResponse({ status: 200 }) as HttpResponse<void>));
                genericConfirmationDialogTest(component.openRevokeChannelAdminRightsDialog.bind(component));
                expect(revokeChannelAdminRightsSpy).toHaveBeenCalledOnce();
                expect(revokeChannelAdminRightsSpy).toHaveBeenCalledWith(course.id!, activeConversation.id!, [conversationMember.login]);
                expect(changesPerformedSpy).toHaveBeenCalledOnce();
            }
        }));

        it('should open the remove from private channel dialog', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            fixture.detectChanges();
            if (isChannelDto(activeConversation)) {
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
            if (isGroupChatDto(activeConversation)) {
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
