import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { faEllipsis, faMessage } from '@fortawesome/free-solid-svg-icons';
import { Subject, debounceTime, distinctUntilChanged, from } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { getAsGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';

@Component({
    selector: '[jhi-conversation-entry]',
    templateUrl: './conversation-entry.component.html',
    styleUrls: ['./conversation-entry.component.scss'],
})
export class ConversationEntryComponent implements OnInit {
    hide$ = new Subject<boolean>();
    favorite$ = new Subject<boolean>();

    @Input()
    course: Course;

    @Input()
    conversation: ConversationDto;

    @Input()
    isConversationUnread = false;

    @Input()
    isActive: boolean | undefined = false;

    @Output()
    settingsChanged = new EventEmitter<void>();

    faEllipsis = faEllipsis;
    faMessage = faMessage;
    constructor(public conversationService: ConversationService, private alertService: AlertService, private modalService: NgbModal) {}

    getAsChannel = getAsChannelDto;
    getAsGroupChat = getAsGroupChatDto;
    getConversationName = this.conversationService.getConversationName;

    onHiddenClicked(event: MouseEvent) {
        event.stopPropagation();
        this.hide$.next(!this.conversation.isHidden);
    }

    onFavoriteClicked($event: MouseEvent) {
        $event.stopPropagation();
        this.favorite$.next(!this.conversation.isFavorite);
    }

    openConversationDetailDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationDetailDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.activeConversation = this.conversation;
        modalRef.componentInstance.selectedTab = ConversationDetailTabs.SETTINGS;
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe(() => {
            this.settingsChanged.emit();
        });
    }
    ngOnInit(): void {
        this.hide$.pipe(debounceTime(500), distinctUntilChanged()).subscribe((shouldHide) => {
            this.conversationService.changeHiddenStatus(this.course.id!, this.conversation.id!, shouldHide).subscribe({
                next: () => {
                    this.conversation.isHidden = shouldHide;
                    this.settingsChanged.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
        this.favorite$.pipe(debounceTime(500), distinctUntilChanged()).subscribe((shouldFavorite) => {
            this.conversationService.changeFavoriteStatus(this.course.id!, this.conversation.id!, shouldFavorite).subscribe({
                next: () => {
                    this.conversation.isFavorite = shouldFavorite;
                    this.settingsChanged.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }
}
