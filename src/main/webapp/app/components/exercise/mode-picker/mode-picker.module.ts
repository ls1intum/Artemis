import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared';
import { ModePickerComponent } from 'app/components/exercise/mode-picker/mode-picker.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ModePickerComponent],
    exports: [ModePickerComponent],
})
export class ArtemisModePickerModule {}
