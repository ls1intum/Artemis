import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { TumUiPopoverComponent } from './tum-ui-popover.component';

describe('TumUiPopoverComponent', () => {
    let component: TumUiPopoverComponent;
    let fixture: ComponentFixture<TumUiPopoverComponent>;
    let origin: HTMLButtonElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiPopoverComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumUiPopoverComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('ariaLabel', 'Test popover');
        fixture.detectChanges();
        origin = document.createElement('button');
        document.body.appendChild(origin);
    });

    afterEach(() => {
        component.close();
        origin.remove();
        vi.restoreAllMocks();
    });

    describe('leaving', () => {
        const panel = () => document.querySelector('.tum-ui-popover-panel');

        /** jsdom has no animation API, so one is stood in whose finishing this test controls. */
        function stubAnimations(): { finish: () => void } {
            let settle: () => void = () => undefined;
            const finished = new Promise<void>((resolve) => (settle = resolve));
            Element.prototype.animate = (() => ({ finished })) as unknown as typeof Element.prototype.animate;
            return { finish: settle };
        }

        afterEach(() => {
            // @ts-expect-error - removing the stub restores jsdom's own absence of the API
            delete Element.prototype.animate;
        });

        it('counts as closed at once, so a caller driving its own state does not wait for the fade', () => {
            const { finish } = stubAnimations();
            component.open(origin);
            const emitSpy = vi.spyOn(component.openChange, 'emit');

            component.close();

            expect(component.isOpen()).toBe(false);
            expect(emitSpy).toHaveBeenCalledWith(false);
            finish();
        });

        it('keeps the panel up until the fade finishes, then takes it away', async () => {
            const { finish } = stubAnimations();
            component.open(origin);
            fixture.detectChanges();
            expect(panel()).not.toBeNull();

            component.close();
            // Still on screen: this is the fade the reader is watching.
            expect(panel()).not.toBeNull();

            finish();
            await Promise.resolve();
            await Promise.resolve();

            expect(panel()).toBeNull();
        });

        it('goes at once for a reader who has asked for no motion', () => {
            stubAnimations();
            const view = document.defaultView!;
            const previous = view.matchMedia;
            view.matchMedia = (() => ({ matches: true })) as unknown as typeof view.matchMedia;
            component.open(origin);
            fixture.detectChanges();

            component.close();

            expect(panel()).toBeNull();
            view.matchMedia = previous;
        });

        it('opens again cleanly while the last one is still leaving', async () => {
            const { finish } = stubAnimations();
            component.open(origin);
            component.close();

            component.open(origin);
            fixture.detectChanges();

            expect(component.isOpen()).toBe(true);
            finish();
            await Promise.resolve();
            await Promise.resolve();
            // The one that was leaving took itself away; the new one stayed.
            expect(panel()).not.toBeNull();
            expect(component.isOpen()).toBe(true);
        });
    });

    describe('the side it grows from', () => {
        const panel = () => document.querySelector('.tum-ui-popover-panel');

        it('starts from the side it was asked for', () => {
            fixture.componentRef.setInput('placement', 'top');

            component.open(origin);

            // The signal rather than the rendered attribute: jsdom lays nothing out, so the overlay measures zero and
            // CDK re-reports a fallback side the moment it positions the panel. The attribute is covered below.
            expect(component['appliedPlacement']()).toBe('top');
        });

        it('follows the panel when there is no room and it is flipped', () => {
            fixture.componentRef.setInput('placement', 'bottom');
            component.open(origin);
            fixture.detectChanges();
            expect(panel()?.getAttribute('data-placement')).toBe('bottom');

            // What CDK reports after flipping a panel that would not fit below its origin.
            const strategy = component['overlayRef']!.getConfig().positionStrategy as unknown as {
                positionChanges: { next: (change: unknown) => void };
            };
            strategy.positionChanges.next({ connectionPair: { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom' } });
            fixture.detectChanges();

            // The animation grows the panel from the edge nearest its origin, which this attribute selects.
            expect(panel()?.getAttribute('data-placement')).toBe('top');
        });
    });

    it('opens: sets isOpen and emits openChange(true)', () => {
        const emitSpy = vi.spyOn(component.openChange, 'emit');
        component.open(origin);
        expect(component.isOpen()).toBe(true);
        expect(emitSpy).toHaveBeenCalledWith(true);
    });

    it('closes: clears isOpen and emits openChange(false)', () => {
        component.open(origin);
        const emitSpy = vi.spyOn(component.openChange, 'emit');
        component.close();
        expect(component.isOpen()).toBe(false);
        expect(emitSpy).toHaveBeenCalledWith(false);
    });

    it('toggle flips the open state', () => {
        component.toggle(origin);
        expect(component.isOpen()).toBe(true);
        component.toggle(origin);
        expect(component.isOpen()).toBe(false);
    });

    it('does not emit again when opening an already-open popover', () => {
        component.open(origin);
        const emitSpy = vi.spyOn(component.openChange, 'emit');
        component.open(origin);
        expect(emitSpy).not.toHaveBeenCalled();
    });

    it('exposes the open panel as a modal dialog', () => {
        component.open(origin);
        fixture.detectChanges();
        const panel = document.querySelector('.tum-ui-popover-panel');
        expect(panel).not.toBeNull();
        expect(panel?.getAttribute('role')).toBe('dialog');
        expect(panel?.getAttribute('aria-modal')).toBe('true');
    });

    it('renders a pointer-capturing backdrop and closes on backdrop click', () => {
        component.open(origin);
        fixture.detectChanges();
        // The backdrop comes from the shared overlay substrate, which uses CDK's transparent backdrop. The
        // package ships `@angular/cdk/overlay-prebuilt.css`, so it is a full-viewport, pointer-capturing
        // surface — without it the click below would never reach the browser. Assert the backdrop exists and
        // that backdropClick is wired to close.
        const backdrop = document.querySelector('.cdk-overlay-transparent-backdrop');
        expect(backdrop).not.toBeNull();
        backdrop!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        expect(component.isOpen()).toBe(false);
    });
});
