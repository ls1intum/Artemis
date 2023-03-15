import { NgModule } from '@angular/core';

import { DifficultyPickerComponent } from 'app/exercises/shared/difficulty-picker/difficulty-picker.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [DifficultyPickerComponent],
    exports: [DifficultyPickerComponent],
})
export class ArtemisDifficultyPickerModule {}
