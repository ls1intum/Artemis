import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { StudentQuestionsComponent, StudentQuestionRowComponent } from './';
import { ArTEMiSSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArTEMiSConfirmIconModule } from 'app/components/confirm-icon/confirm-icon.module';

@NgModule({
    imports: [ArTEMiSSharedModule, ArTEMiSSidePanelModule, ArTEMiSConfirmIconModule],
    declarations: [StudentQuestionsComponent, StudentQuestionRowComponent],
    exports: [StudentQuestionsComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSStudentQuestionsModule {}
