import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, HostListener, OnInit, Renderer2, ViewChild, inject, input, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
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

interface OneToOneChatWithName extends OneToOneChatDTO {
    otherUserName: string | undefined;
    otherUserImg: string | undefined;
}

interface CombinedOption {
    id: number;
    name: string;
    type: 'channel' | 'chat';
    img: string | undefined;
}

@Component({
    selector: 'jhi-forward-message-dialog',
    templateUrl: './forward-message-dialog.component.html',
    styleUrls: ['./forward-message-dialog.component.scss'],
})
export class ForwardMessageDialogComponent implements OnInit, AfterViewInit {
    channels = signal<ChannelDTO[] | []>([]);
    chats = signal<OneToOneChatDTO[] | []>([]);
    postToForward = signal<Post | null>(null);
    filteredChannels: ChannelDTO[] = [];
    filteredChats: OneToOneChatWithName[] = [];
    selectedChannels: ChannelDTO[] = [];
    selectedChats: OneToOneChatWithName[] = [];
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

    constructor(
        public renderer: Renderer2,
        public activeModal: NgbActiveModal,
    ) {}

    ngOnInit(): void {
        this.filteredChannels = this.channels() || [];
        this.defaultActions = [new BoldAction(), new ItalicAction(), new UnderlineAction(), new QuoteAction(), new CodeAction(), new CodeBlockAction(), new UrlAction()];
        this.filteredChats = this.chats()?.map((chat) => {
            const otherUser = chat.members ? chat.members.find((user) => !user.isRequestingUser) : undefined;
            return {
                ...chat,
                otherUserName: otherUser ? otherUser.name : '',
                otherUserImg: otherUser ? otherUser.imageUrl : undefined,
            };
        });

        this.combinedOptions = [
            ...this.channels()
                .filter((channel: ChannelDTO) => channel.id !== undefined && channel.name !== undefined)
                .map((channel) => ({
                    id: channel.id!,
                    name: channel.name!,
                    type: 'channel' as const,
                    img: '',
                })),
            ...this.filteredChats
                .filter((chat) => chat.id !== undefined && chat.otherUserName !== undefined)
                .map((chat) => ({
                    id: chat.id!,
                    name: chat.otherUserName!,
                    type: 'chat' as const,
                    img: chat.otherUserImg,
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
        } else if (option.type === 'chat') {
            const existing = this.selectedChats.find((c) => (c as OneToOneChatWithName).id === option.id);
            if (!existing) {
                const chat = this.filteredChats.find((c) => (c as OneToOneChatWithName).id === option.id);
                if (chat) {
                    this.selectedChats.push(chat);
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

    removeSelectedChat(chat: OneToOneChatWithName): void {
        const index = this.selectedChats.findIndex((c) => c.id === chat.id);
        if (index > -1) {
            this.selectedChats.splice(index, 1);
        }
        this.focusInput();
    }

    send(): void {
        const selectedItems = {
            channels: this.selectedChannels,
            chats: this.selectedChats,
            messageContent: this.newPost.content,
        };
        this.activeModal.close(selectedItems);
    }

    hasSelections(): boolean {
        return this.selectedChannels.length > 0 || this.selectedChats.length > 0;
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
