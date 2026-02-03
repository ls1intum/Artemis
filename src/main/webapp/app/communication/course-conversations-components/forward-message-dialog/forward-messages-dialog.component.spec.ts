import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { ForwardMessageDialogComponent } from 'app/communication/course-conversations-components/forward-message-dialog/forward-message-dialog.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';

describe('ForwardMessageDialogComponent', () => {
    let component: ForwardMessageDialogComponent;
    let fixture: ComponentFixture<ForwardMessageDialogComponent>;
    let searchInput: any;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [
                ForwardMessageDialogComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(MarkdownEditorMonacoComponent),
                MockComponent(ProfilePictureComponent),
                MockComponent(PostingContentComponent),
            ],
            providers: [
                MockProvider(NgbActiveModal),
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
            imports: [FormsModule],
        }).compileComponents();

        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

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
        jest.restoreAllMocks();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize combined options with channels and users', () => {
        expect(component.combinedOptions).toEqual([
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

        expect(component.filteredOptions).toHaveLength(1);
        expect(component.filteredOptions[0].name).toBe('General');
    });

    it('should select a channel and add it to selectedChannels', () => {
        const option = component.combinedOptions.find((opt) => opt.type === 'channel' && opt.name === 'General');
        component.selectOption(option!);
        fixture.detectChanges();

        expect(component.selectedChannels).toHaveLength(1);
        expect(component.selectedChannels[0].name).toBe('General');
    });

    it('should select a user and add it to selectedUsers', () => {
        const option = component.combinedOptions.find((opt) => opt.type === 'user' && opt.name === 'User1');
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

    it('should remove a selected chat', () => {
        component.selectedUsers = [{ id: 3, otherUserName: 'User1', otherUserImg: 'user1.png' } as UserPublicInfoDTO];
        component.removeSelectedUser(component.selectedUsers[0]);
        fixture.detectChanges();

        expect(component.selectedUsers).toHaveLength(0);
    });

    it('should send selected items when Send button is clicked', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.selectedChannels = [{ id: 1, name: 'General' } as ChannelDTO];
        component.newPost.content = 'Test content';
        fixture.detectChanges();

        const sendButton = fixture.debugElement.query(By.css('button.btn-primary')).nativeElement;
        sendButton.click();

        expect(closeSpy).toHaveBeenCalledWith({
            channels: component.selectedChannels,
            users: [],
            messageContent: 'Test content',
        });
    });

    it('should close the modal when cancel button is clicked', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');

        const cancelButton = fixture.debugElement.query(By.css('.btn-close')).nativeElement;
        cancelButton.click();

        expect(dismissSpy).toHaveBeenCalled();
    });

    it('should focus the input field', () => {
        const inputElement = fixture.debugElement.query(By.css('input.tag-input')).nativeElement;
        jest.spyOn(inputElement, 'focus');

        component.focusInput();

        expect(inputElement.focus).toHaveBeenCalled();
    });

    it('should handle missing input element gracefully when focusInput is called', () => {
        document.body.innerHTML = ``;
        expect(() => component.focusInput()).not.toThrow();
    });

    it('should open dropdown when input is focused and close when clicked outside', () => {
        const inputElement = fixture.debugElement.query(By.css('input.tag-input')).nativeElement;

        inputElement.dispatchEvent(new Event('focus'));
        fixture.detectChanges();
        expect(component.showDropdown).toBeTrue();

        document.body.click();
        fixture.detectChanges();
        expect(component.showDropdown).toBeFalse();
    });

    it('should clear filteredOptions when no matching results are found', async () => {
        const searchInput = fixture.debugElement.query(By.css('input.tag-input')).nativeElement;
        searchInput.value = 'NonExistentOption';
        searchInput.dispatchEvent(new Event('input'));

        await component.filterOptions();
        fixture.detectChanges();

        expect(component.filteredOptions).toHaveLength(0);
    });

    it('should detect if content overflows and set isContentLong correctly', () => {
        fixture.detectChanges();

        const messageContentDebugElement = fixture.debugElement.query(By.css('#messageContent'));
        const nativeElement = messageContentDebugElement.nativeElement as HTMLElement;

        Object.defineProperty(nativeElement, 'scrollHeight', { value: 200, configurable: true });
        Object.defineProperty(nativeElement, 'clientHeight', { value: 100, configurable: true });

        component.checkIfContentOverflows();

        expect(component.isContentLong).toBeTrue();
    });

    it('should disable Send button if no content and no selections are made', () => {
        component.selectedChannels = [];
        component.selectedUsers = [];
        component.newPost.content = '';
        fixture.detectChanges();

        const sendButton = fixture.debugElement.query(By.css('button.btn-primary')).nativeElement;
        expect(sendButton.disabled).toBeTrue();
    });

    it('should send both channels and chats when selections are made', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');

        component.selectedChannels = [{ id: 1, name: 'General' } as ChannelDTO];
        component.selectedUsers = [{ id: 3 } as UserPublicInfoDTO];
        component.newPost.content = 'Test content';
        fixture.detectChanges();

        const sendButton = fixture.debugElement.query(By.css('button.btn-primary')).nativeElement;
        sendButton.click();

        expect(closeSpy).toHaveBeenCalledWith({
            channels: component.selectedChannels,
            users: component.selectedUsers,
            messageContent: 'Test content',
        });
    });

    it('should not add duplicate channels or users to selected lists', () => {
        const channelOption = component.combinedOptions.find((opt) => opt.type === 'channel' && opt.name === 'General');
        component.selectOption(channelOption!);
        component.selectOption(channelOption!);

        expect(component.selectedChannels).toHaveLength(1);

        const userOption = component.combinedOptions.find((opt) => opt.type === 'user' && opt.name === 'User1');
        component.selectOption(userOption!);
        component.selectOption(userOption!);

        expect(component.selectedUsers).toHaveLength(1);
    });

    it('should toggle showFullForwardedMessage and reflect changes', () => {
        expect(component.showFullForwardedMessage).toBeFalse();

        component.toggleShowFullForwardedMessage();
        expect(component.showFullForwardedMessage).toBeTrue();

        component.toggleShowFullForwardedMessage();
        expect(component.showFullForwardedMessage).toBeFalse();
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
        expect(component.filteredUsers).toHaveLength(0);
    });
});
