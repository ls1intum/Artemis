import { Component, Input } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { isChannelDto, isPrivateChannel } from 'app/entities/metis/conversation/channel.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { getUserLabel } from '../../other/conversation.util';

@Component({
    selector: 'jhi-private-channel-remove-user-dialog',
    templateUrl: './private-channel-remove-user-dialog.component.html',
})
export class PrivateChannelRemoveUserDialog {
    @Input()
    userToRemove: ConversationUserDTO;
    @Input()
    course: Course;
    @Input()
    activeConversation: ConversationDto;

    isInitialized = false;

    getUserLabel = getUserLabel;

    initialize() {
        if (!this.course || !this.activeConversation || !this.userToRemove || !isChannelDto(this.activeConversation) || !isPrivateChannel(this.activeConversation)) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
            this.channelName = this.activeConversation.name ?? '';
            this.userLabel = this.getUserLabel(this.userToRemove);
        }
    }
    userLabel = '';
    channelName = '';

    constructor(private alertService: AlertService, private activeModal: NgbActiveModal, public channelService: ChannelService) {}
    removeUser() {
        const loginsToRemove = this.userToRemove?.login ? [this.userToRemove.login] : [];

        if (loginsToRemove.length > 0) {
            this.channelService.deregisterUsersFromChannel(this.course.id!, this.activeConversation.id!, loginsToRemove).subscribe({
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

    clear() {
        this.activeModal.dismiss();
    }
}
