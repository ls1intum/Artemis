import { Component, input, output } from '@angular/core';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { PresentationStudentTableRow } from 'app/presentation/manage/presentation-assessment-management.helper';

/** Shows a student with picture, name and email; clicking it shows or hides the details of the row. */
@Component({
    selector: 'jhi-presentation-student-toggle',
    templateUrl: './presentation-student-toggle.component.html',
    imports: [ProfilePictureComponent, ArtemisTranslatePipe],
})
export class PresentationStudentToggleComponent {
    readonly row = input.required<PresentationStudentTableRow>();
    readonly expanded = input.required<boolean>();
    /** Id of the element that shows the details of the row. */
    readonly detailsId = input.required<string>();

    readonly toggled = output<void>();
}
