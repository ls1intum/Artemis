import { NgModule, inject, provideAppInitializer } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ModalConfirmAutofocusComponent } from './modal-confirm-autofocus/modal-confirm-autofocus.component';
import { TranslateModule } from '@ngx-translate/core';
import { OrionFilterDirective } from './orion-filter.directive';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';

export function initOrionConnector(connector: OrionConnectorService) {
    return () => OrionConnectorService.initConnector(connector);
}

@NgModule({
    imports: [CommonModule, ArtemisSharedModule, TranslateModule, OrionButtonComponent, ModalConfirmAutofocusComponent, OrionFilterDirective],
    exports: [OrionButtonComponent, OrionFilterDirective],
    providers: [
        provideAppInitializer(() => {
            const initializerFn = initOrionConnector(inject(OrionConnectorService));
            return initializerFn();
        }),
        OrionBuildAndTestService,
        OrionAssessmentService,
    ],
})
export class OrionModule {}
