import { Observable } from 'rxjs';

import { DeleteDialogData, EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/component/delete-dialog.component';

export class MockDeleteDialogService {
    openDeleteDialog = (deleteDialogData: DeleteDialogData) => deleteDialogData.delete.emit();
    fetchAndSetEntitySummary = (fetchEntitySummary: Observable<EntitySummary>, componentInstance: DeleteDialogComponent) => {};
}
