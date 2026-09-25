import { DestroyRef, Directive, InjectionToken, Injector, InputSignal, TemplateRef, ViewContainerRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { Menu, MenuTrigger } from '@angular/aria/menu';
import { Directionality } from '@angular/cdk/bidi';
import {
    ConnectedPosition,
    OverlayRef,
    STANDARD_DROPDOWN_BELOW_POSITIONS,
    createFlexibleConnectedPositionStrategy,
    createOverlayRef,
    createRepositionScrollStrategy,
} from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';

/** The trigger that opened a menu, provided to the content it renders so the menu can register with it. */
export const TUM_UI_MENU_TRIGGER = new InjectionToken<TumUiMenuTriggerDirective>('TUM_UI_MENU_TRIGGER');

/**
 * Opens a {@link TumUiMenuComponent} from the element it sits on, which is normally a button.
 *
 * Point it at the `ng-template` that holds the menu: `<button [tumUiMenuTrigger]="actions">`. The Angular Aria menu
 * trigger owns `aria-expanded` / `aria-controls`, opening on click, Enter, Space, or ArrowDown (ArrowUp focuses the
 * last entry), closing on Escape or when focus leaves the menu, and returning focus to the trigger. This directive
 * renders the template in a CDK overlay while the trigger is expanded.
 *
 * A disabled trigger stays focusable and is announced as disabled, but does not open its menu.
 */
@Directive({
    selector: '[tumUiMenuTrigger]',
    host: {
        // Aria announces the popup as `true`; `menu` is the same popup type, spelled out.
        '[attr.aria-haspopup]': '"menu"',
    },
})
export class TumUiMenuTriggerDirective extends MenuTrigger<unknown> {
    private readonly injector = inject(Injector);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly directionality = inject(Directionality, { optional: true });

    /** The `ng-template` holding the `tum-ui-menu` to open. */
    readonly menuTemplate = input.required<TemplateRef<unknown>>({ alias: 'tumUiMenuTrigger' });
    /** Overlay positions to try, in order; defaults to below the trigger, aligned to its start edge. */
    readonly tumUiMenuPosition = input<ConnectedPosition[] | undefined>(undefined);
    /** Emits when the menu opens. */
    readonly menuOpened = output<void>();
    /** Emits when the menu closes. */
    readonly menuClosed = output<void>();

    /**
     * The menu rendered from the template while the trigger is expanded.
     *
     * Aria wires a trigger to its menu through the `menu` input, but here the menu only exists once the template has
     * been rendered into the overlay, and a directive cannot bind an input of the class it extends. The rendered menu
     * registers itself instead (see `attachMenu`), and aria reads it from this signal, which it only ever calls.
     */
    private readonly renderedMenu = signal<Menu<unknown> | undefined>(undefined);
    override readonly menu = this.renderedMenu.asReadonly() as InputSignal<Menu<unknown> | undefined>;

    private overlayRef?: OverlayRef;

    constructor() {
        super();
        effect(() => {
            const expanded = this.expanded();
            untracked(() => (expanded ? this.attachOverlay() : this.detachOverlay()));
        });
        inject(DestroyRef).onDestroy(() => this.overlayRef?.dispose());
    }

    /**
     * Registers the menu rendered from the template, and returns the function that withdraws it again.
     *
     * @internal Called by `tum-ui-menu`; not part of the public API.
     */
    attachMenu(menu: Menu<unknown>): () => void {
        this.renderedMenu.set(menu);
        return () => {
            if (this.renderedMenu() === menu) {
                this.renderedMenu.set(undefined);
            }
        };
    }

    /**
     * Closes the menu and moves focus back to the trigger.
     *
     * @internal Called by `tum-ui-menu` when Tab leaves it, so focus continues from the trigger instead of from the
     * overlay at the end of the document.
     */
    closeAndFocus(): void {
        this.close();
        this.element.focus();
    }

    private attachOverlay(): void {
        if (this.overlayRef?.hasAttached()) {
            return;
        }
        const positionStrategy = createFlexibleConnectedPositionStrategy(this.injector, this.element)
            .withLockedPosition()
            .withFlexibleDimensions(false)
            .withPositions(this.tumUiMenuPosition() ?? STANDARD_DROPDOWN_BELOW_POSITIONS);
        if (this.overlayRef) {
            this.overlayRef.updatePositionStrategy(positionStrategy);
        } else {
            this.overlayRef = createOverlayRef(this.injector, {
                positionStrategy,
                scrollStrategy: createRepositionScrollStrategy(this.injector),
                direction: this.directionality ?? undefined,
            });
        }
        const menuInjector = Injector.create({ parent: this.injector, providers: [{ provide: TUM_UI_MENU_TRIGGER, useValue: this }] });
        this.overlayRef.attach(new TemplatePortal(this.menuTemplate(), this.viewContainerRef, undefined, menuInjector));
        this.menuOpened.emit();
    }

    private detachOverlay(): void {
        if (this.overlayRef?.hasAttached()) {
            this.overlayRef.detach();
            this.menuClosed.emit();
        }
    }
}
