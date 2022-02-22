import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { OrderedListCommand } from 'app/shared/markdown-editor/commands/orderedListCommand';

describe('OrderedListCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should add ordered list to lines on execute', () => {
        const command = new OrderedListCommand();
        comp.defaultCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('line 1\nline 2');

        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('1. line 1\n2. line 2\n');
    });

    it('should add new ordered list on execute', () => {
        const command = new OrderedListCommand();
        comp.defaultCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('');

        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('1. ');
    });

    it('should remove ordered list on execute', () => {
        const command = new OrderedListCommand();
        comp.defaultCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('1. line 1\n2. line 2');

        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('line 1\nline 2\n');
    });
});
