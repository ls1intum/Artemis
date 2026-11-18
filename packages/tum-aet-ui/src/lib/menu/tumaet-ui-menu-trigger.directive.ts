import {
    DestroyRef,
    Directive,
    InjectionToken,
    Injector,
    InputSignal,
    TemplateRef,
    ViewContainerRef,
    booleanAttribute,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
} from '@angular/core';
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
export const TUM_AET_UI_MENU_TRIGGER = new InjectionToken<TumAetUiMenuTriggerDirective>('TUM_AET_UI_MENU_TRIGGER');

/**
 * Opens a {@link TumAetUiMenuComponent} from the element it sits on, which is normally a button.
 *
 * Point it at the `ng-template` that holds the menu: `<button [tumAetUiMenuTrigger]="actions">`. The Angular Aria menu
 * trigger owns `aria-expanded` / `aria-controls`, opening on click, Enter, Space, or ArrowDown (ArrowUp focuses the
 * last entry), closing on Escape or when focus leaves the menu, and returning focus to the trigger. This directive
 * renders the template in a CDK overlay while the trigger is expanded.
 *
 * `disabled` disables the trigger natively, like the `disabled` attribute of a button: it cannot be focused or clicked,
 * and `tumAetUiButton` shows it as disabled.
 *
 * Aria opens the menu from Enter and Space on keydown and cancels the key, so a keyboard user produces no `click` on
 * the trigger. React to the menu with `menuOpened` and to a chosen entry with its `triggered` output, never with a
 * `(click)` handler on the trigger, which only mouse users would reach.
 *
 * The class extends the aria trigger, which reads its inputs lazily through `this`, for example `this.menu()` inside
 * its computed signals and effects. The `menu` and `softDisabled` overrides below only take effect because aria keeps
 * doing that; if an aria update captures them at construction instead, the menu no longer opens.
 */
@Directive({
    selector: '[tumAetUiMenuTrigger]',
    host: {
        // Aria announces the popup as `true`; `menu` is the same popup type, spelled out.
        '[attr.aria-haspopup]': '"menu"',
    },
})
export class TumAetUiMenuTriggerDirective extends MenuTrigger<unknown> {
    private readonly injector = inject(Injector);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly directionality = inject(Directionality, { optional: true });

    /**
     * Aria soft-disables a trigger by default, which keeps it focusable and drops the native `disabled` attribute. A
     * menu button in Artemis is an ordinary button, so `disabled` keeps its native meaning.
     */
    override readonly softDisabled = input(false, { transform: booleanAttribute });

    /** The `ng-template` holding the `tumaet-ui-menu` to open. */
    readonly menuTemplate = input.required<TemplateRef<unknown>>({ alias: 'tumAetUiMenuTrigger' });
    /** Overlay positions to try, in order; defaults to below the trigger, aligned to its start edge. */
    readonly tumAetUiMenuPosition = input<ConnectedPosition[] | undefined>(undefined);
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
    /**
     * The menu aria operates on. Do not bind `[menu]` on the trigger: the name stays an input inherited from aria, and
     * the template type check accepts the binding, but it throws at runtime, because the trigger always takes its menu
     * from the template it points at. Narrowing the input so the check rejects it would break the override of aria's type.
     */
    override readonly menu = this.renderedMenu.asReadonly() as InputSignal<Menu<unknown> | undefined>;

    private overlayRef?: OverlayRef;

    constructor() {
        super();
        // Aria handles Escape on the trigger even while the menu is closed, and cancels it and stops its propagation, so
        // an enclosing dialog or drawer would never see it. Escape only concerns the trigger while its menu is open.
        const handleKeydown = this._pattern.onKeydown.bind(this._pattern);
        this._pattern.onKeydown = (event: KeyboardEvent) => {
            if (event.key !== 'Escape' || this.expanded()) {
                handleKeydown(event);
            }
        };
        effect(() => {
            const expanded = this.expanded();
            untracked(() => (expanded ? this.attachOverlay() : this.detachOverlay()));
        });
        inject(DestroyRef).onDestroy(() => this.overlayRef?.dispose());
    }

    /**
     * Registers the menu rendered from the template, and returns the function that withdraws it again.
     *
     * @internal Called by `tumaet-ui-menu`; not part of the public API.
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
     * @internal Called by `tumaet-ui-menu` when Tab leaves it, so focus continues from the trigger instead of from the
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
            .withPositions(this.tumAetUiMenuPosition() ?? STANDARD_DROPDOWN_BELOW_POSITIONS);
        if (this.overlayRef) {
            this.overlayRef.updatePositionStrategy(positionStrategy);
        } else {
            this.overlayRef = createOverlayRef(this.injector, {
                positionStrategy,
                scrollStrategy: createRepositionScrollStrategy(this.injector),
                direction: this.directionality ?? undefined,
            });
        }
        const menuInjector = Injector.create({ parent: this.injector, providers: [{ provide: TUM_AET_UI_MENU_TRIGGER, useValue: this }] });
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
