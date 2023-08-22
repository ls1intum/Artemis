import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LookAheadSearchFieldComponent } from 'app/shared/data-table/look-ahead-search-field/look-ahead-search-field.component';

@NgModule({
    imports: [ArtemisSharedCommonModule],
    declarations: [LookAheadSearchFieldComponent],
    exports: [LookAheadSearchFieldComponent],
})
export class LookAheadSearchFieldModule {}
