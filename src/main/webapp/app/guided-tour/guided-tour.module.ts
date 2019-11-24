import { ErrorHandler, NgModule } from '@angular/core';
import { ModuleWithProviders } from '@angular/compiler/src/core';
import { ArtemisSharedModule } from 'app/shared';
import { GuidedTourService } from './guided-tour.service';
import { GuidedTourComponent } from './guided-tour.component';

@NgModule({
    declarations: [GuidedTourComponent],
    imports: [ArtemisSharedModule],
    exports: [GuidedTourComponent],
    entryComponents: [GuidedTourComponent],
})
export class GuidedTourModule {}
