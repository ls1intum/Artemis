import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CollapsibleCardComponent } from 'app/exam/overview/summary/collapsible-card/collapsible-card.component';
import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let fixture: ComponentFixture<CollapsibleCardComponent>;
let component: CollapsibleCardComponent;

describe('CollapsibleCardComponent', () => {
    const toggleSpy = vi.fn();

    beforeEach(async () => {
        await TestBed.configureTestingModule({}).compileComponents();
        fixture = TestBed.createComponent(CollapsibleCardComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('toggleCollapse', toggleSpy);
        fixture.componentRef.setInput('isCardContentCollapsed', false);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should collapse and expand exercise when collapse button is clicked', () => {
        toggleSpy.mockReset();

        fixture.detectChanges();
        const toggleCollapseHeader = fixture.debugElement.query(By.css('[data-testid="collapsible-card-toggle"]'));

        expect(toggleCollapseHeader).not.toBeNull();

        toggleCollapseHeader.nativeElement.click();
        expect(toggleSpy).toHaveBeenCalledOnce();

        toggleCollapseHeader.nativeElement.click();
        expect(toggleSpy).toHaveBeenCalledTimes(2);

        // Reference component to silence unused-variable warnings.
        expect(component).toBeDefined();
    });

    it('should hide the content while the card is collapsed and keep it in the DOM', () => {
        fixture.componentRef.setInput('isCardContentCollapsed', true);
        fixture.detectChanges();

        const content = fixture.debugElement.query(By.css('[data-testid="collapsible-card-content"]'));
        const toggle = fixture.debugElement.query(By.css('[data-testid="collapsible-card-toggle"]'));
        expect(content.nativeElement.classList).toContain('hidden');
        expect(toggle.nativeElement.getAttribute('aria-expanded')).toBe('false');

        fixture.componentRef.setInput('isCardContentCollapsed', false);
        fixture.detectChanges();
        expect(content.nativeElement.classList).not.toContain('hidden');
        expect(toggle.nativeElement.getAttribute('aria-expanded')).toBe('true');
    });
});
