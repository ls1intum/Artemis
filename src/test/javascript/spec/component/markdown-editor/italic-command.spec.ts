import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';

import { AceEditorModule } from 'ng2-ace-editor';
import { ItalicCommand } from 'app/markdown-editor/commands';
import { ArtemisMarkdownEditorModule, MarkdownEditorComponent } from 'app/markdown-editor';

chai.use(sinonChai);
const expect = chai.expect;

describe('ItalicCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
            });
    });
    it('should add **** on execute', () => {
        const italicCommand = new ItalicCommand();
        comp.defaultCommands = [italicCommand];
        fixture.detectChanges();
        comp.ngAfterViewInit();

        italicCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('**');
    });
});
