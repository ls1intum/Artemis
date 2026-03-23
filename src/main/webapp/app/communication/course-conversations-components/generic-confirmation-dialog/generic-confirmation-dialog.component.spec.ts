import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GenericConfirmationDialogComponent } from 'app/communication/course-conversations-components/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('GenericConfirmationDialogComponent', () => {
    setupTestBed({ zoneless: true });
    let component: GenericConfirmationDialogComponent;
    let fixture: ComponentFixture<GenericConfirmationDialogComponent>;
    const translationKeys = {
        titleKey: 'title',
        questionKey: 'question',
        descriptionKey: 'description',
        confirmButtonKey: 'confirm',
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [MockProvider(NgbActiveModal)],
        })
            .overrideComponent(GenericConfirmationDialogComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(GenericConfirmationDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('canBeUndone', true);
        fixture.componentRef.setInput('isDangerousAction', false);
        fixture.componentRef.setInput('translationParameters', {});
        initializeDialog(component, fixture, { translationKeys });
    });

    afterEach(() => vi.restoreAllMocks());

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should close modal if confirm is selected', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = vi.spyOn(activeModal, 'close');
        const confirmButton = fixture.debugElement.nativeElement.querySelector('.confirm');
        confirmButton.click();
        expect(closeSpy).toHaveBeenCalled();
    });

    it('should dismiss modal if cancel is selected', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('.cancel');
        cancelButton.click();
        expect(dismissSpy).toHaveBeenCalled();
    });
});
