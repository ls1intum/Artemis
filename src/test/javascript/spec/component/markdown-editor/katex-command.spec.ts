/* tslint:disable:no-unused-expression */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';

import { AceEditorModule } from 'ng2-ace-editor';
import { KatexCommand } from 'app/markdown-editor/commands';
import { ArTEMiSMarkdownEditorModule, MarkdownEditorComponent } from 'app/markdown-editor';

chai.use(sinonChai);
const expect = chai.expect;

describe('KatexCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), AceEditorModule, ArTEMiSMarkdownEditorModule],
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
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('$$ e^{\frac{1}{4} y^2} $$');
    });
});
