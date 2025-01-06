import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ChannelAction, ChannelActionType } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelIconComponent } from 'app/overview/course-conversations/other/channel-icon/channel-icon.component';
import { canJoinChannel, canLeaveConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-channel-item',
    templateUrl: './channel-item.component.html',
    styleUrls: ['./channel-item.component.scss'],
    imports: [ChannelIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class ChannelItemComponent {
    canJoinChannel = canJoinChannel;
    canLeaveConversation = canLeaveConversation;

    @Output()
    channelAction = new EventEmitter<ChannelAction>();
    @Input()
    channel: ChannelDTO;

    emitChannelAction($event: MouseEvent, action: ChannelActionType) {
        $event.stopPropagation();
        this.channelAction.emit({
            action,
            channel: this.channel,
        } as ChannelAction);
    }
}
