import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { ChannelDTO, getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { faEllipsis } from '@fortawesome/free-solid-svg-icons';
import { EMPTY, Subject, debounceTime, distinctUntilChanged, from, takeUntil } from 'rxjs';
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
import { isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { defaultFirstLayerDialogOptions, getChannelSubTypeReferenceTranslationKey } from 'app/overview/course-conversations/other/conversation.util';
import { catchError } from 'rxjs/operators';
import { MetisService } from 'app/shared/metis/metis.service';

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
    conversation: ConversationDto;

    @Input()
    activeConversation: ConversationDto | undefined;

    @Output()
    settingsChanged = new EventEmitter<void>();

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

    get isMutedConversation() {
        return this.conversation.isMuted;
    }

    getAsGroupChat = getAsGroupChatDto;

    isOneToOneChat = isOneToOneChatDto;

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
                this.settingsChanged.emit();
            });
    }
    ngOnInit(): void {
        this.favorite$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isFavorite) => {
            this.conversationService.updateIsFavorite(this.course.id!, this.conversation.id!, isFavorite).subscribe({
                next: () => {
                    this.conversation.isFavorite = isFavorite;
                    this.conversationIsFavoriteDidChange.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
        this.hide$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isHidden) => {
            this.conversationService.updateIsHidden(this.course.id!, this.conversation.id!, isHidden).subscribe({
                next: () => {
                    this.conversation.isHidden = isHidden;
                    this.conversationIsHiddenDidChange.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
        this.mute$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isMuted) => {
            this.conversationService.updateIsMuted(this.course.id!, this.conversation.id!, isMuted).subscribe({
                next: () => {
                    this.conversation.isMuted = isMuted;
                    this.conversationIsMutedDidChange.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
        this.conversationAsChannel = getAsChannelDto(this.conversation);
        this.channelSubTypeReferenceTranslationKey = getChannelSubTypeReferenceTranslationKey(this.conversationAsChannel?.subType);
        this.channelSubTypeReferenceRouterLink = this.metisService.getLinkForChannelSubType(this.conversationAsChannel);
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
