import { Component, Input } from '@angular/core';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';

@Component({
    selector: 'jhi-ide-button',
    templateUrl: './intellij-button.component.html',
    styleUrls: ['./intellij-button.component.scss'],
})
export class IntellijButtonComponent {
    @Input() buttonLabel: string;
    @Input() buttonLoading = false;
    @Input() outlined = false;
    @Input() smallButton = false;

    javaBridge: JavaBridgeService;

    constructor(javaBridge: JavaBridgeService) {
        this.javaBridge = javaBridge;
    }

    public get btnPrimary(): boolean {
        return !this.outlined;
    }
}
