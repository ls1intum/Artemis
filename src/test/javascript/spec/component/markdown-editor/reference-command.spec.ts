import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { ReferenceCommand } from 'app/shared/markdown-editor/commands/reference.command';

describe('ReferenceCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let referenceCommand: ReferenceCommand;

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

                referenceCommand = new ReferenceCommand();
                comp.defaultCommands = [referenceCommand];
                fixture.detectChanges();
                comp.ngAfterViewInit();
            });
    });

    it('should add > Reference on execute when no text is selected', () => {
        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('> Reference');
    });

    it('should remove > lorem on execute when reference is selected', () => {
        comp.aceEditorContainer.getEditor().setValue('> lorem');
        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('lorem');
    });

    it('should remove > Reference on execute when reference is selected', () => {
        comp.aceEditorContainer.getEditor().setValue('> Reference');
        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('');
    });

    it('should add > on execute when text is selected', () => {
        comp.aceEditorContainer.getEditor().setValue('lorem');
        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('> lorem');
    });
});
