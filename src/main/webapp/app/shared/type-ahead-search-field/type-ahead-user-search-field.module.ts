import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { TypeAheadUserSearchFieldComponent } from 'app/shared/type-ahead-search-field/type-ahead-user-search-field.component';

@NgModule({
    imports: [ArtemisSharedCommonModule],
    declarations: [TypeAheadUserSearchFieldComponent],
    exports: [TypeAheadUserSearchFieldComponent],
})
export class TypeAheadUserSearchFieldModule {}
