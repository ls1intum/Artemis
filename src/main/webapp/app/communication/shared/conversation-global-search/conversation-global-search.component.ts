import { Component, ElementRef, HostListener, OnDestroy, OnInit, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { faQuestionCircle, faSearch, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ConversationDTO } from '../entities/conversation/conversation.model';
import { isChannelDTO } from '../entities/conversation/channel.model';
import { isGroupChatDTO } from '../entities/conversation/group-chat.model';
import { isOneToOneChatDTO } from '../entities/conversation/one-to-one-chat.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { User, UserPublicInfoDTO } from 'app/account/user/user.model';
import { Subject, catchError, map, of, takeUntil } from 'rxjs';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { addPublicFilePrefix } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ButtonComponent, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';

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

const PREFIX_USER_SEARCH_ME = 'me';

enum SearchMode {
    NORMAL,
    CONVERSATION,
    USER,
}

type SearchFilter = { mode: SearchMode.CONVERSATION; value: 'in:' } | { mode: SearchMode.USER; value: 'from:' };

const CONVERSATION_FILTER: SearchFilter = { mode: SearchMode.CONVERSATION, value: 'in:' };
const USER_FILTER: SearchFilter = { mode: SearchMode.USER, value: 'from:' };

enum UserSearchStatus {
    TOO_SHORT,
    LOADING,
    RESULTS,
}

@Component({
    selector: 'jhi-conversation-global-search',
    templateUrl: './conversation-global-search.component.html',
    styleUrls: ['./conversation-global-search.component.scss'],
    imports: [FormsModule, ButtonComponent, TranslateDirective, ArtemisTranslatePipe, ProfilePictureComponent, FaIconComponent, NgbTooltip],
})
export class ConversationGlobalSearchComponent implements OnInit, OnDestroy {
    protected readonly addPublicFilePrefix = addPublicFilePrefix;
    readonly SearchMode = SearchMode;
    readonly UserSearchStatus = UserSearchStatus;
    readonly CONVERSATION_FILTER = CONVERSATION_FILTER;
    readonly USER_FILTER = USER_FILTER;

    private courseManagementService = inject(CourseManagementService);
    private accountService = inject(AccountService);

    conversations = input<ConversationDTO[]>([]);
    courseId = input<number | undefined>(undefined);
    onSearch = output<ConversationGlobalSearchConfig>();
    onSelectionChange = output<ConversationGlobalSearchConfig>();
    onClearSearch = output<void>();

    readonly searchElement = viewChild<ElementRef>('searchInput');

    fullSearchTerm = '';
    readonly searchTermWithoutPrefix = signal('');
    readonly selectedConversations = signal<ConversationDTO[]>([]);
    readonly selectedAuthors = signal<UserPublicInfoDTO[]>([]);

    readonly showDropdown = signal(false);
    readonly isSearchActive = signal(false);
    readonly searchMode = signal<SearchMode>(SearchMode.NORMAL);
    readonly userSearchStatus = signal<UserSearchStatus>(UserSearchStatus.LOADING);

    readonly filteredOptions = signal<CombinedOption[]>([]);
    filteredUsers: UserPublicInfoDTO[] = [];
    user: User | undefined;
    readonly activeDropdownIndex = signal(-1);
    private destroy$ = new Subject<void>();

    // Icons
    faTimes = faTimes;
    faQuestionCircle = faQuestionCircle;
    faSearch = faSearch;
    faSpinner = faSpinner;
    readonly ButtonType = ButtonType;

    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    clearSearch() {
        this.fullSearchTerm = '';
        this.selectedConversations.set([]);
        this.selectedAuthors.set([]);
        this.emitSelectionChange();
        this.onClearSearch.emit();
    }

    filterItems(event: Event): void {
        this.fullSearchTerm = (event.target as HTMLInputElement).value;
        this.startFiltering();
    }

    startFiltering(): void {
        this.activeDropdownIndex.set(0);

        // Check if search starts with "in:" for conversations
        if (this.fullSearchTerm.startsWith(CONVERSATION_FILTER.value)) {
            const searchQuery = this.fullSearchTerm.substring(CONVERSATION_FILTER.value.length).toLowerCase();
            this.searchTermWithoutPrefix.set(searchQuery);
            this.filterConversations(searchQuery);
            this.searchMode.set(CONVERSATION_FILTER.mode);
            this.showDropdown.set(true);
        }
        // Check if search starts with "from:" for users
        else if (this.fullSearchTerm.startsWith(USER_FILTER.value)) {
            const searchQuery = this.fullSearchTerm.substring(USER_FILTER.value.length).toLowerCase();
            this.searchTermWithoutPrefix.set(searchQuery);
            this.filterUsers(searchQuery);
            this.searchMode.set(USER_FILTER.mode);
            this.showDropdown.set(true);
        } else {
            this.searchMode.set(SearchMode.NORMAL);
            this.closeDropdown();
        }
    }

    closeDropdown(): void {
        this.showDropdown.set(false);
        this.activeDropdownIndex.set(-1);
    }

    filterConversations(searchQuery: string): void {
        const notYetSelectedConversations = this.conversations().filter((conversation) => {
            return !this.selectedConversations().some((selected) => selected.id === conversation.id);
        });

        let matchingConversations = notYetSelectedConversations;
        if (searchQuery) {
            matchingConversations = matchingConversations.filter((conversation) => {
                const name = this.getConversationName(conversation);
                return name.toLowerCase().includes(searchQuery);
            });
        }

        this.filteredOptions.set(
            matchingConversations.map((conversation) => ({
                id: conversation.id!,
                name: this.getConversationName(conversation),
                type: 'conversation',
            })),
        );
    }

    filterUsers(searchQuery: string): void {
        const courseId = this.courseId();

        if (this.user && searchQuery === PREFIX_USER_SEARCH_ME) {
            this.addOwnUserToOptions();
            return;
        }

        if (!searchQuery || searchQuery.length < 3 || !courseId) {
            this.filteredOptions.set([]);
            this.userSearchStatus.set(UserSearchStatus.TOO_SHORT);
            return;
        }

        this.userSearchStatus.set(UserSearchStatus.LOADING);
        this.courseManagementService
            .searchUsers(courseId, searchQuery, ['students', 'tutors', 'instructors'])
            .pipe(
                map((response) => response.body || []),
                map((users) => users.filter((user) => !this.selectedAuthors().some((selected) => selected.id === user.id))),
                catchError(() => of([])),
                takeUntil(this.destroy$),
            )
            .subscribe((users) => {
                this.filteredUsers = users;
                this.filteredOptions.set(
                    users.map((user) => ({
                        id: user.id!,
                        name: user.name!,
                        type: 'user',
                        img: user.imageUrl,
                    })),
                );
                if (
                    (this.user && this.user.name?.toLowerCase().includes(searchQuery.toLowerCase())) ||
                    (this.user && this.user.login?.toLowerCase().includes(searchQuery.toLowerCase()))
                ) {
                    this.addOwnUserToOptions();
                }
                this.userSearchStatus.set(UserSearchStatus.RESULTS);
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
                this.selectedConversations.update((conversations) => [...conversations, conversation]);
                this.closeDropdown();
                this.fullSearchTerm = '';
                this.focusInput();
                this.emitSelectionChange();
            }
        } else if (option.type === 'user') {
            const user = this.filteredUsers.find((user) => user.id === option.id);
            if (user) {
                this.selectedAuthors.update((authors) => [...authors, user]);
                this.closeDropdown();
                this.fullSearchTerm = '';
                this.focusInput();
                this.emitSelectionChange();
            }
        }
    }

    addOwnUserToOptions(): void {
        const alreadySelected = this.selectedAuthors().some((selected) => selected.id === this.user?.id);

        if (this.user && !alreadySelected) {
            this.filteredUsers = [this.user, ...this.filteredUsers];
            this.filteredOptions.set([
                {
                    id: this.user.id!,
                    name: this.user.name!,
                    type: 'user',
                    img: this.user.imageUrl,
                },
                ...this.filteredOptions(),
            ]);
            this.userSearchStatus.set(UserSearchStatus.RESULTS);
        }
    }

    removeSelectedChannel(conversation: ConversationDTO): void {
        this.selectedConversations.update((conversations) => conversations.filter((conv) => conv.id !== conversation.id));
        this.focusInput();
        this.emitSelectionChange();
    }

    removeSelectedAuthor(author: UserPublicInfoDTO): void {
        this.selectedAuthors.update((authors) => authors.filter((user) => user.id !== author.id));
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
            this.selectedConversations.set([conversation]);
            this.emitSelectionChange();
        }
        this.focusInput();
    }

    private emitSelectionChange(): void {
        this.onSelectionChange.emit({
            searchTerm: this.fullSearchTerm,
            selectedConversations: this.selectedConversations(),
            selectedAuthors: this.selectedAuthors(),
        });
    }

    onSearchInputClick(): void {
        this.isSearchActive.set(true);
    }

    onPreselectFilter(filter: SearchFilter): void {
        this.fullSearchTerm = filter.value;
        this.searchMode.set(filter.mode);
        this.showDropdown.set(true);
        this.startFiltering();
        this.focusInput();
    }

    onTriggerSearch() {
        this.onSearch.emit({
            searchTerm: this.fullSearchTerm,
            selectedConversations: this.selectedConversations(),
            selectedAuthors: this.selectedAuthors(),
        });
        this.isSearchActive.set(false);
    }

    navigateDropdown(step: number, event: Event): void {
        const optionsLength = this.filteredOptions().length;
        if (this.showDropdown() && optionsLength > 0) {
            event.preventDefault();
            const newIndex = this.activeDropdownIndex() + step;
            this.setActiveDropdownIndex((newIndex + optionsLength) % optionsLength);
        }
    }

    setActiveDropdownIndex(index: number): void {
        this.activeDropdownIndex.set(index);

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
        const options = this.filteredOptions();
        const activeDropdownIndex = this.activeDropdownIndex();
        if (this.fullSearchTerm && activeDropdownIndex >= 0 && activeDropdownIndex < options.length) {
            const selectedOption = options[activeDropdownIndex];
            this.selectOption(selectedOption);
        }
    }

    @HostListener('document:click', ['$event'])
    onClickOutside(event: Event): void {
        // Close dropdown when clicking outside
        if (this.searchElement && !this.searchElement()!.nativeElement.contains(event.target)) {
            this.closeDropdown();
            this.isSearchActive.set(false);
        }
    }

    @HostListener('document:keydown', ['$event'])
    handleSearchShortcut(event: KeyboardEvent) {
        if ((event.metaKey || event.ctrlKey) && event.key === 's') {
            event.preventDefault();
            this.focusInput();
        }
    }
}
