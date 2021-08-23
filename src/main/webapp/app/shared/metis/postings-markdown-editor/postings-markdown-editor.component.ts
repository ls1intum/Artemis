import { AfterContentChecked, ChangeDetectorRef, Component, EventEmitter, forwardRef, Input, OnInit, Output } from '@angular/core';
import { Command } from 'app/shared/markdown-editor/commands/command';
import { BoldCommand } from 'app/shared/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/shared/markdown-editor/commands/italic.command';
import { ReferenceCommand } from 'app/shared/markdown-editor/commands/reference.command';
import { UnderlineCommand } from 'app/shared/markdown-editor/commands/underline.command';
import { CodeBlockCommand } from 'app/shared/markdown-editor/commands/codeblock.command';
import { CodeCommand } from 'app/shared/markdown-editor/commands/code.command';
import { LinkCommand } from 'app/shared/markdown-editor/commands/link.command';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
    selector: 'jhi-postings-markdown-editor',
    templateUrl: './postings-markdown-editor.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => PostingsMarkdownEditorComponent),
            multi: true,
        },
    ],
})
export class PostingsMarkdownEditorComponent implements OnInit, ControlValueAccessor, AfterContentChecked {
    @Input() content?: string;
    @Input() maxContentLength: number;
    @Output() valueChange = new EventEmitter();
    defaultCommands: Command[];

    constructor(private cdref: ChangeDetectorRef) {}

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
        ];
    }

    /**
     * this lifecycle hook is required to avoid causing "Expression has changed after it was checked"-error when dismissing all changes in the markdown editor
     * on dismissing the edit-create-modal -> we do not want to store changes in the create-edit-modal that are not saved
     */
    ngAfterContentChecked() {
        this.cdref.detectChanges();
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _onChange = (val: string) => {};

    /**
     * emits the value change from component
     */
    valueChanged() {
        this.valueChange.emit();
    }

    /**
     * writes the current value of a form group into the `content` variable
     * @param value
     */
    writeValue(value: any): void {
        if (value !== undefined) {
            this.content = value;
        }
    }

    /**
     * registers a callback function used when form group input changes (required)
     * @param fn
     */
    registerOnChange(fn: any): void {
        this._onChange = fn;
    }

    /**
     * defines a behavior when from group input is touched (required)
     */
    registerOnTouched(): void {}

    /**
     * function that is called on changes in markdown editor component
     * @param newValue
     */
    updateField(newValue: string) {
        this.content = newValue;
        this._onChange(this.content);
        this.valueChanged();
    }
}
