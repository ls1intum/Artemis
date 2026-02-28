import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, HostListener, OnInit, Renderer2, inject, input, signal, viewChild } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { BoldAction } from 'app/shared/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/shared/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/shared/monaco-editor/model/actions/underline.action';
import { QuoteAction } from 'app/shared/monaco-editor/model/actions/quote.action';
import { CodeAction } from 'app/shared/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/shared/monaco-editor/model/actions/code-block.action';
import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { catchError, map, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { NgClass } from '@angular/common';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { MetisService } from 'app/communication/service/metis.service';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { LinkPreviewService } from 'app/communication/link-preview/services/link-preview.service';
import { LinkifyService } from 'app/communication/link-preview/services/linkify.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faHashtag, faPeopleGroup } from '@fortawesome/free-solid-svg-icons';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';

interface CombinedOption {
    id: number;
    name: string;
    type: string;
    img: string;
}

@Component({
    selector: 'jhi-forward-message-dialog',
    templateUrl: './forward-message-dialog.component.html',
    styleUrls: ['./forward-message-dialog.component.scss'],
    imports: [ArtemisTranslatePipe, ProfilePictureComponent, NgClass, PostingContentComponent, MarkdownEditorMonacoComponent, FormsModule, TranslateDirective, FaIconComponent],
    providers: [MetisService, LinkPreviewService, LinkifyService, MetisConversationService],
})
export class ForwardMessageDialogComponent implements OnInit, AfterViewInit {
    channels = signal<(ChannelDTO | GroupChatDTO)[] | []>([]);
    users = signal<UserPublicInfoDTO[] | []>([]);
    postToForward = signal<Post | undefined>(undefined);
    courseId = signal<number | undefined>(undefined);
    editorHeight = input<MarkdownEditorHeight>(MarkdownEditorHeight.INLINE);
    filteredChannels: (ChannelDTO | GroupChatDTO)[] = [];
    filteredUsers: UserPublicInfoDTO[] = [];
    selectedChannels: (ChannelDTO | GroupChatDTO)[] = [];
    selectedUsers: UserPublicInfoDTO[] = [];
    combinedOptions: CombinedOption[] = [];
    filteredOptions: CombinedOption[] = [];
    defaultActions: TextEditorAction[];
    searchTerm: string = '';
    newPost = new Post();
    isInputFocused = false;
    showDropdown = false;
    showFullForwardedMessage = false;
    isContentLong = false;

    protected activeModal = inject(NgbActiveModal);
    protected searchInput = viewChild<ElementRef>('searchInput');
    protected messageContent = viewChild<ElementRef>('messageContent');

    private courseManagementService = inject(CourseManagementService);
    private cdr = inject(ChangeDetectorRef);
    private renderer = inject(Renderer2);

    protected readonly faPeopleGroup = faPeopleGroup;
    protected readonly faHashtag = faHashtag;

    ngOnInit(): void {
        this.filteredChannels = this.channels() || [];
        this.defaultActions = [new BoldAction(), new ItalicAction(), new UnderlineAction(), new QuoteAction(), new CodeAction(), new CodeBlockAction(), new UrlAction()];
        this.filteredUsers = this.users();

        // Combine users and channels into a single options list
        this.combinedOptions = [
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
        ];

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
        if (this.messageContent) {
            const nativeElement = this.messageContent()!.nativeElement;
            this.isContentLong = nativeElement.scrollHeight > nativeElement.clientHeight;
            this.cdr.detectChanges();
        }
    }

    /** Toggles whether full forwarded message content should be shown */
    toggleShowFullForwardedMessage(): void {
        this.showFullForwardedMessage = !this.showFullForwardedMessage;
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
    }

    /**
     * Filters combined options list based on current search term.
     * Makes remote API call to search users if query has sufficient length.
     */
    filterOptions(): void {
        if (this.searchTerm) {
            const lowerCaseSearchTerm = this.searchTerm.toLowerCase();

            if (lowerCaseSearchTerm.length >= 3) {
                this.courseManagementService
                    .searchUsers(this.courseId()!, lowerCaseSearchTerm, ['students', 'tutors', 'instructors'])
                    .pipe(
                        map((response) => response.body || []),
                        map((users) => users.filter((user) => !this.selectedUsers.find((selectedUser) => selectedUser.id === user.id))),
                        catchError(() => {
                            return of([]);
                        }),
                    )
                    .subscribe((users) => {
                        this.filteredUsers = users;
                        this.updateCombinedOptions();
                        this.cdr.detectChanges();
                    });
            } else {
                this.filteredUsers = [];
            }

            this.filteredChannels = this.channels().filter((channel: ChannelDTO | GroupChatDTO) => channel.name?.toLowerCase().includes(lowerCaseSearchTerm));
            this.updateCombinedOptions();
        } else {
            this.filteredUsers = [...this.users()];
            this.filteredChannels = [...this.channels()];
            this.updateCombinedOptions();
        }
    }

    /**
     * Combines filtered channels and users into a unified options list.
     */
    private updateCombinedOptions(): void {
        this.filteredOptions = [
            ...this.filteredChannels.map((channel) => ({
                id: channel.id!,
                name: channel.name!,
                type: channel.type!,
                img: '',
            })),
            ...this.filteredUsers.map((user) => ({
                id: user.id!,
                name: user.name!,
                type: 'user',
                img: user.imageUrl!,
            })),
        ];
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
                const user = this.filteredUsers.find((user) => user.id === option.id);
                if (user) {
                    this.selectedUsers.push(user);
                }
            }
        }
        this.searchTerm = '';
        this.filterOptions();
        this.showDropdown = false;
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
        this.activeModal.close(selectedItems);
    }

    /** Returns true if any users or channels are selected */
    hasSelections(): boolean {
        return this.selectedChannels.length > 0 || this.selectedUsers.length > 0;
    }

    /** Sets input focus and opens dropdown */
    onInputFocus(): void {
        this.isInputFocused = true;
        this.showDropdown = true;
    }

    /** Hides dropdown when input loses focus */
    onInputBlur(): void {
        this.isInputFocused = false;
        this.showDropdown = false;
    }

    /** Programmatically focuses on the search input field */
    focusInput(): void {
        if (this.searchInput) {
            this.renderer.selectRootElement(this.searchInput()!.nativeElement, true).focus();
        }
    }

    /**
     * Detects clicks outside the search input and hides dropdown accordingly.
     */
    @HostListener('document:click', ['$event'])
    onClickOutside(event: Event): void {
        if (this.searchInput && !this.searchInput()!.nativeElement.contains(event.target)) {
            this.showDropdown = false;
        }
    }

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
