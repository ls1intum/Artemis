import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type TumAetUiSkeletonShape = 'rectangle' | 'circle';

/**
 * Placeholder block shown in the place of content that is still loading.
 *
 * The skeleton has no size of its own: give it the width and height of the content it stands for with layout classes
 * or styles on the host, and compose several of them to sketch a row, a card, or a list.
 *
 * ```html
 * <div aria-busy="true" [attr.aria-label]="'Loading results'">
 *     <tumaet-ui-skeleton shape="circle" class="size-10" />
 *     <tumaet-ui-skeleton class="h-4 w-1/2" />
 *     <tumaet-ui-skeleton class="h-3 w-3/4" />
 * </div>
 * ```
 *
 * A skeleton is decorative and hidden from assistive technology. Announce the loading state on the container, for
 * example with `aria-busy` or a visually hidden status text. The pulse stops when the user prefers reduced motion.
 */
@Component({
    selector: 'tumaet-ui-skeleton',
    template: '',
    styleUrl: './tumaet-ui-skeleton.component.scss',
    host: {
        class: 'tumaet-ui-skeleton',
        'aria-hidden': 'true',
        '[class.tumaet-ui-skeleton-circle]': "shape() === 'circle'",
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiSkeletonComponent {
    /** `rectangle` has slightly rounded corners and suits text lines and blocks; `circle` suits avatars and icons. */
    readonly shape = input<TumAetUiSkeletonShape>('rectangle');
}
