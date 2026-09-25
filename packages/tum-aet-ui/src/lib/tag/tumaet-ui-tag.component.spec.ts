import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumAetUiTagComponent } from './tumaet-ui-tag.component';

describe('TumAetUiTagComponent', () => {
    let fixture: ComponentFixture<TumAetUiTagComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumAetUiTagComponent],
        }).compileComponents();
        fixture = TestBed.createComponent(TumAetUiTagComponent);
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

    it('renders the value input', () => {
        fixture.componentRef.setInput('value', 'Active');
        fixture.detectChanges();
        expect(tag().textContent?.trim()).toBe('Active');
    });
});
