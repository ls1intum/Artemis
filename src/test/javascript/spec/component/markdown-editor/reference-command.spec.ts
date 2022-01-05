import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { ReferenceCommand } from 'app/shared/markdown-editor/commands/reference.command';

describe('ReferenceCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let referenceCommand = new ReferenceCommand();

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
                referenceCommand = new ReferenceCommand();
                comp.defaultCommands = [referenceCommand];
            });
    });
    it('should add > Reference on execute when no text is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();

        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('> Reference');
    });

    it('should remove > Reference on execute when reference is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('> lorem');
        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('lorem');
    });

    it('should add > on execute when text is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('lorem');
        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toEqual('> lorem');
    });
});
