import { Component, ElementRef, HostListener, OnDestroy, input, output, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { faSearch, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ConversationDTO } from '../entities/conversation/conversation.model';
import { isChannelDTO } from '../entities/conversation/channel.model';
import { isGroupChatDTO } from '../entities/conversation/group-chat.model';
import { isOneToOneChatDTO } from '../entities/conversation/one-to-one-chat.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { Subject, catchError, map, of, takeUntil } from 'rxjs';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ButtonComponent, ButtonType } from 'app/shared/components/button/button.component';

export interface ConversationGlobalSearchConfig {
    searchTerm: string;
    selectedConversations: ConversationDTO[];
    selectedAuthors: UserPublicInfoDTO[];
}

interface CombinedOption {
    id: number;
    name: string;
    type: string;
    img?: string;
}

const PREFIX_CONVERSATION_SEARCH = 'in:';
const PREFIX_USER_SEARCH = 'by:';

enum SearchMode {
    NORMAL,
    CONVERSATION,
    USER,
}

enum UserSearchStatus {
    TOO_SHORT,
    LOADING,
    RESULTS,
}

@Component({
    selector: 'jhi-conversation-global-search',
    templateUrl: './conversation-global-search.component.html',
    styleUrls: ['./conversation-global-search.component.scss'],
    imports: [FormsModule, ButtonComponent, TranslateDirective, ArtemisTranslatePipe, ProfilePictureComponent, FaIconComponent],
})
export class ConversationGlobalSearchComponent implements OnDestroy {
    conversations = input<ConversationDTO[]>([]);
    courseId = input<number | undefined>(undefined);
    onSearch = output<ConversationGlobalSearchConfig>();
    onSelectionChange = output<ConversationGlobalSearchConfig>();

    readonly searchElement = viewChild<ElementRef>('searchInput');

    fullSearchTerm = '';
    searchTermWithoutPrefix = '';
    selectedConversations: ConversationDTO[] = [];
    selectedAuthors: UserPublicInfoDTO[] = [];

    showDropdown = false;
    searchMode: SearchMode = SearchMode.NORMAL;
    userSearchStatus: UserSearchStatus = UserSearchStatus.LOADING;

    filteredOptions: CombinedOption[] = [];
    filteredUsers: UserPublicInfoDTO[] = [];
    activeDropdownIndex: number = -1;
    private destroy$ = new Subject<void>();

    // Icons
    faTimes = faTimes;
    faSearch = faSearch;
    faSpinner = faSpinner;
    readonly ButtonType = ButtonType;

    constructor(private courseManagementService: CourseManagementService) {}

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    clearSearch() {
        this.fullSearchTerm = '';
        this.selectedConversations = [];
        this.selectedAuthors = [];
        this.emitSelectionChange();
    }

    filterItems(event: Event): void {
        this.fullSearchTerm = (event.target as HTMLInputElement).value;
        this.activeDropdownIndex = 0;

        // Check if search starts with "in:" for conversations
        if (this.fullSearchTerm.startsWith(PREFIX_CONVERSATION_SEARCH)) {
            const searchQuery = this.fullSearchTerm.substring(PREFIX_CONVERSATION_SEARCH.length).toLowerCase();
            this.searchTermWithoutPrefix = searchQuery;
            this.filterConversations(searchQuery);
            this.searchMode = SearchMode.CONVERSATION;
            this.showDropdown = true;
        }
        // Check if search starts with "by:" for users
        else if (this.fullSearchTerm.startsWith(PREFIX_USER_SEARCH)) {
            const searchQuery = this.fullSearchTerm.substring(PREFIX_USER_SEARCH.length).toLowerCase();
            this.searchTermWithoutPrefix = searchQuery;
            this.filterUsers(searchQuery);
            this.searchMode = SearchMode.USER;
            this.showDropdown = true;
        } else {
            this.searchMode = SearchMode.NORMAL;
            this.closeDropdown();
        }
    }

    closeDropdown(): void {
        this.showDropdown = false;
        this.activeDropdownIndex = -1;
    }

    filterConversations(searchQuery: string): void {
        const notYetSelectedConversations = this.conversations().filter((conversation) => {
            return !this.selectedConversations.some((selected) => selected.id === conversation.id);
        });

        let matchingConversations = notYetSelectedConversations;
        if (searchQuery) {
            matchingConversations = matchingConversations.filter((conversation) => {
                const name = this.getConversationName(conversation);
                return name.toLowerCase().includes(searchQuery);
            });
        }

        this.filteredOptions = matchingConversations.map(
            (conversation) =>
                ({
                    id: conversation.id!,
                    name: this.getConversationName(conversation),
                    type: 'conversation',
                }) as CombinedOption,
        );
    }

    filterUsers(searchQuery: string): void {
        const courseId = this.courseId();
        if (!searchQuery || searchQuery.length < 3 || !courseId) {
            this.filteredOptions = [];
            this.userSearchStatus = UserSearchStatus.TOO_SHORT;
            return;
        }

        this.userSearchStatus = UserSearchStatus.LOADING;
        this.courseManagementService
            .searchUsers(courseId, searchQuery, ['students', 'tutors', 'instructors'])
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
                this.userSearchStatus = UserSearchStatus.RESULTS;
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
        if (option.type === 'conversation') {
            const conversation = this.conversations().find((conv) => conv.id === option.id);
            if (conversation) {
                this.selectedConversations.push(conversation);
                this.closeDropdown();
                this.fullSearchTerm = '';
                this.focusInput();
                this.emitSelectionChange();
            }
        } else if (option.type === 'user') {
            const user = this.filteredUsers.find((user) => user.id === option.id);
            if (user) {
                this.selectedAuthors.push(user);
                this.closeDropdown();
                this.fullSearchTerm = '';
                this.focusInput();
                this.emitSelectionChange();
            }
        }
    }

    removeSelectedChannel(conversation: ConversationDTO): void {
        this.selectedConversations = this.selectedConversations.filter((conv) => conv.id !== conversation.id);
        this.focusInput();
        this.emitSelectionChange();
    }

    removeSelectedAuthor(author: UserPublicInfoDTO): void {
        this.selectedAuthors = this.selectedAuthors.filter((user) => user.id !== author.id);
        this.focusInput();
        this.emitSelectionChange();
    }

    focusInput(): void {
        setTimeout(() => {
            if (this.searchElement) {
                this.searchElement()!.nativeElement.focus();
            }
        }, 0);
    }

    /**
     * Programmatically focus the search input and select a conversation
     * @param conversation The conversation to select
     */
    focusWithSelectedConversation(conversation: ConversationDTO | undefined): void {
        if (conversation) {
            this.selectedConversations = [conversation];
            this.emitSelectionChange();
        }
        this.focusInput();
    }

    private emitSelectionChange(): void {
        this.onSelectionChange.emit({
            searchTerm: this.fullSearchTerm,
            selectedConversations: this.selectedConversations,
            selectedAuthors: this.selectedAuthors,
        });
    }

    onTriggerSearch() {
        this.onSearch.emit({
            searchTerm: this.fullSearchTerm,
            selectedConversations: this.selectedConversations,
            selectedAuthors: this.selectedAuthors,
        });
    }

    navigateDropdown(step: number, event: Event): void {
        if (this.showDropdown && this.filteredOptions.length > 0) {
            event.preventDefault();
            const newIndex = this.activeDropdownIndex + step;
            this.setActiveDropdownIndex((newIndex + this.filteredOptions.length) % this.filteredOptions.length);
        }
    }

    setActiveDropdownIndex(index: number): void {
        this.activeDropdownIndex = index;

        // Allow DOM to update before scrolling
        setTimeout(() => {
            const dropdownContainer = document.querySelector('.autocomplete-dropdown') as HTMLElement;
            const activeElement = document.querySelector('.dropdown-option.active') as HTMLElement;

            if (dropdownContainer && activeElement) {
                const containerRect = dropdownContainer.getBoundingClientRect();
                const activeRect = activeElement.getBoundingClientRect();

                // Check if the active element is not fully visible in the container
                if (activeRect.bottom > containerRect.bottom || activeRect.top < containerRect.top) {
                    activeElement.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
                }
            }
        }, 0);
    }

    selectActiveOption(): void {
        if (this.activeDropdownIndex >= 0 && this.activeDropdownIndex < this.filteredOptions.length) {
            const selectedOption = this.filteredOptions[this.activeDropdownIndex];
            this.selectOption(selectedOption);
        }
    }

    @HostListener('document:click', ['$event'])
    onClickOutside(event: Event): void {
        // Close dropdown when clicking outside
        if (this.searchElement && !this.searchElement()!.nativeElement.contains(event.target)) {
            this.closeDropdown();
        }
    }

    @HostListener('document:keydown', ['$event'])
    handleSearchShortcut(event: KeyboardEvent) {
        if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
            event.preventDefault();
            this.focusInput();
        }
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
    protected readonly SearchMode = SearchMode;
    protected readonly UserSearchStatus = UserSearchStatus;
    protected readonly PREFIX_CONVERSATION_SEARCH = PREFIX_CONVERSATION_SEARCH;
    protected readonly PREFIX_USER_SEARCH = PREFIX_USER_SEARCH;
}
