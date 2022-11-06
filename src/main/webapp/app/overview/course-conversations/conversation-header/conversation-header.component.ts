import { Component, Input, OnInit } from '@angular/core';
import { faChevronDown, faUserGroup, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { from, Subject } from 'rxjs';
import { ConversationDetailDialogComponent, ConversationDetailTabs } from 'app/overview/course-conversations/conversation-detail-dialog/conversation-detail-dialog.component';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getConversationName } from 'app/shared/metis/conversations/conversation.util';

@Component({
    selector: 'jhi-conversation-header',
    templateUrl: './conversation-header.component.html',
    styleUrls: ['./conversation-header.component.scss'],
})
export class ConversationHeaderComponent implements OnInit {
    @Input()
    refreshConversations$ = new Subject<void>();
    @Input()
    course: Course;
    @Input()
    activeConversation: ConversationDto;

    faUserPlus = faUserPlus;
    faUserGroup = faUserGroup;
    faChevronDown = faChevronDown;

    constructor(public conversationService: ConversationService, private modalService: NgbModal) {}

    getAsChannel = getAsChannelDto;

    getConversationName = getConversationName;

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

    openConversationDetailDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationDetailDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.selectedTab = ConversationDetailTabs.MEMBERS;
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.conversation = this.activeConversation;
        from(modalRef.result).subscribe(() => {
            this.refreshConversations$.next();
        });
    }
}
