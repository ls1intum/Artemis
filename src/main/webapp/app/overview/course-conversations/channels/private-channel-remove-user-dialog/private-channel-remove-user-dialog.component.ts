import { Component, Input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { User } from 'app/core/user/user.model';
import { ChannelDTO, isChannelDto, isPrivateChannel } from 'app/entities/metis/conversation/channel.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-private-channel-remove-user-dialog',
    templateUrl: './private-channel-remove-user-dialog.component.html',
})
export class PrivateChannelRemoveUserDialog {
    @Input()
    set userToRemove(user: User) {
        this._userToRemove = user;
        if (this._userToRemove) {
            this.userLabel = this.getUserLabel(this._userToRemove);
        }
    }
    @Input()
    course: Course;
    @Input()
    set activeConversation(activeConversation: ConversationDto) {
        if (activeConversation && isChannelDto(activeConversation) && isPrivateChannel(activeConversation)) {
            this.privateChannelDTO = activeConversation;
            this.channelName = this.privateChannelDTO.name ?? '';
        }
    }

    _userToRemove: User;
    privateChannelDTO: ChannelDTO;

    userLabel = '';
    channelName = '';

    constructor(private alertService: AlertService, private activeModal: NgbActiveModal, public channelService: ChannelService) {}
    removeUser() {
        const loginsToRemove = this._userToRemove?.login ? [this._userToRemove.login] : [];

        if (this.privateChannelDTO && loginsToRemove.length > 0) {
            this.channelService.deregisterUsersFromChannel(this.course.id!, this.privateChannelDTO.id!, loginsToRemove).subscribe({
                next: () => {
                    this.activeModal.close();
                },
                error: (errorResponse: HttpErrorResponse) => {
                    onError(this.alertService, errorResponse);
                    this.activeModal.close();
                },
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

    clear() {
        this.activeModal.dismiss();
    }
}
