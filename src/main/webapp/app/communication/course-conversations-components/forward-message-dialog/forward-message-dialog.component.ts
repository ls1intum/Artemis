import {
    AfterViewInit,
    Component,
    DestroyRef,
    ElementRef,
    HostListener,
    OnInit,
    Renderer2,
    afterRenderEffect,
    computed,
    inject,
    signal,
    viewChild,
    viewChildren,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiChipComponent } from '@tumaet/ui-angular/chip';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular/dialog';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { UserPublicInfoDTO } from 'app/account/user/user.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Subscription, catchError, map, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { CommunicationService } from 'app/communication/service/communication.service';
import { FormsModule } from '@angular/forms';
import { MessageEditorComponent } from 'app/communication/message-editor/message-editor.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { LinkPreviewService } from 'app/communication/link-preview/services/link-preview.service';
import { LinkifyService } from 'app/communication/link-preview/services/linkify.service';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faHashtag } from '@fortawesome/free-solid-svg-icons/faHashtag';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons/faPeopleGroup';
import { faXmark } from '@fortawesome/free-solid-svg-icons/faXmark';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { MAX_CONTENT_LENGTH } from 'app/communication/directive/posting-create-edit.directive';

/** The data the opener passes to the dialog. */
interface ForwardMessageDialogData {
    users?: UserPublicInfoDTO[];
    channels?: (ChannelDTO | GroupChatDTO)[];
    postToForward?: Post;
    courseId?: number;
}

interface CombinedOption {
    id: number;
    name: string;
    type: string;
    img: string;
}

let nextDestinationListboxId = 0;

@Component({
    selector: 'jhi-forward-message-dialog',
    templateUrl: './forward-message-dialog.component.html',
    styleUrls: ['./forward-message-dialog.component.css'],
    imports: [
        ArtemisTranslatePipe,
        ProfilePictureComponent,
        PostingContentComponent,
        MessageEditorComponent,
        FormsModule,
        TranslateDirective,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiChipComponent,
    ],
    providers: [CommunicationService, LinkPreviewService, LinkifyService, CourseConversationsService],
})
export class ForwardMessageDialogComponent implements OnInit, AfterViewInit {
    protected dialogRef = inject(TumAetUiDialogRef);
    private dialogConfig = inject(TumAetUiDialogConfig<ForwardMessageDialogData>);
    private courseManagementService = inject(CourseManagementService);
    private renderer = inject(Renderer2);
    private destroyRef = inject(DestroyRef);

    private userSearchSubscription?: Subscription;

    channels = signal<(ChannelDTO | GroupChatDTO)[]>([]);
    users = signal<UserPublicInfoDTO[]>([]);
    postToForward = signal<Post | undefined>(undefined);
    courseId = signal<number | undefined>(undefined);
    filteredChannels: (ChannelDTO | GroupChatDTO)[] = [];
    readonly filteredUsers = signal<UserPublicInfoDTO[]>([]);
    selectedChannels: (ChannelDTO | GroupChatDTO)[] = [];
    selectedUsers: UserPublicInfoDTO[] = [];
    readonly combinedOptions = signal<CombinedOption[]>([]);
    readonly filteredOptions = signal<CombinedOption[]>([]);
    searchTerm: string = '';
    newPost = new Post();
    isInputFocused = false;
    readonly showDropdown = signal(false);
    protected readonly destinationListVisible = computed(() => this.showDropdown() && this.filteredOptions().length > 0);
    protected readonly activeDestinationIndex = signal(-1);
    protected readonly destinationListboxId = `forward-message-destinations-${nextDestinationListboxId++}`;
    protected readonly activeDestinationId = computed(() =>
        this.destinationListVisible() && this.activeDestinationIndex() >= 0 && this.activeDestinationIndex() < this.filteredOptions().length
            ? `${this.destinationListboxId}-option-${this.activeDestinationIndex()}`
            : null,
    );
    readonly showFullForwardedMessage = signal(false);
    readonly isContentLong = signal(false);

    protected searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');
    private readonly destinationOptions = viewChildren<ElementRef<HTMLElement>>('destinationOption');
    protected messageContent = viewChild<ElementRef>('messageContent');
    readonly maxContentLength = MAX_CONTENT_LENGTH;

    protected readonly faPeopleGroup = faPeopleGroup;
    protected readonly faHashtag = faHashtag;
    protected readonly faXmark = faXmark;

    constructor() {
        afterRenderEffect(() => {
            if (this.showDropdown()) {
                this.destinationOptions()[this.activeDestinationIndex()]?.nativeElement.scrollIntoView?.({ block: 'nearest' });
            }
        });
    }

    ngOnInit(): void {
        // Populate signals from the dialog config data if available
        if (this.dialogConfig?.data) {
            if (this.dialogConfig.data.users !== undefined) {
                this.users.set(this.dialogConfig.data.users);
            }
            if (this.dialogConfig.data.channels !== undefined) {
                this.channels.set(this.dialogConfig.data.channels);
            }
            if (this.dialogConfig.data.postToForward !== undefined) {
                this.postToForward.set(this.dialogConfig.data.postToForward);
            }
            if (this.dialogConfig.data.courseId !== undefined) {
                this.courseId.set(this.dialogConfig.data.courseId);
            }
        }
        this.filteredChannels = this.channels() || [];
        this.filteredUsers.set(this.users());

        // Combine users and channels into a single options list
        this.combinedOptions.set([
            ...this.channels()
                .filter((channel: ChannelDTO | GroupChatDTO) => channel.name !== undefined)
                .map((channel) => ({
                    id: channel.id!,
                    name: channel.name!,
                    type: channel.type!,
                    img: '',
                })),
            ...this.users().map((user) => ({
                id: user.id!,
                name: user.name!,
                type: 'user',
                img: user.imageUrl!,
            })),
        ]);

        this.filterOptions();
        this.focusInput();
    }

    ngAfterViewInit(): void {
        setTimeout(() => {
            this.checkIfContentOverflows();
        }, 0);
    }

    /**
     * Checks whether the forwarded message content exceeds its visible container height.
     */
    checkIfContentOverflows(): void {
        if (this.messageContent()) {
            const nativeElement = this.messageContent()!.nativeElement;
            this.isContentLong.set(nativeElement.scrollHeight > nativeElement.clientHeight);
        }
    }

    /** Toggles whether full forwarded message content should be shown */
    toggleShowFullForwardedMessage(): void {
        this.showFullForwardedMessage.update((value) => !value);
    }

    /** Updates content of the new post with editor input */
    updateField(content: string): void {
        this.newPost.content = content;
    }

    /**
     * Triggered on user input to filter available user/channel options.
     */
    filterItems(event: Event): void {
        this.searchTerm = (event.target as HTMLInputElement).value;
        this.filterOptions();
        this.showDropdown.set(true);
    }

    /**
     * Filters combined options list based on current search term.
     * Makes remote API call to search users if query has sufficient length.
     */
    filterOptions(): void {
        this.userSearchSubscription?.unsubscribe();
        this.userSearchSubscription = undefined;
        const lowerCaseSearchTerm = this.searchTerm.toLowerCase();
        this.filteredUsers.set(lowerCaseSearchTerm ? this.users().filter((user) => user.name?.toLowerCase().includes(lowerCaseSearchTerm)) : [...this.users()]);
        this.filteredChannels = lowerCaseSearchTerm ? this.channels().filter((channel) => channel.name?.toLowerCase().includes(lowerCaseSearchTerm)) : [...this.channels()];
        this.updateCombinedOptions();

        const courseId = this.courseId();
        if (lowerCaseSearchTerm.length >= 3 && courseId) {
            this.userSearchSubscription = this.courseManagementService
                .searchUsers(courseId, lowerCaseSearchTerm, ['students', 'tutors', 'instructors'])
                .pipe(
                    takeUntilDestroyed(this.destroyRef),
                    map((response) => response.body || []),
                    map((users) => users.filter((user) => !this.selectedUsers.find((selectedUser) => selectedUser.id === user.id))),
                    catchError(() => of([])),
                )
                .subscribe((users) => {
                    this.filteredUsers.set(users);
                    this.updateCombinedOptions(true);
                });
        }
    }

    /**
     * Combines filtered channels and users into a unified options list.
     */
    private updateCombinedOptions(preserveActiveDestination = false): void {
        const activeDestination = preserveActiveDestination ? this.filteredOptions()[this.activeDestinationIndex()] : undefined;
        const options = [
            ...this.filteredChannels.map((channel) => ({
                id: channel.id!,
                name: channel.name!,
                type: channel.type!,
                img: '',
            })),
            ...this.filteredUsers().map((user) => ({
                id: user.id!,
                name: user.name!,
                type: 'user',
                img: user.imageUrl!,
            })),
        ];
        this.filteredOptions.set(options);
        this.activeDestinationIndex.set(activeDestination ? options.findIndex((option) => option.id === activeDestination.id && option.type === activeDestination.type) : -1);
    }

    /**
     * Adds selected option (user or channel) to the appropriate list.
     * Ensures no duplicates.
     */
    selectOption(option: CombinedOption): void {
        if (option.type === 'channel' || option.type === 'groupChat') {
            const existing = this.selectedChannels.find((c) => c.id === option.id);
            if (!existing) {
                const channel = this.channels()?.find((c) => c.id === option.id);
                if (channel) {
                    this.selectedChannels.push(channel);
                }
            }
        } else if (option.type === 'user') {
            const existing = this.selectedUsers.find((user) => user.id === option.id);
            if (!existing) {
                const user = this.filteredUsers().find((user) => user.id === option.id);
                if (user) {
                    this.selectedUsers.push(user);
                }
            }
        }
        this.searchTerm = '';
        this.filterOptions();
        this.showDropdown.set(false);
        this.focusInput();
    }

    /** Removes selected channel from the list */
    removeSelectedChannel(channel: ChannelDTO | GroupChatDTO): void {
        const index = this.selectedChannels.findIndex((c) => c.id === channel.id);
        if (index > -1) {
            this.selectedChannels.splice(index, 1);
        }
        this.focusInput();
    }

    /** Removes selected user from the list */
    removeSelectedUser(user: UserPublicInfoDTO): void {
        const index = this.selectedUsers.findIndex((u) => u.id === user.id);
        if (index > -1) {
            this.selectedUsers.splice(index, 1);
        }
        this.focusInput();
    }

    /**
     * Closes modal and emits selected recipients and message content
     * to the parent component or caller.
     */
    send(): void {
        const selectedItems = {
            channels: this.selectedChannels,
            users: this.selectedUsers,
            messageContent: this.newPost.content,
        };
        this.dialogRef.close(selectedItems);
    }

    /** Returns true if any users or channels are selected */
    hasSelections(): boolean {
        return this.selectedChannels.length > 0 || this.selectedUsers.length > 0;
    }

    /** Returns true if the message content is valid, i.e. does not exceed the max length */
    isMessageValid(): boolean {
        const content = this.newPost.content ?? '';
        return content.length <= this.maxContentLength;
    }

    /** Remembers the input focus. The dropdown is not opened here, because the input is also focused programmatically when the dialog opens. */
    onInputFocus(): void {
        this.isInputFocused = true;
    }

    /** Opens the dropdown when the user clicks into the search input */
    onInputClick(): void {
        this.showDropdown.set(true);
    }

    /** Keep combobox focus in the input while navigating and selecting its destinations. */
    onRecipientKeydown(event: KeyboardEvent): void {
        if (event.target !== this.searchInput()?.nativeElement) {
            return;
        }
        // Safari may confirm an IME candidate after compositionend with keyCode 229 and isComposing=false.
        // eslint-disable-next-line @typescript-eslint/no-deprecated -- keyCode 229 is the only signal of an IME confirmation in Safari
        if (event.isComposing || event.keyCode === 229) {
            event.stopPropagation();
            return;
        }

        const options = this.filteredOptions();
        const index = this.activeDestinationIndex();
        if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && options.length > 0) {
            event.preventDefault();
            this.showDropdown.set(true);
            const nextIndex = event.key === 'ArrowDown' ? Math.min(index + 1, options.length - 1) : index < 0 ? options.length - 1 : Math.max(index - 1, 0);
            this.activeDestinationIndex.set(nextIndex);
        } else if (event.key === 'Enter' && this.showDropdown() && index >= 0 && index < options.length) {
            event.preventDefault();
            this.selectOption(options[index]);
        } else if (event.key === 'Escape' && this.destinationListVisible()) {
            event.preventDefault();
            event.stopPropagation();
            this.showDropdown.set(false);
            this.activeDestinationIndex.set(-1);
        }
    }

    /** Hides dropdown when input loses focus */
    onInputBlur(): void {
        this.isInputFocused = false;
        this.showDropdown.set(false);
    }

    /** Programmatically focuses on the search input field */
    focusInput(): void {
        if (this.searchInput()) {
            this.renderer.selectRootElement(this.searchInput()!.nativeElement, true).focus();
        }
    }

    /**
     * Detects clicks outside the search input and hides dropdown accordingly.
     */
    @HostListener('document:click', ['$event'])
    onClickOutside(event: Event): void {
        const input = this.searchInput()?.nativeElement;
        if (input && (!(event.target instanceof Node) || !input.contains(event.target))) {
            this.showDropdown.set(false);
        }
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
