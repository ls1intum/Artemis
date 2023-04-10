import { NgModule } from '@angular/core';

import { PrivacyStatementUpdateComponent } from 'app/admin/legal/privacy-statement/privacy-statement-update.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { PrivacyStatementUpdateRoutingModule } from 'app/admin/legal/privacy-statement/privacy-statement-update-routing.module';
import { LegalDocumentUnsavedChangesWarningModule } from 'app/admin/legal/unsaved-changes-warning/legal-document-unsaved-changes-warning.module';

@NgModule({
    imports: [
        ArtemisSharedComponentModule,
        ArtemisSharedCommonModule,
        PrivacyStatementUpdateRoutingModule,
        ArtemisMarkdownEditorModule,
        ArtemisModePickerModule,
        LegalDocumentUnsavedChangesWarningModule,
    ],
    declarations: [PrivacyStatementUpdateComponent],
})
export class PrivacyStatementUpdateModule {}
