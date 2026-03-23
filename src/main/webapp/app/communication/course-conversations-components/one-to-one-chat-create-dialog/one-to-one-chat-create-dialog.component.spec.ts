import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { OneToOneChatCreateDialogComponent } from 'app/communication/course-conversations-components/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('OneToOneChatCreateDialogComponent', () => {
    setupTestBed({ zoneless: true });
    let component: OneToOneChatCreateDialogComponent;
    let fixture: ComponentFixture<OneToOneChatCreateDialogComponent>;
    const course = { id: 1 } as Course;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OneToOneChatCreateDialogComponent],
            providers: [MockProvider(NgbActiveModal)],
        })
            .overrideComponent(OneToOneChatCreateDialogComponent, {
                remove: { imports: [CourseUsersSelectorComponent, ArtemisTranslatePipe, TranslateDirective] },
                add: { imports: [MockComponent(CourseUsersSelectorComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(OneToOneChatCreateDialogComponent);
        component = fixture.componentInstance;
        initializeDialog(component, fixture, { course });
    });

    afterEach(() => vi.restoreAllMocks());

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.isInitialized).toBe(true);
    });

    it('should dismiss modal if cancel is selected', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
        const dismissButton = fixture.debugElement.nativeElement.querySelector('.dismiss');
        dismissButton.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });

    it('should close the dialog with the selected user once one is selected', () => {
        const selectedUser = { id: 1 };
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = vi.spyOn(activeModal, 'close');
        component.onSelectedUsersChange([selectedUser]);
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(selectedUser);
    });
});
