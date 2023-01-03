import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CourseUsersSelectorComponent } from 'app/shared/course-users-selector/course-users-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { initializeDialog } from '../dialog-test-helpers';

describe('GroupChatCreateDialogComponent', () => {
    let component: GroupChatCreateDialogComponent;
    let fixture: ComponentFixture<GroupChatCreateDialogComponent>;
    const course = { id: 1 };

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            declarations: [GroupChatCreateDialogComponent, MockComponent(CourseUsersSelectorComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(NgbActiveModal)],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(GroupChatCreateDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        initializeDialog(component, fixture, {
            course,
        });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.isInitialized).toBeTrue();
    });

    it('should close the dialog with the selected users', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');
        const selectedUsers = [{ id: 1 }, { id: 2 }];
        component.selectedUsersControl!.setValue(selectedUsers);
        fixture.detectChanges();
        const submitButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
        submitButton.click();
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(selectedUsers);
    });

    it('should dismiss modal if cancel is selected', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        const dismissButton = fixture.debugElement.nativeElement.querySelector('.dismiss');
        dismissButton.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });
});
