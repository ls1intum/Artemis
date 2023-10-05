import { InteractiveSearchCommand } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { faHashtag } from '@fortawesome/free-solid-svg-icons';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';

export class ChannelMentionCommand extends InteractiveSearchCommand {
    buttonIcon = faHashtag;

    constructor(
        private readonly channelService: ChannelService,
        private readonly metisService: MetisService,
    ) {
        super();
    }

    protected getAssociatedInputCharacter(): string {
        return '#';
    }

    performSearch(): Observable<HttpResponse<ChannelDTO[]>> {
        return this.channelService.getChannelsOfCourse(this.metisService.getCourse().id!);
    }

    protected selectionToText(selected: ChannelDTO): string {
        return `[channel]${selected['name'] ?? 'empty'}(${selected.id})[/channel]`;
    }
}
