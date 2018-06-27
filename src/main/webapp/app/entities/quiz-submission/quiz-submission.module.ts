import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from '../../shared';
import {
    QuizSubmissionService
} from './';

@NgModule({
    imports: [
        ArTEMiSSharedModule,
    ],
    providers: [
        QuizSubmissionService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizSubmissionModule {}
