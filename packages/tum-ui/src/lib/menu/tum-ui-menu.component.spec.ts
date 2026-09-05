import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DOWN_ARROW, ESCAPE } from '@angular/cdk/keycodes';
import { TumUiMenuComponent } from './tum-ui-menu.component';
import { TumUiMenuItemDirective } from './tum-ui-menu-item.directive';
import { TumUiMenuTriggerDirective } from './tum-ui-menu-trigger.directive';

@Component({
    imports: [TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective],
    template: `
        <button id="trigger" [tumUiMenuTrigger]="actions">Actions</button>
        <ng-template #actions>
            <tum-ui-menu>
                <button tumUiMenuItem (triggered)="picked.set('students')">Add students</button>
                <button tumUiMenuItem [disabled]="tutorsDisabled()" (triggered)="picked.set('tutors')">Add tutors</button>
                <a tumUiMenuItem href="#editors">Add editors</a>
            </tum-ui-menu>
        </ng-template>
    `,
})
class HostComponent {
    readonly picked = signal<string | undefined>(undefined);
    readonly tutorsDisabled = signal(false);
}

describe('TumUiMenuComponent', () => {
    let fixture: ComponentFixture<HostComponent>;
    let host: HostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        host = fixture.componentInstance;
        fixture.detectChanges();
    });

    const trigger = () => fixture.debugElement.query(By.css('#trigger')).nativeElement as HTMLButtonElement;
    const menu = () => document.querySelector('[role="menu"]') as HTMLElement | null;
    const items = () => Array.from(document.querySelectorAll('[role="menuitem"]')) as HTMLElement[];

    function open(): void {
        trigger().click();
        fixture.detectChanges();
    }

    it('marks the trigger as a closed menu button', () => {
        expect(trigger().getAttribute('aria-haspopup')).toBe('menu');
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
        expect(menu()).toBeNull();
    });

    it('opens a menu of menu items on click', () => {
        open();

        expect(menu()).not.toBeNull();
        expect(trigger().getAttribute('aria-expanded')).toBe('true');
        expect(items().map((item) => item.textContent!.trim())).toEqual(['Add students', 'Add tutors', 'Add editors']);
    });

    it('renders a link item as an anchor so it keeps native link behaviour', () => {
        open();

        const link = items()[2];
        expect(link.tagName).toBe('A');
        expect(link.getAttribute('href')).toBe('#editors');
    });

    it('moves focus to the first item when the menu opens', () => {
        open();

        expect(document.activeElement).toBe(items()[0]);
    });

    it('moves focus to the next item on ArrowDown', () => {
        open();

        menu()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: DOWN_ARROW, bubbles: true }));
        fixture.detectChanges();

        expect(document.activeElement).toBe(items()[1]);
    });

    it('closes on Escape and returns focus to the trigger', () => {
        open();

        menu()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: ESCAPE, bubbles: true }));
        fixture.detectChanges();

        expect(menu()).toBeNull();
        expect(document.activeElement).toBe(trigger());
    });

    it('emits the item and closes the menu when an item is chosen', () => {
        open();

        items()[0].click();
        fixture.detectChanges();

        expect(host.picked()).toBe('students');
        expect(menu()).toBeNull();
    });

    it('does not trigger a disabled item', () => {
        host.tutorsDisabled.set(true);
        fixture.detectChanges();
        open();

        expect(items()[1].getAttribute('aria-disabled')).toBe('true');

        items()[1].click();
        fixture.detectChanges();

        expect(host.picked()).toBeUndefined();
    });
});
