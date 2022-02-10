import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { HeadingOneCommand } from 'app/shared/markdown-editor/commands/headingOne.command';
import { HeadingTwoCommand } from 'app/shared/markdown-editor/commands/headingTwo.command';
import { HeadingThreeCommand } from 'app/shared/markdown-editor/commands/headingThree.command';

describe('HeadingOneCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let headingOneCommand = new HeadingOneCommand();
    let headingTwoCommand = new HeadingTwoCommand();
    let headingThreeCommand = new HeadingThreeCommand();

    afterEach(() => {
        jest.restoreAllMocks();
    });

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
                headingOneCommand = new HeadingOneCommand();
                headingTwoCommand = new HeadingTwoCommand();
                headingThreeCommand = new HeadingThreeCommand();
                comp.defaultCommands = [headingOneCommand, headingTwoCommand, headingThreeCommand];
            });
    });
    it('should add # Heading 1,2,3 on execute when no text is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();

        headingOneCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('# Heading 1');
        comp.aceEditorContainer.getEditor().setValue('');
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('## Heading 2');
        comp.aceEditorContainer.getEditor().setValue('');
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('### Heading 3');
    });
    it('should add #, ##, ### on execute when text is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();
        jest.spyOn(comp.aceEditorContainer.getEditor(), 'getSelectedText').mockReturnValue('lorem');

        headingOneCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('# lorem');
        comp.aceEditorContainer.getEditor().setValue('lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('lorem');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('## lorem');
        comp.aceEditorContainer.getEditor().setValue('lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('lorem');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('### lorem');
    });

    it('should remove #, ##, ### on execute when text of header selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('# lorem');
        headingOneCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('lorem');
        comp.aceEditorContainer.getEditor().setValue('## lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('## lorem');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('lorem');
        comp.aceEditorContainer.getEditor().setValue('### lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('### lorem');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('lorem');
    });
});
