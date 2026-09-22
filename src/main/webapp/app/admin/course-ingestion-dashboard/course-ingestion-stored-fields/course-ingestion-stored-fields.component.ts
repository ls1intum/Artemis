import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiTableDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/** One stored property, prepared for display. */
interface StoredField {
    key: string;
    value: string;
}

/**
 * Renders a stored Weaviate record field by field.
 *
 * The browser exists to show what is really in the index, so the values are shown as stored rather than mapped onto
 * anything friendlier: an unexpected value is the interesting case, and formatting it away would hide it. Fields the
 * row has no value for are left out, since the schema is a wide sparse superset and most of it is absent for any row.
 */
@Component({
    selector: 'jhi-course-ingestion-stored-fields',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiTableDirective, TranslateDirective],
    templateUrl: './course-ingestion-stored-fields.component.html',
})
export class CourseIngestionStoredFieldsComponent {
    readonly properties = input.required<Record<string, unknown>>();

    /** The populated properties, sorted by key so the same record always reads the same way. */
    readonly fields = computed<StoredField[]>(() =>
        Object.entries(this.properties())
            .filter(([, value]) => value !== undefined && value !== null && value !== '')
            .map(([key, value]) => ({ key, value: format(value) }))
            .sort((a, b) => a.key.localeCompare(b.key)),
    );
}

/** Primitives read as themselves; anything structured is shown as JSON rather than as `[object Object]`. */
function format(value: unknown): string {
    if (typeof value === 'string') {
        return value;
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
        return String(value);
    }
    return JSON.stringify(value);
}
