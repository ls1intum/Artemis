import { UsersImportDialogComponent } from 'app/shared/import/users-import-dialog.component';
import { UsersImportButtonComponent } from 'app/shared/import/users-import-button.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule],
    declarations: [UsersImportDialogComponent, UsersImportButtonComponent],
    exports: [UsersImportDialogComponent, UsersImportButtonComponent],
})
export class UserImportModule {}
