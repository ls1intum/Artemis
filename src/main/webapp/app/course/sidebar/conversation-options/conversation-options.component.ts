import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewEncapsulation, inject, input, output, signal } from '@angular/core';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { ChannelDTO, getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { faBoxArchive } from '@fortawesome/free-solid-svg-icons/faBoxArchive';
import { faBoxOpen } from '@fortawesome/free-solid-svg-icons/faBoxOpen';
import { faEllipsisVertical } from '@fortawesome/free-solid-svg-icons/faEllipsisVertical';
import { faGear } from '@fortawesome/free-solid-svg-icons/faGear';
import { faHeart as faHearthSolid } from '@fortawesome/free-solid-svg-icons/faHeart';
import { faVolumeUp } from '@fortawesome/free-solid-svg-icons/faVolumeUp';
import { faVolumeXmark } from '@fortawesome/free-solid-svg-icons/faVolumeXmark';
import { faHeart as faHeartRegular } from '@fortawesome/free-regular-svg-icons/faHeart';
import { Subject, debounceTime, distinctUntilChanged, filter, takeUntil } from 'rxjs';
import { Course } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { getAsGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogService } from '@tumaet/ui-angular/dialog';
import { TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective } from '@tumaet/ui-angular/menu';

import { isOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { defaultFirstLayerDialogOptions, getChannelSubTypeReferenceTranslationKey } from 'app/communication/course-conversations-components/other/conversation.util';
import { CommunicationService } from 'app/communication/service/communication.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-conversation-options',
    templateUrl: './conversation-options.component.html',
    styleUrls: ['./conversation-options.component.css'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective, RouterLink, ArtemisTranslatePipe],
})
export class ConversationOptionsComponent implements OnInit, OnDestroy {
    conversationService = inject(ConversationService);
    private communicationService = inject(CommunicationService);
    private alertService = inject(AlertService);
    private dialogService = inject(TumAetUiDialogService);
    private translateService = inject(TranslateService);

    private ngUnsubscribe = new Subject<void>();

    favorite$ = new Subject<boolean>();
    hide$ = new Subject<boolean>();
    mute$ = new Subject<boolean>();

    course!: Course; // set in ngOnInit() from communicationService.getCourse()

    readonly conversation = input.required<ConversationDTO>();

    readonly onUpdateSidebar = output<void>();

    readonly conversationAsChannel = signal<ChannelDTO | undefined>(undefined);
    readonly channelSubTypeReferenceTranslationKey = signal<string | undefined>(undefined);
    readonly channelSubTypeReferenceRouterLink = signal<string | undefined>(undefined);

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
        this.course = this.communicationService.getCourse();
        this.updateConversationIsFavorite();
        this.updateConversationIsHidden();
        this.updateConversationIsMuted();
        const conversationAsChannel = getAsChannelDTO(this.conversation());
        this.conversationAsChannel.set(conversationAsChannel);
        this.channelSubTypeReferenceTranslationKey.set(getChannelSubTypeReferenceTranslationKey(conversationAsChannel?.subType));
        this.channelSubTypeReferenceRouterLink.set(this.communicationService.getLinkForChannelSubType(conversationAsChannel));
    }

    onArchiveClicked() {
        const conversation = this.conversation();
        if (!this.course.id || !conversation.id) {
            return;
        }

        if (!conversation.isHidden && conversation.isFavorite) {
            this.conversationService.updateIsFavorite(this.course.id, conversation.id, false).subscribe({
                next: () => {
                    conversation.isFavorite = false;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        }
        this.hide$.next(!conversation.isHidden);
    }

    onFavoriteClicked($event: MouseEvent) {
        $event.stopPropagation();
        const conversation = this.conversation();
        if (!this.course.id || !conversation.id) {
            return;
        }

        if (conversation.isHidden && !conversation.isFavorite) {
            this.conversationService.updateIsHidden(this.course.id, conversation.id, false).subscribe({
                next: () => {
                    conversation.isHidden = false;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        }
        this.favorite$.next(!conversation.isFavorite);
    }

    onMuteClicked() {
        this.mute$.next(!this.conversation().isMuted);
    }

    openConversationDetailDialog() {
        const conversation = this.conversation();
        const ref = this.dialogService.open(
            ConversationDetailDialogComponent,
            cloneWith(defaultFirstLayerDialogOptions, {
                ariaLabel: this.conversationService.getConversationName(conversation, true) || this.translateService.instant('artemisApp.dialogs.conversationDetail.tabs.settings'),
                data: {
                    course: this.course,
                    activeConversation: conversation,
                    selectedTab: ConversationDetailTabs.SETTINGS,
                },
            }),
        );
        ref.onClose
            .pipe(
                filter((result) => !!result),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.onUpdateSidebar.emit();
            });
    }

    private updateConversationIsFavorite() {
        this.favorite$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isFavorite) => {
            const conversation = this.conversation();
            if (!this.course.id || !conversation.id) return;

            this.conversationService.updateIsFavorite(this.course.id, conversation.id, isFavorite).subscribe({
                next: () => {
                    conversation.isFavorite = isFavorite;
                    this.onUpdateSidebar.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    private updateConversationIsHidden() {
        this.hide$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isHidden) => {
            const conversation = this.conversation();
            if (!this.course.id || !conversation.id) return;

            this.conversationService.updateIsHidden(this.course.id, conversation.id, isHidden).subscribe({
                next: () => {
                    conversation.isHidden = isHidden;
                    this.onUpdateSidebar.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    private updateConversationIsMuted() {
        this.mute$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isMuted) => {
            const conversation = this.conversation();
            if (!this.course.id || !conversation.id) return;

            this.conversationService.updateIsMuted(this.course.id, conversation.id, isMuted).subscribe({
                next: () => {
                    conversation.isMuted = isMuted;
                    this.onUpdateSidebar.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
