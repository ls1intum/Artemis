import { Component, EventEmitter, HostBinding, Input, OnInit, Output } from '@angular/core';
import { faChalkboardTeacher, faEllipsis, faUser, faUserCheck, faUserGear } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { from } from 'rxjs';
import { PrivateChannelRemoveUserDialog } from 'app/overview/course-conversations/dialogs/private-channel-remove-user-dialog/private-channel-remove-user-dialog.component';
import { Course } from 'app/entities/course.model';
import { canGrantChannelAdminRights, canRemoveUsersFromConversation, canRevokeChannelAdminRights } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { getUserLabel } from 'app/overview/course-conversations/other/conversation.util';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { getAsChannelDto, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { TranslateService } from '@ngx-translate/core';
import { GenericConfirmationDialog } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { onError } from 'app/shared/util/global.utils';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: '[jhi-conversation-member-row]',
    templateUrl: './conversation-member-row.component.html',
    styleUrls: ['./conversation-member-row.component.scss'],
})
export class ConversationMemberRowComponent implements OnInit {
    @Input()
    activeConversation: ConversationDto;

    @Input()
    course: Course;

    @Output()
    changePerformed: EventEmitter<void> = new EventEmitter<void>();

    @Input()
    user: ConversationUserDTO;

    userId: number;

    @HostBinding('class.active')
    isCurrentUser = false;

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
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        if (this.user && this.activeConversation) {
            this.accountService.identity().then((user: User) => {
                this.userId = user.id!;
                if (this.user.id === this.userId) {
                    this.isCurrentUser = true;
                }
                this.userLabel = getUserLabel(this.user);
                this.setUserAuthorityIconAndTooltip();
                this.canBeDeleted = !this.isCurrentUser && canRemoveUsersFromConversation(this.activeConversation);

                if (isChannelDto(this.activeConversation)) {
                    this.canBeGrantedChannelAdminRights = canGrantChannelAdminRights(this.activeConversation) && !this.user.isChannelAdmin;
                    this.canBeRevokedChannelAdminRights =
                        canRevokeChannelAdminRights(this.activeConversation) && this.activeConversation?.creator?.id !== this.user?.id && !!this.user.isChannelAdmin;
                }
            });
        }
    }

    openGrantChannelAdminRightsDialog(event: MouseEvent) {
        const channel = getAsChannelDto(this.activeConversation);
        if (!channel) {
            return;
        }

        const keys = {
            titleKey: 'artemisApp.pages.makeChannelAdmin.title',
            questionKey: 'artemisApp.pages.makeChannelAdmin.question',
            descriptionKey: 'artemisApp.pages.makeChannelAdmin.description',
            confirmButtonKey: 'artemisApp.pages.makeChannelAdmin.confirmButton',
        };

        const translationParams = {
            channelName: channel.name,
            userName: this.userLabel,
        };

        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(GenericConfirmationDialog, {
            size: 'lg',
            scrollable: false,
            backdrop: 'static',
        });
        modalRef.componentInstance.translationParameters = translationParams;
        modalRef.componentInstance.translationKeys = keys;
        modalRef.componentInstance.canBeUndone = true;
        modalRef.componentInstance.isDangerousAction = true;
        modalRef.componentInstance.initialize();

        from(modalRef.result).subscribe(() => {
            this.channelService.grantChannelAdminRights(this.course?.id!, channel.id!, [this.user.login!]).subscribe({
                next: () => {
                    this.changePerformed.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    openRevokeChannelAdminRightsDialog(event: MouseEvent) {
        const channel = getAsChannelDto(this.activeConversation);
        if (!channel) {
            return;
        }

        const keys = {
            titleKey: 'artemisApp.pages.revokeChannelAdmin.title',
            questionKey: 'artemisApp.pages.revokeChannelAdmin.question',
            descriptionKey: 'artemisApp.pages.revokeChannelAdmin.description',
            confirmButtonKey: 'artemisApp.pages.revokeChannelAdmin.confirmButton',
        };

        const translationParams = {
            channelName: channel.name,
            userName: this.userLabel,
        };

        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(GenericConfirmationDialog, {
            size: 'lg',
            scrollable: false,
            backdrop: 'static',
        });
        modalRef.componentInstance.translationParameters = translationParams;
        modalRef.componentInstance.translationKeys = keys;
        modalRef.componentInstance.canBeUndone = true;
        modalRef.componentInstance.isDangerousAction = true;
        modalRef.componentInstance.initialize();

        from(modalRef.result).subscribe(() => {
            this.channelService.revokeChannelAdminRights(this.course?.id!, channel.id!, [this.user.login!]).subscribe({
                next: () => {
                    this.changePerformed.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    openRemoveUserDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(PrivateChannelRemoveUserDialog, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.userToRemove = this.user;
        modalRef.componentInstance.activeConversation = this.activeConversation;
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe(() => {
            this.changePerformed.emit();
        });
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
