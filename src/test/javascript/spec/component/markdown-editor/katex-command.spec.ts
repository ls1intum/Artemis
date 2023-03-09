import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';

describe('KatexCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should add insert the sample e-function into the editor on execute', () => {
        const katexCommand = new KatexCommand();
        comp.domainCommands = [katexCommand];
        fixture.detectChanges();
        comp.ngAfterViewInit();

        katexCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('$$ e^{\\frac{1}{4} y^2} $$');
    });
});
