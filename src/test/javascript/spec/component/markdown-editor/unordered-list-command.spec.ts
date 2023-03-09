import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { Command } from 'app/shared/markdown-editor/commands/command';
import { UnorderedListCommand } from 'app/shared/markdown-editor/commands/unorderedListCommand';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';

describe('UnorderedListCommand', () => {
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

                command = new UnorderedListCommand();
                comp.defaultCommands = [command];
                fixture.detectChanges();
                comp.ngAfterViewInit();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should add ordered list to lines on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('line 1\nline 2');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('- line 1\n- line 2');
    });

    it('should add new ordered list on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('- ');
    });

    it('should remove ordered list on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('- line 1\n- line 2');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('line 1\nline 2');
    });

    it('should handle empty lines and create new list', () => {
        comp.aceEditorContainer.getEditor().setValue('Test\n\nTest');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('- Test\n\n- Test');
    });

    it('should handle empty lines and remove lists', () => {
        comp.aceEditorContainer.getEditor().setValue('- Test\n- \n- Test');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('Test\n\nTest');
    });

    it('should handle multiple sequential empty lines and remove lists', () => {
        comp.aceEditorContainer.getEditor().setValue('- Test\n\n- Test');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('Test\n\nTest');
    });

    it('should handle dots in sentences and remove lists', () => {
        comp.aceEditorContainer.getEditor().setValue('- Test with dash at the end-\n- Test with dash- in the center\n- -Test with dash at the start');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('Test with dash at the end-\nTest with dash- in the center\n-Test with dash at the start');
    });

    it('should handle dots in sentences and create lists', () => {
        comp.aceEditorContainer.getEditor().setValue('Test with dash at the end-\nTest with dash- in the center\n-Test with dash at the start');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('- Test with dash at the end-\n- Test with dash- in the center\n- -Test with dash at the start');
    });

    it('should handle single empty lines and remove list', () => {
        comp.aceEditorContainer.getEditor().setValue('- ');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('');
    });

    it('should keep whitespaces at the beginning and create list', () => {
        comp.aceEditorContainer.getEditor().setValue('Test\n   Test');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('- Test\n   - Test');
    });

    it('should keep whitespaces at the beginning and remove list', () => {
        comp.aceEditorContainer.getEditor().setValue('- Test\n  - Test');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('Test\n  Test');
    });

    it('should handle single dash and remove list', () => {
        comp.aceEditorContainer.getEditor().setValue('- -');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('-');
    });

    it('should handle single dash and create list', () => {
        comp.aceEditorContainer.getEditor().setValue('-');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('- -');
    });
});
