import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { OrderedListCommand } from 'app/shared/markdown-editor/commands/orderedListCommand';
import { Command } from 'app/shared/markdown-editor/commands/command';

describe('OrderedListCommand', () => {
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

                command = new OrderedListCommand();
                comp.defaultCommands = [command];
                fixture.detectChanges();
                comp.ngAfterViewInit();
            });
    });

    it('should add ordered list to lines on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('line 1\nline 2');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('1. line 1\n2. line 2\n');
    });

    it('should add new ordered list on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('1. ');
    });

    it('should remove ordered list on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('1. line 1\n2. line 2');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('line 1\nline 2\n');
    });
});
