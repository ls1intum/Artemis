import { AfterViewInit, Component, ElementRef, HostListener, OnInit, Renderer2, inject, signal, viewChild } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { UserPublicInfoDTO } from 'app/account/user/user.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { catchError, map, of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { NgClass } from '@angular/common';
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
import { faHashtag, faPeopleGroup } from '@fortawesome/free-solid-svg-icons';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { MAX_CONTENT_LENGTH } from 'app/communication/directive/posting-create-edit.directive';

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
    imports: [ArtemisTranslatePipe, ProfilePictureComponent, NgClass, PostingContentComponent, MessageEditorComponent, FormsModule, TranslateDirective, FaIconComponent],
    providers: [CommunicationService, LinkPreviewService, LinkifyService, CourseConversationsService],
})
export class ForwardMessageDialogComponent implements OnInit, AfterViewInit {
    protected dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);
    private courseManagementService = inject(CourseManagementService);
    private renderer = inject(Renderer2);

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
    readonly showFullForwardedMessage = signal(false);
    readonly isContentLong = signal(false);

    protected searchInput = viewChild<ElementRef>('searchInput');
    protected messageContent = viewChild<ElementRef>('messageContent');
    readonly maxContentLength = MAX_CONTENT_LENGTH;

    protected readonly faPeopleGroup = faPeopleGroup;
    protected readonly faHashtag = faHashtag;

    ngOnInit(): void {
        // Populate signals from DynamicDialogConfig data if available
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
        if (this.searchTerm) {
            const lowerCaseSearchTerm = this.searchTerm.toLowerCase();

            if (lowerCaseSearchTerm.length >= 3 && this.courseId()) {
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
                        this.filteredUsers.set(users);
                        this.updateCombinedOptions();
                    });
            } else {
                this.filteredUsers.set([]);
            }

            this.filteredChannels = this.channels().filter((channel: ChannelDTO | GroupChatDTO) => channel.name?.toLowerCase().includes(lowerCaseSearchTerm));
            this.updateCombinedOptions();
        } else {
            this.filteredUsers.set([...this.users()]);
            this.filteredChannels = [...this.channels()];
            this.updateCombinedOptions();
        }
    }

    /**
     * Combines filtered channels and users into a unified options list.
     */
    private updateCombinedOptions(): void {
        this.filteredOptions.set([
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
        ]);
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
        if (this.searchInput() && !this.searchInput()!.nativeElement.contains(event.target)) {
            this.showDropdown.set(false);
        }
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
