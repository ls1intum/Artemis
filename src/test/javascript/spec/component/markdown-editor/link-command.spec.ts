import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { LinkCommand } from 'app/shared/markdown-editor/commands/link.command';
import { Command } from 'app/shared/markdown-editor/commands/command';

describe('LinkCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let command: Command;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;

                command = new LinkCommand();
                comp.defaultCommands = [command];
                fixture.detectChanges();
                comp.ngAfterViewInit();
            });
    });

    it('should add [](http://) on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('[](http://)');
    });

    it('should remove [](http://) on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('[](http://)test');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('test');
    });
});
