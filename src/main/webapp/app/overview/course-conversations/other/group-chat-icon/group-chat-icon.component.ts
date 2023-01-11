import { Component } from '@angular/core';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-group-chat-icon',
    templateUrl: './group-chat-icon.component.html',
})
export class GroupChatIconComponent {
    // icons
    faPeopleGroup = faPeopleGroup;
}
