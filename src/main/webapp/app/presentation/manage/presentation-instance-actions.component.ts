import { Component, input, output } from '@angular/core';
import { faPencilAlt, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonComponent } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';

/** Edit and delete buttons of an assigned student; tutors only edit the presentations they assess, only editors may delete. */
@Component({
    selector: 'jhi-presentation-instance-actions',
    templateUrl: './presentation-instance-actions.component.html',
    imports: [TumAetUiButtonComponent, DeleteButtonDirective, ArtemisTranslatePipe],
})
export class PresentationInstanceActionsComponent {
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faTrash = faTrash;
    protected readonly ActionType = ActionType;

    readonly canEdit = input(true);
    readonly canDelete = input(false);

    readonly editRequested = output<void>();
    /** Emitted once the deletion is confirmed in the dialog. */
    readonly deleteRequested = output<void>();
}
