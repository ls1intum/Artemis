import { Component, HostBinding, Input } from '@angular/core';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';

@Component({
    /* tslint:disable-next-line component-selector */
    selector: 'button[jhi-intellij-button]',
    templateUrl: './intellij-button.component.html',
    styleUrls: ['./intellij-button.component.scss'],
})
export class IntellijButtonComponent {
    @Input() buttonLabel: string;
    @Input() buttonLoading = false;
    @HostBinding('class.btn-outline-primary') @Input() outlined = false;
    @HostBinding('class.btn-sm') @Input() smallButton = false;
    @HostBinding('class.btn') isButton = true;

    javaBridge: JavaBridgeService;

    constructor(javaBridge: JavaBridgeService) {
        this.javaBridge = javaBridge;
    }

    @HostBinding('class.btn-primary')
    public get btnPrimary(): boolean {
        return !this.outlined;
    }
}
