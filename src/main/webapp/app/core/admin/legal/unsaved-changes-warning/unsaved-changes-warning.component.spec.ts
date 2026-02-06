/**
 * Vitest tests for UnsavedChangesWarningComponent.
 * Tests the modal warning functionality for unsaved changes.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { UnsavedChangesWarningComponent } from 'app/core/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';

describe('UnsavedChangesWarningComponent', () => {
    setupTestBed({ zoneless: true });

    let component: UnsavedChangesWarningComponent;
    let fixture: ComponentFixture<UnsavedChangesWarningComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UnsavedChangesWarningComponent],
        })
            .overrideTemplate(UnsavedChangesWarningComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(UnsavedChangesWarningComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create component', () => {
        expect(component).toBeTruthy();
    });

    it('should have undefined textMessage by default', () => {
        expect(component.textMessage()).toBeUndefined();
    });

    it('should accept textMessage via input', () => {
        fixture.componentRef.setInput('textMessage', 'artemisApp.legal.privacyStatement.unsavedChangesWarning');
        expect(component.textMessage()).toBe('artemisApp.legal.privacyStatement.unsavedChangesWarning');
    });

    describe('discardContent', () => {
        it('should emit discarded and set visible to false when discarding content', () => {
            const discardedSpy = vi.fn();
            component.discarded.subscribe(discardedSpy);
            component.visible.set(true);

            component.discardContent();

            expect(discardedSpy).toHaveBeenCalledOnce();
            expect(component.visible()).toBe(false);
        });
    });

    describe('continueEditing', () => {
        it('should set visible to false when continuing editing', () => {
            component.visible.set(true);

            component.continueEditing();

            expect(component.visible()).toBe(false);
        });
    });
});
