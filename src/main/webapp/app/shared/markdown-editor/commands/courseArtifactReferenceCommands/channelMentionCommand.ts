import { InteractiveSearchCommand } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { faHashtag } from '@fortawesome/free-solid-svg-icons';
import { HttpResponse } from '@angular/common/http';
import { Observable, map, of } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';

export class ChannelMentionCommand extends InteractiveSearchCommand {
    buttonIcon = faHashtag;
    cachedResponse: HttpResponse<ChannelDTO[]>;

    constructor(
        private readonly channelService: ChannelService,
        private readonly metisService: MetisService,
    ) {
        super();
    }

    protected getAssociatedInputCharacter(): string {
        return '#';
    }

    performSearch(searchTerm: string): Observable<HttpResponse<ChannelDTO[]>> {
        // all channels are returned within a response. Therefore, the command can cache it
        if (this.cachedResponse) {
            return of(this.filterCachedResponse(searchTerm));
        }
        return this.channelService.getChannelsOfCourse(this.metisService.getCourse().id!).pipe(
            map((response) => {
                this.cachedResponse = response;
                return this.filterCachedResponse(searchTerm);
            }),
        );
    }

    protected selectionToText(selected: ChannelDTO): string {
        return `[channel]${selected['name'] ?? 'empty'}(${selected.id})[/channel]`;
    }

    private filterCachedResponse(searchTerm: string): HttpResponse<ChannelDTO[]> {
        const channels = this.cachedResponse.body!.filter((dto) => dto.name?.includes(searchTerm));
        return new HttpResponse({ body: channels });
    }
}
