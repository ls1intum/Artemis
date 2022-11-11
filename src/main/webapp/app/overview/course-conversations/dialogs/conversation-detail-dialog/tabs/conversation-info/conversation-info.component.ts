import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getAsGroupChatDto } from 'app/entities/metis/conversation/groupChat.model';
import { getUserLabel } from 'app/overview/course-conversations/other/conversation.util';
import { ChannelAction } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-conversation-info',
    templateUrl: './conversation-info.component.html',
    styleUrls: ['./conversation-info.component.scss'],
})
export class ConversationInfoComponent implements OnInit {
    getAsChannel = getAsChannelDto;
    getUserLabel = getUserLabel;

    @Input()
    activeConversation: ConversationDto;

    @Input()
    course: Course;

    @Output()
    channelLeave: EventEmitter<void> = new EventEmitter<void>();

    constructor(private channelService: ChannelService) {}

    ngOnInit(): void {}

    leaveChannel($event: MouseEvent) {
        $event.stopPropagation();
        this.channelService.deregisterUsersFromChannel(this.course?.id!, this.activeConversation.id!).subscribe(() => {
            this.channelLeave.emit();
        });
    }
}
