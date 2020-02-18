import { NgModule } from '@angular/core';

import { ModePickerComponent } from 'app/components/exercise/mode-picker/mode-picker.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ModePickerComponent],
    exports: [ModePickerComponent],
})
export class ArtemisModePickerModule {}
