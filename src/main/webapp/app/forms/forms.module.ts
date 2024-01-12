import { NgModule } from '@angular/core';
import { FormStatusBarComponent } from 'app/forms/form-status-bar/form-status-bar.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedCommonModule, ArtemisSharedComponentModule],
    declarations: [FormStatusBarComponent],
    exports: [FormStatusBarComponent],
})
export class FormsModule {}
