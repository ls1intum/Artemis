import { Component, EventEmitter, HostBinding, Input, OnInit, Output } from '@angular/core';
import { faChalkboardTeacher, faEllipsis, faUser, faUserCheck, faUserGear } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { from } from 'rxjs';
import { PrivateChannelRemoveUserDialog } from 'app/overview/course-conversations/dialogs/private-channel-remove-user-dialog/private-channel-remove-user-dialog.component';
import { Course } from 'app/entities/course.model';
import { canRemoveUsersFromConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { getUserLabel } from 'app/overview/course-conversations/other/conversation.util';
import { ConversationUser } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { UserRole } from 'app/shared/metis/metis.util';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { TranslateService } from '@ngx-translate/core';

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
    userRemoved: EventEmitter<void> = new EventEmitter<void>();

    @Input()
    user: ConversationUser;

    userId: number;

    @HostBinding('class.active')
    isCurrentUser = false;

    canDeleteUser = false;

    userLabel: string;
    // icons
    userIcon: IconProp = faUser;
    userTooltip = '';

    faEllipsis = faEllipsis;
    faUserGear = faUserGear;

    isChannel = isChannelDto;

    constructor(private accountService: AccountService, private modalService: NgbModal, private translateService: TranslateService) {}

    ngOnInit(): void {
        if (this.user && this.activeConversation) {
            this.accountService.identity().then((user: User) => {
                this.userId = user.id!;
                if (this.user.id === this.userId) {
                    this.isCurrentUser = true;
                }
                this.userLabel = getUserLabel(this.user);
                this.setUserAuthorityIconAndTooltip();
                this.canDeleteUser = !this.isCurrentUser && canRemoveUsersFromConversation(this.activeConversation);
            });
        }
    }
    openRemoveUserDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(PrivateChannelRemoveUserDialog, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.userToRemove = this.user;
        modalRef.componentInstance.activeConversation = this.activeConversation;
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe(() => {
            this.userRemoved.emit();
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
