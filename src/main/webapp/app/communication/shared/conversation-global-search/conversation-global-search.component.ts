import { Component, ElementRef, EventEmitter, HostListener, Input, OnDestroy, Output, ViewChild } from '@angular/core';
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
import { CourseManagementService } from 'app/core/course/manage/course-management.service';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { Subject, catchError, map, of, takeUntil } from 'rxjs';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';

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
    imports: [NgIf, NgFor, FormsModule, ButtonComponent, TranslateDirective, ArtemisTranslatePipe, ProfilePictureComponent],
})
export class ConversationGlobalSearchComponent implements OnDestroy {
    @Input() conversations: ConversationDTO[] = [];
    @Input() courseId?: number;
    @Output() onSearch = new EventEmitter<{ searchTerm: string; selectedConversations: ConversationDTO[]; selectedAuthors: UserPublicInfoDTO[] }>();

    @ViewChild('searchInput', { static: false }) searchElement?: ElementRef;

    courseWideSearchTerm = '';
    selectedConversations: ConversationDTO[] = [];
    selectedAuthors: UserPublicInfoDTO[] = [];

    showDropdown = false;
    filteredOptions: CombinedOption[] = [];
    filteredUsers: UserPublicInfoDTO[] = [];
    activeDropdownIndex: number = -1;
    private destroy$ = new Subject<void>();

    // Icons
    faTimes = faTimes;
    faSearch = faSearch;
    readonly ButtonType = ButtonType;

    constructor(private courseManagementService: CourseManagementService) {}

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

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
        this.selectedAuthors = [];
        this.showDropdown = false;
    }

    filterItems(event: Event): void {
        this.courseWideSearchTerm = (event.target as HTMLInputElement).value;

        // Check if search starts with "in:" for conversations
        if (this.courseWideSearchTerm.startsWith('in:')) {
            const searchQuery = this.courseWideSearchTerm.substring(3).toLowerCase();
            this.filterConversations(searchQuery);
            this.showDropdown = true;
        }
        // Check if search starts with "from:" for users
        else if (this.courseWideSearchTerm.startsWith('from:')) {
            const searchQuery = this.courseWideSearchTerm.substring(5).toLowerCase();
            this.filterUsers(searchQuery);
            this.showDropdown = true;
        } else {
            this.showDropdown = false;
        }
    }

    filterConversations(searchQuery: string): void {
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

    filterUsers(searchQuery: string): void {
        if (!searchQuery || searchQuery.length < 3 || !this.courseId) {
            this.filteredOptions = [];
            return;
        }

        this.courseManagementService
            .searchUsers(this.courseId, searchQuery, ['students', 'tutors', 'instructors'])
            .pipe(
                map((response) => response.body || []),
                map((users) => users.filter((user) => !this.selectedAuthors.some((selected) => selected.id === user.id))),
                catchError(() => of([])),
                takeUntil(this.destroy$),
            )
            .subscribe((users) => {
                this.filteredUsers = users;
                this.filteredOptions = users.map((user) => ({
                    id: user.id!,
                    name: user.name!,
                    type: 'user',
                    img: user.imageUrl,
                }));
            });
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
        } else if (option.type === 'user') {
            const user = this.filteredUsers.find((user) => user.id === option.id);
            if (user) {
                this.selectedAuthors.push(user);
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

    removeSelectedAuthor(author: UserPublicInfoDTO): void {
        this.selectedAuthors = this.selectedAuthors.filter((user) => user.id !== author.id);
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
            selectedAuthors: this.selectedAuthors,
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

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
