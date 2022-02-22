import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { CodeCommand } from 'app/shared/markdown-editor/commands/code.command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';

describe('CodeCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should add `` on execute', () => {
        const command = new CodeCommand();
        comp.defaultCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('code');

        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('`code`');
    });

    it('should remove `` on execute', () => {
        const command = new CodeCommand();
        comp.defaultCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('`code`');

        command.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('code');
    });
});
