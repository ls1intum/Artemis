import { TumUiButtonDirective, TumUiPopoverComponent, TumUiPopoverTriggerDirective, TumUiTooltipDirective } from '@tumaet/ui-angular';
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

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionItem } from 'app/exercise/exercise-action-bar/exercise-action-bar.model';

// Flex gap (gap-1 = 0.25rem) between items, the fixed ellipsis-trigger width (see SCSS `.action-more`), and a small
// safety margin so a button is always collapsed slightly before it would be clipped — never shown partially.
const GAP_PX = 4;
const ELLIPSIS_WIDTH_PX = 40;
const SAFETY_MARGIN_PX = 8;
const EXTRA_ACTION_PRIORITY = 1000;

/**
 * Element width in fractional CSS pixels. Unlike `offsetWidth`/`clientWidth`, `getBoundingClientRect` keeps the
 * fraction, so summing many button widths stays exact at non-100% zoom and never understates the total enough to clip
 * the leftmost button.
 */
function widthOf(element: HTMLElement): number {
    return element.getBoundingClientRect().width;
}

/**
 * Owned, data-driven collapsible action row: renders {@link ActionItem}s inline (Delete/Edit/Scores kept longest via
 * {@link keepPriorityIds}) and folds whatever doesn't fit into a trailing ellipsis menu, based on measured button
 * widths and the row's available width. Extracted from the exercise-management table's action bar so both the
 * course-exercise and exam-exercise row actions render identically and collapse the same way on small screens —
 * only the `ActionItem[]` each context builds (its routes, role gates, and delete wiring) differs.
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
        TumUiButtonDirective,
        TumUiPopoverComponent,
        TumUiPopoverTriggerDirective,
        TumUiTooltipDirective,
        ArtemisTranslatePipe,
        TranslateDirective,
        DeleteButtonDirective,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseActionBarComponent {
    readonly items = input.required<ActionItem[]>();
    /** Whether the `[actionBarReserved]`-projected prefix renders anything, so the separator next to it only shows when needed. */
    readonly hasReservedContent = input<boolean>(false);
    /**
     * Ids kept inline longest when the row runs out of width (in priority order); any id not listed shares the next
     * priority tier, in original display order (the collapse sort is stable). Defaults to the exercise-management
     * table's convention: Delete, Edit, Scores.
     */
    readonly keepPriorityIds = input<string[]>(['delete', 'edit', 'scores']);
    /**
     * Width (px) the actions column must reserve to keep this row's always-visible reserved content plus the ellipsis
     * trigger on screen; 0 when there is no reserved content. A shared table column floors itself at the max reported
     * across its rows, so the column collapses the main buttons before scrolling, never clipping reserved content.
     */
    readonly columnMinWidthChange = output<number>();

    protected readonly faEllipsis = faEllipsis;

    private readonly destroyRef = inject(DestroyRef);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly translateService = inject(TranslateService);

    private readonly menu = viewChild<TumUiPopoverComponent>('menu');
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

    private readonly keepPriorityOf = (action: ActionItem): number => {
        const index = this.keepPriorityIds().indexOf(action.id);
        return index === -1 ? EXTRA_ACTION_PRIORITY : index;
    };

    /** Signature that determines a button's rendered width: same signature ⇒ same width. Uses the translated label so a
     * language switch or a changed label re-measures. */
    protected signatureOf(action: ActionItem): string {
        return `${action.id}|${this.translateService.instant(action.labelKey)}`;
    }

    /**
     * Ids of the main actions that do not fit and are collapsed into the ellipsis menu. Computed from the cached button
     * widths and the available width — the DOM stays stable, only the buttons' `display` toggles, so resizing never
     * recreates button elements. Collapses in ascending keep-priority order so the highest-priority ids stay visible
     * longest.
     */
    readonly hiddenIds = computed<ReadonlySet<string>>(() => {
        const actions = this.items();
        const widths = this.buttonWidths();
        const available = this.availableWidth() - SAFETY_MARGIN_PX;
        // Not yet measured (or sized): show everything until widths/width are known.
        if (available <= 0 || actions.some((action) => !widths.has(this.signatureOf(action)))) {
            return new Set();
        }
        const widthOfAction = (action: ActionItem) => widths.get(this.signatureOf(action)) ?? 0;
        // N buttons laid out with N-1 gaps between them.
        const widthForCount = (count: number) => (count <= 0 ? 0 : (count - 1) * GAP_PX);

        // Everything fits inline: no ellipsis, no hiding.
        const totalAll = actions.reduce((sum, action) => sum + widthOfAction(action), 0) + widthForCount(actions.length);
        if (totalAll <= available) {
            return new Set();
        }

        // Collapsing: reserve the ellipsis (plus the gap before it) and keep buttons in priority order, stopping at
        // the first that no longer fits. The template still renders the kept buttons in their original display order
        // (only their `display` toggles).
        const budget = available - ELLIPSIS_WIDTH_PX - GAP_PX;
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
        return new Set(actions.filter((action) => !keptIds.has(action.id)).map((action) => action.id));
    });

    /**
     * The inline main buttons with their per-item render state precomputed, so the template reads plain fields instead of
     * calling `signatureOf(...)` and `hiddenIds().has(...)` on every change-detection cycle. `context` is built here too
     * so the `ngTemplateOutlet` binding keeps a stable object identity across cycles.
     */
    protected readonly inlineActions = computed(() => {
        // The signature embeds a translated label, so re-derive it on a language switch.
        this.languageVersion();
        const hidden = this.hiddenIds();
        return this.items().map((action) => ({
            action,
            signature: this.signatureOf(action),
            hidden: hidden.has(action.id),
            context: { $implicit: action, inMenu: false },
        }));
    });

    readonly hasOverflow = computed<boolean>(() => this.hiddenIds().size > 0);

    /** The collapsed actions, shown in the ellipsis menu. */
    readonly hiddenActions = computed<ActionItem[]>(() => this.items().filter((action) => this.hiddenIds().has(action.id)));

    constructor() {
        this.destroyRef.onDestroy(() => this.buttonObserver.disconnect());

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
            this.items();
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
    }

    /**
     * Caches the measured width of every button that is currently laid out. Collapsed buttons are `display: none` and
     * measure 0 — their last known width is kept, since that is exactly the width the overflow calculation needs in
     * order to decide whether they could be shown again. Writes only on a real change, so toggling a button's
     * visibility settles instead of feeding back into itself.
     */
    private onButtonsResized(entries: ResizeObserverEntry[]): void {
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
