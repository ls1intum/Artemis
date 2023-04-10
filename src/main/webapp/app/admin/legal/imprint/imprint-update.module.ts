import { NgModule } from '@angular/core';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ImprintUpdateComponent } from 'app/admin/legal/imprint/imprint-update.component';
import { LegalDocumentUnsavedChangesWarningModule } from 'app/admin/legal/unsaved-changes-warning/legal-document-unsaved-changes-warning.module';
import { ImprintUpdateRoutingModule } from 'app/admin/legal/imprint/imprint-update-routing.module';

@NgModule({
    imports: [
        ArtemisSharedComponentModule,
        ArtemisSharedCommonModule,
        ImprintUpdateRoutingModule,
        ArtemisMarkdownEditorModule,
        ArtemisModePickerModule,
        LegalDocumentUnsavedChangesWarningModule,
    ],
    declarations: [ImprintUpdateComponent],
})
export class ImprintUpdateModule {}
