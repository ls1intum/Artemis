import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../../shared';
import { ModelingAssessmentService } from './modeling-assessment.service';

@NgModule({
    imports: [
        ArTEMiSSharedModule
    ],
    declarations: [

    ],
    entryComponents: [

    ],
    providers: [
        ModelingAssessmentService
    ]
})
export class ArTEMiSModelingAssessmentModule {}
