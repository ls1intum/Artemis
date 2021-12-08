import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { ItalicCommand } from 'app/shared/markdown-editor/commands/italic.command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('ItalicCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
            });
    });
    it('should add ** on execute', () => {
        const italicCommand = new ItalicCommand();
        comp.defaultCommands = [italicCommand];
        fixture.detectChanges();
        comp.ngAfterViewInit();

        italicCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('**');
    });
});
