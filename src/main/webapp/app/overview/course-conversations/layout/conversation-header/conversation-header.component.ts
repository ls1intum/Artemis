import { Component, OnInit } from '@angular/core';
import { faChevronDown, faUserGroup, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { from } from 'rxjs';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';

@Component({
    selector: 'jhi-conversation-header',
    templateUrl: './conversation-header.component.html',
    styleUrls: ['./conversation-header.component.scss'],
})
export class ConversationHeaderComponent implements OnInit {
    INFO = ConversationDetailTabs.INFO;
    MEMBERS = ConversationDetailTabs.MEMBERS;

    course: Course;
    activeConversation?: ConversationDto;

    faUserPlus = faUserPlus;
    faUserGroup = faUserGroup;
    faChevronDown = faChevronDown;

    constructor(
        private modalService: NgbModal,
        // instantiated at course-conversation.component.ts
        public metisConversationService: MetisConversationService,
        public conversationService: ConversationService,
    ) {}

    getAsChannel = getAsChannelDto;

    getConversationName = this.conversationService.getConversationName;

    ngOnInit(): void {
        this.course = this.metisConversationService.course!;
        this.subscribeToActiveConversation();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
        });
    }

    openAddUsersDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationAddUsersDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.activeConversation = this.activeConversation;
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe(() => {
            this.metisConversationService.forceRefresh().subscribe();
        });
    }

    openConversationDetailDialog(event: MouseEvent, tab: ConversationDetailTabs) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationDetailDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.activeConversation = this.activeConversation;
        modalRef.componentInstance.selectedTab = tab;
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe(() => {
            this.metisConversationService.forceRefresh().subscribe();
        });
    }
}
