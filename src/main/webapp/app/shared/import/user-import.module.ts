import { NgModule } from '@angular/core';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { UsersImportButtonComponent } from 'app/shared/import/users-import-button.component';
import { UsersImportDialogComponent } from 'app/shared/import/users-import-dialog.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule],
    declarations: [UsersImportDialogComponent, UsersImportButtonComponent],
    exports: [UsersImportDialogComponent, UsersImportButtonComponent],
})
export class UserImportModule {}
