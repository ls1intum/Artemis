import { Component, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleNotch, faCode } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CodeButtonPopoverContentComponent } from './code-button-popover-content.component';

@Component({
    selector: 'jhi-code-button-new',
    templateUrl: './code-button-new.component.html',
    imports: [TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, FaIconComponent, ArtemisTranslatePipe, CodeButtonPopoverContentComponent],
})
export class CodeButtonNewComponent {
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCode = faCode;

    loadingData = signal(false);
}
