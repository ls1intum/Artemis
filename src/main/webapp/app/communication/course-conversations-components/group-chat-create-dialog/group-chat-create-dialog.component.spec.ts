import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GroupChatCreateDialogComponent } from 'app/communication/course-conversations-components/group-chat-create-dialog/group-chat-create-dialog.component';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('GroupChatCreateDialogComponent', () => {
    setupTestBed({ zoneless: true });
    let component: GroupChatCreateDialogComponent;
    let fixture: ComponentFixture<GroupChatCreateDialogComponent>;
    const course = { id: 1 };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [GroupChatCreateDialogComponent],
            providers: [MockProvider(NgbActiveModal)],
        })
            .overrideComponent(GroupChatCreateDialogComponent, {
                remove: { imports: [CourseUsersSelectorComponent, ArtemisTranslatePipe, TranslateDirective] },
                add: { imports: [MockComponent(CourseUsersSelectorComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(GroupChatCreateDialogComponent);
        component = fixture.componentInstance;
        initializeDialog(component, fixture, { course });
    });

    afterEach(() => vi.restoreAllMocks());

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.isInitialized).toBe(true);
    });

    it('should close the dialog with the selected users', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = vi.spyOn(activeModal, 'close');
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
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
        const dismissButton = fixture.debugElement.nativeElement.querySelector('.dismiss');
        dismissButton.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });
});
