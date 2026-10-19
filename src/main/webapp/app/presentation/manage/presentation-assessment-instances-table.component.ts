import { Component, input, linkedSignal, output } from '@angular/core';
import { TumAetUiTableDirective, TumAetUiTagComponent } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationAssessment, PresentationAssessmentInstance } from 'app/presentation/shared/entities/presentation-assessment.model';
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

    readonly editInstance = output<PresentationAssessmentInstance>();
    readonly deleteInstance = output<PresentationAssessmentInstance>();

    /** Keys of the rows whose details are shown; a different presentation starts with all rows collapsed. */
    protected readonly expandedRowKeys = linkedSignal<number | undefined, string[]>({
        source: () => this.presentationAssessment().id,
        computation: () => [],
    });

    protected toggleDetails(row: PresentationStudentTableRow): void {
        this.expandedRowKeys.update((keys) => (keys.includes(row.rowKey) ? keys.filter((key) => key !== row.rowKey) : [...keys, row.rowKey]));
    }
}
