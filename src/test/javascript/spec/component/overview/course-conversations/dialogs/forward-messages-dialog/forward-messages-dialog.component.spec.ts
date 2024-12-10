import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { Post } from 'app/entities/metis/post.model';
import { ForwardMessageDialogComponent } from '../../../../../../../../main/webapp/app/overview/course-conversations/dialogs/forward-message-dialog/forward-message-dialog.component';
import { MarkdownEditorMonacoComponent } from '../../../../../../../../main/webapp/app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ProfilePictureComponent } from '../../../../../../../../main/webapp/app/shared/profile-picture/profile-picture.component';
import { ElementRef, runInInjectionContext, signal } from '@angular/core';
import { UserPublicInfoDTO } from '../../../../../../../../main/webapp/app/core/user/user.model';
import { CourseManagementService } from '../../../../../../../../main/webapp/app/course/manage/course-management.service';
import { MockCourseManagementService } from '../../../../../helpers/mocks/service/mock-course-management.service';

describe('ForwardMessageDialogComponent', () => {
    let component: ForwardMessageDialogComponent;
    let fixture: ComponentFixture<ForwardMessageDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [ForwardMessageDialogComponent, MockPipe(ArtemisTranslatePipe), MockComponent(MarkdownEditorMonacoComponent), MockComponent(ProfilePictureComponent)],
            providers: [MockProvider(NgbActiveModal), { provide: CourseManagementService, useClass: MockCourseManagementService }],
            imports: [FormsModule],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ForwardMessageDialogComponent);
        component = fixture.componentInstance;

        component.channels.set([{ id: 1, name: 'General' } as ChannelDTO, { id: 2, name: 'Announcements' } as ChannelDTO]);
        component.users.set([
            {
                id: 3,
                name: 'User1',
                imageUrl: 'user1.png',
            } as UserPublicInfoDTO,
        ]);
        component.postToForward.set({ id: 10, content: 'Test Message', author: { id: 1, name: 'Author', imageUrl: 'author.png' } } as Post);

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
            { id: 3, name: 'User1', type: 'user', img: 'user1.png' },
        ]);
    });

    it('should filter options based on search term', async () => {
        const searchInput = fixture.debugElement.query(By.css('input.tag-input')).nativeElement;
        searchInput.value = 'General';
        searchInput.dispatchEvent(new Event('input'));
        await fixture.whenStable();
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
        component.selectedUsers = [{ id: 3, otherUserName: 'User1', otherUserImg: 'user1.png' } as any];
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
        component.messageContent = {
            nativeElement: {
                scrollHeight: 200,
                clientHeight: 100,
            },
        } as ElementRef;

        component.checkIfContentOverflows();
        expect(component.isContentLong).toBeTrue();
    });

    it('should truncate forwarded content if it is too long', () => {
        const post = new Post();
        post.content = 'Line1\nLine2\nLine3\nLine4\nLine5\nLine6\nLine7';
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.postToForward = signal<Post | null>(post);
            component.isContentLong = true;
            component.maxLines = 5;

            const displayedContent = component.displayedForwardedContent();
            expect(displayedContent).toContain('Line1\nLine2\nLine3\nLine4\nLine5...');
        });
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
});
