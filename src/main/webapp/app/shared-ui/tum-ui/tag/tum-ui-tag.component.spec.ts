import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { TumUiTagComponent } from 'app/shared-ui/tum-ui/tag/tum-ui-tag.component';

describe('TumUiTagComponent', () => {
    setupTestBed({ zoneless: true });

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

    it('applies the success tint classes', () => {
        fixture.componentRef.setInput('severity', 'success');
        fixture.detectChanges();
        expect(tag().getAttribute('data-severity')).toBe('success');
        expect(tag().className).toContain('text-state-success');
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
});
