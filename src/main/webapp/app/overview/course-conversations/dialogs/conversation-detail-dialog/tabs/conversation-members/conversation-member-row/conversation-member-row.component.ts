import { Component, EventEmitter, HostBinding, Input, OnInit, Output } from '@angular/core';
import { faEllipsis, faUser } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { from } from 'rxjs';
import { PrivateChannelRemoveUserDialog } from 'app/overview/course-conversations/dialogs/private-channel-remove-user-dialog/private-channel-remove-user-dialog.component';
import { Course } from 'app/entities/course.model';

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
    user: User;

    userId: number;

    @HostBinding('class.active')
    isCurrentUser = false;

    canDeleteUser = false;

    userLabel: string;
    // icons
    faUser = faUser;
    faEllipsis = faEllipsis;
    constructor(private accountService: AccountService, private modalService: NgbModal) {}

    ngOnInit(): void {
        if (this.user && this.activeConversation) {
            this.accountService.identity().then((user: User) => {
                this.userId = user.id!;
                if (this.user.id === this.userId) {
                    this.isCurrentUser = true;
                }
                this.userLabel = this.getUserLabel(this.user);
                this.canDeleteUser = isChannelDto(this.activeConversation) && !this.activeConversation.isPublic && !this.isCurrentUser;
            });
        }
    }
    getUserLabel({ firstName, lastName, login }: User) {
        let label = '';
        if (firstName) {
            label += `${firstName} `;
        }
        if (lastName) {
            label += `${lastName} `;
        }
        if (login) {
            label += `(${login})`;
        }
        return label.trim();
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
}
