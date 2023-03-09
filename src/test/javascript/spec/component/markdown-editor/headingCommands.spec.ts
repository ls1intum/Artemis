import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { HeadingOneCommand } from 'app/shared/markdown-editor/commands/headingOne.command';
import { HeadingThreeCommand } from 'app/shared/markdown-editor/commands/headingThree.command';
import { HeadingTwoCommand } from 'app/shared/markdown-editor/commands/headingTwo.command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';

describe('HeadingOneCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let headingOneCommand = new HeadingOneCommand();
    let headingTwoCommand = new HeadingTwoCommand();
    let headingThreeCommand = new HeadingThreeCommand();

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
                headingOneCommand = new HeadingOneCommand();
                headingTwoCommand = new HeadingTwoCommand();
                headingThreeCommand = new HeadingThreeCommand();
                comp.defaultCommands = [headingOneCommand, headingTwoCommand, headingThreeCommand];
                fixture.detectChanges();
                comp.ngAfterViewInit();
            });
    });

    it('should add # Heading 1,2,3 on execute when no text is selected', () => {
        headingOneCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('# Heading 1');
        comp.aceEditorContainer.getEditor().setValue('');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('## Heading 2');
        comp.aceEditorContainer.getEditor().setValue('');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('### Heading 3');
    });

    it('should add #, ##, ### on execute when text is selected', () => {
        jest.spyOn(comp.aceEditorContainer.getEditor(), 'getSelectedText').mockReturnValue('lorem');

        headingOneCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('# lorem');
        comp.aceEditorContainer.getEditor().setValue('lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('lorem');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('## lorem');
        comp.aceEditorContainer.getEditor().setValue('lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('lorem');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('### lorem');
    });

    it('should remove #, ##, ### on execute when text of header selected', () => {
        comp.aceEditorContainer.getEditor().setValue('# lorem');
        headingOneCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('lorem');
        comp.aceEditorContainer.getEditor().setValue('## lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('## lorem');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('lorem');
        comp.aceEditorContainer.getEditor().setValue('### lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('### lorem');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('lorem');
    });

    it('should remove #, ##, ### with Heading text on execute when text of header selected', () => {
        comp.aceEditorContainer.getEditor().setValue('# Heading 1');
        headingOneCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('');
        comp.aceEditorContainer.getEditor().setValue('## Heading 2');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('## Heading 2');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('');
        comp.aceEditorContainer.getEditor().setValue('### Heading 3');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('### Heading 3');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('');
    });
});
