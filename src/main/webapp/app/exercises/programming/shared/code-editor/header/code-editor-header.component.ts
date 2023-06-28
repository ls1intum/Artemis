import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MAX_TAB_SIZE } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { faFileAlt } from '@fortawesome/free-regular-svg-icons';
import { faCircleNotch, faGear } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-code-editor-header',
    templateUrl: './code-editor-header.component.html',
})
export class CodeEditorHeaderComponent {
    @Input()
    filename: string;

    @Input()
    isLoading: boolean;

    @Output()
    tabSizeChanged = new EventEmitter<number>();

    tabSize = 4;

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
        this.tabSize = Math.max(1, Math.min(this.tabSize, MAX_TAB_SIZE));
        this.tabSizeChanged.emit(this.tabSize);
    }
}
