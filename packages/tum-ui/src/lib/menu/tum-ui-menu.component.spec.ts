import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MenuHarness } from '@angular/aria/menu/testing';
import { TumUiMenuComponent } from './tum-ui-menu.component';
import { TumUiMenuItemDirective } from './tum-ui-menu-item.directive';
import { TumUiMenuTriggerDirective } from './tum-ui-menu-trigger.directive';

@Component({
    imports: [TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective],
    template: `
        <button id="before">Before</button>
        <button id="trigger" [tumUiMenuTrigger]="actions" (menuOpened)="events.push('opened')" (menuClosed)="events.push('closed')">Actions</button>
        <button id="after">After</button>
        <ng-template #actions>
            <tum-ui-menu>
                <button tumUiMenuItem (triggered)="picked.set('students')">Add students</button>
                <button tumUiMenuItem [disabled]="tutorsDisabled()" (triggered)="picked.set('tutors')">Add tutors</button>
                <a tumUiMenuItem href="#editors" (click)="$event.preventDefault(); linkClicks = linkClicks + 1" (triggered)="picked.set('editors')">Add editors</a>
            </tum-ui-menu>
        </ng-template>
    `,
})
class HostComponent {
    readonly picked = signal<string | undefined>(undefined);
    readonly tutorsDisabled = signal(false);
    readonly events: string[] = [];
    linkClicks = 0;
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

    afterEach(() => fixture.destroy());

    const trigger = () => fixture.debugElement.query(By.css('#trigger')).nativeElement as HTMLButtonElement;
    const menu = () => document.querySelector('[role="menu"]') as HTMLElement | null;
    const items = () => Array.from(document.querySelectorAll('[role="menuitem"]')) as HTMLElement[];

    async function settle(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
    }

    async function open(): Promise<void> {
        trigger().click();
        await settle();
    }

    async function press(target: HTMLElement, key: string, init: KeyboardEventInit = {}): Promise<KeyboardEvent> {
        const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init });
        target.dispatchEvent(event);
        await settle();
        return event;
    }

    it('marks the trigger as a closed menu button', () => {
        expect(trigger().getAttribute('aria-haspopup')).toBe('menu');
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
        expect(menu()).toBeNull();
    });

    it('opens a menu of menu items on click and links the trigger to it', async () => {
        await open();

        expect(menu()).not.toBeNull();
        expect(trigger().getAttribute('aria-expanded')).toBe('true');
        expect(trigger().getAttribute('aria-controls')).toBe(menu()!.id);
        expect(items().map((item) => item.textContent!.trim())).toEqual(['Add students', 'Add tutors', 'Add editors']);
        expect(host.events).toEqual(['opened']);
    });

    it('renders a link item as an anchor so it keeps native link behaviour', async () => {
        await open();

        const link = items()[2];
        expect(link.tagName).toBe('A');
        expect(link.getAttribute('href')).toBe('#editors');
    });

    it('moves focus to the first item when the menu opens', async () => {
        await open();

        expect(document.activeElement).toBe(items()[0]);
        expect(items().map((item) => item.getAttribute('tabindex'))).toEqual(['0', '-1', '-1']);
    });

    it.each(['Enter', ' ', 'ArrowDown'])('opens from the keyboard with %j and focuses the first item', async (key) => {
        trigger().focus();
        const event = await press(trigger(), key);

        expect(menu()).not.toBeNull();
        expect(event.defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(items()[0]);
    });

    it('opens with ArrowUp and focuses the last item', async () => {
        trigger().focus();
        await press(trigger(), 'ArrowUp');

        expect(document.activeElement).toBe(items()[2]);
    });

    it('moves focus with ArrowDown / ArrowUp, wrapping at either end', async () => {
        await open();

        await press(items()[0], 'ArrowDown');
        expect(document.activeElement).toBe(items()[1]);

        await press(items()[1], 'ArrowUp');
        await press(items()[0], 'ArrowUp');
        expect(document.activeElement).toBe(items()[2]);

        await press(items()[2], 'ArrowDown');
        expect(document.activeElement).toBe(items()[0]);
    });

    it('jumps to the first / last item with Home / End', async () => {
        await open();

        await press(items()[0], 'End');
        expect(document.activeElement).toBe(items()[2]);

        await press(items()[2], 'Home');
        expect(document.activeElement).toBe(items()[0]);
    });

    it('moves focus to the item whose label starts with the typed characters', async () => {
        await open();

        await press(items()[0], 'a');
        await press(document.activeElement as HTMLElement, 'd');
        await press(document.activeElement as HTMLElement, 'd');
        await press(document.activeElement as HTMLElement, ' ');
        await press(document.activeElement as HTMLElement, 'e');
        expect(document.activeElement).toBe(items()[2]);
        // A space while typing extends the search instead of choosing the focused item.
        expect(host.picked()).toBeUndefined();
    });

    it('closes on Escape and returns focus to the trigger', async () => {
        await open();

        await press(items()[0], 'Escape');

        expect(menu()).toBeNull();
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
        expect(document.activeElement).toBe(trigger());
        expect(host.events).toEqual(['opened', 'closed']);
    });

    it('closes on Tab and continues from the trigger, so focus stays in the page', async () => {
        await open();

        const event = await press(items()[1], 'Tab');

        expect(menu()).toBeNull();
        expect(document.activeElement).toBe(trigger());
        // The browser then moves focus on from the trigger, as for any other Tab.
        expect(event.defaultPrevented).toBe(false);
    });

    it('closes when focus moves elsewhere', async () => {
        await open();

        (fixture.nativeElement.querySelector('#after') as HTMLButtonElement).focus();
        await settle();

        expect(menu()).toBeNull();
        expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#after'));
    });

    it('emits the item and closes the menu when an item is clicked', async () => {
        await open();

        items()[0].click();
        await settle();

        expect(host.picked()).toBe('students');
        expect(menu()).toBeNull();
        expect(document.activeElement).toBe(trigger());
    });

    it.each(['Enter', ' '])('emits the focused item and closes the menu on %j', async (key) => {
        await open();
        await press(items()[0], 'ArrowDown');

        await press(items()[1], key);

        expect(host.picked()).toBe('tutors');
        expect(menu()).toBeNull();
        expect(document.activeElement).toBe(trigger());
    });

    it('lets Enter on a link item activate the link natively', async () => {
        await open();
        await press(items()[0], 'End');

        const event = await press(items()[2], 'Enter');

        // Not cancelled, so the browser turns the key press into a click on the link, which then chooses the item.
        expect(event.defaultPrevented).toBe(false);
        items()[2].click();
        await settle();
        expect(host.linkClicks).toBe(1);
        expect(host.picked()).toBe('editors');
        expect(menu()).toBeNull();
    });

    it('keeps a disabled item focusable but does not trigger it', async () => {
        host.tutorsDisabled.set(true);
        await settle();
        await open();

        expect(items()[1].getAttribute('aria-disabled')).toBe('true');

        items()[1].click();
        await settle();
        expect(host.picked()).toBeUndefined();
        expect(menu()).not.toBeNull();

        await press(document.activeElement as HTMLElement, 'Home');
        await press(items()[0], 'ArrowDown');
        expect(document.activeElement).toBe(items()[1]);
        await press(items()[1], 'Enter');
        expect(host.picked()).toBeUndefined();
        expect(menu()).not.toBeNull();
    });

    it('renders a fresh menu each time it opens', async () => {
        await open();
        await press(items()[0], 'End');
        await press(items()[2], 'Escape');

        await open();

        expect(menu()).not.toBeNull();
        expect(document.activeElement).toBe(items()[0]);
        expect(trigger().getAttribute('aria-controls')).toBe(menu()!.id);
        expect(host.events).toEqual(['opened', 'closed', 'opened']);
    });

    it('lets Escape on a closed trigger reach enclosing handlers', async () => {
        const escapes: KeyboardEvent[] = [];
        const listener = (event: Event) => escapes.push(event as KeyboardEvent);
        document.body.addEventListener('keydown', listener);
        trigger().focus();

        const event = await press(trigger(), 'Escape');
        document.body.removeEventListener('keydown', listener);

        expect(escapes).toEqual([event]);
        expect(event.defaultPrevented).toBe(false);
        expect(menu()).toBeNull();
    });

    it('closes on Escape when focus is on the trigger of an open menu', async () => {
        await open();
        trigger().focus();
        await settle();

        const event = await press(trigger(), 'Escape');

        expect(event.defaultPrevented).toBe(true);
        expect(menu()).toBeNull();
        expect(document.activeElement).toBe(trigger());
    });

    it('chooses the active item with Enter while focus is on the menu surface', async () => {
        await open();
        await press(items()[0], 'ArrowDown');
        menu()!.focus();
        await settle();

        await press(menu()!, 'Enter');

        expect(host.picked()).toBe('tutors');
        expect(menu()).toBeNull();
    });

    it('toggles closed when the trigger is clicked again', async () => {
        await open();

        trigger().click();
        await settle();

        expect(menu()).toBeNull();
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
    });

    it('is driven by the Angular Aria menu harness', async () => {
        const loader = TestbedHarnessEnvironment.documentRootLoader(fixture);
        await open();
        const harness = await loader.getHarness(MenuHarness.with({ triggerText: 'Actions' }));

        expect(await harness.isOpen()).toBe(true);
        const [students] = await harness.getItems({ text: 'Add students' });
        await students.click();

        expect(host.picked()).toBe('students');
        expect(menu()).toBeNull();
    });

    it('releases the overlay when the trigger is destroyed', async () => {
        await open();
        const overlayPane = menu()!.closest('.cdk-overlay-pane');
        expect(overlayPane).not.toBeNull();

        fixture.destroy();

        expect(overlayPane!.isConnected).toBe(false);
    });
});

@Component({
    imports: [TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective],
    template: `
        <button id="bound" [tumUiMenuTrigger]="actions" [disabled]="disabled()" (click)="clicks = clicks + 1">Bound</button>
        <button id="static" [tumUiMenuTrigger]="actions" disabled>Static</button>
        <ng-template #actions>
            <tum-ui-menu>
                <button tumUiMenuItem>Add students</button>
            </tum-ui-menu>
        </ng-template>
    `,
})
class DisabledTriggerHostComponent {
    readonly disabled = signal(true);
    clicks = 0;
}

describe('TumUiMenuTriggerDirective (disabled)', () => {
    let fixture: ComponentFixture<DisabledTriggerHostComponent>;

    beforeEach(async () => {
        fixture = TestBed.createComponent(DisabledTriggerHostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => fixture.destroy());

    const button = (id: string) => fixture.nativeElement.querySelector(`#${id}`) as HTMLButtonElement;

    it('disables the trigger natively, so it can neither be clicked nor open its menu', async () => {
        expect(button('bound').disabled).toBe(true);
        expect(button('bound').getAttribute('aria-disabled')).toBe('true');

        button('bound').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.componentInstance.clicks).toBe(0);
        expect(document.querySelector('[role="menu"]')).toBeNull();
    });

    it('keeps a static disabled attribute', () => {
        expect(button('static').disabled).toBe(true);
        expect(button('static').hasAttribute('disabled')).toBe(true);
    });

    it('opens once it is enabled again', async () => {
        fixture.componentInstance.disabled.set(false);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(button('bound').disabled).toBe(false);
        button('bound').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(fixture.componentInstance.clicks).toBe(1);
        expect(document.querySelector('[role="menu"]')).not.toBeNull();
    });
});
