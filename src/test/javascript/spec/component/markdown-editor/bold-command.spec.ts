import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { BoldCommand } from 'app/shared/markdown-editor/commands/bold.command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';

describe('BoldCommand', () => {
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

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should add **** on execute', () => {
        const command = new BoldCommand();
        jest.spyOn(command, 'getSelectedText').mockReturnValue('bold');

        comp.defaultCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();

        command.execute();
        expect(command.getSelectedText).toHaveBeenCalledTimes(1);
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('**bold**');
    });

    it('should remove **** on execute', () => {
        const command = new BoldCommand();
        jest.spyOn(command, 'getSelectedText').mockReturnValue('**bold**');

        comp.defaultCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();

        command.execute();
        expect(command.getSelectedText).toHaveBeenCalledTimes(1);
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('bold');
    });
});
