import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { BoldCommand } from 'app/shared/markdown-editor/commands/bold.command';
import { Command } from 'app/shared/markdown-editor/commands/command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';

describe('BoldCommand', () => {
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

                command = new BoldCommand();
                comp.defaultCommands = [command];
                fixture.detectChanges();
                comp.ngAfterViewInit();
            });
    });

    it('should add **** on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('bold');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('**bold**');
    });

    it('should remove **** on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('**bold**');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('bold');
    });
});
