import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/**
 * The focus file of a comprehension check question as a small chip: the name of the file and the number of lines the
 * student changed in it.
 *
 * A focus file is a path in the student's repository, such as `src/de/tum/in/www1/example/MergeSort.java`, which is too
 * long for the narrow student panel. The chip therefore shows only the last segment of the path. When even the name does
 * not fit, the name gives way and ends in an ellipsis, while the count of changed lines stays readable. The full path is
 * the tooltip of the chip, and screen readers read it in place of the shortened name. The student panel, the dashboard
 * and the question review use the chip.
 */
@Component({
    selector: 'jhi-focus-chip',
    templateUrl: './focus-chip.component.html',
    styleUrl: './focus-chip.component.scss',
    imports: [TranslateDirective],
    host: {
        class: 'relative inline-flex min-w-0 max-w-full items-center gap-1',
        'data-testid': 'metis-focus-chip',
        '[attr.title]': 'path()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FocusChipComponent {
    /** The path of the focus file in the student's repository. */
    readonly path = input.required<string>();
    /** The number of lines the student changed in the file: the lines added plus the lines removed. */
    readonly changedLines = input(0);

    /** The name the chip shows: the last segment of the path, or the path itself if it has no segment after its last slash. */
    protected readonly fileName = computed(() => {
        const path = this.path();
        return path.slice(path.lastIndexOf('/') + 1) || path;
    });
}
