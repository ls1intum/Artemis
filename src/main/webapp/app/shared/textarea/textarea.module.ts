import { NgModule } from '@angular/core';
import { TextAreaCounterComponent } from 'app/shared/textarea/text-area-counter.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [TextAreaCounterComponent],
    exports: [TextAreaCounterComponent],
})
export class TextareaModule {}
