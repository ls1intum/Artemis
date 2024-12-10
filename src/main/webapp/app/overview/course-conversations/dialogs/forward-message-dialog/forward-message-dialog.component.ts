import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, HostListener, OnInit, Renderer2, ViewChild, inject, input, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { Post } from 'app/entities/metis/post.model';
import { BoldAction } from 'app/shared/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/shared/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/shared/monaco-editor/model/actions/underline.action';
import { QuoteAction } from 'app/shared/monaco-editor/model/actions/quote.action';
import { CodeAction } from 'app/shared/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/shared/monaco-editor/model/actions/code-block.action';
import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { UserPublicInfoDTO } from 'app/core/user/user.model';

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
})
export class ForwardMessageDialogComponent implements OnInit, AfterViewInit {
    channels = signal<ChannelDTO[] | []>([]);
    users = signal<UserPublicInfoDTO[] | []>([]);
    postToForward = signal<Post | null>(null);
    filteredChannels: ChannelDTO[] = [];
    filteredUsers: UserPublicInfoDTO[] = [];
    selectedChannels: ChannelDTO[] = [];
    selectedUsers: UserPublicInfoDTO[] = [];
    searchTerm: string = '';
    isInputFocused: boolean = false;
    newPost: Post = new Post();
    defaultActions: TextEditorAction[];
    editorHeight = input<MarkdownEditorHeight>(MarkdownEditorHeight.INLINE);
    @ViewChild('searchInput') searchInput!: ElementRef;
    @ViewChild('messageContent') messageContent!: ElementRef;

    showDropdown: boolean = false;
    combinedOptions: CombinedOption[] = [];
    filteredOptions: CombinedOption[] = [];
    showFullForwardedMessage: boolean = false;
    maxLines: number = 5;
    isContentLong: boolean = false;
    private cdr = inject(ChangeDetectorRef);
    public renderer = inject(Renderer2);
    public activeModal = inject(NgbActiveModal);

    ngOnInit(): void {
        this.filteredChannels = this.channels() || [];
        this.defaultActions = [new BoldAction(), new ItalicAction(), new UnderlineAction(), new QuoteAction(), new CodeAction(), new CodeBlockAction(), new UrlAction()];
        this.filteredUsers = this.users();

        this.combinedOptions = [
            ...this.channels()
                .filter((channel: ChannelDTO) => channel.name !== undefined)
                .map((channel) => ({
                    id: channel.id!,
                    name: channel.name!,
                    type: 'channel',
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

    checkIfContentOverflows(): void {
        if (this.messageContent) {
            const nativeElement = this.messageContent.nativeElement;
            this.isContentLong = nativeElement.scrollHeight > nativeElement.clientHeight;
            this.cdr.detectChanges();
        }
    }

    displayedForwardedContent(): string {
        if (!this.postToForward || !this.postToForward()?.content) {
            return '';
        }

        if (this.showFullForwardedMessage || !this.isContentLong) {
            return this.postToForward()?.content!;
        } else {
            const lines = this.postToForward()?.content?.split('\n');
            return lines?.slice(0, this.maxLines).join('\n') + '...';
        }
    }

    toggleShowFullForwardedMessage(): void {
        this.showFullForwardedMessage = !this.showFullForwardedMessage;
    }

    updateField(content: string): void {
        this.newPost.content = content;
    }

    filterItems(event: Event): void {
        this.searchTerm = (event.target as HTMLInputElement).value.toLowerCase();
        this.filterOptions();
    }

    filterOptions(): void {
        if (this.searchTerm) {
            this.filteredOptions = this.combinedOptions.filter((option) => option.name.toLowerCase().includes(this.searchTerm));
        } else {
            this.filteredOptions = [...this.combinedOptions];
        }
    }

    selectOption(option: CombinedOption): void {
        if (option.type === 'channel') {
            const existing = this.selectedChannels.find((c) => (c as ChannelDTO).id === option.id);
            if (!existing) {
                const channel = this.channels()?.find((c) => (c as ChannelDTO).id === option.id);
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

    removeSelectedChannel(channel: ChannelDTO): void {
        const index = this.selectedChannels.findIndex((c) => c.id === channel.id);
        if (index > -1) {
            this.selectedChannels.splice(index, 1);
        }
        this.focusInput();
    }

    removeSelectedUser(user: UserPublicInfoDTO): void {
        const index = this.selectedUsers.findIndex((u) => u.id === user.id);
        if (index > -1) {
            this.selectedUsers.splice(index, 1);
        }
        this.focusInput();
    }

    send(): void {
        const selectedItems = {
            channels: this.selectedChannels,
            users: this.selectedUsers,
            messageContent: this.newPost.content,
        };
        this.activeModal.close(selectedItems);
    }

    hasSelections(): boolean {
        return this.selectedChannels.length > 0 || this.selectedUsers.length > 0;
    }

    onInputFocus(): void {
        this.isInputFocused = true;
        this.showDropdown = true;
    }

    onInputBlur(): void {
        this.isInputFocused = false;
        this.showDropdown = false;
    }

    focusInput(): void {
        if (this.searchInput) {
            this.renderer.selectRootElement(this.searchInput.nativeElement, true).focus();
        }
    }

    @HostListener('document:click', ['$event'])
    onClickOutside(event: Event): void {
        if (this.searchInput && !this.searchInput.nativeElement.contains(event.target)) {
            this.showDropdown = false;
        }
    }

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
}
