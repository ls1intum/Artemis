import { Observable } from 'rxjs';

import { DeleteDialogData, EntitySummary, triggerDeleteDialogDelete } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { DeleteDialogComponent } from 'app/shared-ui/delete-dialog/component/delete-dialog.component';

export class MockDeleteDialogService {
    openDeleteDialog = (deleteDialogData: DeleteDialogData) => triggerDeleteDialogDelete(deleteDialogData.delete);
    fetchAndSetEntitySummary = (fetchEntitySummary: Observable<EntitySummary>, componentInstance: DeleteDialogComponent) => {};
}
