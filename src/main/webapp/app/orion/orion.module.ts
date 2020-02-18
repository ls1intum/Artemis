import { APP_INITIALIZER, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrionButtonComponent } from 'app/orion/orion-button/orion-button.component';
import { MomentModule } from 'ngx-moment';
import { OrionConnectorService } from 'app/orion/orion-connector.service';
import { WindowRef } from 'app/core/websocket/window.service';
import { ModalConfirmAutofocusComponent } from './modal-confirm-autofocus/modal-confirm-autofocus.component';
import { TranslateModule } from '@ngx-translate/core';
import { OrionFilterDirective } from './orion-filter.directive';
import { OrionBuildAndTestService } from 'app/orion/orion-build-and-test.service';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

export function initOrionConnector(bridge: OrionConnectorService, win: WindowRef) {
    return () => OrionConnectorService.initBridge(bridge, win);
}

@NgModule({
    declarations: [OrionButtonComponent, ModalConfirmAutofocusComponent, OrionFilterDirective],
    entryComponents: [ModalConfirmAutofocusComponent],
    imports: [CommonModule, ArtemisSharedModule, MomentModule, TranslateModule, FeatureToggleModule],
    exports: [OrionButtonComponent, OrionFilterDirective],
    providers: [{ provide: APP_INITIALIZER, useFactory: initOrionConnector, deps: [OrionConnectorService, WindowRef], multi: true }, OrionBuildAndTestService],
})
export class OrionModule {}
