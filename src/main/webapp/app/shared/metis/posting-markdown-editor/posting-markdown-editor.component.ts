import { AfterContentChecked, ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, ViewEncapsulation, forwardRef } from '@angular/core';
import { Command } from 'app/shared/markdown-editor/commands/command';
import { BoldCommand } from 'app/shared/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/shared/markdown-editor/commands/italic.command';
import { ReferenceCommand } from 'app/shared/markdown-editor/commands/reference.command';
import { UnderlineCommand } from 'app/shared/markdown-editor/commands/underline.command';
import { CodeBlockCommand } from 'app/shared/markdown-editor/commands/codeblock.command';
import { CodeCommand } from 'app/shared/markdown-editor/commands/code.command';
import { LinkCommand } from 'app/shared/markdown-editor/commands/link.command';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { ExerciseReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/exerciseReferenceCommand';
import { LectureAttachmentReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/lectureAttachmentReferenceCommand';

@Component({
    selector: 'jhi-posting-markdown-editor',
    templateUrl: './posting-markdown-editor.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => PostingMarkdownEditorComponent),
            multi: true,
        },
    ],
    encapsulation: ViewEncapsulation.None,
})
export class PostingMarkdownEditorComponent implements OnInit, ControlValueAccessor, AfterContentChecked {
    @Input() maxContentLength: number;
    @Input() editorHeight: MarkdownEditorHeight = MarkdownEditorHeight.SMALL;
    @Input() isInputLengthDisplayed = true;
    @Output() valueChange = new EventEmitter();
    defaultCommands: Command[];
    content?: string;
    previewMode = false;

    constructor(private cdref: ChangeDetectorRef, private metisService: MetisService) {}

    /**
     * on initialization: sets commands that will be available as formatting buttons during creation/editing of postings
     */
    ngOnInit(): void {
        this.defaultCommands = [
            new BoldCommand(),
            new ItalicCommand(),
            new UnderlineCommand(),
            new ReferenceCommand(),
            new CodeCommand(),
            new CodeBlockCommand(),
            new LinkCommand(),
            new ExerciseReferenceCommand(this.metisService),
            new LectureAttachmentReferenceCommand(this.metisService),
        ];
    }

    /**
     * this lifecycle hook is required to avoid causing "Expression has changed after it was checked"-error when dismissing all changes in the markdown editor
     * on dismissing the edit-create-modal -> we do not want to store changes in the create-edit-modal that are not saved
     */
    ngAfterContentChecked() {
        this.cdref.detectChanges();
    }

    /**
     * the callback function to register on UI change
     */
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    onChange = (val: string) => {};

    /**
     * emits the value change from component
     */
    valueChanged() {
        this.valueChange.emit();
    }

    /**
     * writes the current value of a form group into the `content` variable,
     * i.e. sets the value programmatically
     * @param value
     */
    writeValue(value: any): void {
        if (value !== undefined) {
            this.content = value;
        }
    }

    /**
     * upon UI element value changes, this method is triggered (required)
     * @param fn
     */
    registerOnChange(fn: any): void {
        this.onChange = fn;
    }

    /**
     * upon touching the element, this method gets triggered (required)
     */
    registerOnTouched(): void {}

    /**
     * changes in bound markdown content
     * @param newValue
     */
    updateField(newValue: string) {
        this.content = newValue;
        this.onChange(this.content);
        this.valueChanged();
    }
}
