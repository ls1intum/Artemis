import { Component, AfterViewInit, ViewChild } from '@angular/core';
import { AceEditorComponent } from 'ng2-ace-editor';
import 'brace/theme/chrome';
import 'brace/mode/markdown';
import { Command } from 'app/markdown-editor/commands/command';
import { BoldCommand } from 'app/markdown-editor/commands/bold.command';

@Component({
    selector: 'jhi-markdown-editor',
    styleUrls: ['./markdown-editor.scss'],
    templateUrl: './markdown-editor.component.html'
})
export class MarkdownEditorComponent implements AfterViewInit {
    @ViewChild('aceEditor')
    aceEditorContainer: AceEditorComponent;

    height: string = '300px';
    text: string = 'hallo';

    commands: Command[] = [new BoldCommand()];

    ngAfterViewInit(): void {
        this.aceEditorContainer.setTheme('chrome');
    }
}
