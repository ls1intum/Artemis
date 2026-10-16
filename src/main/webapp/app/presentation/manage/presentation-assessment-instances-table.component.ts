import { Component, input, output } from '@angular/core';
import { faPencilAlt, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonComponent, TumAetUiTableDirective, TumAetUiTagComponent } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { PresentationAssessment, PresentationAssessmentInstance } from 'app/presentation/shared/entities/presentation-assessment.model';
import { PresentationAssessmentInstanceDetailsComponent } from 'app/presentation/manage/presentation-assessment-instance-details.component';
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
        TumAetUiButtonComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        ProfilePictureComponent,
        DeleteButtonDirective,
        PresentationAssessmentInstanceDetailsComponent,
    ],
})
export class PresentationAssessmentInstancesTableComponent {
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faTrash = faTrash;
    protected readonly ActionType = ActionType;

    readonly rows = input.required<PresentationStudentTableRow[]>();
    readonly presentationAssessment = input.required<PresentationAssessment>();
    /** Editors remove assigned students; tutors only grade them. */
    readonly canDelete = input(false);

    readonly toggleDetails = output<PresentationStudentTableRow>();
    readonly editInstance = output<PresentationAssessmentInstance>();
    readonly deleteInstance = output<PresentationAssessmentInstance>();
}
