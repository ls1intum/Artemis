import { Component, computed, input, linkedSignal, output, signal } from '@angular/core';
import { orderBy } from 'lodash-es';
import { TumAetUiTableDirective, TumAetUiTableSortEvent, TumAetUiTableSortableColumnComponent, TumAetUiTagComponent } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentInstanceDetailsComponent } from 'app/presentation/manage/presentation-assessment-instance-details.component';
import { PresentationInstanceActionsComponent } from 'app/presentation/manage/presentation-instance-actions.component';
import { PresentationStudentToggleComponent } from 'app/presentation/manage/presentation-student-toggle.component';
import { PresentationStudentTableRow } from 'app/presentation/manage/presentation-assessment-management.helper';

/** Lists the students assigned to one presentation with their grade, date and remark. */
@Component({
    selector: 'jhi-presentation-assessment-instances-table',
    templateUrl: './presentation-assessment-instances-table.component.html',
    styleUrl: './presentation-assessment-instances-table.component.scss',
    host: { class: 'block' },
    imports: [
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        TumAetUiTagComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        PresentationAssessmentInstanceDetailsComponent,
        PresentationInstanceActionsComponent,
        PresentationStudentToggleComponent,
    ],
})
export class PresentationAssessmentInstancesTableComponent {
    readonly rows = input.required<PresentationStudentTableRow[]>();
    readonly presentationAssessment = input.required<PresentationAssessment>();
    /** Editors remove assigned students; tutors only grade them. */
    readonly canDelete = input(false);

    protected readonly sortField = signal<string | undefined>(undefined);
    protected readonly sortOrder = signal(1);

    /** The rows ordered by the selected column; the server delivers all students of the presentation, so the client sorts them. */
    protected readonly sortedRows = computed(() => {
        const field = this.sortField();
        if (!field) {
            return this.rows();
        }
        return orderBy(this.rows(), [(row) => PresentationAssessmentInstancesTableComponent.sortValue(row, field)], [this.sortOrder() === 1 ? 'asc' : 'desc']);
    });

    readonly editInstance = output<PresentationStudentTableRow>();
    readonly deleteInstance = output<PresentationStudentTableRow>();

    /** Keys of the rows whose details are shown; a different presentation starts with all rows collapsed. */
    protected readonly expandedRowKeys = linkedSignal<number | undefined, string[]>({
        source: () => this.presentationAssessment().id,
        computation: () => [],
    });

    protected onSortChange(event: TumAetUiTableSortEvent): void {
        this.sortField.set(event.field);
        this.sortOrder.set(event.order);
    }

    private static sortValue(row: PresentationStudentTableRow, field: string): string | number {
        switch (field) {
            case 'assessed':
                return row.assessed ? 1 : 0;
            case 'resultPoints':
                return row.instance.resultPoints ?? Number.NEGATIVE_INFINITY;
            case 'presentationDate':
                return row.instance.presentationDate?.valueOf() ?? Number.NEGATIVE_INFINITY;
            default:
                return row.displayName.toLowerCase();
        }
    }

    /** Shows the details of a collapsed row and hides those of an expanded one. */
    protected toggleDetails(row: PresentationStudentTableRow): void {
        this.expandedRowKeys.update((keys) => (keys.includes(row.rowKey) ? keys.filter((key) => key !== row.rowKey) : [...keys, row.rowKey]));
    }
}
