import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { faPlus } from '@fortawesome/free-solid-svg-icons';

describe('PostingButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<PostingButtonComponent>;
    let debugElement: DebugElement;

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [PostingButtonComponent, FaIconComponent],
        });
        fixture = TestBed.createComponent(PostingButtonComponent);
        fixture.componentRef.setInput('buttonLabel', 'test');
        debugElement = fixture.debugElement;
    });

    it('should have icon shown if property set', () => {
        fixture.componentRef.setInput('buttonIcon', faPlus);
        fixture.changeDetectorRef.detectChanges();
        const button = getElement(debugElement, 'fa-icon');
        expect(button).not.toBeNull();
    });

    it('should not have icon shown if property not set', () => {
        fixture.detectChanges();
        const button = getElement(debugElement, '#icon');
        expect(button).toBeNull();
    });

    it('should show spinner if loading', () => {
        fixture.componentRef.setInput('buttonLoading', true);
        fixture.changeDetectorRef.detectChanges();
        const button = getElement(debugElement, '.posting-btn-loading-icon');
        expect(button.hasAttribute('hidden')).toBe(false);
    });

    it('should not show spinner if not loading', () => {
        fixture.componentRef.setInput('buttonLoading', false);
        fixture.changeDetectorRef.detectChanges();
        const button = getElement(debugElement, '.posting-btn-loading-icon');
        expect(button.hasAttribute('hidden')).toBe(true);
    });
});
