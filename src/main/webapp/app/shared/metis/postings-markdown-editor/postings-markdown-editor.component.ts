import { Component, EventEmitter, Input, OnInit, Output, forwardRef } from '@angular/core';
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
    styleUrls: ['../../../overview/discussion/discussion.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => PostingsMarkdownEditorComponent),
            multi: true,
        },
    ],
})
export class PostingsMarkdownEditorComponent implements OnInit, ControlValueAccessor {
    @Input() content?: string;
    @Input() maxContentLength: number;
    defaultCommands: Command[];
    propagateChange: any = () => {};

    constructor() {}

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

    writeValue(value: string): void {
        if (value !== undefined) {
            this.content = value;
            this.propagateChange(this.content);
        }
    }

    registerOnChange(fn: any): void {
        this.propagateChange = fn;
    }

    registerOnTouched(): void {}
}
