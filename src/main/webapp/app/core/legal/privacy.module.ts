import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { PrivacyComponent } from 'app/core/legal/privacy.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { PrivacyRoutingModule } from 'app/core/legal/privacy-routing.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { DataExportComponent } from 'app/core/legal/data-export/data-export.component';
import { DataExportConfirmationDialogComponent } from 'app/core/legal/data-export/confirmation/data-export-confirmation-dialog.component';
import { DataExportRequestButtonDirective } from 'app/core/legal/data-export/confirmation/data-export-request-button.directive';
import { TypeAheadUserSearchFieldModule } from 'app/shared/type-ahead-search-field/type-ahead-user-search-field.module';

@NgModule({
    declarations: [PrivacyComponent, DataExportComponent, DataExportConfirmationDialogComponent, DataExportRequestButtonDirective],
    imports: [CommonModule, ArtemisSharedComponentModule, ArtemisSharedModule, PrivacyRoutingModule, ArtemisMarkdownModule, TypeAheadUserSearchFieldModule],
})
export class ArtemisPrivacyModule {}
