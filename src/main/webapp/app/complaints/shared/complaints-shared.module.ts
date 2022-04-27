import { NgModule } from '@angular/core';
import { ComplaintsTextAreaCounterComponent } from 'app/complaints/shared/complaints-textarea-counter.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ComplaintsTextAreaCounterComponent],
    exports: [ComplaintsTextAreaCounterComponent],
})
export class ComplaintsSharedModule {}
