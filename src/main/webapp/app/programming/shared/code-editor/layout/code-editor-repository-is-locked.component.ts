import { Component } from '@angular/core';
import { faInfoCircle } from '@fortawesome/free-solid-svg-icons/faInfoCircle';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-code-editor-repository-is-locked',
    template: `
        <span
            id="repository-locked-warning"
            class="locked-container flex items-center rounded-[0.15rem] bg-state-warning px-[0.65em] py-[0.35em] text-[0.75em] leading-none font-bold whitespace-nowrap"
        >
            <fa-icon [icon]="faInfoCircle" class="text-white" size="2x" />
            <span
                class="ms-2 locked-lable"
                jhiTranslate="artemisApp.programmingExercise.repositoryIsLocked.title"
                [tumAetUiTooltip]="'artemisApp.programmingExercise.repositoryIsLocked.tooltip' | artemisTranslate"
            >
                The due date has passed, your repository is locked. You can still read the code but not make any changes to it.
            </span>
        </span>
    `,
    styles: ['.locked-lable {font-size: 1.2rem; color: white}'],
    imports: [FaIconComponent, TranslateDirective, TumAetUiTooltipDirective, ArtemisTranslatePipe],
})
export class CodeEditorRepositoryIsLockedComponent {
    // Icons
    faInfoCircle = faInfoCircle;
}
