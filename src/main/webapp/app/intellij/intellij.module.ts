import { APP_INITIALIZER, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IntellijButtonComponent } from 'app/intellij/intellij-button/intellij-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MomentModule } from 'ngx-moment';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { WindowRef } from 'app/core';
import { ModalConfirmAutofocusComponent } from './modal-confirm-autofocus/modal-confirm-autofocus.component';
import { TranslateModule } from '@ngx-translate/core';
import { IdeFilterDirective } from './ide-filter.directive';
import { IdeBuildAndTestService } from 'app/intellij/ide-build-and-test.service';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';

export function initJavaBridge(bridge: JavaBridgeService, win: WindowRef) {
    return () => JavaBridgeService.initBridge(bridge, win);
}

@NgModule({
    declarations: [IntellijButtonComponent, ModalConfirmAutofocusComponent, IdeFilterDirective],
    entryComponents: [ModalConfirmAutofocusComponent],
    imports: [CommonModule, FontAwesomeModule, MomentModule, TranslateModule, FeatureToggleModule],
    exports: [IntellijButtonComponent, IdeFilterDirective],
    providers: [JavaBridgeService, { provide: APP_INITIALIZER, useFactory: initJavaBridge, deps: [JavaBridgeService, WindowRef], multi: true }, IdeBuildAndTestService],
})
export class IntellijModule {}
