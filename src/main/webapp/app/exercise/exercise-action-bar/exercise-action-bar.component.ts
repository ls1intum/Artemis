import {
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    DestroyRef,
    ElementRef,
    afterNextRender,
    afterRenderEffect,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    viewChild,
    viewChildren,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { NgTemplateOutlet } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faEllipsis } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionItem } from 'app/exercise/exercise-action-bar/exercise-action-bar.model';

// Flex gap (gap-1 = 0.25rem) between items, the fixed ellipsis-trigger width (see SCSS `.action-more`), and a small
// safety margin so a button is always collapsed slightly before it would be clipped — never shown partially.
const GAP_PX = 4;
const ELLIPSIS_WIDTH_PX = 34;
const SAFETY_MARGIN_PX = 8;
const EXTRA_ACTION_PRIORITY = 1000;
const COLLAPSE_FIRST_PRIORITY = 2000;

let nextBarId = 0;

/**
 * Element width in fractional CSS pixels. Unlike `offsetWidth`/`clientWidth`, `getBoundingClientRect` keeps the
 * fraction, so summing many button widths stays exact at non-100% zoom and never understates the total enough to clip
 * the leftmost button.
 */
function widthOf(element: HTMLElement): number {
    return element.getBoundingClientRect().width;
}

/**
 * Owned, data-driven action row: renders the {@link inlineIds} actions as icon buttons, at most {@link maxInline} of
 * them, and every other {@link ActionItem} in a trailing ellipsis menu. Once the menu shows, Delete goes into it
 * ({@link collapseFirstIds}); buttons that don't fit the row's available width fold into it too, Edit/Participations/Edit
 * in editor last ({@link keepPriorityIds}), based on measured button widths.
 *
 * Both exercise row-action components render through this bar — `ExerciseActionsComponent` (course exercises) and
 * `ExamExerciseRowButtonsComponent` (exam exercises) — so they look and collapse identically; only the `ActionItem[]`
 * each builds (its routes, role gates, and delete wiring) differs.
 *
 * An optional `[actionBarReserved]`-projected prefix (e.g. quiz lifecycle buttons, an exam test-run warning icon)
 * stays always visible; its measured width is reserved up front and reported via {@link columnMinWidthChange} so a
 * shared table column can floor its width at the widest row's reserved content, exactly like the collapsible items.
 */
@Component({
    selector: 'jhi-exercise-action-bar',
    templateUrl: './exercise-action-bar.component.html',
    styleUrl: './exercise-action-bar.component.scss',
    imports: [
        RouterLink,
        NgTemplateOutlet,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
        TumAetUiTooltipDirective,
        ArtemisTranslatePipe,
        TranslateDirective,
        DeleteButtonDirective,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseActionBarComponent {
    private readonly destroyRef = inject(DestroyRef);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly translateService = inject(TranslateService);

    readonly items = input.required<ActionItem[]>();
    /** Whether the `[actionBarReserved]`-projected prefix renders anything, so the separator next to it only shows when needed. */
    readonly hasReservedContent = input<boolean>(false);
    /**
     * Ids kept inline longest when the row runs out of width (in priority order); any id not listed shares the next
     * priority tier, in original display order (the collapse sort is stable).
     */
    readonly keepPriorityIds = input<string[]>(['edit', 'participations', 'edit-in-editor']);
    /**
     * Ids folded into the ellipsis menu before any other action (in this order). Delete, the last trailing action, goes
     * first, so the ellipsis trigger, as wide as a button, takes its place and the remaining trailing actions stay in line
     * with the other rows.
     */
    readonly collapseFirstIds = input<string[]>(['delete']);
    /** Ids that may show as buttons; every other action always sits in the ellipsis menu. */
    readonly inlineIds = input<string[]>(['edit-in-editor', 'participations', 'edit', 'delete']);
    /**
     * Most buttons shown inline. A row with more actions shows the ellipsis, the {@link collapseFirstIds} ones always go
     * into it, and at most this many of the rest stay inline (in keep-priority order).
     */
    readonly maxInline = input<number>(3);
    /**
     * Ids rendered last, in this order, after all other actions. Every row then ends in the same buttons, so they line up
     * across the rows of a table.
     */
    readonly trailingIds = input<string[]>(['participations', 'edit', 'delete']);
    /**
     * Width (px) the actions column must reserve to keep this row's always-visible reserved content plus the ellipsis
     * trigger on screen; 0 when there is no reserved content. A shared table column floors itself at the max reported
     * across its rows, so the column collapses the main buttons before scrolling, never clipping reserved content.
     */
    readonly columnMinWidthChange = output<number>();
    /**
     * Width (px) this row needs to show every action inline, including its reserved content; 0 until all buttons are
     * measured. A shared table column sizes itself to the max reported across its rows, so it takes no more room than the
     * widest row needs and the leftover width goes to the other columns.
     */
    readonly naturalWidthChange = output<number>();

    protected readonly faEllipsis = faEllipsis;

    /** Makes the ids of the disabled-reason descriptions unique across the rows of a table. */
    private readonly barId = nextBarId++;

    private readonly menu = viewChild<TumAetUiPopoverComponent>('menu');
    /** The full-width action row; its width minus the reserved content is the budget for the collapsible main buttons. */
    private readonly actionsRow = viewChild<ElementRef<HTMLElement>>('actionsRow');
    /** The always-visible projected prefix; its width is reserved up front. */
    private readonly reservedGroup = viewChild<ElementRef<HTMLElement>>('reservedGroup');
    /** The inline main-button wrappers, read once per distinct button to learn its natural width. */
    private readonly inlineItems = viewChildren<ElementRef<HTMLElement>>('inlineItem');

    /** Full row width (updated by a ResizeObserver) and the reserved width of the always-visible prefix content. */
    private readonly rowWidth = signal(0);
    private readonly reservedWidth = signal(0);
    /** Width available for the collapsible main buttons: the row minus the reserved prefix content. */
    private readonly availableWidth = computed<number>(() => this.rowWidth() - this.reservedWidth());
    /** Natural width per button, keyed by a signature (id + label) so changing labels re-measure. */
    private readonly buttonWidths = signal<ReadonlyMap<string, number>>(new Map());
    /** Bumped on a language change so the (signal-unaware) translated measurements recompute. */
    private readonly languageVersion = signal(0);

    /** Watches the inline buttons so {@link buttonWidths} tracks their real rendered width (see the constructor). */
    private readonly buttonObserver = new ResizeObserver((entries) => this.onButtonsResized(entries));

    /**
     * Set once the view is gone. `disconnect()` stops new notifications, but one already queued before destruction
     * still runs its callback, and `detectChanges()` on a destroyed view throws.
     */
    private destroyed = false;

    /** The actions in display order: the other actions first, then the {@link trailingIds} ones in that order. */
    private readonly orderedItems = computed<ActionItem[]>(() => {
        const trailingIds = this.trailingIds();
        const items = this.items();
        const trailing = trailingIds.map((id) => items.find((action) => action.id === id)).filter((action) => action !== undefined);
        return items.filter((action) => !trailingIds.includes(action.id)).concat(trailing);
    });

    private readonly keepPriorityOf = (action: ActionItem): number => {
        const index = this.keepPriorityIds().indexOf(action.id);
        if (index !== -1) {
            return index;
        }
        // Reversed, so the first id of collapseFirstIds gets the lowest priority and folds first.
        const collapseIndex = this.collapseFirstIds().indexOf(action.id);
        return collapseIndex === -1 ? EXTRA_ACTION_PRIORITY : COLLAPSE_FIRST_PRIORITY - collapseIndex;
    };

    /** Signature that determines a button's rendered width: same signature ⇒ same width. Uses the translated label so a
     * language switch or a changed label re-measures. */
    protected signatureOf(action: ActionItem): string {
        return `${action.id}|${this.translateService.instant(action.labelKey)}`;
    }

    /**
     * Ids of the actions that do not fit and are collapsed into the ellipsis menu. Computed from the cached button
     * widths and the available width — the DOM stays stable, only the buttons' `display` toggles, so resizing never
     * recreates button elements. Collapses in ascending keep-priority order so the highest-priority ids stay visible
     * longest.
     */
    readonly hiddenIds = computed<ReadonlySet<string>>(() => {
        const countHidden = this.countHiddenIds();
        const actions = this.orderedItems().filter((action) => !countHidden.has(action.id));
        const widths = this.buttonWidths();
        const available = this.availableWidth() - SAFETY_MARGIN_PX;
        // Not yet measured (or sized): show everything within the limit until widths/width are known.
        if (available <= 0 || actions.some((action) => !widths.has(this.signatureOf(action)))) {
            return countHidden;
        }
        const widthOfAction = (action: ActionItem) => widths.get(this.signatureOf(action)) ?? 0;
        // N buttons laid out with N-1 gaps between them.
        const widthForCount = (count: number) => (count <= 0 ? 0 : (count - 1) * GAP_PX);
        const ellipsisWidth = ELLIPSIS_WIDTH_PX + GAP_PX;

        // Everything within the limit fits inline (next to the ellipsis, if the limit already needs it).
        const totalAll = actions.reduce((sum, action) => sum + widthOfAction(action), 0) + widthForCount(actions.length);
        if (totalAll + (countHidden.size > 0 ? ellipsisWidth : 0) <= available) {
            return countHidden;
        }

        // Collapsing: reserve the ellipsis (plus the gap before it) and keep buttons in priority order, stopping at
        // the first that no longer fits. The template still renders the kept buttons in their original display order
        // (only their `display` toggles).
        const budget = available - ellipsisWidth;
        const byPriority = [...actions].sort((a, b) => this.keepPriorityOf(a) - this.keepPriorityOf(b));
        const keptIds = new Set<string>();
        let used = 0;
        for (const action of byPriority) {
            const addition = widthOfAction(action) + (keptIds.size > 0 ? GAP_PX : 0);
            if (used + addition <= budget) {
                used += addition;
                keptIds.add(action.id);
            } else {
                break;
            }
        }
        return new Set(
            this.orderedItems()
                .filter((action) => !keptIds.has(action.id))
                .map((action) => action.id),
        );
    });

    /**
     * Ids in the ellipsis menu whatever the width: the actions outside {@link inlineIds}, and once the menu shows, the
     * {@link collapseFirstIds} ones and any beyond the {@link maxInline} limit (lowest keep-priority first). A menu with
     * a single entry is not worth the extra click, so that action shows as a button instead.
     */
    private readonly countHiddenIds = computed<ReadonlySet<string>>(() => {
        const actions = this.orderedItems();
        const inlineIds = this.inlineIds();
        const hidden = new Set(actions.filter((action) => !inlineIds.includes(action.id)).map((action) => action.id));
        let inline = actions.filter((action) => !hidden.has(action.id));
        if (hidden.size === 0 && inline.length <= this.maxInline()) {
            return hidden;
        }
        // The ellipsis shows, so Delete goes into it, keeping the other buttons in line with rows that end in Delete.
        const collapseFirstIds = this.collapseFirstIds();
        inline.filter((action) => collapseFirstIds.includes(action.id)).forEach((action) => hidden.add(action.id));
        inline = inline.filter((action) => !hidden.has(action.id));
        const byPriority = [...inline].sort((a, b) => this.keepPriorityOf(a) - this.keepPriorityOf(b));
        byPriority.slice(this.maxInline()).forEach((action) => hidden.add(action.id));
        return hidden.size === 1 ? new Set() : hidden;
    });

    /**
     * The inline main buttons with their per-item render state precomputed, so the template reads plain fields instead of
     * calling `signatureOf(...)`, `hiddenIds().has(...)` or translating the tooltip on every change-detection cycle.
     * `context` is built here too so the `ngTemplateOutlet` binding keeps a stable object identity across cycles.
     */
    protected readonly inlineActions = computed(() => {
        // The signature embeds a translated label, so re-derive it on a language switch.
        this.languageVersion();
        const hidden = this.hiddenIds();
        const items = this.orderedItems();
        const firstTrailing = items.findIndex((action) => this.trailingIds().includes(action.id));
        return items.map((action, index) => ({
            action,
            signature: this.signatureOf(action),
            hidden: hidden.has(action.id),
            // A wider gap sets the trailing actions apart from the others before them.
            groupStart: index > 0 && index === firstTrailing,
            context: this.contextOf(action, false),
        }));
    });

    readonly hasOverflow = computed<boolean>(() => this.hiddenIds().size > 0);

    /** Natural width of the buttons outside the menu, plus the ellipsis when the menu shows; undefined until they are measured. */
    private readonly buttonsWidth = computed<number | undefined>(() => {
        const countHidden = this.countHiddenIds();
        const actions = this.orderedItems().filter((action) => !countHidden.has(action.id));
        const widths = this.buttonWidths();
        if (actions.length === 0 || actions.some((action) => !widths.has(this.signatureOf(action)))) {
            return undefined;
        }
        const buttons = actions.reduce((sum, action) => sum + (widths.get(this.signatureOf(action)) ?? 0), 0) + (actions.length - 1) * GAP_PX;
        return buttons + (countHidden.size > 0 ? GAP_PX + ELLIPSIS_WIDTH_PX : 0);
    });

    /** The collapsed actions, shown in the ellipsis menu in display order. */
    readonly hiddenActions = computed<ActionItem[]>(() => this.orderedItems().filter((action) => this.hiddenIds().has(action.id)));

    /** Template contexts for the menu entries, precomputed for the same reason as {@link inlineActions}. */
    protected readonly menuActions = computed(() => {
        this.languageVersion();
        return this.hiddenActions().map((action) => this.contextOf(action, true));
    });

    constructor() {
        this.destroyRef.onDestroy(() => {
            this.destroyed = true;
            this.buttonObserver.disconnect();
        });

        // Measurements use TranslateService.instant (not a signal), so on a language change drop the cached widths to
        // re-measure and bump the version the width effects watch.
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.buttonWidths.set(new Map());
            this.languageVersion.update((version) => version + 1);
        });

        // Observe (not read once) the row width and the reserved-content width: reserved content (e.g. lazily-styled
        // PrimeNG buttons) can render unstyled and too narrow on the first pass, and the observer also tracks the
        // reserved group appearing/disappearing and label-width changes. Change detection is flushed synchronously in
        // the callback (after layout, before paint) so the show/hide lands on the same frame.
        afterNextRender(() => {
            const rowEl = this.actionsRow()?.nativeElement;
            const reservedEl = this.reservedGroup()?.nativeElement;
            const measure = () => {
                if (rowEl) {
                    this.rowWidth.set(widthOf(rowEl));
                }
                this.reservedWidth.set(reservedEl ? widthOf(reservedEl) : 0);
            };

            const observer = new ResizeObserver(() => {
                if (this.destroyed) {
                    return;
                }
                measure();
                this.changeDetectorRef.detectChanges();
            });
            if (rowEl) {
                observer.observe(rowEl);
            }
            if (reservedEl) {
                observer.observe(reservedEl);
            }
            this.destroyRef.onDestroy(() => observer.disconnect());
            measure();
        });

        // Keep each distinct button's natural width up to date: a web font, a longer label, or lazily-injected CSS can
        // change a width after the first layout, so a one-shot measurement would cache a too-narrow width and the
        // overflow calculation would keep a button inline that then gets clipped.
        afterRenderEffect(() => {
            // Re-observe whenever the items or their labels change. Observing always emits an initial callback, so this
            // also seeds a brand-new signature after a language switch. The elements persist across a collapse (only
            // their `display` toggles), so this does not re-run on every resize.
            this.languageVersion();
            this.orderedItems();
            const elements = this.inlineItems();
            this.buttonObserver.disconnect();
            for (const ref of elements) {
                this.buttonObserver.observe(ref.nativeElement);
            }
        });

        // Report the width this row's actions column must reserve: the reserved content (its measured width already
        // includes its own trailing separator) plus the gap, the ellipsis trigger, and a safety margin against
        // sub-pixel clipping. Reserved-content width doesn't depend on the column width, so this never feeds back.
        // Rows without reserved content report 0.
        effect(() => {
            const reserved = this.reservedWidth();
            this.columnMinWidthChange.emit(reserved > 0 ? reserved + GAP_PX + ELLIPSIS_WIDTH_PX + SAFETY_MARGIN_PX : 0);
        });

        // Report the width that shows every button: the measured buttons (collapsed ones keep their last width), their
        // gaps and the reserved content. None of these depend on the column width, so this never feeds back either.
        effect(() => {
            const buttons = this.buttonsWidth();
            const reserved = this.reservedWidth();
            if (buttons === undefined) {
                this.naturalWidthChange.emit(0);
                return;
            }
            // Rounded up: measured widths are fractional, and a column a fraction too narrow would fold a button.
            this.naturalWidthChange.emit(Math.ceil(buttons + (reserved > 0 ? reserved + GAP_PX : 0)) + SAFETY_MARGIN_PX);
        });
    }

    /**
     * Caches the measured width of every button that is currently laid out. Collapsed buttons are `display: none` and
     * measure 0 — their last known width is kept, since that is exactly the width the overflow calculation needs in
     * order to decide whether they could be shown again. Writes only on a real change, so toggling a button's
     * visibility settles instead of feeding back into itself.
     */
    private onButtonsResized(entries: ResizeObserverEntry[]): void {
        if (this.destroyed) {
            return;
        }
        const current = this.buttonWidths();
        let next: Map<string, number> | undefined;
        for (const entry of entries) {
            const element = entry.target as HTMLElement;
            const signature = element.getAttribute('data-signature');
            const width = widthOf(element);
            if (signature && width > 0 && current.get(signature) !== width) {
                next ??= new Map(current);
                next.set(signature, width);
            }
        }
        if (next) {
            this.buttonWidths.set(next);
            // Land the show/hide on this frame (the callback runs after layout, before paint), as the row observer does.
            this.changeDetectorRef.detectChanges();
        }
    }

    /**
     * The template context of an action. A disabled action with a reason gets the id of a permanent, visually hidden
     * description: its tooltip only exists while shown, and a disabled control never receives focus to show it.
     */
    private contextOf(action: ActionItem, inMenu: boolean) {
        const reasonId = action.disabled && action.disabledTooltip ? `exercise-action-reason-${this.barId}-${action.id}-${inMenu ? 'menu' : 'inline'}` : undefined;
        return { $implicit: action, inMenu, tooltip: this.tooltipOf(action, inMenu), reasonId };
    }

    /** An inline button's tooltip names it; a disabled action, inline or in the menu, explains why instead. */
    private tooltipOf(action: ActionItem, inMenu: boolean): string {
        if (action.disabled && action.disabledTooltip) {
            return this.translateService.instant(action.disabledTooltip);
        }
        return inMenu ? '' : this.translateService.instant(action.labelKey);
    }

    protected runAction(item: ActionItem): void {
        item.onClick?.();
        this.menu()?.close();
    }

    protected closeMenuIfOpen(inMenu: boolean): void {
        if (inMenu) {
            this.menu()?.close();
        }
    }

    protected onDelete(action: ActionItem, event: { [key: string]: boolean }): void {
        action.delete?.onDelete(event);
    }
}
