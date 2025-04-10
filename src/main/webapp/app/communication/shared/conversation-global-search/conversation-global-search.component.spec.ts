import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ConversationGlobalSearchComponent } from './conversation-global-search.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ConversationDTO, ConversationType } from '../entities/conversation/conversation.model';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { By } from '@angular/platform-browser';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { OneToOneChat } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';

describe('ConversationGlobalSearchComponent', () => {
    let component: ConversationGlobalSearchComponent;
    let fixture: ComponentFixture<ConversationGlobalSearchComponent>;
    let courseManagementService: CourseManagementService;

    const mockConversations: ConversationDTO[] = [
        { id: 1, name: 'General Channel', type: ConversationType.CHANNEL } as ChannelDTO,
        { id: 1, name: 'Exercise Channel', type: ConversationType.CHANNEL } as ChannelDTO,
        { id: 3, name: 'One-to-One Chat', type: ConversationType.ONE_TO_ONE } as OneToOneChat,
    ];

    const mockUsers: UserPublicInfoDTO[] = [
        { id: 1, name: 'John Doe', imageUrl: 'john.jpg' } as UserPublicInfoDTO,
        { id: 2, name: 'Jane Smith', imageUrl: 'jane.jpg' } as UserPublicInfoDTO,
    ];

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule],
            declarations: [
                ConversationGlobalSearchComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ButtonComponent),
                MockComponent(FaIconComponent),
                MockComponent(ProfilePictureComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(CourseManagementService, {
                    searchUsers: jest.fn(() => of(new HttpResponse({ body: mockUsers }))),
                }),
            ],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ConversationGlobalSearchComponent);
        component = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);

        // Set up necessary inputs
        fixture.componentRef.setInput('conversations', mockConversations);
        fixture.componentRef.setInput('courseId', 1);

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should filter conversations when using "in:" prefix', fakeAsync(() => {
        // Simulate input with "in:" prefix
        const inputEl = fixture.debugElement.query(By.css('input#search')).nativeElement;
        inputEl.value = 'in:general';
        inputEl.dispatchEvent(new Event('input'));
        tick();
        fixture.detectChanges();

        // Verify that dropdown is shown with filtered results
        expect(component.showDropdown).toBeTrue();
        expect(component.searchMode).toBe(component.SearchMode.CONVERSATION);
        expect(component.filteredOptions).toHaveLength(1);
        expect(component.filteredOptions[0].name).toBe('General Channel');
    }));

    it('should filter users when using "by:" prefix and search length >= 3', fakeAsync(() => {
        // Simulate input with "by:" prefix
        const inputEl = fixture.debugElement.query(By.css('input#search')).nativeElement;
        inputEl.value = 'by:john';
        inputEl.dispatchEvent(new Event('input'));
        tick();
        fixture.detectChanges();

        // Verify API is called to search users
        expect(courseManagementService.searchUsers).toHaveBeenCalledWith(1, 'john', ['students', 'tutors', 'instructors']);

        // Verify dropdown shows user results
        expect(component.showDropdown).toBeTrue();
        expect(component.searchMode).toBe(component.SearchMode.USER);
        expect(component.userSearchStatus).toBe(component.UserSearchStatus.RESULTS);
        expect(component.filteredUsers).toEqual(mockUsers);
    }));

    it('should select conversation from dropdown and emit selection change', fakeAsync(() => {
        const selectionChangeSpy = jest.spyOn(component.onSelectionChange, 'emit');
        component.fullSearchTerm = 'in:general';
        component.filterItems({ target: { value: 'in:general' } } as unknown as Event);
        tick();
        fixture.detectChanges();

        component.selectOption(component.filteredOptions[0]);
        tick();
        fixture.detectChanges();

        // Verify selection was added and event was emitted
        expect(component.selectedConversations).toHaveLength(1);
        expect(selectionChangeSpy).toHaveBeenCalledWith({
            searchTerm: '',
            selectedConversations: component.selectedConversations,
            selectedAuthors: [],
        });
    }));

    it('should remove selected conversation and emit selection change', fakeAsync(() => {
        const selectionChangeSpy = jest.spyOn(component.onSelectionChange, 'emit');
        const focusSpy = jest.spyOn(component, 'focusInput');
        component.selectedConversations = [mockConversations[0]];
        fixture.detectChanges();

        component.removeSelectedChannel(mockConversations[0]);
        tick();
        fixture.detectChanges();

        // Verify conversation was removed and event was emitted
        expect(component.selectedConversations).toHaveLength(0);
        expect(selectionChangeSpy).toHaveBeenCalled();
        expect(focusSpy).toHaveBeenCalled();
    }));

    it('should navigate dropdown with keyboard and select with enter', fakeAsync(() => {
        const selectOptionSpy = jest.spyOn(component, 'selectOption');
        component.fullSearchTerm = 'in:general';
        component.filterItems({ target: { value: 'in:general' } } as unknown as Event);
        tick();
        fixture.detectChanges();

        component.navigateDropdown(1, { preventDefault: jest.fn() } as unknown as Event);
        expect(component.activeDropdownIndex).toBe(0);
        // Simulate enter press
        component.selectActiveOption();

        expect(selectOptionSpy).toHaveBeenCalledWith(component.filteredOptions[0]);
    }));

    it('should focus input and select a conversation when focusWithSelectedConversation is called', fakeAsync(() => {
        const focusSpy = jest.spyOn(component, 'focusInput');
        component.focusWithSelectedConversation(mockConversations[0]);
        tick();
        fixture.detectChanges();

        expect(component.selectedConversations).toEqual([mockConversations[0]]);
        expect(focusSpy).toHaveBeenCalled();
    }));
});
