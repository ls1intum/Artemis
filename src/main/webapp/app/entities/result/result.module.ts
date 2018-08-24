import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from '../../shared';
import { ResultService } from './';
import { ExerciseResultService } from './result.service';
import { ResultComponent, ResultDetailComponent } from './result.component';
import { MomentModule } from 'angular2-moment';

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        MomentModule
    ],
    declarations: [
        ResultComponent,
        ResultDetailComponent
    ],
    exports: [
        ResultComponent
    ],
    entryComponents: [
        ResultComponent,
        ResultDetailComponent
    ],
    providers: [
        ResultService,
        ExerciseResultService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSResultModule {}
