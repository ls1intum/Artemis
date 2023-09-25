import { InteractiveSearchCommand } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { faAt } from '@fortawesome/free-solid-svg-icons';
import { ConversationMemberSearchFilter, ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

export class UserMentionCommand extends InteractiveSearchCommand {
    buttonIcon = faAt;

    constructor(
        private readonly conversationService: ConversationService,
        private readonly metisService: MetisService,
    ) {
        super();
    }

    performSearch(searchTerm: string): Observable<HttpResponse<ConversationUserDTO[]>> {
        return this.conversationService.searchMembersOfConversation(this.metisService.getCourse().id!, 487, searchTerm, 0, 10, ConversationMemberSearchFilter.ALL);
    }

    selectionToText(selected: ConversationUserDTO): void {
        this.insertText(`[user]${selected.name}(${selected.login})[/user]`);
    }
}
