import { NgModule } from '@angular/core';
import { TextareaCounterComponent } from 'app/shared/textarea/textarea-counter.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [TextareaCounterComponent],
    exports: [TextareaCounterComponent],
})
export class TextareaModule {}
