import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { ColorPickerCommand } from 'app/shared/markdown-editor/commands/colorPicker.command';
import { Command } from 'app/shared/markdown-editor/commands/command';

describe('ColorPickerCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let command: Command;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, ArtemisMarkdownEditorModule],
            declarations: [MockComponent(FaIconComponent), MockComponent(FaLayersComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;

                command = new ColorPickerCommand();
                comp.colorCommands = [command];
                fixture.detectChanges();
                comp.ngAfterViewInit();
            });
    });

    const colorTable = [
        ['#ca2024', 'red'],
        ['#3ea119', 'green'],
        ['#ffffff', 'white'],
        ['#fffa5c', 'yellow'],
        ['#0d3cc2', 'blue'],
        ['#b05db8', 'lila'],
        ['#d86b1f', 'orange'],
    ];

    it.each(colorTable)('should add color %s on execute', (hex, color) => {
        comp.aceEditorContainer.getEditor().setValue('test');
        command.execute(hex);
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('<span class="' + color + '">test</span>');
    });

    it('should add color black on execute', () => {
        comp.aceEditorContainer.getEditor().setValue('test');
        command.execute('#000000');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('test');
    });

    it.each(colorTable)('should remove color %s on execute', (hex, color) => {
        comp.aceEditorContainer.getEditor().setValue('<span class="' + color + '">test</span>');
        command.execute('#ffffff');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('test');
    });
});
