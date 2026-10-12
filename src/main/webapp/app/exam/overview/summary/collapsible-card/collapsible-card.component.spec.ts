import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CollapsibleCardComponent } from 'app/exam/overview/summary/collapsible-card/collapsible-card.component';
import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

let fixture: ComponentFixture<CollapsibleCardComponent>;
let component: CollapsibleCardComponent;

@Component({
    imports: [CollapsibleCardComponent],
    template: `
        <jhi-collapsible-card [isCardContentCollapsed]="false" [toggleCollapse]="toggle">
            <div class="header" data-testid="projected-header">A very long title</div>
            <div class="collapsible-content" data-testid="projected-content">Content</div>
        </jhi-collapsible-card>
    `,
})
class CollapsibleCardHostComponent {
    toggle = () => {};
}

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

    describe('chrome', () => {
        const toggle = () => fixture.debugElement.query(By.css('[data-testid="collapsible-card-toggle"]')).nativeElement as HTMLElement;
        const content = () => fixture.debugElement.query(By.css('[data-testid="collapsible-card-content"]')).nativeElement as HTMLElement;

        it('should use a 40px header with the shared 16px inset', () => {
            fixture.detectChanges();

            expect(toggle().classList).toContain('h-10');
            expect(toggle().classList).toContain('px-[16px]');
            expect(content().classList).toContain('px-[16px]');
            expect(content().classList).toContain('py-[12px]');
        });

        it('should draw the header rule only while the content is open', () => {
            fixture.detectChanges();
            expect(toggle().classList).toContain('border-b');

            fixture.componentRef.setInput('isCardContentCollapsed', true);
            fixture.detectChanges();
            expect(toggle().classList).not.toContain('border-b');

            fixture.componentRef.setInput('isCardContentCollapsed', false);
            fixture.detectChanges();
            expect(toggle().classList).toContain('border-b');
        });

        it('should separate consecutive cards by the 6px divider and use one border colour', () => {
            fixture.detectChanges();

            const root = fixture.nativeElement.firstElementChild as HTMLElement;
            expect(root.classList).toContain('mb-(--spacing-divider)');
            expect(root.classList).not.toContain('first-of-type:mt-4');
            expect(root.classList).toContain('border-(--border-color)');
            expect(toggle().classList).toContain('border-(--border-color)');
        });

        it('should render a small chevron that rotates with the state', () => {
            fixture.detectChanges();
            const chevron = toggle().querySelector('span[aria-hidden="true"]') as HTMLElement;

            expect(chevron.classList).toContain('size-6');
            expect(chevron.classList).toContain('rotate-90');
            expect(chevron.querySelector('fa-icon')?.getAttribute('size')).toBeNull();

            fixture.componentRef.setInput('isCardContentCollapsed', true);
            fixture.detectChanges();
            expect(chevron.classList).not.toContain('rotate-90');
        });
    });

    it('should let the projected header shrink so that a long title cannot push the chevron out of the card', () => {
        const hostFixture = TestBed.createComponent(CollapsibleCardHostComponent);
        hostFixture.detectChanges();

        const header = hostFixture.nativeElement.querySelector('[data-testid="projected-header"]') as HTMLElement;
        const wrapper = header.parentElement as HTMLElement;
        expect(wrapper.classList).toContain('min-w-0');
        expect(wrapper.classList).toContain('flex-1');
        expect(wrapper.parentElement?.getAttribute('data-testid')).toBe('collapsible-card-toggle');
        expect(hostFixture.nativeElement.querySelector('[data-testid="collapsible-card-content"] [data-testid="projected-content"]')).not.toBeNull();
    });
});
