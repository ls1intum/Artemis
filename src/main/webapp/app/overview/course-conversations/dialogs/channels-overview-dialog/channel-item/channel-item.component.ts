import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ChannelAction, ChannelActionType } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { canJoinChannel, canLeaveConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';

@Component({
    selector: 'jhi-channel-item',
    templateUrl: './channel-item.component.html',
    styleUrls: ['./channel-item.component.scss'],
})
export class ChannelItemComponent {
    canJoinChannel = canJoinChannel;
    canLeaveConversation = canLeaveConversation;

    @Output()
    channelAction = new EventEmitter<ChannelAction>();
    @Input()
    channel: ChannelDTO;

    constructor() {}

    emitChannelAction($event: MouseEvent, action: ChannelActionType) {
        $event.stopPropagation();
        this.channelAction.emit({
            action,
            channel: this.channel,
        } as ChannelAction);
    }
}
