import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextareaCounterComponent } from 'app/shared/textarea/textarea-counter.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [TextareaCounterComponent],
    exports: [TextareaCounterComponent],
})
export class TextareaModule {}
