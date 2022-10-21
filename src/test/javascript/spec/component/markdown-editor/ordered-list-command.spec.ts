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
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('1. line 1\n2. line 2');
    });

    it('should add new ordered list on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('1. ');
    });

    it('should remove ordered list on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('1. line 1\n2. line 2');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('line 1\nline 2');
    });

    it('should handle empty lines and create new list', () => {
        comp.aceEditorContainer.getEditor().setValue('Test\n\nTest');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('1. Test\n\n2. Test');
    });

    it('should handle empty lines and remove lists', () => {
        comp.aceEditorContainer.getEditor().setValue('1. Test\n2.\n3. Test');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('Test\n\nTest');
    });

    it('should handle multiple sequential empty lines and remove lists', () => {
        comp.aceEditorContainer.getEditor().setValue('1. Test\n\n3. Test');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('Test\n\nTest');
    });

    it('should handle dots in sentences and remove lists', () => {
        comp.aceEditorContainer.getEditor().setValue('1. Test with dot at the end.\n2. Test with dot. in the center\n3. .Test with dot at the start');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('Test with dot at the end.\nTest with dot. in the center\n.Test with dot at the start');
    });

    it('should handle dots in sentences and create lists', () => {
        comp.aceEditorContainer.getEditor().setValue('Test with dot at the end.\nTest with dot. in the center\n.Test with dot at the start');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('1. Test with dot at the end.\n2. Test with dot. in the center\n3. .Test with dot at the start');
    });

    it('should handle single empty lines and create list', () => {
        comp.aceEditorContainer.getEditor().setValue('');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('1. ');
    });

    it('should handle single empty lines and remove list', () => {
        comp.aceEditorContainer.getEditor().setValue('1. ');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('');
    });

    it('should keep whitespaces at the beginning and create list', () => {
        comp.aceEditorContainer.getEditor().setValue('Test\n   Test');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('1. Test\n   2. Test');
    });

    it('should keep whitespaces at the beginning and remove list', () => {
        comp.aceEditorContainer.getEditor().setValue('1. Test\n   2. Test');
        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('Test\n   Test');
    });
});
