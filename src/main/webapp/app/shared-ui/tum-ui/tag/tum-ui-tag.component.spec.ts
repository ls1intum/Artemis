import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumUiTagComponent } from 'app/shared-ui/tum-ui/tag/tum-ui-tag.component';

describe('TumUiTagComponent', () => {
    let fixture: ComponentFixture<TumUiTagComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TumUiTagComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TumUiTagComponent);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function tag(): HTMLElement {
        return fixture.debugElement.query(By.css('span')).nativeElement;
    }

    it('defaults to the secondary severity', () => {
        expect(tag().getAttribute('data-severity')).toBe('secondary');
        expect(tag().className).toContain('bg-surface-200');
        expect(tag().className).toContain('rounded-md');
    });

    it('exposes the severity via data-severity (drives the p-tag-matched colors in the stylesheet)', () => {
        fixture.componentRef.setInput('severity', 'success');
        fixture.detectChanges();
        expect(tag().getAttribute('data-severity')).toBe('success');
    });

    it('renders the value input', () => {
        fixture.componentRef.setInput('value', 'Active');
        fixture.detectChanges();
        expect(tag().textContent?.trim()).toBe('Active');
    });

    it('uses a full radius when rounded', () => {
        fixture.componentRef.setInput('rounded', true);
        fixture.detectChanges();
        expect(tag().className).toContain('rounded-full');
    });

    it('forwards styleClass onto the tag pill', () => {
        fixture.componentRef.setInput('styleClass', 'break whitespace-nowrap');
        fixture.detectChanges();
        expect(tag().className).toContain('break');
        expect(tag().className).toContain('whitespace-nowrap');
    });
});
