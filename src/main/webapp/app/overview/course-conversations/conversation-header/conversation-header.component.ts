import { Component, OnInit } from '@angular/core';
import { faChevronDown, faUserGroup, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { ConversationDetailDialogComponent, ConversationDetailTabs } from 'app/overview/course-conversations/conversation-detail-dialog/conversation-detail-dialog.component';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getConversationName } from 'app/shared/metis/conversations/conversation.util';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';

@Component({
    selector: 'jhi-conversation-header',
    templateUrl: './conversation-header.component.html',
    styleUrls: ['./conversation-header.component.scss'],
})
export class ConversationHeaderComponent implements OnInit {
    course: Course;
    activeConversation?: ConversationDto;

    faUserPlus = faUserPlus;
    faUserGroup = faUserGroup;
    faChevronDown = faChevronDown;

    constructor(
        private modalService: NgbModal,
        // instantiated at course-conversation.component.ts
        public metisConversationService: MetisConversationService,
    ) {}

    getAsChannel = getAsChannelDto;

    getConversationName = getConversationName;

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
        modalRef.componentInstance.metisConversationService = this.metisConversationService;
    }

    openConversationDetailDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationDetailDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.selectedTab = ConversationDetailTabs.MEMBERS;
        modalRef.componentInstance.metisConversationService = this.metisConversationService;
    }
}
