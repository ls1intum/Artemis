import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faFileAlt } from '@fortawesome/free-regular-svg-icons';
import { faCircleNotch, faGear } from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown } from '@ng-bootstrap/ng-bootstrap';
import { MAX_TAB_SIZE } from 'app/shared/monaco-editor/monaco-editor.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-code-editor-header',
    templateUrl: './code-editor-header.component.html',
    imports: [NgbDropdown, ArtemisTranslatePipe, FormsModule, FontAwesomeModule, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CodeEditorHeaderComponent {
    readonly fileName = input<string>();
    readonly isLoading = input<boolean>(false);
    readonly showTabSizeSelector = input<boolean>(true);
    readonly onValidateTabSize = output<number>();

    readonly tabSize = model<number>(4);

    readonly MAX_TAB_SIZE = MAX_TAB_SIZE;

    // Icons
    readonly farFileAlt = faFileAlt;
    readonly faCircleNotch = faCircleNotch;
    readonly faGear = faGear;

    /**
     * Changes the tab size to a valid value in case it is not.
     *
     * Valid values are in range [1, {@link MAX_TAB_SIZE}].
     */
    validateTabSize(): void {
        this.tabSize.set(Math.max(1, Math.min(this.tabSize(), MAX_TAB_SIZE)));
        this.onValidateTabSize.emit(this.tabSize());
    }
}
