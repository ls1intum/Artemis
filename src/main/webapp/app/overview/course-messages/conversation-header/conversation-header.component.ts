import { Component, Input, OnInit } from '@angular/core';
import { faChevronDown, faUserGroup, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { ConversationService } from 'app/shared/metis/conversation.service';
import { ConversationType } from 'app/shared/metis/metis.util';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-messages/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { from, Subject } from 'rxjs';

@Component({
    selector: 'jhi-conversation-header',
    templateUrl: './conversation-header.component.html',
    styleUrls: ['./conversation-header.component.scss'],
})
export class ConversationHeaderComponent implements OnInit {
    readonly CHANNEL = ConversationType.CHANNEL;
    @Input()
    refreshConversations$ = new Subject<void>();
    @Input()
    course: Course;
    @Input()
    activeConversation: Conversation;

    faUserPlus = faUserPlus;
    faUserGroup = faUserGroup;
    faChevronDown = faChevronDown;

    constructor(public conversationService: ConversationService, private modalService: NgbModal) {}

    ngOnInit(): void {}

    openAddUsersDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationAddUsersDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.conversation = this.activeConversation;
        from(modalRef.result).subscribe(() => {
            this.refreshConversations$.next();
        });
    }
}
