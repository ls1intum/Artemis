import { NgModule } from '@angular/core';

import { DifficultyPickerComponent } from 'app/exercises/shared/difficulty-picker/difficulty-picker.component';

@NgModule({
    imports: [DifficultyPickerComponent],
    exports: [DifficultyPickerComponent],
})
export class ArtemisDifficultyPickerModule {}
