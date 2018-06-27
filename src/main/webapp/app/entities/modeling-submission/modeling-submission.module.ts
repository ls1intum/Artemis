import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from '../../shared';
import {
    ModelingSubmissionService
} from './';

@NgModule({
    imports: [
        ArTEMiSSharedModule
    ],
    providers: [
        ModelingSubmissionService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSModelingSubmissionModule {}
