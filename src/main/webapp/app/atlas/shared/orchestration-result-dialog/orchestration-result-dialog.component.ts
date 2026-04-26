import { Component, input, model } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-orchestration-result-dialog',
    templateUrl: './orchestration-result-dialog.component.html',
    styleUrl: './orchestration-result-dialog.component.scss',
    imports: [DialogModule, ButtonModule, ArtemisTranslatePipe, TranslateDirective],
})
export class OrchestrationResultDialogComponent {
    readonly visible = model<boolean>(false);
    readonly summary = input<string | undefined>(undefined);

    protected close() {
        this.visible.set(false);
    }
}
