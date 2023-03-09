import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { UnderlineCommand } from 'app/shared/markdown-editor/commands/underline.command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';

describe('Underline Command', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let underlineCommand: UnderlineCommand;

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;

                underlineCommand = new UnderlineCommand();
                comp.defaultCommands = [underlineCommand];
                fixture.detectChanges();
                comp.ngAfterViewInit();
            });
    });

    it('should add <ins></ins> brackets on execute when no text is selected', () => {
        underlineCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('<ins></ins>');
    });

    it('should add <ins></ins> around selected text on execute when text is selected', () => {
        comp.aceEditorContainer.getEditor().setValue('lorem');
        underlineCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('<ins>lorem</ins>');
    });

    it('should remove <ins></ins> around selected text on execute when text is selected', () => {
        comp.aceEditorContainer.getEditor().setValue('<ins>lorem</ins>');
        underlineCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('lorem');
    });
});
