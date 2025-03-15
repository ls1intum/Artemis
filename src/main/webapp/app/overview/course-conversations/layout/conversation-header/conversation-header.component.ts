import { ChangeDetectorRef, Component, EventEmitter, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, inject, input, output } from '@angular/core';
import { faChevronLeft, faPeopleGroup, faSearch, faUserGroup, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import {
    ConversationDetailDialogComponent,
    ConversationDetailTabs,
} from 'app/overview/course-conversations/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';
import { ChannelDTO, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { EMPTY, Subject, from, takeUntil } from 'rxjs';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { canAddUsersToConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { getAsGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { defaultFirstLayerDialogOptions, getChannelSubTypeReferenceTranslationKey } from 'app/overview/course-conversations/other/conversation.util';
import { catchError } from 'rxjs/operators';
import { MetisService } from 'app/shared/metis/metis.service';
import { CourseSidebarService } from 'app/overview/course-sidebar.service';
import { getAsOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ChannelIconComponent } from '../../other/channel-icon/channel-icon.component';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { EmojiComponent } from 'app/shared/metis/emoji/emoji.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-conversation-header',
    templateUrl: './conversation-header.component.html',
    styleUrls: ['./conversation-header.component.scss'],
    imports: [FaIconComponent, ChannelIconComponent, ProfilePictureComponent, TranslateDirective, RouterLink, EmojiComponent, ArtemisTranslatePipe],
})
export class ConversationHeaderComponent implements OnInit, OnChanges, OnDestroy {
    private modalService = inject(NgbModal);
    metisConversationService = inject(MetisConversationService);
    conversationService = inject(ConversationService);
    private metisService = inject(MetisService);
    pinnedMessageCount = input<number>(0);
    togglePinnedMessage = output<void>();

    private ngUnsubscribe = new Subject<void>();

    @Output() collapseSearch = new EventEmitter<void>();
    @Output() onUpdateSidebar = new EventEmitter<void>();

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

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['pinnedMessageCount']) {
            const currentCount = changes['pinnedMessageCount'].currentValue;
            if (this.showPinnedMessages && currentCount === 0) {
                this.showPinnedMessages = false;
                this.cdr.detectChanges();
            }
        }
    }

    togglePinnedMessages(): void {
        this.togglePinnedMessage.emit();
        this.showPinnedMessages = !this.showPinnedMessages;
        this.cdr.detectChanges();
    }

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
        if (this.getAsOneToOneChat(this.activeConversation)) {
            modalRef.componentInstance.selectedTab = ConversationDetailTabs.INFO;
        }
        modalRef.componentInstance.initialize();

        const userNameClicked = modalRef.componentInstance.userNameClicked;
        if (userNameClicked) {
            const subscription = userNameClicked.subscribe((username: number) => {
                modalRef.dismiss();
                this.metisConversationService
                    .createOneToOneChatWithId(username)
                    .pipe(
                        catchError((error) => {
                            return EMPTY;
                        }),
                    )
                    .subscribe();
            });

            modalRef.closed.subscribe(() => subscription.unsubscribe());
        }

        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
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
        this.collapseSearch.emit();
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
