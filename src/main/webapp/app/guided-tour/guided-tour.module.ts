import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { GuidedTourComponent } from './guided-tour.component';

@NgModule({
    declarations: [GuidedTourComponent],
    imports: [ArtemisSharedModule],
    exports: [GuidedTourComponent],
    entryComponents: [GuidedTourComponent],
})
export class GuidedTourModule {}
