import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumUiTagComponent } from './tum-ui-tag.component';

describe('TumUiTagComponent', () => {
    let fixture: ComponentFixture<TumUiTagComponent>;
    let host: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiTagComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiTagComponent);
        host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function tag(): HTMLElement {
        return fixture.debugElement.query(By.css('span')).nativeElement;
    }

    it('defaults to the secondary severity, at medium size, on the host', () => {
        // On the host and not on the inner span: `tum-ui-tag[data-severity='danger']` has to match from outside,
        // at the same depth `tum-ui-message` already publishes it.
        expect(host.getAttribute('data-slot')).toBe('tag');
        expect(host.getAttribute('data-severity')).toBe('secondary');
        expect(host.getAttribute('data-size')).toBe('medium');
        expect(host.getAttribute('data-variant')).toBe('solid');
    });

    it('reflects the severity state', () => {
        fixture.componentRef.setInput('severity', 'success');
        fixture.detectChanges();
        expect(host.getAttribute('data-severity')).toBe('success');
    });

    it('normalises the deprecated warn spelling onto the package vocabulary', () => {
        fixture.componentRef.setInput('severity', 'warn');
        fixture.detectChanges();
        expect(host.getAttribute('data-severity')).toBe('warning');
    });

    it('renders the value input', () => {
        fixture.componentRef.setInput('value', 'Active');
        fixture.detectChanges();
        expect(tag().textContent?.trim()).toBe('Active');
    });

    it('can be sized to match the control beside it', () => {
        fixture.componentRef.setInput('size', 'small');
        fixture.detectChanges();
        expect(host.getAttribute('data-size')).toBe('small');
        expect(tag().className).toContain('tum:text-xs');
    });

    it('drops the weight but keeps the colour in the quiet variant', () => {
        fixture.componentRef.setInput('severity', 'danger');
        fixture.componentRef.setInput('variant', 'quiet');
        fixture.detectChanges();
        expect(host.getAttribute('data-variant')).toBe('quiet');
        expect(tag().className).not.toContain('tum:font-bold');
        expect(host.getAttribute('data-severity')).toBe('danger');
    });
});
