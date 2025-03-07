import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewEncapsulation, inject } from '@angular/core';
import { ConversationDTO, shouldNotifyRecipient } from 'app/entities/metis/conversation/conversation.model';
import { ChannelDTO, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { faBoxArchive, faBoxOpen, faEllipsisVertical, faGear, faHeart as faHearthSolid, faVolumeUp, faVolumeXmark } from '@fortawesome/free-solid-svg-icons';
import { faHeart as faHeartRegular } from '@fortawesome/free-regular-svg-icons';
import { EMPTY, Subject, debounceTime, distinctUntilChanged, from, takeUntil } from 'rxjs';
import { catchError, mergeWith } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { getAsGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { isOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { defaultFirstLayerDialogOptions, getChannelSubTypeReferenceTranslationKey } from 'app/overview/course-conversations/other/conversation.util';
import { MetisService } from 'app/shared/metis/metis.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-conversation-options',
    templateUrl: './conversation-options.component.html',
    styleUrls: ['./conversation-options.component.scss'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, NgbDropdown, NgbDropdownToggle, NgbDropdownMenu, NgbDropdownItem, RouterLink, NgbDropdownButtonItem, ArtemisTranslatePipe],
})
export class ConversationOptionsComponent implements OnInit, OnDestroy {
    conversationService = inject(ConversationService);
    private metisService = inject(MetisService);
    private notificationService = inject(NotificationService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);

    private ngUnsubscribe = new Subject<void>();

    favorite$ = new Subject<boolean>();
    hide$ = new Subject<boolean>();
    mute$ = new Subject<boolean>();

    course: Course;

    @Input() conversation: ConversationDTO;

    @Output() onUpdateSidebar = new EventEmitter<void>();

    conversationAsChannel?: ChannelDTO;
    channelSubTypeReferenceTranslationKey?: string;
    channelSubTypeReferenceRouterLink?: string;

    faEllipsisVertical = faEllipsisVertical;
    faHeartSolid = faHearthSolid;
    faHeartRegular = faHeartRegular;
    faBoxArchive = faBoxArchive;
    faBoxOpen = faBoxOpen;
    faVolumeXmark = faVolumeXmark;
    faVolumeUp = faVolumeUp;
    faGear = faGear;

    getAsGroupChat = getAsGroupChatDTO;

    isOneToOneChat = isOneToOneChatDTO;

    ngOnInit(): void {
        this.course = this.metisService.getCourse();
        this.updateConversationIsFavorite();
        this.updateConversationIsHidden();
        this.updateConversationIsMuted();
        this.updateConversationShouldNotifyRecipient();
        this.conversationAsChannel = getAsChannelDTO(this.conversation);
        this.channelSubTypeReferenceTranslationKey = getChannelSubTypeReferenceTranslationKey(this.conversationAsChannel?.subType);
        this.channelSubTypeReferenceRouterLink = this.metisService.getLinkForChannelSubType(this.conversationAsChannel);
    }

    onArchiveClicked(event: MouseEvent) {
        event.stopPropagation();
        if (!this.course.id || !this.conversation.id) {
            return;
        }

        if (!this.conversation.isHidden && this.conversation.isFavorite) {
            this.conversationService.updateIsFavorite(this.course.id, this.conversation.id, false).subscribe({
                next: () => {
                    this.conversation.isFavorite = false;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        }
        this.hide$.next(!this.conversation.isHidden);
    }

    onFavoriteClicked($event: MouseEvent) {
        $event.stopPropagation();
        if (!this.course.id || !this.conversation.id) {
            return;
        }

        if (this.conversation.isHidden && !this.conversation.isFavorite) {
            this.conversationService.updateIsHidden(this.course.id, this.conversation.id, false).subscribe({
                next: () => {
                    this.conversation.isHidden = false;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        }
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
                this.onUpdateSidebar.emit();
            });
    }

    private updateConversationIsFavorite() {
        this.favorite$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isFavorite) => {
            if (!this.course.id || !this.conversation.id) return;

            this.conversationService.updateIsFavorite(this.course.id, this.conversation.id, isFavorite).subscribe({
                next: () => {
                    this.conversation.isFavorite = isFavorite;
                    this.onUpdateSidebar.emit();
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
                    this.onUpdateSidebar.emit();
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
                    this.onUpdateSidebar.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    private updateConversationShouldNotifyRecipient() {
        this.onUpdateSidebar.pipe(mergeWith(this.onUpdateSidebar), takeUntil(this.ngUnsubscribe)).subscribe(() => {
            if (!this.conversation.id) return;

            if (shouldNotifyRecipient(this.conversation)) {
                this.notificationService.unmuteNotificationsForConversation(this.conversation.id);
            } else {
                this.notificationService.muteNotificationsForConversation(this.conversation.id);
            }
        });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
