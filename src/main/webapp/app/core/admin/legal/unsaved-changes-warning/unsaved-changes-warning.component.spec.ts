/**
 * Vitest tests for UnsavedChangesWarningComponent.
 * Tests the modal warning functionality for unsaved changes.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

import { UnsavedChangesWarningComponent } from 'app/core/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';

describe('UnsavedChangesWarningComponent', () => {
    setupTestBed({ zoneless: true });

    let component: UnsavedChangesWarningComponent;
    let fixture: ComponentFixture<UnsavedChangesWarningComponent>;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UnsavedChangesWarningComponent],
            providers: [{ provide: NgbActiveModal, useValue: { close: vi.fn(), dismiss: vi.fn() } }],
        })
            .overrideTemplate(UnsavedChangesWarningComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(UnsavedChangesWarningComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        fixture.detectChanges();
    });

    it('should create component', () => {
        expect(component).toBeTruthy();
    });

    it('should have undefined textMessage by default', () => {
        expect(component.textMessage).toBeUndefined();
    });

    it('should allow setting textMessage', () => {
        component.textMessage = 'artemisApp.legal.privacyStatement.unsavedChangesWarning';
        expect(component.textMessage).toBe('artemisApp.legal.privacyStatement.unsavedChangesWarning');
    });

    describe('discardContent', () => {
        it('should close the modal when discarding content', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');

            component.discardContent();

            expect(closeSpy).toHaveBeenCalledOnce();
        });
    });

    describe('continueEditing', () => {
        it('should dismiss the modal with cancel reason when continuing editing', () => {
            const dismissSpy = vi.spyOn(activeModal, 'dismiss');

            component.continueEditing();

            expect(dismissSpy).toHaveBeenCalledOnce();
            expect(dismissSpy).toHaveBeenCalledWith('cancel');
        });
    });
});
