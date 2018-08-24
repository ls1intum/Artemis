import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../../shared';
import { ResultComponent, ResultDetailComponent, ResultService } from './';
import { ExerciseResultService } from './result.service';
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
        ResultComponent,
        ResultDetailComponent
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
