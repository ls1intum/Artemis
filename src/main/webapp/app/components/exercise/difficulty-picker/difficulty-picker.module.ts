import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { DifficultyPickerComponent } from 'app/components/exercise/difficulty-picker/difficulty-picker.component';

@NgModule({
    imports: [ArTEMiSSharedModule],
    declarations: [
        DifficultyPickerComponent
    ],
    exports: [
        DifficultyPickerComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSDifficultyPickerModule {
}
