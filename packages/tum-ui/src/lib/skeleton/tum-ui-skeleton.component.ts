import { ChangeDetectionStrategy, Component, computed, input, numberAttribute } from '@angular/core';

/**
 * A muted block standing in for content that is on its way.
 *
 * Use it for a **1–10 second** first load, in a box the arriving content will occupy, so the page does not resize
 * around the reader when the data lands. Under a second, show nothing — a placeholder that flashes is worse than a
 * beat of stillness. Past ten seconds, a placeholder stops being honest: show progress instead.
 *
 * ```html
 * <div [attr.aria-busy]="loading() || null">
 *     @if (loading()) {
 *         <span class="tum:sr-only">Loading files</span>
 *         <tum-ui-skeleton lines="3" />
 *     } @else { … }
 * </div>
 * ```
 *
 * **It does not shimmer, and that is a decision, not an omission.** A shimmer is an infinite, auto-starting
 * animation that runs well past five seconds alongside other content, which is what WCAG 2.2.2 is about; and it
 * says nothing a still block does not. The one motion here is the crossfade *out*: give the skeleton and the
 * content the same grid cell and they exchange places without a jump.
 *
 * **The skeleton is `aria-hidden`, and the container carries the announcement.** A placeholder is a picture of
 * absent content, not a status; assistive technology needs `aria-busy` on the region plus a word, which is the
 * consumer's to write because only the consumer knows what is loading.
 */
@Component({
    selector: 'tum-ui-skeleton',
    templateUrl: './tum-ui-skeleton.component.html',
    styleUrl: './tum-ui-skeleton.component.scss',
    host: {
        class: 'tum-ui-skeleton',
        'aria-hidden': 'true',
        '[attr.data-slot]': '"skeleton"',
        '[attr.data-lines]': 'lineCount()',
        '[style.width]': 'width()',
        '[style.height]': 'height()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiSkeletonComponent {
    /** Any CSS length. Omit it and the placeholder fills its container, which is usually what you want. */
    readonly width = input<string>();

    /** Any CSS length. Set it to reserve the exact box the arriving content will occupy. */
    readonly height = input<string>();

    /**
     * Number of stacked text lines. The last line is drawn short, because that is what a paragraph of prose looks
     * like and the difference is what stops a stack of bars reading as a table.
     */
    readonly lines = input(1, { transform: numberAttribute });

    protected readonly lineCount = computed(() => {
        const lines = Math.trunc(this.lines());
        return Number.isFinite(lines) && lines > 1 ? lines : 1;
    });

    protected readonly lineIndices = computed(() => Array.from({ length: this.lineCount() }, (_value, index) => index));
}
