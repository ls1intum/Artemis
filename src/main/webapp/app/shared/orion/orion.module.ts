import { APP_INITIALIZER, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrionButtonComponent } from 'app/shared/orion/orion-button/orion-button.component';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { ModalConfirmAutofocusComponent } from './modal-confirm-autofocus/modal-confirm-autofocus.component';
import { TranslateModule } from '@ngx-translate/core';
import { OrionFilterDirective } from './orion-filter.directive';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { OrionAssessmentService } from 'app/orion/assessment/orion-assessment.service';

export function initOrionConnector(connector: OrionConnectorService) {
    return () => OrionConnectorService.initConnector(connector);
}

@NgModule({
    declarations: [OrionButtonComponent, ModalConfirmAutofocusComponent, OrionFilterDirective],
    imports: [CommonModule, ArtemisSharedModule, TranslateModule, FeatureToggleModule],
    exports: [OrionButtonComponent, OrionFilterDirective],
    providers: [{ provide: APP_INITIALIZER, useFactory: initOrionConnector, deps: [OrionConnectorService], multi: true }, OrionBuildAndTestService, OrionAssessmentService],
})
export class OrionModule {}
