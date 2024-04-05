import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { ConversationDTO, shouldNotifyRecipient } from 'app/entities/metis/conversation/conversation.model';
import { ChannelDTO, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { faEllipsis } from '@fortawesome/free-solid-svg-icons';
import { EMPTY, Subject, debounceTime, distinctUntilChanged, from, takeUntil } from 'rxjs';
import { mergeWith } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { getAsGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { isOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { defaultFirstLayerDialogOptions, getChannelSubTypeReferenceTranslationKey } from 'app/overview/course-conversations/other/conversation.util';
import { catchError } from 'rxjs/operators';
import { MetisService } from 'app/shared/metis/metis.service';
import { NotificationService } from 'app/shared/notification/notification.service';

@Component({
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: '[jhi-conversation-sidebar-entry]',
    templateUrl: './conversation-sidebar-entry.component.html',
    styleUrls: ['./conversation-sidebar-entry.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ConversationSidebarEntryComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    favorite$ = new Subject<boolean>();
    hide$ = new Subject<boolean>();
    mute$ = new Subject<boolean>();

    @Input()
    course: Course;

    @Input()
    conversation: ConversationDTO;

    @Input()
    activeConversation: ConversationDTO | undefined;

    @Output()
    settingsDidChange = new EventEmitter<void>();

    @Output()
    conversationIsFavoriteDidChange = new EventEmitter<void>();

    @Output()
    conversationIsHiddenDidChange = new EventEmitter<void>();

    @Output()
    conversationIsMutedDidChange = new EventEmitter<void>();

    conversationAsChannel?: ChannelDTO;
    channelSubTypeReferenceTranslationKey?: string;
    channelSubTypeReferenceRouterLink?: string;

    faEllipsis = faEllipsis;

    constructor(
        public conversationService: ConversationService,
        private metisService: MetisService,
        private notificationService: NotificationService,
        private alertService: AlertService,
        private modalService: NgbModal,
    ) {}

    get isConversationUnread(): boolean {
        // do not show unread count for open conversation that the user is currently reading
        if (this.isActiveConversation || !this.conversation) {
            return false;
        } else {
            return !!this.conversation.unreadMessagesCount && this.conversation.unreadMessagesCount > 0;
        }
    }

    get isActiveConversation() {
        return this.activeConversation && this.conversation && this.activeConversation.id! === this.conversation.id!;
    }

    getAsGroupChat = getAsGroupChatDTO;

    isOneToOneChat = isOneToOneChatDTO;

    onHiddenClicked(event: MouseEvent) {
        event.stopPropagation();
        this.hide$.next(!this.conversation.isHidden);
    }

    onFavoriteClicked($event: MouseEvent) {
        $event.stopPropagation();
        this.favorite$.next(!this.conversation.isFavorite);
    }

    onMuteClicked($event: MouseEvent) {
        $event.stopPropagation();
        this.mute$.next(!this.conversation.isMuted);
    }

    openConversationDetailDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationDetailDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.activeConversation = this.conversation;
        modalRef.componentInstance.selectedTab = ConversationDetailTabs.SETTINGS;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.settingsDidChange.emit();
            });
    }

    private updateConversationIsFavorite() {
        this.favorite$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isFavorite) => {
            if (!this.course.id || !this.conversation.id) return;

            this.conversationService.updateIsFavorite(this.course.id, this.conversation.id, isFavorite).subscribe({
                next: () => {
                    this.conversation.isFavorite = isFavorite;
                    this.conversationIsFavoriteDidChange.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    private updateConversationIsHidden() {
        this.hide$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isHidden) => {
            if (!this.course.id || !this.conversation.id) return;

            this.conversationService.updateIsHidden(this.course.id, this.conversation.id, isHidden).subscribe({
                next: () => {
                    this.conversation.isHidden = isHidden;
                    this.conversationIsHiddenDidChange.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    private updateConversationIsMuted() {
        this.mute$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isMuted) => {
            if (!this.course.id || !this.conversation.id) return;

            this.conversationService.updateIsMuted(this.course.id, this.conversation.id, isMuted).subscribe({
                next: () => {
                    this.conversation.isMuted = isMuted;
                    this.conversationIsMutedDidChange.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    private updateConversationShouldNotifyRecipient() {
        this.conversationIsHiddenDidChange.pipe(mergeWith(this.conversationIsMutedDidChange), takeUntil(this.ngUnsubscribe)).subscribe(() => {
            if (!this.conversation.id) return;

            if (shouldNotifyRecipient(this.conversation)) {
                this.notificationService.unmuteNotificationsForConversation(this.conversation.id);
            } else {
                this.notificationService.muteNotificationsForConversation(this.conversation.id);
            }
        });
    }

    ngOnInit(): void {
        this.updateConversationIsFavorite();
        this.updateConversationIsHidden();
        this.updateConversationIsMuted();
        this.updateConversationShouldNotifyRecipient();
        this.conversationAsChannel = getAsChannelDTO(this.conversation);
        this.channelSubTypeReferenceTranslationKey = getChannelSubTypeReferenceTranslationKey(this.conversationAsChannel?.subType);
        this.channelSubTypeReferenceRouterLink = this.metisService.getLinkForChannelSubType(this.conversationAsChannel);
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
