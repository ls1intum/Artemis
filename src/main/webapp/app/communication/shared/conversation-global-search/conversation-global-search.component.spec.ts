import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ConversationGlobalSearchComponent } from './conversation-global-search.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ConversationDTO, ConversationType } from '../entities/conversation/conversation.model';
import { User, UserPublicInfoDTO } from 'app/core/user/user.model';
import { By } from '@angular/platform-browser';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { OneToOneChat } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ConversationGlobalSearchComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ConversationGlobalSearchComponent;
    let fixture: ComponentFixture<ConversationGlobalSearchComponent>;
    let courseManagementService: CourseManagementService;

    const mockConversations: ConversationDTO[] = [
        { id: 1, name: 'General Channel', type: ConversationType.CHANNEL } as ChannelDTO,
        { id: 2, name: 'Exercise Channel', type: ConversationType.CHANNEL } as ChannelDTO,
        { id: 3, name: 'One-to-One Chat', type: ConversationType.ONE_TO_ONE } as OneToOneChat,
    ];

    const mockUsers: UserPublicInfoDTO[] = [
        { id: 1, name: 'John Doe', imageUrl: 'john.jpg' } as UserPublicInfoDTO,
        { id: 2, name: 'Jane Smith', imageUrl: 'jane.jpg' } as UserPublicInfoDTO,
    ];

    const mockCurrentUser: User = { internal: false, id: 42, name: 'James Smith', imageUrl: 'james.jpg' };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                FaIconComponent,
                ConversationGlobalSearchComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ButtonComponent),
                MockComponent(ProfilePictureComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(CourseManagementService, {
                    searchUsers: vi.fn(() => of(new HttpResponse({ body: mockUsers }))),
                }),
                MockProvider(AccountService, {
                    identity: vi.fn(() => Promise.resolve(mockCurrentUser)),
                }),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
    });

    beforeEach(() => {
        vi.useFakeTimers();
        fixture = TestBed.createComponent(ConversationGlobalSearchComponent);
        component = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);

        // Set up necessary inputs
        fixture.componentRef.setInput('conversations', mockConversations);
        fixture.componentRef.setInput('courseId', 1);

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.clearAllMocks();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should filter conversations when using "in:" prefix', () => {
        // Simulate input with "in:" prefix
        const inputEl = fixture.debugElement.query(By.css('input#search')).nativeElement;
        inputEl.value = 'in:general';
        inputEl.dispatchEvent(new Event('input'));
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        // Verify that dropdown is shown with filtered results
        expect(component.showDropdown).toBe(true);
        expect(component.searchMode).toBe(component.SearchMode.CONVERSATION);
        expect(component.filteredOptions).toHaveLength(1);
        expect(component.filteredOptions[0].name).toBe('General Channel');
    });

    it('should filter users when using "from:" prefix and search length >= 3', () => {
        // Simulate input with "from:" prefix
        const inputEl = fixture.debugElement.query(By.css('input#search')).nativeElement;
        inputEl.value = 'from:john';
        inputEl.dispatchEvent(new Event('input'));
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        // Verify API is called to search users
        expect(courseManagementService.searchUsers).toHaveBeenCalledWith(1, 'john', ['students', 'tutors', 'instructors']);

        // Verify dropdown shows user results
        expect(component.showDropdown).toBe(true);
        expect(component.searchMode).toBe(component.SearchMode.USER);
        expect(component.userSearchStatus).toBe(component.UserSearchStatus.RESULTS);
        expect(component.filteredUsers).toEqual(mockUsers);
    });

    it('should filter own user when using "from:me" prefix', () => {
        // Simulate input with "from:" prefix
        const inputEl = fixture.debugElement.query(By.css('input#search')).nativeElement;
        inputEl.value = 'from:me';
        inputEl.dispatchEvent(new Event('input'));
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        // Verify dropdown shows current user
        expect(component.showDropdown).toBe(true);
        expect(component.searchMode).toBe(component.SearchMode.USER);
        expect(component.userSearchStatus).toBe(component.UserSearchStatus.RESULTS);
        expect(component.filteredUsers).toEqual([mockCurrentUser]);
    });

    it('should select conversation from dropdown and emit selection change', () => {
        const selectionChangeSpy = vi.spyOn(component.onSelectionChange, 'emit');
        component.fullSearchTerm = 'in:general';
        component.filterItems({ target: { value: 'in:general' } } as unknown as Event);
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        component.selectOption(component.filteredOptions[0]);
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        // Verify selection was added and event was emitted
        expect(component.selectedConversations).toHaveLength(1);
        expect(selectionChangeSpy).toHaveBeenCalledWith({
            searchTerm: '',
            selectedConversations: component.selectedConversations,
            selectedAuthors: [],
        });
    });

    it('should remove selected conversation and emit selection change', () => {
        const selectionChangeSpy = vi.spyOn(component.onSelectionChange, 'emit');
        const focusSpy = vi.spyOn(component, 'focusInput');
        component.selectedConversations = [mockConversations[0]];

        component.removeSelectedChannel(mockConversations[0]);
        vi.advanceTimersByTime(0);

        // Verify conversation was removed and event was emitted
        expect(component.selectedConversations).toHaveLength(0);
        expect(selectionChangeSpy).toHaveBeenCalled();
        expect(focusSpy).toHaveBeenCalled();
    });

    it('should remove selected author and emit selection change', () => {
        const selectionChangeSpy = vi.spyOn(component.onSelectionChange, 'emit');
        const focusSpy = vi.spyOn(component, 'focusInput');

        component.selectedAuthors = mockUsers;
        fixture.changeDetectorRef.detectChanges();

        component.removeSelectedAuthor(component.selectedAuthors[0]);
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(component.selectedAuthors).toHaveLength(1);
        expect(component.selectedAuthors[0].id).toBe(2);

        expect(focusSpy).toHaveBeenCalled();

        expect(selectionChangeSpy).toHaveBeenCalledWith({
            searchTerm: component.fullSearchTerm,
            selectedConversations: component.selectedConversations,
            selectedAuthors: component.selectedAuthors,
        });
    });

    it('should clear search term, selected conversations, and selected authors, and emit selection change when clearing search', () => {
        const selectionChangeSpy = vi.spyOn(component.onSelectionChange, 'emit');

        component.fullSearchTerm = 'in:general';
        component.selectedConversations = [mockConversations[0]];
        component.selectedAuthors = [mockUsers[0]];

        component.clearSearch();
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(component.fullSearchTerm).toBe('');
        expect(component.selectedConversations).toHaveLength(0);
        expect(component.selectedAuthors).toHaveLength(0);

        expect(selectionChangeSpy).toHaveBeenCalledWith({
            searchTerm: '',
            selectedConversations: [],
            selectedAuthors: [],
        });
    });

    it('should set search mode to NORMAL and close dropdown for invalid prefix', () => {
        const inputEl = fixture.debugElement.query(By.css('input#search')).nativeElement;
        inputEl.value = 'invalidPrefix';
        inputEl.dispatchEvent(new Event('input'));
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(component.searchMode).toBe(component.SearchMode.NORMAL);
        expect(component.showDropdown).toBe(false);
    });

    it('should activate search when input is clicked', () => {
        component.isSearchActive = false;

        component.onSearchInputClick();

        expect(component.isSearchActive).toBe(true);
    });

    it('should navigate dropdown with keyboard and select with enter', () => {
        const selectOptionSpy = vi.spyOn(component, 'selectOption');
        component.fullSearchTerm = 'in:general';
        component.filterItems({ target: { value: 'in:general' } } as unknown as Event);
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        component.navigateDropdown(1, { preventDefault: vi.fn() } as unknown as Event);
        expect(component.activeDropdownIndex).toBe(0);
        // Simulate enter press
        component.selectActiveOption();

        expect(selectOptionSpy).toHaveBeenCalledWith(component.filteredOptions[0]);
    });

    it('should focus input and select a conversation when focusWithSelectedConversation is called', () => {
        const focusSpy = vi.spyOn(component, 'focusInput');
        component.focusWithSelectedConversation(mockConversations[0]);
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(component.selectedConversations).toEqual([mockConversations[0]]);
        expect(focusSpy).toHaveBeenCalled();
    });

    it('should select a user from the dropdown and emit selection change', () => {
        const selectionChangeSpy = vi.spyOn(component.onSelectionChange, 'emit');
        const focusSpy = vi.spyOn(component, 'focusInput');

        component.filteredUsers = [mockUsers[0]];
        component.filteredOptions = [
            {
                id: mockUsers[0].id!,
                name: mockUsers[0].name!,
                type: 'user',
                img: mockUsers[0].imageUrl,
            },
        ];

        component.selectOption(component.filteredOptions[0]);
        vi.advanceTimersByTime(0);

        expect(component.selectedAuthors).toEqual([mockUsers[0]]);
        expect(component.showDropdown).toBe(false);
        expect(component.fullSearchTerm).toBe('');
        expect(focusSpy).toHaveBeenCalled();
        expect(selectionChangeSpy).toHaveBeenCalledWith({
            searchTerm: '',
            selectedConversations: [],
            selectedAuthors: [mockUsers[0]],
        });
    });

    it('should emit onSearch event with correct data when onTriggerSearch is called', () => {
        const onSearchSpy = vi.spyOn(component.onSearch, 'emit');

        component.fullSearchTerm = 'test search';
        component.selectedConversations = [mockConversations[0]];
        component.selectedAuthors = [mockUsers[0]];

        component.onTriggerSearch();

        expect(onSearchSpy).toHaveBeenCalledWith({
            searchTerm: 'test search',
            selectedConversations: [mockConversations[0]],
            selectedAuthors: [mockUsers[0]],
        });
    });

    it('should close the dropdown when clicking outside the search input', () => {
        component.showDropdown = true;
        component.isSearchActive = true;

        const mockEvent = {
            target: document.createElement('div'),
        } as unknown as Event;

        component.onClickOutside(mockEvent);
        vi.advanceTimersByTime(0);

        expect(component.showDropdown).toBe(false);
        expect(component.isSearchActive).toBe(false);
    });

    it('should preselect a filter when onPreselectFilter is called', () => {
        const startFilteringSpy = vi.spyOn(component, 'startFiltering');
        const focusSpy = vi.spyOn(component, 'focusInput');

        component.onPreselectFilter(component.CONVERSATION_FILTER);
        vi.advanceTimersByTime(0);

        expect(component.fullSearchTerm).toBe('in:');
        expect(component.searchMode).toBe(component.SearchMode.CONVERSATION);
        expect(component.showDropdown).toBe(true);
        expect(startFilteringSpy).toHaveBeenCalled();
        expect(focusSpy).toHaveBeenCalled();

        startFilteringSpy.mockClear();
        focusSpy.mockClear();

        component.onPreselectFilter(component.USER_FILTER);
        vi.advanceTimersByTime(0);

        expect(component.fullSearchTerm).toBe('from:');
        expect(component.searchMode).toBe(component.SearchMode.USER);
        expect(component.showDropdown).toBe(true);
        expect(startFilteringSpy).toHaveBeenCalled();
        expect(focusSpy).toHaveBeenCalled();
    });

    it('should focus the input when Ctrl+K or Cmd+K is pressed', () => {
        const focusInputSpy = vi.spyOn(component, 'focusInput');
        const mockEvent = {
            metaKey: true, // Simulate Cmd key on macOS
            ctrlKey: false,
            key: 'k',
            preventDefault: vi.fn(),
        } as unknown as KeyboardEvent;

        component.handleSearchShortcut(mockEvent);

        expect(mockEvent.preventDefault).toHaveBeenCalled();
        expect(focusInputSpy).toHaveBeenCalled();

        // Test for Ctrl+K (non-macOS)
        const mockEventCtrl = {
            metaKey: false,
            ctrlKey: true, // Simulate Ctrl key on non-macOS
            key: 'k',
            preventDefault: vi.fn(),
        } as unknown as KeyboardEvent;

        component.handleSearchShortcut(mockEventCtrl);

        expect(mockEventCtrl.preventDefault).toHaveBeenCalled();
        expect(focusInputSpy).toHaveBeenCalled();
    });

    it('should handle errors in filterUsers gracefully', () => {
        vi.spyOn(courseManagementService, 'searchUsers').mockReturnValue(throwError(() => new Error('Error')));
        component.filterUsers('test');
        vi.advanceTimersByTime(0);
        fixture.changeDetectorRef.detectChanges();

        expect(component.filteredUsers).toEqual([]);
        expect(component.userSearchStatus).toBe(component.UserSearchStatus.RESULTS);
    });

    it('should not select a conversation when focusWithSelectedConversation is called with undefined', () => {
        component.focusWithSelectedConversation(undefined);
        expect(component.selectedConversations).toEqual([]);
    });
});
