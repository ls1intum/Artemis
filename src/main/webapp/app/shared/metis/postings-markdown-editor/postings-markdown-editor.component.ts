import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Command } from 'app/shared/markdown-editor/commands/command';
import { BoldCommand } from 'app/shared/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/shared/markdown-editor/commands/italic.command';
import { ReferenceCommand } from 'app/shared/markdown-editor/commands/reference.command';
import { UnderlineCommand } from 'app/shared/markdown-editor/commands/underline.command';
import { CodeBlockCommand } from 'app/shared/markdown-editor/commands/codeblock.command';
import { CodeCommand } from 'app/shared/markdown-editor/commands/code.command';
import { LinkCommand } from 'app/shared/markdown-editor/commands/link.command';

@Component({
    selector: 'jhi-postings-markdown-editor',
    templateUrl: './postings-markdown-editor.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class PostingsMarkdownEditorComponent implements OnInit {
    @Input() content?: string;
    @Output() contentChange: EventEmitter<string> = new EventEmitter<string>();
    defaultCommands: Command[];

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

    markdownChange(value: string) {
        this.content = value;
        this.contentChange.emit(value);
    }
}
