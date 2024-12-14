import { Component, HostBinding, OnDestroy, OnInit, inject, input, output } from '@angular/core';
import { faEllipsis, faUser, faUserCheck, faUserGear, faUserGraduate } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY, Observable, Subject, from, takeUntil } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { canGrantChannelModeratorRole, canRemoveUsersFromConversation, canRevokeChannelModeratorRole } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { defaultSecondLayerDialogOptions, getUserLabel } from 'app/overview/course-conversations/other/conversation.util';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ChannelDTO, getAsChannelDTO, isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { TranslateService } from '@ngx-translate/core';
import { GenericConfirmationDialogComponent } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { onError } from 'app/shared/util/global.utils';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { getAsGroupChatDTO, isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { catchError } from 'rxjs/operators';

@Component({
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: '[jhi-conversation-member-row]',
    templateUrl: './conversation-member-row.component.html',
    styleUrls: ['./conversation-member-row.component.scss'],
})
export class ConversationMemberRowComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    activeConversation = input<ConversationDTO>();
    course = input<Course>();
    changePerformed = output<void>();
    conversationMember = input<ConversationUserDTO>();

    idOfLoggedInUser: number;

    @HostBinding('class.active')
    isCurrentUser = false;

    isCreator = false;

    canBeRemovedFromConversation = false;

    canBeGrantedChannelModeratorRole = false;

    canBeRevokedChannelModeratorRole = false;

    userLabel: string;
    userName: string | undefined;
    userId: number | undefined;
    userImageUrl: string | undefined;
    // icons
    userIcon: IconProp = faUser;
    userTooltip = '';

    faEllipsis = faEllipsis;
    faUserGear = faUserGear;

    isChannel = isChannelDTO;

    canGrantChannelModeratorRole = canGrantChannelModeratorRole;
    canRevokeChannelModeratorRole = canRevokeChannelModeratorRole;
    canRemoveUsersFromConversation = canRemoveUsersFromConversation;

    private accountService = inject(AccountService);
    private modalService = inject(NgbModal);
    private translateService = inject(TranslateService);
    private channelService = inject(ChannelService);
    private groupChatService = inject(GroupChatService);
    private alertService = inject(AlertService);

    ngOnInit(): void {
        if (this.conversationMember() && this.activeConversation()) {
            this.accountService.identity().then((loggedInUser: User) => {
                this.idOfLoggedInUser = loggedInUser.id!;
                if (this.conversationMember()?.id === this.idOfLoggedInUser) {
                    this.isCurrentUser = true;
                }
                if (this.conversationMember()?.id === this.activeConversation()?.creator?.id) {
                    this.isCreator = true;
                }

                this.userImageUrl = this.conversationMember()?.imageUrl;
                this.userId = this.conversationMember()?.id;
                this.userName = this.conversationMember()?.name;
                this.userLabel = getUserLabel(this.conversationMember()!);
                this.setUserAuthorityIconAndTooltip();
                // the creator of a channel can not be removed from the channel
                this.canBeRemovedFromConversation = !this.isCurrentUser && this.canRemoveUsersFromConversation(this.activeConversation()!);
                if (isChannelDTO(this.activeConversation()!)) {
                    // the creator of a channel can not be removed from the channel
                    this.canBeRemovedFromConversation = this.canBeRemovedFromConversation && !this.isCreator && !(this.activeConversation() as ChannelDTO)!.isCourseWide;
                    this.canBeGrantedChannelModeratorRole = this.canGrantChannelModeratorRole(this.activeConversation()!) && !this.conversationMember()?.isChannelModerator;
                    // the creator of a channel cannot be revoked the channel moderator role
                    this.canBeRevokedChannelModeratorRole =
                        this.canRevokeChannelModeratorRole(this.activeConversation()!) && !this.isCreator && !!this.conversationMember()?.isChannelModerator;
                }
            });
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    openGrantChannelModeratorRoleDialog(event: MouseEvent) {
        event.stopPropagation();
        const channel = getAsChannelDTO(this.activeConversation()!);
        if (!channel) {
            return;
        }
        const translationKeys = {
            titleKey: 'artemisApp.dialogs.grantChannelModerator.title',
            questionKey: 'artemisApp.dialogs.grantChannelModerator.question',
            descriptionKey: 'artemisApp.dialogs.grantChannelModerator.description',
            confirmButtonKey: 'artemisApp.dialogs.grantChannelModerator.confirmButton',
        };
        const translationParams = {
            channelName: channel.name!,
            userName: this.userLabel,
        };
        const confirmedCallback = () => {
            const courseId = this.course?.()?.id;
            const channelId = channel?.id;
            const memberLogin = this.conversationMember?.()?.login;
            if (!courseId || !channelId || !memberLogin) {
                throw new Error('Required parameters are missing');
            }
            return this.channelService.grantChannelModeratorRole(courseId, channelId, [memberLogin]);
        };
        this.openConfirmationDialog(translationKeys, translationParams, confirmedCallback);
    }

    openRevokeChannelModeratorRoleDialog(event: MouseEvent) {
        event.stopPropagation();
        const channel = getAsChannelDTO(this.activeConversation()!);
        if (!channel) {
            return;
        }
        const translationKeys = {
            titleKey: 'artemisApp.dialogs.revokeChannelModerator.title',
            questionKey: 'artemisApp.dialogs.revokeChannelModerator.question',
            descriptionKey: 'artemisApp.dialogs.revokeChannelModerator.description',
            confirmButtonKey: 'artemisApp.dialogs.revokeChannelModerator.confirmButton',
        };
        const translationParams = {
            channelName: channel.name!,
            userName: this.userLabel,
        };
        const confirmedCallback = () => {
            const courseId = this.course?.()?.id;
            const channelId = channel?.id;
            const memberLogin = this.conversationMember?.()?.login;
            if (!courseId || !channelId || !memberLogin) {
                throw new Error('Required parameters are missing');
            }
            return this.channelService.revokeChannelModeratorRole(courseId, channelId, [memberLogin]);
        };
        this.openConfirmationDialog(translationKeys, translationParams, confirmedCallback);
    }

    openRemoveFromChannelDialog(event: MouseEvent) {
        event.stopPropagation();
        const channel = getAsChannelDTO(this.activeConversation()!);
        if (!channel) {
            return;
        }
        let translationKeys: { titleKey: string; questionKey: string; descriptionKey: string; confirmButtonKey: string };
        if (channel.isPublic) {
            translationKeys = {
                titleKey: 'artemisApp.dialogs.removeUserPublicChannel.title',
                questionKey: 'artemisApp.dialogs.removeUserPublicChannel.question',
                descriptionKey: 'artemisApp.dialogs.removeUserPublicChannel.warning',
                confirmButtonKey: 'artemisApp.dialogs.removeUserPublicChannel.remove',
            };
        } else {
            translationKeys = {
                titleKey: 'artemisApp.dialogs.removeUserPrivateChannel.title',
                questionKey: 'artemisApp.dialogs.removeUserPrivateChannel.question',
                descriptionKey: 'artemisApp.dialogs.removeUserPrivateChannel.warning',
                confirmButtonKey: 'artemisApp.dialogs.removeUserPrivateChannel.remove',
            };
        }

        const translationParams = {
            userName: this.userLabel,
            channelName: channel.name!,
        };
        const confirmedCallback = () => {
            const courseId = this.course?.()?.id;
            const activeConversationId = this.activeConversation()?.id;
            const memberLogin = this.conversationMember?.()?.login;
            if (!courseId || !activeConversationId || !memberLogin) {
                throw new Error('Required parameters are missing');
            }
            return this.channelService.deregisterUsersFromChannel(courseId, activeConversationId, [memberLogin]);
        };
        this.openConfirmationDialog(translationKeys, translationParams, confirmedCallback);
    }

    openRemoveFromGroupChatDialog(event: MouseEvent) {
        event.stopPropagation();
        const groupChat = getAsGroupChatDTO(this.activeConversation()!);
        if (!groupChat) {
            return;
        }
        const translationKeys = {
            titleKey: 'artemisApp.dialogs.removeUserGroupChat.title',
            questionKey: 'artemisApp.dialogs.removeUserGroupChat.question',
            descriptionKey: 'artemisApp.dialogs.removeUserGroupChat.warning',
            confirmButtonKey: 'artemisApp.dialogs.removeUserGroupChat.remove',
        };
        const translationParams = {
            userName: this.userLabel,
        };
        const confirmedCallback = () => {
            const courseId = this.course?.()?.id;
            const activeConversationId = this.activeConversation()?.id;
            const memberLogin = this.conversationMember?.()?.login;
            if (!courseId || !activeConversationId || !memberLogin) {
                throw new Error('Required parameters are missing');
            }
            return this.groupChatService.removeUsersFromGroupChat(courseId, activeConversationId, [memberLogin]);
        };
        this.openConfirmationDialog(translationKeys, translationParams, confirmedCallback);
    }

    private openConfirmationDialog(
        translationKeys: { titleKey: string; questionKey: string; descriptionKey: string; confirmButtonKey: string },
        translationParams: { [key: string]: string },
        confirmedCallback: () => Observable<HttpResponse<void>>,
    ) {
        const modalRef: NgbModalRef = this.modalService.open(GenericConfirmationDialogComponent, defaultSecondLayerDialogOptions);
        modalRef.componentInstance.translationParameters = translationParams;
        modalRef.componentInstance.translationKeys = translationKeys;
        modalRef.componentInstance.canBeUndone = true;
        modalRef.componentInstance.isDangerousAction = true;
        modalRef.componentInstance.initialize();

        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                confirmedCallback()
                    .pipe(takeUntil(this.ngUnsubscribe))
                    .subscribe({
                        next: () => {
                            this.changePerformed.emit();
                        },
                        error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                    });
            });
    }

    openRemoveFromConversationDialog(event: MouseEvent) {
        if (isChannelDTO(this.activeConversation()!)) {
            this.openRemoveFromChannelDialog(event);
        } else if (isGroupChatDTO(this.activeConversation()!)) {
            this.openRemoveFromGroupChatDialog(event);
        } else {
            throw new Error('Unsupported conversation type');
        }
    }

    setUserAuthorityIconAndTooltip(): void {
        const toolTipTranslationPath = 'artemisApp.metis.userAuthorityTooltips.';
        // highest authority is displayed
        if (this.conversationMember()?.isInstructor) {
            this.userIcon = faUserGraduate;
            this.userTooltip = this.translateService.instant(toolTipTranslationPath + 'instructor');
        } else if (this.conversationMember()?.isEditor || this.conversationMember()?.isTeachingAssistant) {
            this.userIcon = faUserCheck;
            this.userTooltip = this.translateService.instant(toolTipTranslationPath + 'tutor');
        } else {
            this.userIcon = faUser;
            this.userTooltip = this.translateService.instant(toolTipTranslationPath + 'student');
        }
    }
}
