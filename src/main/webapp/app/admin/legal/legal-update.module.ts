import { NgModule } from '@angular/core';

import { LegalDocumentUpdateComponent } from 'app/admin/legal/legal-document-update.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LegalDocumentUpdateRoutingModule } from 'app/admin/legal/legal-document-update-routing.module';
import { LegalDocumentUnsavedChangesWarningModule } from 'app/admin/legal/unsaved-changes-warning/legal-document-unsaved-changes-warning.module';

@NgModule({
    imports: [
        ArtemisSharedComponentModule,
        ArtemisSharedCommonModule,
        LegalDocumentUpdateRoutingModule,
        ArtemisMarkdownEditorModule,
        ArtemisModePickerModule,
        LegalDocumentUnsavedChangesWarningModule,
    ],
    declarations: [LegalDocumentUpdateComponent],
})
export class LegalUpdateModule {}
