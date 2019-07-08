import { GuidedTourService } from './guided-tour.service';
import { GuidedTourComponent } from './guided-tour.component';
import { NgModule, ErrorHandler } from '@angular/core';
import { ModuleWithProviders } from '@angular/compiler/src/core';

@NgModule({
    declarations: [GuidedTourComponent],
    exports: [GuidedTourComponent],
    entryComponents: [GuidedTourComponent],
})
export class NgxGuidedTourModule {
    public static forRoot(): ModuleWithProviders {
        return {
            ngModule: NgxGuidedTourModule,
            providers: [ErrorHandler, GuidedTourService],
        };
    }
}
