import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';

import { AceEditorModule } from 'ng2-ace-editor';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { HeadingOneCommand } from 'app/shared/markdown-editor/commands/headingOne.command';
import * as sinon from 'sinon';
import { HeadingTwoCommand } from 'app/shared/markdown-editor/commands/headingTwo.command';
import { HeadingThreeCommand } from 'app/shared/markdown-editor/commands/headingThree.command';

chai.use(sinonChai);
const expect = chai.expect;

describe('HeadingOneCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let headingOneCommand = new HeadingOneCommand();
    let headingTwoCommand = new HeadingTwoCommand();
    let headingThreeCommand = new HeadingThreeCommand();

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
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('# Heading 1');
        comp.aceEditorContainer.getEditor().setValue('');
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('## Heading 2');
        comp.aceEditorContainer.getEditor().setValue('');
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('### Heading 3');
    });
    it('should add #, ##, ### on execute when text is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();
        sinon.stub(comp.aceEditorContainer.getEditor(), 'getSelectedText').returns('lorem');

        headingOneCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('# lorem');
        comp.aceEditorContainer.getEditor().setValue('lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('lorem');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('## lorem');
        comp.aceEditorContainer.getEditor().setValue('lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('lorem');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('### lorem');
    });

    it('should remove #, ##, ### on execute when text of header selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('# lorem');
        headingOneCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('lorem');
        comp.aceEditorContainer.getEditor().setValue('## lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('## lorem');
        headingTwoCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('lorem');
        comp.aceEditorContainer.getEditor().setValue('### lorem');
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('### lorem');
        headingThreeCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('lorem');
    });
});
