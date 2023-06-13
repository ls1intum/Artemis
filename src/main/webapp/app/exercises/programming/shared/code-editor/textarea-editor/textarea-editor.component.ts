import { AfterViewInit, Component, ElementRef, Input, ViewChild } from '@angular/core';

@Component({
    selector: 'jhi-textarea-editor',
    templateUrl: './textarea-editor.component.html',
    styleUrls: ['./textarea-editor.component.scss'],
})
export class TextareaEditorComponent implements AfterViewInit {
    @ViewChild('textarea') textArea: ElementRef;
    @ViewChild('lineNumbers') lineNumbersDiv: ElementRef;

    linesNumber?: number;
    _content?: string;

    get content(): string | undefined {
        return this._content;
    }

    @Input()
    set content(value: string | undefined) {
        this._content = value;
        this.updateLinesNumber(value);
    }

    ngAfterViewInit() {
        this.textArea.nativeElement.addEventListener('scroll', () => {
            this.lineNumbersDiv!.nativeElement.scrollTop = this.textArea.nativeElement.scrollTop;
        });
    }

    /**
     * Counts the number of lines a string has, and assigns it to the linesNumber property
     * @param content The string representing the file's content
     */
    updateLinesNumber(content?: string) {
        this.linesNumber = content?.split(/\r\n|\r|\n/).length;
    }
}
