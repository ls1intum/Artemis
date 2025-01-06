import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';
import { ModalConfirmAutofocusComponent } from './modal-confirm-autofocus/modal-confirm-autofocus.component';
import { TranslateModule } from '@ngx-translate/core';
import { OrionFilterDirective } from './orion-filter.directive';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';

@NgModule({
    imports: [CommonModule, ArtemisSharedModule, TranslateModule, OrionButtonComponent, ModalConfirmAutofocusComponent, OrionFilterDirective],
    exports: [OrionButtonComponent, OrionFilterDirective],
    providers: [OrionBuildAndTestService, OrionAssessmentService],
})
export class OrionModule {}
