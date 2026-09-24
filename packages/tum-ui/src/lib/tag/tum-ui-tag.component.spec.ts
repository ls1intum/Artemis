import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumUiTagComponent } from './tum-ui-tag.component';

describe('TumUiTagComponent', () => {
    let fixture: ComponentFixture<TumUiTagComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiTagComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiTagComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function tag(): HTMLElement {
        return fixture.debugElement.query(By.css('span')).nativeElement;
    }

    it('defaults to the secondary severity', () => {
        expect(tag().getAttribute('data-severity')).toBe('secondary');
    });

    it('reflects the severity state', () => {
        fixture.componentRef.setInput('severity', 'success');
        fixture.detectChanges();
        expect(tag().getAttribute('data-severity')).toBe('success');
    });

    it('keeps the selected shape and semantic color classes', () => {
        fixture.componentRef.setInput('rounded', true);
        fixture.componentRef.setInput('severity', 'contrast');
        fixture.detectChanges();
        expect(tag().classList.contains('tum:rounded-full')).toBe(true);
        expect(tag().classList.contains('tum:rounded-md')).toBe(false);
        expect(tag().classList.contains('tum:bg-contrast-background')).toBe(true);
    });

    it('renders the value input', () => {
        fixture.componentRef.setInput('value', 'Active');
        fixture.detectChanges();
        expect(tag().textContent?.trim()).toBe('Active');
    });
});
