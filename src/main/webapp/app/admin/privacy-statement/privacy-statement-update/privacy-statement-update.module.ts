import { NgModule } from '@angular/core';

import { PrivacyStatementUpdateComponent } from 'app/admin/privacy-statement/privacy-statement-update/privacy-statement-update.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { PrivacyStatementUnsavedChangesWarningComponent } from 'app/admin/privacy-statement/unsaved-changes-warning/privacy-statement-unsaved-changes-warning.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { PrivacyStatementUpdateRoutingModule } from 'app/admin/privacy-statement/privacy-statement-update/privacy-statement-update-routing.module';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule, PrivacyStatementUpdateRoutingModule, ArtemisMarkdownEditorModule, ArtemisModePickerModule],
    declarations: [PrivacyStatementUpdateComponent, PrivacyStatementUnsavedChangesWarningComponent],
})
export class PrivacyStatementUpdateModule {}
