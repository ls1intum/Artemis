import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import * as sinon from 'sinon';
import { ReferenceCommand } from 'app/shared/markdown-editor/commands/reference.command';

chai.use(sinonChai);
const expect = chai.expect;

describe('ReferenceCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;
    let referenceCommand = new ReferenceCommand();

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
                referenceCommand = new ReferenceCommand();
                comp.defaultCommands = [referenceCommand];
            });
    });
    it('should add > Reference on execute when no text is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();

        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('> Reference');
    });

    it('should remove > Reference on execute when reference is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('> lorem');
        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('lorem');
    });

    it('should add > on execute when text is selected', () => {
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('lorem');
        referenceCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).to.equal('> lorem');
    });
});
