import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from '../../shared';
import { ResultService } from './';
import { ExerciseResultService } from './result.service';

@NgModule({
    imports: [
        ArTEMiSSharedModule
    ],
    providers: [
        ResultService,
        ExerciseResultService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSResultModule {}
