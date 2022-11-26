import { Component, EventEmitter, HostBinding, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { faChalkboardTeacher, faEllipsis, faUser, faUserCheck, faUserGear } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Observable, Subject, from, takeUntil } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { canGrantChannelAdminRights, canRemoveUsersFromConversation, canRevokeChannelAdminRights } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { defaultSecondLayerDialogOptions, getUserLabel } from 'app/overview/course-conversations/other/conversation.util';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { getAsChannelDto, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { TranslateService } from '@ngx-translate/core';
import { GenericConfirmationDialogComponent } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { onError } from 'app/shared/util/global.utils';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { getAsGroupChatDto, isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';

@Component({
    selector: '[jhi-conversation-member-row]',
    templateUrl: './conversation-member-row.component.html',
    styleUrls: ['./conversation-member-row.component.scss'],
})
export class ConversationMemberRowComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    @Input()
    activeConversation: ConversationDto;

    @Input()
    course: Course;

    @Output()
    changePerformed: EventEmitter<void> = new EventEmitter<void>();

    @Input()
    user: ConversationUserDTO;

    idOfLoggedInUser: number;

    @HostBinding('class.active')
    isCurrentUser = false;

    isCreator = false;

    canBeDeleted = false;

    canBeGrantedChannelAdminRights = false;

    canBeRevokedChannelAdminRights = false;

    userLabel: string;
    // icons
    userIcon: IconProp = faUser;
    userTooltip = '';

    faEllipsis = faEllipsis;
    faUserGear = faUserGear;

    isChannel = isChannelDto;

    constructor(
        private accountService: AccountService,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private channelService: ChannelService,
        private groupChatService: GroupChatService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        if (this.user && this.activeConversation) {
            this.accountService.identity().then((loggedInUser: User) => {
                this.idOfLoggedInUser = loggedInUser.id!;
                if (this.user.id === this.idOfLoggedInUser) {
                    this.isCurrentUser = true;
                }
                if (this.user.id === this.activeConversation?.creator?.id) {
                    this.isCreator = true;
                }

                this.userLabel = getUserLabel(this.user);
                this.setUserAuthorityIconAndTooltip();
                this.canBeDeleted = !this.isCurrentUser && !this.isCreator && canRemoveUsersFromConversation(this.activeConversation);

                if (isChannelDto(this.activeConversation)) {
                    this.canBeGrantedChannelAdminRights = canGrantChannelAdminRights(this.activeConversation) && !this.user.isChannelAdmin;
                    this.canBeRevokedChannelAdminRights =
                        canRevokeChannelAdminRights(this.activeConversation) && this.activeConversation?.creator?.id !== this.user?.id && !!this.user.isChannelAdmin;
                }
            });
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    openGrantChannelAdminRightsDialog(event: MouseEvent) {
        event.stopPropagation();
        const channel = getAsChannelDto(this.activeConversation);
        if (!channel) {
            return;
        }
        const translationKeys = {
            titleKey: 'artemisApp.pages.makeChannelAdmin.title',
            questionKey: 'artemisApp.pages.makeChannelAdmin.question',
            descriptionKey: 'artemisApp.pages.makeChannelAdmin.description',
            confirmButtonKey: 'artemisApp.pages.makeChannelAdmin.confirmButton',
        };
        const translationParams = {
            channelName: channel.name!,
            userName: this.userLabel,
        };
        const confirmedCallback = () => this.channelService.grantChannelAdminRights(this.course?.id!, channel.id!, [this.user.login!]);
        this.openConfirmationDialog(translationKeys, translationParams, confirmedCallback);
    }

    openRevokeChannelAdminRightsDialog(event: MouseEvent) {
        event.stopPropagation();
        const channel = getAsChannelDto(this.activeConversation);
        if (!channel) {
            return;
        }
        const translationKeys = {
            titleKey: 'artemisApp.pages.revokeChannelAdmin.title',
            questionKey: 'artemisApp.pages.revokeChannelAdmin.question',
            descriptionKey: 'artemisApp.pages.revokeChannelAdmin.description',
            confirmButtonKey: 'artemisApp.pages.revokeChannelAdmin.confirmButton',
        };
        const translationParams = {
            channelName: channel.name!,
            userName: this.userLabel,
        };
        const confirmedCallback = () => this.channelService.revokeChannelAdminRights(this.course?.id!, channel.id!, [this.user.login!]);
        this.openConfirmationDialog(translationKeys, translationParams, confirmedCallback);
    }

    openRemoveFromPrivateChannelDialog(event: MouseEvent) {
        event.stopPropagation();
        const channel = getAsChannelDto(this.activeConversation);
        if (!channel) {
            return;
        }
        const translationKeys = {
            titleKey: 'artemisApp.messages.removeUserPrivateChannel.title',
            questionKey: 'artemisApp.messages.removeUserPrivateChannel.question',
            descriptionKey: 'artemisApp.messages.removeUserPrivateChannel.warning',
            confirmButtonKey: 'artemisApp.messages.removeUserPrivateChannel.remove',
        };
        const translationParams = {
            userName: this.userLabel,
            channelName: channel.name!,
        };
        const confirmedCallback = () => this.channelService.deregisterUsersFromChannel(this.course.id!, this.activeConversation.id!, [this.user.login!]);
        this.openConfirmationDialog(translationKeys, translationParams, confirmedCallback);
    }

    openRemoveFromGroupChatDialog(event: MouseEvent) {
        event.stopPropagation();
        const groupChat = getAsGroupChatDto(this.activeConversation);
        if (!groupChat) {
            return;
        }
        const translationKeys = {
            titleKey: 'artemisApp.messages.removeUserGroupChat.title',
            questionKey: 'artemisApp.messages.removeUserGroupChat.question',
            descriptionKey: 'artemisApp.messages.removeUserGroupChat.warning',
            confirmButtonKey: 'artemisApp.messages.removeUserGroupChat.remove',
        };
        const translationParams = {
            userName: this.userLabel,
        };
        const confirmedCallback = () => this.groupChatService.removeUsersFromGroupChat(this.course.id!, this.activeConversation.id!, [this.user.login!]);
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
            .pipe(takeUntil(this.ngUnsubscribe))
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
        if (isChannelDto(this.activeConversation)) {
            this.openRemoveFromPrivateChannelDialog(event);
        } else if (isGroupChatDto(this.activeConversation)) {
            this.openRemoveFromGroupChatDialog(event);
        } else {
            throw new Error('Unsupported conversation type');
        }
    }

    setUserAuthorityIconAndTooltip(): void {
        const toolTipTranslationPath = 'artemisApp.metis.userAuthorityTooltips.';
        // highest authority is displayed
        if (this.user.isInstructor) {
            this.userIcon = faChalkboardTeacher;
            this.userTooltip = this.translateService.instant(toolTipTranslationPath + 'instructor');
        } else if (this.user.isEditor || this.user.isTeachingAssistant) {
            this.userIcon = faUserCheck;
            this.userTooltip = this.translateService.instant(toolTipTranslationPath + 'ta');
        } else {
            this.userIcon = faUser;
            this.userTooltip = this.translateService.instant(toolTipTranslationPath + 'student');
        }
    }
}
