import { Component, OnDestroy, OnInit } from '@angular/core';
import { faUserGroup, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { ChannelDTO, getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { EMPTY, Subject, from, takeUntil } from 'rxjs';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { canAddUsersToConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { getAsGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { defaultFirstLayerDialogOptions, getChannelSubTypeReferenceTranslationKey } from 'app/overview/course-conversations/other/conversation.util';
import { catchError } from 'rxjs/operators';
import { MetisService } from 'app/shared/metis/metis.service';

@Component({
    selector: 'jhi-conversation-header',
    templateUrl: './conversation-header.component.html',
    styleUrls: ['./conversation-header.component.scss'],
})
export class ConversationHeaderComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    INFO = ConversationDetailTabs.INFO;
    MEMBERS = ConversationDetailTabs.MEMBERS;

    course: Course;
    activeConversation?: ConversationDto;

    activeConversationAsChannel?: ChannelDTO;
    channelSubTypeReferenceTranslationKey?: string;
    channelSubTypeReferenceRouterLink?: string;

    faUserPlus = faUserPlus;
    faUserGroup = faUserGroup;

    constructor(
        private modalService: NgbModal,
        // instantiated at course-conversation.component.ts
        public metisConversationService: MetisConversationService,
        public conversationService: ConversationService,
        private metisService: MetisService,
    ) {}

    getAsGroupChat = getAsGroupChatDto;

    canAddUsers = canAddUsersToConversation;

    ngOnInit(): void {
        this.course = this.metisConversationService.course!;
        this.subscribeToActiveConversation();
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
            this.activeConversationAsChannel = getAsChannelDto(conversation);
            this.channelSubTypeReferenceTranslationKey = getChannelSubTypeReferenceTranslationKey(this.activeConversationAsChannel?.subType);
            this.channelSubTypeReferenceRouterLink = this.metisService.getLinkForChannelSubType(this.activeConversationAsChannel);
        });
    }

    openAddUsersDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationAddUsersDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.activeConversation = this.activeConversation;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.metisConversationService.forceRefresh().subscribe({
                    complete: () => {},
                });
            });
    }

    openConversationDetailDialog(event: MouseEvent, tab: ConversationDetailTabs) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationDetailDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.activeConversation = this.activeConversation;
        modalRef.componentInstance.selectedTab = tab;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.metisConversationService.forceRefresh().subscribe({
                    complete: () => {},
                });
            });
    }
}
