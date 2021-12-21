import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import * as sinon from 'sinon';
import { UnderlineCommand } from 'app/shared/markdown-editor/commands/underline.command';

chai.use(sinonChai);
const expect = chai.expect;

describe('Underline Command', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let underlineCommand = new UnderlineCommand();

    afterEach(() => {
        sinon.restore();
    });

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
                underlineCommand = new UnderlineCommand();
                comp.defaultCommands = [underlineCommand];
            });
    });
    it('should add <ins></ins> brackets on execute when no text is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();

        underlineCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('<ins></ins>');
    });

    it('should add <ins></ins> around selected text on execute when text is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('lorem');
        underlineCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('<ins>lorem</ins>');
    });
});
