import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { StudentQuestionsComponent, StudentQuestionRowComponent } from './';
import { ArTEMiSSidePanelModule } from 'app/components/side-panel/side-panel.module';

@NgModule({
    imports: [ArTEMiSSharedModule, ArTEMiSSidePanelModule],
    declarations: [StudentQuestionsComponent, StudentQuestionRowComponent],
    exports: [StudentQuestionsComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSStudentQuestionsModule {}
