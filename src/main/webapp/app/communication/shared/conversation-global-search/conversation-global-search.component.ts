import { Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent, ButtonType } from 'app/shared/components/button.component';
import { ConversationDTO } from '../entities/conversation/conversation.model';
import { NgFor, NgIf } from '@angular/common';
import { isChannelDTO } from '../entities/conversation/channel.model';
import { isGroupChatDTO } from '../entities/conversation/group-chat.model';
import { isOneToOneChatDTO } from '../entities/conversation/one-to-one-chat.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export interface CombinedOption {
    id: number;
    name: string;
    type: string;
    img?: string;
}

@Component({
    selector: 'jhi-conversation-global-search',
    templateUrl: './conversation-global-search.component.html',
    styleUrls: ['./conversation-global-search.component.scss'],
    standalone: true,
    imports: [NgIf, NgFor, FormsModule, ButtonComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class ConversationGlobalSearchComponent {
    @Input() conversations: ConversationDTO[] = [];
    @Output() onSearch = new EventEmitter<{ searchTerm: string; selectedConversations: ConversationDTO[] }>();

    @ViewChild('searchInput', { static: false }) searchElement?: ElementRef;

    courseWideSearchTerm = '';
    selectedConversations: ConversationDTO[] = [];

    showDropdown = false;
    filteredOptions: CombinedOption[] = [];
    activeDropdownIndex: number = -1;

    // Icons
    faTimes = faTimes;
    faSearch = faSearch;
    readonly ButtonType = ButtonType;

    navigateDropdown(step: number, event: Event): void {
        if (this.showDropdown) {
            event.preventDefault();
            this.activeDropdownIndex = (this.activeDropdownIndex + step + this.filteredOptions.length) % this.filteredOptions.length;
        }
    }

    selectActiveOption(): void {
        if (this.activeDropdownIndex >= 0 && this.activeDropdownIndex < this.filteredOptions.length) {
            const selectedOption = this.filteredOptions[this.activeDropdownIndex];
            this.selectOption(selectedOption);
        }
    }

    hideSearchTerm() {
        this.courseWideSearchTerm = '';
        this.selectedConversations = [];
        this.showDropdown = false;
    }

    filterItems(event: Event): void {
        this.courseWideSearchTerm = (event.target as HTMLInputElement).value;

        // Check if search starts with "in:"
        if (this.courseWideSearchTerm.startsWith('in:')) {
            const searchQuery = this.courseWideSearchTerm.substring(3).toLowerCase();
            this.filterOptions(searchQuery);
            this.showDropdown = this.filteredOptions.length > 0;
        } else {
            this.showDropdown = false;
        }
    }

    filterOptions(searchQuery: string): void {
        if (!searchQuery) {
            this.filteredOptions = this.conversations.map((conv) => ({
                id: conv.id!,
                name: this.getConversationName(conv),
                type: 'channel',
            }));
        } else {
            this.filteredOptions = this.conversations
                .filter((conversation) => {
                    const name = this.getConversationName(conversation);
                    return name.toLowerCase().includes(searchQuery);
                })
                .map((channel) => ({
                    id: channel.id!,
                    name: this.getConversationName(channel),
                    type: 'channel',
                }));
        }
    }

    getConversationName(conversation: ConversationDTO): string {
        if (isChannelDTO(conversation)) {
            return conversation.name || 'Unnamed Channel';
        } else if (isGroupChatDTO(conversation)) {
            return conversation.name || 'Unnamed Group Chat';
        } else if (isOneToOneChatDTO(conversation)) {
            const user = conversation.members?.find((member) => !member.isRequestingUser);
            return user ? (user.name ?? '') : 'Unnamed One-to-One Chat';
        }

        return 'Unknown Conversation';
    }

    selectOption(option: CombinedOption): void {
        if (option.type === 'channel') {
            const conversation = this.conversations.find((conv) => conv.id === option.id);
            if (conversation) {
                this.selectedConversations.push(conversation);
                this.showDropdown = false;
                this.courseWideSearchTerm = '';
                this.updateSearchWithSelectedChannel();
            }
        }
    }

    updateSearchWithSelectedChannel(): void {
        // Focus the search input after selecting a channel
        setTimeout(() => {
            if (this.searchElement) {
                this.searchElement.nativeElement.focus();
            }
        }, 0);
    }

    removeSelectedChannel(conversation: ConversationDTO): void {
        this.selectedConversations = this.selectedConversations.filter((conv) => conv.id !== conversation.id);
        this.focusInput();
    }

    focusInput(): void {
        setTimeout(() => {
            if (this.searchElement) {
                this.searchElement.nativeElement.focus();
            }
        }, 0);
    }

    onTriggerSearch() {
        this.onSearch.emit({
            searchTerm: this.courseWideSearchTerm,
            selectedConversations: this.selectedConversations,
        });
    }

    @HostListener('document:click', ['$event'])
    onClickOutside(event: Event): void {
        // Close dropdown when clicking outside
        if (this.searchElement && !this.searchElement.nativeElement.contains(event.target)) {
            this.showDropdown = false;
        }
    }

    @HostListener('document:keydown', ['$event'])
    handleSearchShortcut(event: KeyboardEvent) {
        if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
            event.preventDefault();
            if (this.searchElement) {
                this.searchElement.nativeElement.focus();
            }
        }
    }
}
