import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { spy } from 'sinon';

import { AceEditorModule } from 'ng2-ace-editor';
import { CodeEditorActionsComponent } from 'app/code-editor';
import { CommitState, EditorState } from 'app/entities/ace-editor';
import { ArTEMiSTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorActionsComponent', () => {
    let comp: CodeEditorActionsComponent;
    let fixture: ComponentFixture<CodeEditorActionsComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule],
            declarations: [CodeEditorActionsComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorActionsComponent);
                comp = fixture.componentInstance;
            });
    }));

    it('should show save and submit button without any inputs', () => {
        const saveButton = fixture.debugElement.query(By.css('#save_button'));
        const submitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(saveButton).to.exist;
        expect(submitButton).to.exist;
    });

    it('should disable save button if building', () => {
        comp.isBuilding = false;
        fixture.detectChanges();

        const saveButton = fixture.debugElement.query(By.css('#save_button'));
        expect(saveButton.nativeElement.disabled).to.be.true;
    });

    it('should disable submit button if saving', () => {
        comp.editorState = EditorState.SAVING;
        fixture.detectChanges();

        const submitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(submitButton.nativeElement.disabled).to.be.true;
    });

    it('should disable submit button if building', () => {
        comp.isBuilding = true;
        fixture.detectChanges();

        const submitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(submitButton.nativeElement.disabled).to.be.true;
    });

    it('should disable submit button if commiting', () => {
        comp.commitState = CommitState.COMMITTING;
        fixture.detectChanges();

        const submitButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(submitButton.nativeElement.disabled).to.be.true;
    });

    it('should update ui when saving', () => {
        const saveButton = fixture.debugElement.query(By.css('#save_button'));
        comp.editorState = EditorState.UNSAVED_CHANGES;
        fixture.detectChanges();
        const saveButtonFeedbackBeforeSave = saveButton.nativeElement.innerHTML;
        comp.editorState = EditorState.SAVING;
        fixture.detectChanges();
        const saveButtonFeedbackAfterSave = saveButton.nativeElement.innerHTML;
        expect(saveButtonFeedbackAfterSave).not.to.be.equal(saveButtonFeedbackBeforeSave);
    });

    it('should update ui when building', () => {
        const commitButton = fixture.debugElement.query(By.css('#submit_button'));
        comp.editorState = EditorState.COMMITTING;
        fixture.detectChanges();
        const commitButtonFeedbackBeforeStartBuild = commitButton.nativeElement.innerHTML;
        comp.isBuilding = true;
        fixture.detectChanges();
        const commitButtonFeedbackAfterStartBuild = commitButton.nativeElement.innerHTML;
        expect(commitButtonFeedbackAfterStartBuild).not.to.be.equal(commitButtonFeedbackBeforeStartBuild);
    });

    it('should call onSave if clicked on save', () => {
        const onSave = spy(() => {});
        comp.onSave = onSave;
        const onCommit = spy(() => {});
        comp.onCommit = onCommit;
        comp.editorState = EditorState.UNSAVED_CHANGES;
        comp.isBuilding = false;
        fixture.detectChanges();

        const saveButton = fixture.debugElement.query(By.css('#save_button'));
        expect(saveButton.nativeElement.disabled).to.be.false;

        saveButton.nativeElement.click();
        expect(onSave).to.have.been.calledOnce;
        expect(onCommit).to.not.have.been.called;
    });

    it('should call onCommit if clicked on submit', () => {
        const onSave = spy(() => {});
        comp.onSave = onSave;
        const onCommit = spy(() => {});
        comp.onCommit = onCommit;

        comp.commitState = CommitState.UNCOMMITTED_CHANGES;
        comp.isBuilding = false;
        fixture.detectChanges();

        const saveButton = fixture.debugElement.query(By.css('#submit_button'));
        expect(saveButton.nativeElement.disabled).to.be.false;

        saveButton.nativeElement.click();
        expect(onCommit).to.have.been.calledOnce;
        expect(onSave).to.not.have.been.called;
    });
});
