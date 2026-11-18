import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular';
import { By } from '@angular/platform-browser';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { ForwardMessageDialogComponent } from 'app/communication/course-conversations-components/forward-message-dialog/forward-message-dialog.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { UserPublicInfoDTO } from 'app/account/user/user.model';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MessageEditorComponent } from 'app/communication/message-editor/message-editor.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CommunicationService } from 'app/communication/service/communication.service';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';

describe('ForwardMessageDialogComponent', () => {
    let component: ForwardMessageDialogComponent;
    let fixture: ComponentFixture<ForwardMessageDialogComponent>;
    let searchInput: any;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                FormsModule,
                ForwardMessageDialogComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(MessageEditorComponent),
                MockComponent(ProfilePictureComponent),
                MockComponent(PostingContentComponent),
            ],
            providers: [
                MockProvider(TumAetUiDialogRef),
                MockProvider(TumAetUiDialogConfig),
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(ForwardMessageDialogComponent, {
                remove: { imports: [PostingContentComponent, MessageEditorComponent] },
                add: { imports: [MockComponent(PostingContentComponent), MockComponent(MessageEditorComponent)] },
            })
            .overrideComponent(ForwardMessageDialogComponent, {
                remove: { providers: [CommunicationService] },
                add: { providers: [MockProvider(CommunicationService)] },
            });

        global.ResizeObserver = class extends MockResizeObserver {
            constructor(callback: ResizeObserverCallback) {
                super(callback);
            }
        } as any;

        fixture = TestBed.createComponent(ForwardMessageDialogComponent);
        component = fixture.componentInstance;
        component.channels.set([
            { id: 1, name: 'General', type: 'channel' } as ChannelDTO,
            {
                id: 2,
                name: 'Announcements',
                type: 'channel',
            } as ChannelDTO,
            { id: 3, name: 'Group 1', type: 'groupChat' } as GroupChatDTO,
        ]);
        component.users.set([
            {
                id: 4,
                name: 'User1',
                imageUrl: 'user1.png',
            } as UserPublicInfoDTO,
        ]);
        component.postToForward.set({
            id: 10,
            content: 'Test Message',
            author: { id: 1, name: 'Author', imageUrl: 'author.png' },
        } as Post);
        searchInput = fixture.debugElement.query(By.css('input.tag-input')).nativeElement;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('uses the rich editor for the forwarding comment and immediately sends its latest content', () => {
        component.courseId.set(123);
        fixture.detectChanges();
        const editorElement = fixture.debugElement.query(By.directive(MessageEditorComponent));
        expect(editorElement).not.toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-markdown-editor-monaco')).toBeNull();
        const editor = editorElement.componentInstance as MessageEditorComponent;
        expect(editor.courseId()).toBe(123);
        expect(editor.suppressNewlineOnEnter()).toBe(false);
        editor.contentChange.emit('**Lecture notes**');
        component.selectedChannels = [{ id: 1, name: 'General' } as ChannelDTO];
        const close = vi.spyOn(TestBed.inject(TumAetUiDialogRef), 'close');
        fixture.detectChanges();
        (fixture.nativeElement.querySelector('[data-testid="forward-send-button"]') as HTMLButtonElement).click();
        expect(close).toHaveBeenCalledExactlyOnceWith({ channels: component.selectedChannels, users: [], messageContent: '**Lecture notes**' });
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('opens destinations with ArrowDown and selects the active recipient with Enter', () => {
        searchInput.focus();
        const down = new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true });
        searchInput.dispatchEvent(down);
        fixture.detectChanges();

        const firstOption = fixture.nativeElement.querySelector('[data-testid="forward-destination-option"]') as HTMLElement;
        expect(down.defaultPrevented).toBe(true);
        expect(searchInput.getAttribute('aria-expanded')).toBe('true');
        expect(searchInput.getAttribute('aria-activedescendant')).toBe(firstOption.id);
        expect(firstOption.getAttribute('aria-selected')).toBe('true');

        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        fixture.detectChanges();

        expect(component.selectedChannels.map((channel) => channel.name)).toEqual(['General']);
        expect(searchInput.getAttribute('aria-expanded')).toBe('false');
        expect(searchInput.getAttribute('aria-activedescendant')).toBeNull();
        expect(document.activeElement).toBe(searchInput);
    });

    it('selects the last recipient with ArrowUp and clears a stale active recipient after filtering', () => {
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true }));
        fixture.detectChanges();
        const options = fixture.nativeElement.querySelectorAll('[data-testid="forward-destination-option"]') as NodeListOf<HTMLElement>;
        expect(options).toHaveLength(4);
        expect(searchInput.getAttribute('aria-activedescendant')).toBe(options[options.length - 1].id);

        searchInput.value = 'General';
        searchInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        expect(searchInput.getAttribute('aria-activedescendant')).toBeNull();

        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(component.hasSelections()).toBe(false);
    });

    it.each(['ArrowDown', 'ArrowUp', 'Enter', 'Escape'])('leaves composing %s events to the input method', (key) => {
        searchInput.value = 'General';
        searchInput.dispatchEvent(new Event('input'));
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        fixture.detectChanges();
        const activeOption = searchInput.getAttribute('aria-activedescendant');
        expect(activeOption).toBeTruthy();
        const event = new KeyboardEvent('keydown', { key, isComposing: true, bubbles: true, cancelable: true });
        const enclosingKeydown = vi.fn();
        fixture.nativeElement.addEventListener('keydown', enclosingKeydown);

        searchInput.dispatchEvent(event);
        fixture.detectChanges();

        expect(event.defaultPrevented).toBe(false);
        expect(component.hasSelections()).toBe(false);
        expect(searchInput.value).toBe('General');
        expect(component.showDropdown()).toBe(true);
        expect(searchInput.getAttribute('aria-activedescendant')).toBe(activeOption);
        expect(enclosingKeydown).not.toHaveBeenCalled();
    });

    it('leaves Safari IME confirmation to the input method after compositionend', () => {
        searchInput.value = 'General';
        searchInput.dispatchEvent(new Event('input'));
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        fixture.detectChanges();
        const activeOption = searchInput.getAttribute('aria-activedescendant');
        const enclosingKeydown = vi.fn();
        fixture.nativeElement.addEventListener('keydown', enclosingKeydown);
        const confirmation = new KeyboardEvent('keydown', { key: 'Enter', keyCode: 229, isComposing: false, bubbles: true, cancelable: true });

        searchInput.dispatchEvent(confirmation);
        fixture.detectChanges();

        expect(confirmation.defaultPrevented).toBe(false);
        expect(component.hasSelections()).toBe(false);
        expect(component.showDropdown()).toBe(true);
        expect(searchInput.getAttribute('aria-activedescendant')).toBe(activeOption);
        expect(enclosingKeydown).not.toHaveBeenCalled();
    });

    it('dismisses destinations with Escape without propagating dismissal to the dialog', () => {
        searchInput.click();
        fixture.detectChanges();
        const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
        const parentKeydown = vi.fn();
        fixture.nativeElement.addEventListener('keydown', parentKeydown);

        searchInput.dispatchEvent(escape);
        fixture.detectChanges();

        expect(component.showDropdown()).toBe(false);
        expect(escape.defaultPrevented).toBe(true);
        expect(parentKeydown).not.toHaveBeenCalled();
    });

    it('scrolls the active destination into view when navigating a long list', () => {
        searchInput.click();
        fixture.detectChanges();
        const options = fixture.nativeElement.querySelectorAll('[data-testid="forward-destination-option"]') as NodeListOf<HTMLElement>;
        const lastOption = options[options.length - 1];
        lastOption.scrollIntoView = vi.fn();

        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true }));
        fixture.detectChanges();

        expect(lastOption.scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
    });

    it('keeps Tab and recipient removal buttons independent from combobox navigation', () => {
        component.selectOption(component.combinedOptions()[0]);
        searchInput.click();
        fixture.detectChanges();
        const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
        searchInput.dispatchEvent(tab);
        expect(tab.defaultPrevented).toBe(false);

        const removeButton = fixture.nativeElement.querySelector('.tag-input-container button') as HTMLButtonElement;
        const down = new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true });
        removeButton.dispatchEvent(down);
        expect(down.defaultPrevented).toBe(false);
        removeButton.click();
        expect(component.selectedChannels).toHaveLength(0);
    });

    it('reports the destination list as collapsed when filtering has no matches', () => {
        searchInput.value = 'Nonexistent destination';
        searchInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(searchInput.getAttribute('aria-expanded')).toBe('false');
        expect(searchInput.getAttribute('aria-controls')).toBeNull();
        expect(searchInput.getAttribute('aria-activedescendant')).toBeNull();
        const down = new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true });
        searchInput.dispatchEvent(down);
        expect(down.defaultPrevented).toBe(false);
    });

    it('lets Escape reach the dialog when an open search has no matching destinations', () => {
        searchInput.value = 'Nonexistent destination';
        searchInput.dispatchEvent(new Event('input'));
        searchInput.click();
        fixture.detectChanges();
        expect(component.showDropdown()).toBe(true);
        expect(searchInput.getAttribute('aria-expanded')).toBe('false');
        expect(fixture.nativeElement.querySelector('[role="listbox"]')).toBeNull();
        const dialogKeydown = vi.fn();
        fixture.nativeElement.addEventListener('keydown', dialogKeydown);
        const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });

        searchInput.dispatchEvent(escape);

        expect(escape.defaultPrevented).toBe(false);
        expect(dialogKeydown).toHaveBeenCalledExactlyOnceWith(escape);
    });

    it('keeps the active recipient at each end of the list while navigating with arrows', () => {
        const navigate = (key: string) => {
            searchInput.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
            fixture.detectChanges();
        };
        navigate('ArrowDown');
        const options = fixture.nativeElement.querySelectorAll('[data-testid="forward-destination-option"]') as NodeListOf<HTMLElement>;
        navigate('ArrowUp');
        expect(searchInput.getAttribute('aria-activedescendant')).toBe(options[0].id);
        for (let index = 0; index <= options.length; index++) {
            navigate('ArrowDown');
        }
        expect(searchInput.getAttribute('aria-activedescendant')).toBe(options[options.length - 1].id);
        navigate('ArrowUp');
        expect(searchInput.getAttribute('aria-activedescendant')).toBe(options[options.length - 2].id);
    });

    it('collapses the recipient list when focus leaves the input and lets Escape reach the dialog', () => {
        searchInput.click();
        fixture.detectChanges();
        expect(component.isInputFocused).toBe(true);
        searchInput.dispatchEvent(new FocusEvent('blur'));
        fixture.detectChanges();
        expect(component.isInputFocused).toBe(false);
        expect(searchInput.getAttribute('aria-expanded')).toBe('false');
        const keydown = vi.fn();
        fixture.nativeElement.addEventListener('keydown', keydown);
        const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
        searchInput.dispatchEvent(escape);
        expect(escape.defaultPrevented).toBe(false);
        expect(keydown).toHaveBeenCalledOnce();
    });

    it('offers remote user matches without selected users and sends the newly selected recipient', () => {
        component.courseId.set(123);
        component.selectedUsers = [component.users()[0]];
        const recipient = { id: 5, name: 'User Two', imageUrl: 'user2.png' } as UserPublicInfoDTO;
        const search = vi.spyOn(TestBed.inject(CourseManagementService), 'searchUsers').mockReturnValue(of(new HttpResponse({ body: [component.users()[0], recipient] })));
        searchInput.value = 'USER';
        searchInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(search).toHaveBeenCalledExactlyOnceWith(123, 'user', ['students', 'tutors', 'instructors']);
        const options = fixture.nativeElement.querySelectorAll('[data-testid="forward-destination-option"]') as NodeListOf<HTMLElement>;
        expect(options).toHaveLength(1);
        expect(options[0].textContent).toContain('User Two');
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        fixture.detectChanges();

        expect(component.selectedUsers).toEqual([component.users()[0], recipient]);
        expect(searchInput.getAttribute('aria-expanded')).toBe('false');
        const close = vi.spyOn(TestBed.inject(TumAetUiDialogRef), 'close');
        (fixture.nativeElement.querySelector('[data-testid="forward-send-button"]') as HTMLButtonElement).click();
        expect(close).toHaveBeenCalledWith({ channels: [], users: [component.users()[0], recipient], messageContent: undefined });
    });

    it('clears previous user matches while a different query is loading', () => {
        component.courseId.set(123);
        const alice = { id: 5, name: 'Alice' } as UserPublicInfoDTO;
        const pending = new Subject<HttpResponse<UserPublicInfoDTO[]>>();
        vi.spyOn(TestBed.inject(CourseManagementService), 'searchUsers')
            .mockReturnValueOnce(of(new HttpResponse({ body: [alice] })))
            .mockReturnValue(pending);
        searchInput.value = 'Alice';
        searchInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();
        expect(component.filteredOptions().map((option) => option.name)).toEqual(['Alice']);

        searchInput.value = 'Bob';
        searchInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component.filteredUsers()).toEqual([]);
        expect(fixture.nativeElement.querySelector('[data-testid="forward-destination-option"]')).toBeNull();
    });

    it.each([
        { query: 'Us', courseId: 123 },
        { query: 'User1', courseId: undefined },
    ])('keeps supplied users searchable for $query with course $courseId without a remote request', ({ query, courseId }) => {
        component.courseId.set(courseId);
        const search = vi.spyOn(TestBed.inject(CourseManagementService), 'searchUsers');
        searchInput.value = query;
        searchInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component.filteredOptions().map((option) => option.name)).toEqual(['User1']);
        expect(search).not.toHaveBeenCalled();
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(component.selectedUsers).toEqual(component.users());
    });

    it('ignores an older search response that arrives after the latest query results', () => {
        component.courseId.set(123);
        const older = new Subject<HttpResponse<UserPublicInfoDTO[]>>();
        const latest = new Subject<HttpResponse<UserPublicInfoDTO[]>>();
        const bob = { id: 6, name: 'Bob' } as UserPublicInfoDTO;
        vi.spyOn(TestBed.inject(CourseManagementService), 'searchUsers').mockReturnValueOnce(older).mockReturnValueOnce(latest);
        searchInput.value = 'Alice';
        searchInput.dispatchEvent(new Event('input'));
        searchInput.value = 'Bob';
        searchInput.dispatchEvent(new Event('input'));
        latest.next(new HttpResponse({ body: [bob] }));

        older.next(new HttpResponse({ body: [{ id: 5, name: 'Alice' } as UserPublicInfoDTO] }));
        fixture.detectChanges();

        expect(component.filteredOptions().map((option) => option.name)).toEqual(['Bob']);
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(component.selectedUsers).toEqual([bob]);
    });

    it('preserves a keyboard-selected channel when matching users arrive', () => {
        component.courseId.set(123);
        const pending = new Subject<HttpResponse<UserPublicInfoDTO[]>>();
        vi.spyOn(TestBed.inject(CourseManagementService), 'searchUsers').mockReturnValue(pending);
        searchInput.value = 'General';
        searchInput.dispatchEvent(new Event('input'));
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        fixture.detectChanges();
        const activeOption = searchInput.getAttribute('aria-activedescendant');
        expect(activeOption).toBeTruthy();

        pending.next(new HttpResponse({ body: [{ id: 6, name: 'General User' } as UserPublicInfoDTO] }));
        fixture.detectChanges();

        expect(searchInput.getAttribute('aria-activedescendant')).toBe(activeOption);
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        expect(component.selectedChannels.map((channel) => channel.name)).toEqual(['General']);
    });

    it('keeps the restored default users when an earlier search finishes after selection', () => {
        component.courseId.set(123);
        const pending = new Subject<HttpResponse<UserPublicInfoDTO[]>>();
        vi.spyOn(TestBed.inject(CourseManagementService), 'searchUsers').mockReturnValue(pending);
        searchInput.value = 'General';
        searchInput.dispatchEvent(new Event('input'));
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));

        pending.next(new HttpResponse({ body: [{ id: 6, name: 'Late search result' } as UserPublicInfoDTO] }));
        fixture.detectChanges();

        expect(component.selectedChannels.map((channel) => channel.name)).toEqual(['General']);
        expect(component.filteredUsers()).toEqual(component.users());
        expect(component.filteredOptions().map((option) => option.name)).toEqual(['General', 'Announcements', 'Group 1', 'User1']);
    });

    it.each([of(new HttpResponse<UserPublicInfoDTO[]>({})), throwError(() => new Error('Search unavailable'))])(
        'keeps matching channels available when remote user search returns no data or fails',
        (response) => {
            component.courseId.set(123);
            vi.spyOn(TestBed.inject(CourseManagementService), 'searchUsers').mockReturnValue(response);
            searchInput.value = 'General';
            searchInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();

            expect(component.filteredUsers()).toEqual([]);
            expect(component.filteredOptions().map((option) => option.name)).toEqual(['General']);
            searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
            searchInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
            expect(component.selectedChannels.map((channel) => channel.name)).toEqual(['General']);
        },
    );

    it('should initialize combined options with channels and users', () => {
        expect(component.combinedOptions()).toEqual([
            { id: 1, name: 'General', type: 'channel', img: '' },
            { id: 2, name: 'Announcements', type: 'channel', img: '' },
            { id: 3, name: 'Group 1', type: 'groupChat', img: '' },
            { id: 4, name: 'User1', type: 'user', img: 'user1.png' },
        ]);
    });

    it('should filter options based on search term', async () => {
        searchInput.value = 'General';
        searchInput.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component.filteredOptions()).toHaveLength(1);
        expect(component.filteredOptions()[0].name).toBe('General');
    });

    it('should select a channel and add it to selectedChannels', () => {
        const option = component.combinedOptions().find((opt) => opt.type === 'channel' && opt.name === 'General');
        component.selectOption(option!);
        fixture.detectChanges();

        expect(component.selectedChannels).toHaveLength(1);
        expect(component.selectedChannels[0].name).toBe('General');
    });

    it('should select a user and add it to selectedUsers', () => {
        const option = component.combinedOptions().find((opt) => opt.type === 'user' && opt.name === 'User1');
        component.selectOption(option!);
        fixture.detectChanges();

        expect(component.selectedUsers).toHaveLength(1);
        expect(component.selectedUsers[0].name).toBe('User1');
    });

    it('should remove a selected channel', () => {
        component.selectedChannels = [{ id: 1, name: 'General' } as ChannelDTO];
        component.removeSelectedChannel(component.selectedChannels[0]);
        fixture.detectChanges();

        expect(component.selectedChannels).toHaveLength(0);
    });

    it('shows every selected recipient as a removable chip', () => {
        component.selectOption(component.combinedOptions().find((option) => option.name === 'General')!);
        component.selectOption(component.combinedOptions().find((option) => option.name === 'User1')!);
        fixture.detectChanges();

        const chips = fixture.nativeElement.querySelectorAll('.tag-input-container tumaet-ui-chip') as NodeListOf<HTMLElement>;
        expect(chips).toHaveLength(2);
        expect(chips[0].textContent).toContain('General');
        expect(chips[1].textContent).toContain('User1');

        (chips[1].querySelector('button') as HTMLButtonElement).click();
        fixture.detectChanges();
        expect(component.selectedUsers).toHaveLength(0);
        expect(fixture.nativeElement.querySelectorAll('.tag-input-container tumaet-ui-chip')).toHaveLength(1);
    });

    it('should remove a selected chat', () => {
        component.selectedUsers = [{ id: 3, otherUserName: 'User1', otherUserImg: 'user1.png' } as UserPublicInfoDTO];
        component.removeSelectedUser(component.selectedUsers[0]);
        fixture.detectChanges();

        expect(component.selectedUsers).toHaveLength(0);
    });

    it('should send selected items when Send button is clicked', () => {
        const dialogRef = TestBed.inject(TumAetUiDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');
        component.selectedChannels = [{ id: 1, name: 'General' } as ChannelDTO];
        component.newPost.content = 'Test content';
        fixture.detectChanges();

        const sendButton = fixture.debugElement.query(By.css('[data-testid="forward-send-button"]')).nativeElement;
        sendButton.click();

        expect(closeSpy).toHaveBeenCalledWith({
            channels: component.selectedChannels,
            users: [],
            messageContent: 'Test content',
        });
    });

    it('should close the modal when cancel button is clicked', () => {
        const dialogRef = TestBed.inject(TumAetUiDialogRef);
        const dismissSpy = vi.spyOn(dialogRef, 'dismiss');

        const cancelButton = fixture.debugElement.query(By.css('[data-testid="dialog-close-button"]')).nativeElement;
        cancelButton.click();

        expect(dismissSpy).toHaveBeenCalled();
    });

    it('should focus the input field', () => {
        const inputElement = fixture.debugElement.query(By.css('input.tag-input')).nativeElement;
        vi.spyOn(inputElement, 'focus');

        component.focusInput();

        expect(inputElement.focus).toHaveBeenCalled();
    });

    it('should handle missing input element gracefully when focusInput is called', () => {
        // Clear DOM so the input element is not found
        const bodyChildren = Array.from(document.body.children);
        bodyChildren.forEach((child) => child.remove());
        expect(() => component.focusInput()).not.toThrow();
    });

    it('should not open the dropdown when the input is focused automatically while the dialog opens', () => {
        fixture.detectChanges();
        const inputElement = fixture.debugElement.query(By.css('input.tag-input')).nativeElement;

        inputElement.dispatchEvent(new Event('focus'));
        fixture.detectChanges();

        expect(component.showDropdown()).toBe(false);
        expect(fixture.debugElement.query(By.css('[data-testid="forward-destination-option"]'))).toBeNull();
    });

    it('should open dropdown when the input is clicked and close when clicked outside', () => {
        fixture.detectChanges();
        const inputElement = fixture.debugElement.query(By.css('input.tag-input')).nativeElement;

        inputElement.dispatchEvent(new Event('focus'));
        inputElement.click();
        fixture.detectChanges();
        expect(component.showDropdown()).toBe(true);
        expect(fixture.debugElement.query(By.css('[data-testid="forward-destination-option"]'))).not.toBeNull();

        document.body.click();
        fixture.detectChanges();
        expect(component.showDropdown()).toBe(false);
    });

    it('should clear filteredOptions when no matching results are found', async () => {
        const localSearchInput = fixture.debugElement.query(By.css('input.tag-input')).nativeElement;
        localSearchInput.value = 'NonExistentOption';
        localSearchInput.dispatchEvent(new Event('input'));

        await component.filterOptions();
        fixture.detectChanges();

        expect(component.filteredOptions()).toHaveLength(0);
    });

    it('should detect if content overflows and set isContentLong correctly', () => {
        fixture.detectChanges();

        const messageContentDebugElement = fixture.debugElement.query(By.css('#messageContent'));
        const nativeElement = messageContentDebugElement.nativeElement as HTMLElement;

        Object.defineProperty(nativeElement, 'scrollHeight', { value: 200, configurable: true });
        Object.defineProperty(nativeElement, 'clientHeight', { value: 100, configurable: true });

        component.checkIfContentOverflows();

        expect(component.isContentLong()).toBe(true);
    });

    it('should disable Send button if no content and no selections are made', () => {
        component.selectedChannels = [];
        component.selectedUsers = [];
        component.newPost.content = '';
        fixture.detectChanges();

        const sendButton = fixture.debugElement.query(By.css('[data-testid="forward-send-button"]')).nativeElement;
        expect(sendButton.disabled).toBe(true);
    });

    it('should send both channels and chats when selections are made', () => {
        const dialogRef = TestBed.inject(TumAetUiDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.selectedChannels = [{ id: 1, name: 'General' } as ChannelDTO];
        component.selectedUsers = [{ id: 3 } as UserPublicInfoDTO];
        component.newPost.content = 'Test content';
        fixture.detectChanges();

        const sendButton = fixture.debugElement.query(By.css('[data-testid="forward-send-button"]')).nativeElement;
        sendButton.click();

        expect(closeSpy).toHaveBeenCalledWith({
            channels: component.selectedChannels,
            users: component.selectedUsers,
            messageContent: 'Test content',
        });
    });

    it('should not add duplicate channels or users to selected lists', () => {
        const channelOption = component.combinedOptions().find((opt) => opt.type === 'channel' && opt.name === 'General');
        component.selectOption(channelOption!);
        component.selectOption(channelOption!);

        expect(component.selectedChannels).toHaveLength(1);

        const userOption = component.combinedOptions().find((opt) => opt.type === 'user' && opt.name === 'User1');
        component.selectOption(userOption!);
        component.selectOption(userOption!);

        expect(component.selectedUsers).toHaveLength(1);
    });

    it('should toggle showFullForwardedMessage and reflect changes', () => {
        expect(component.showFullForwardedMessage()).toBe(false);

        component.toggleShowFullForwardedMessage();
        expect(component.showFullForwardedMessage()).toBe(true);

        component.toggleShowFullForwardedMessage();
        expect(component.showFullForwardedMessage()).toBe(false);
    });

    it('should update newPost.content with the provided value', () => {
        const testContent = 'Updated content';
        component.updateField(testContent);

        expect(component.newPost.content).toBe(testContent);
    });

    it('should filter channels and users based on the search term', () => {
        const event = { target: { value: 'General' } } as unknown as Event;
        component.filterItems(event);

        expect(component.filteredChannels).toHaveLength(1);
        expect(component.filteredChannels[0].name).toBe('General');
        expect(component.filteredUsers()).toHaveLength(0);
    });

    it('should disable send button if message is too long', () => {
        const longText = 'a'.repeat(component.maxContentLength + 1);
        component.updateField(longText);
        component.selectedChannels = [{ id: 1, name: 'General' } as ChannelDTO];
        component.selectedUsers = [{ id: 3 } as UserPublicInfoDTO];
        fixture.detectChanges();

        const sendButton = fixture.debugElement.query(By.css('[data-testid="forward-send-button"]')).nativeElement;

        expect(component.isMessageValid()).toBe(false);
        expect(sendButton.disabled).toBe(true);
    });

    it('should enable send button if message is valid', () => {
        const longText = 'a'.repeat(component.maxContentLength);
        component.updateField(longText);
        component.selectedChannels = [{ id: 1, name: 'General' } as ChannelDTO];
        component.selectedUsers = [{ id: 3 } as UserPublicInfoDTO];
        fixture.detectChanges();

        const sendButton = fixture.debugElement.query(By.css('[data-testid="forward-send-button"]')).nativeElement;

        expect(component.isMessageValid()).toBe(true);
        expect(sendButton.disabled).toBe(false);
    });
});
