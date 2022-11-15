import { Component, OnInit } from '@angular/core';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-group-chat-icon',
    templateUrl: './group-chat-icon.component.html',
    styleUrls: ['./group-chat-icon.component.scss'],
})
export class GroupChatIconComponent implements OnInit {
    faPeopleGroup = faPeopleGroup;
    constructor() {}

    ngOnInit(): void {}
}
