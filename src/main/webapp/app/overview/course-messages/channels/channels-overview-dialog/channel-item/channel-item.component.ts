import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ChannelOverviewDTO } from 'app/shared/metis/conversations/channel.service';

export type ChannelActionType = 'register' | 'deregister' | 'view';
export type ChannelAction = {
    action: ChannelActionType;
    channel: ChannelOverviewDTO;
};

@Component({
    selector: 'jhi-channel-item',
    templateUrl: './channel-item.component.html',
    styleUrls: ['./channel-item.component.scss'],
})
export class ChannelItemComponent {
    @Output()
    channelAction = new EventEmitter<ChannelAction>();
    @Input()
    channel: ChannelOverviewDTO;

    isHover = false;

    constructor() {}

    emitChannelAction($event: MouseEvent, action: ChannelActionType) {
        $event.stopPropagation();
        this.channelAction.emit({
            action,
            channel: this.channel,
        } as ChannelAction);
    }
}
