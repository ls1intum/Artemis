import { ChangeDetectorRef, Component, OnDestroy, OnInit, effect, inject, input, output, untracked } from '@angular/core';
import { faChevronLeft, faPeopleGroup, faSearch, faUserGroup, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { DialogService } from 'primeng/dynamicdialog';
import { Course } from 'app/core/course/shared/entities/course.model';

import { ChannelDTO, getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { Subject, filter, takeUntil } from 'rxjs';
import { getAsGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { defaultFirstLayerDialogOptions, getChannelSubTypeReferenceTranslationKey } from 'app/communication/course-conversations-components/other/conversation.util';

import { MetisService } from 'app/communication/service/metis.service';
import { CourseSidebarService } from 'app/core/course/overview/services/course-sidebar.service';
import { getAsOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ChannelIconComponent } from 'app/communication/course-conversations-components/other/channel-icon/channel-icon.component';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { EmojiComponent } from 'app/communication/emoji/emoji.component';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { canAddUsersToConversation } from 'app/communication/conversations/conversation-permissions.utils';
import { ConversationAddUsersDialogComponent } from 'app/communication/course-conversations-components/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';

@Component({
    selector: 'jhi-conversation-header',
    templateUrl: './conversation-header.component.html',
    styleUrls: ['./conversation-header.component.scss'],
    imports: [FaIconComponent, ChannelIconComponent, ProfilePictureComponent, TranslateDirective, RouterLink, EmojiComponent, ArtemisTranslatePipe],
})
export class ConversationHeaderComponent implements OnInit, OnDestroy {
    constructor() {
        effect(() => {
            // Track pinnedMessageCount signal input (replaces ngOnChanges)
            const currentCount = this.pinnedMessageCount();
            untracked(() => {
                if (this.showPinnedMessages && currentCount === 0) {
                    this.showPinnedMessages = false;
                    this.cdr.detectChanges();
                }
            });
        });
    }

    private dialogService = inject(DialogService);
    metisConversationService = inject(MetisConversationService);
    conversationService = inject(ConversationService);
    private metisService = inject(MetisService);
    pinnedMessageCount = input<number>(0);
    togglePinnedMessage = output<void>();

    private ngUnsubscribe = new Subject<void>();

    onUpdateSidebar = output<void>();
    onSearchClick = output<void>();

    INFO = ConversationDetailTabs.INFO;
    MEMBERS = ConversationDetailTabs.MEMBERS;

    course: Course;
    activeConversation?: ConversationDTO;

    activeConversationAsChannel?: ChannelDTO;
    channelSubTypeReferenceTranslationKey?: string;
    channelSubTypeReferenceRouterLink?: string;
    otherUser?: ConversationUserDTO;

    faUserPlus = faUserPlus;
    faUserGroup = faUserGroup;
    faSearch = faSearch;
    faChevronLeft = faChevronLeft;
    readonly faPeopleGroup = faPeopleGroup;
    showPinnedMessages: boolean = false;

    private courseSidebarService: CourseSidebarService = inject(CourseSidebarService);
    private cdr = inject(ChangeDetectorRef);

    getAsGroupChat = getAsGroupChatDTO;
    getAsOneToOneChat = getAsOneToOneChatDTO;

    canAddUsers = canAddUsersToConversation;

    ngOnInit(): void {
        this.course = this.metisConversationService.course!;
        this.subscribeToActiveConversation();
    }

    /**
     * Toggle the view of pinned messages in the conversation
     */
    togglePinnedMessages(): void {
        this.togglePinnedMessage.emit();
        this.showPinnedMessages = !this.showPinnedMessages;
        this.cdr.detectChanges();
    }

    /**
     * Gets the other user in a one-to-one chat (not the current user)
     */
    getOtherUser() {
        const conversation = getAsOneToOneChatDTO(this.activeConversation);
        if (conversation) {
            this.otherUser = conversation.members?.find((user) => !user.isRequestingUser);
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    openSidebar() {
        this.courseSidebarService.openSidebar();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDTO) => {
            this.activeConversation = conversation;
            this.activeConversationAsChannel = getAsChannelDTO(conversation);
            this.channelSubTypeReferenceTranslationKey = getChannelSubTypeReferenceTranslationKey(this.activeConversationAsChannel?.subType);
            this.channelSubTypeReferenceRouterLink = this.metisService.getLinkForChannelSubType(this.activeConversationAsChannel);
            this.getOtherUser();
        });
    }

    openAddUsersDialog(event: MouseEvent) {
        event.stopPropagation();
        const ref = this.dialogService.open(ConversationAddUsersDialogComponent, {
            ...defaultFirstLayerDialogOptions,
            data: {
                course: this.course,
                activeConversation: this.activeConversation,
            },
        });
        ref?.onClose
            .pipe(
                filter((result) => !!result),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.metisConversationService.forceRefresh().subscribe({
                    complete: () => {},
                });
            });
    }

    /**
     * Opens a dialog showing detailed information about the active conversation,
     * such as metadata or member list, depending on the selected tab.
     * If the conversation is a one-to-one chat, it always defaults to the info tab.
     */
    openConversationDetailDialog(event: MouseEvent, tab: ConversationDetailTabs) {
        event.stopPropagation();
        const selectedTab = this.getAsOneToOneChat(this.activeConversation) ? ConversationDetailTabs.INFO : tab;
        const ref = this.dialogService.open(ConversationDetailDialogComponent, {
            ...defaultFirstLayerDialogOptions,
            data: {
                course: this.course,
                activeConversation: this.activeConversation,
                selectedTab,
                onUserNameClicked: (userId: number) => {
                    ref?.destroy();
                    this.metisConversationService.createOneToOneChatWithId(userId).subscribe();
                },
            },
        });

        ref?.onClose
            .pipe(
                filter((result) => !!result),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.metisConversationService.forceRefresh().subscribe({
                    complete: () => {},
                });
                this.onUpdateSidebar.emit();
            });
    }

    toggleSearchBar() {
        this.onSearchClick.emit();
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
