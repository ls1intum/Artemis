import { InteractiveSearchCommand } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { faAt } from '@fortawesome/free-solid-svg-icons';
import { MetisService } from 'app/shared/metis/metis.service';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/course-management.service';

export class UserMentionCommand extends InteractiveSearchCommand {
    buttonIcon = faAt;

    constructor(
        private readonly courseManagementService: CourseManagementService,
        private readonly metisService: MetisService,
    ) {
        super();
    }

    protected getAssociatedInputCharacter(): string {
        return '@';
    }

    performSearch(searchTerm: string): Observable<HttpResponse<ConversationUserDTO[]>> {
        return this.courseManagementService.searchMembersForUserMentions(this.metisService.getCourse().id!, searchTerm);
    }

    protected selectionToText(selected: ConversationUserDTO): string {
        return `[user]${selected.name}(${selected.login})[/user]`;
    }
}
