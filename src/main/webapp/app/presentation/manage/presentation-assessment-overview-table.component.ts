import { Component, input, output, signal } from '@angular/core';
import { faPencilAlt, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonComponent, TumAetUiTableDirective, TumAetUiTableSortEvent, TumAetUiTableSortableColumnComponent, TumAetUiTagComponent } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { PresentationAssessmentStudentRowsRequest } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentInstanceDetailsComponent } from 'app/presentation/manage/presentation-assessment-instance-details.component';
import { PresentationStudentTableRow } from 'app/presentation/manage/presentation-assessment-management.helper';

/** Lists the presenters of all presentations of a course, one row per student and presentation. */
@Component({
    selector: 'jhi-presentation-assessment-overview-table',
    templateUrl: './presentation-assessment-overview-table.component.html',
    styleUrl: './presentation-assessment-overview-table.component.scss',
    host: { class: 'block' },
    imports: [
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        TumAetUiTagComponent,
        TumAetUiButtonComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        ProfilePictureComponent,
        DeleteButtonDirective,
        PresentationAssessmentInstanceDetailsComponent,
    ],
})
export class PresentationAssessmentOverviewTableComponent {
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faTrash = faTrash;
    protected readonly ActionType = ActionType;

    readonly rows = input.required<PresentationStudentTableRow[]>();
    readonly sortField = input.required<PresentationAssessmentStudentRowsRequest['sortField']>();
    readonly sortOrder = input.required<number>();
    /** Editors remove assigned students; tutors only grade them. */
    readonly canDelete = input(false);

    readonly sortChange = output<TumAetUiTableSortEvent>();
    readonly editInstance = output<PresentationStudentTableRow>();
    readonly deleteInstance = output<PresentationStudentTableRow>();

    /** Keys of the rows whose details are shown. */
    protected readonly expandedRowKeys = signal<string[]>([]);

    protected toggleDetails(row: PresentationStudentTableRow): void {
        this.expandedRowKeys.update((keys) => (keys.includes(row.rowKey) ? keys.filter((key) => key !== row.rowKey) : [...keys, row.rowKey]));
    }
}
