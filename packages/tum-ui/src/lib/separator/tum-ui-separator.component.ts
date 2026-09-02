import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/** Axis the rule is drawn along. */
export type TumUiSeparatorOrientation = 'horizontal' | 'vertical';

/**
 * A rule between two regions or two rows.
 *
 * It exists because a border utility cannot express the one decision that matters here: whether the rule is a
 * picture or a boundary. A purely visual rule leaves the accessibility tree entirely (`decorative`, the default);
 * a semantic one announces the boundary it draws. A stray `role="separator"` between every row of a list is pure
 * screen-reader noise, which is why the default is the quiet one.
 */
@Component({
    selector: 'tum-ui-separator',
    template: '',
    styleUrl: './tum-ui-separator.component.scss',
    host: {
        class: 'tum-ui-separator',
        '[attr.data-slot]': '"separator"',
        '[attr.data-orientation]': 'orientation()',
        '[attr.role]': "decorative() ? 'none' : 'separator'",
        // `separator` is horizontal by default, so only the vertical case needs to say so.
        '[attr.aria-orientation]': "!decorative() && orientation() === 'vertical' ? 'vertical' : null",
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiSeparatorComponent {
    /** Axis the rule is drawn along. A vertical rule stretches to the height of its flex or grid row. */
    readonly orientation = input<TumUiSeparatorOrientation>('horizontal');

    /**
     * Whether the rule is decoration. Leave it on for a rule that merely groups; turn it off for one that marks a
     * genuine boundary a screen-reader user needs to know about, such as between two unlabelled regions.
     */
    readonly decorative = input(true, { transform: booleanAttribute });
}
