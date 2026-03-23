import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { faPlus } from '@fortawesome/free-solid-svg-icons';

describe('PostingButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let component: PostingButtonComponent;
    let fixture: ComponentFixture<PostingButtonComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PostingButtonComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(PostingButtonComponent);
        component = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    it('should have icon shown if property set', () => {
        fixture.componentRef.setInput('buttonIcon', faPlus);
        fixture.detectChanges();
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
        fixture.detectChanges();
        const button = getElement(debugElement, '.posting-btn-loading-icon');
        expect(button.hasAttribute('hidden')).toBe(false);
    });

    it('should not show spinner if not loading', () => {
        fixture.componentRef.setInput('buttonLoading', false);
        fixture.detectChanges();
        const button = getElement(debugElement, '.posting-btn-loading-icon');
        expect(button.hasAttribute('hidden')).toBe(true);
    });
});
