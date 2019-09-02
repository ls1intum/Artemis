import { APP_INITIALIZER, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IntellijButtonComponent } from 'app/intellij/intellij-button/intellij-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MomentModule } from 'ngx-moment';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { WindowRef } from 'app/core';
import { ModalConfirmAutofocusComponent } from './modal-confirm-autofocus/modal-confirm-autofocus.component';
import { TranslateModule } from '@ngx-translate/core';

export function initJavaBridge(bridge: JavaBridgeService, win: WindowRef) {
    return () => JavaBridgeService.initBridge(bridge, win);
}

@NgModule({
    declarations: [IntellijButtonComponent, ModalConfirmAutofocusComponent],
    entryComponents: [ModalConfirmAutofocusComponent],
    imports: [CommonModule, FontAwesomeModule, MomentModule, TranslateModule],
    exports: [IntellijButtonComponent],
    providers: [JavaBridgeService, { provide: APP_INITIALIZER, useFactory: initJavaBridge, deps: [JavaBridgeService, WindowRef], multi: true }],
})
export class IntellijModule {}
