import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CodeEditorStatusComponent } from 'app/programming/shared/code-editor/status/code-editor-status.component';
import { CommitState } from 'app/programming/shared/code-editor/model/code-editor.model';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockModule } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CodeEditorStatusComponent', () => {
    let comp: CodeEditorStatusComponent;
    let fixture: ComponentFixture<CodeEditorStatusComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [CodeEditorStatusComponent, TranslatePipeMock],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorStatusComponent);
                comp = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });

    it('should show an empty status segment for CommitState if no EditorState is given', () => {
        fixture.detectChanges();
        const commitStateSegment = fixture.debugElement.query(By.css('#commit_state'));
        expect(commitStateSegment.children).toHaveLength(0);
    });

    const commitStateToTranslationKey = {
        UNDEFINED: 'artemisApp.editor.changesUndefined',
        COULD_NOT_BE_RETRIEVED: 'artemisApp.editor.changesError',
        CONFLICT: 'artemisApp.editor.changesConflict',
        CLEAN: 'artemisApp.editor.changesSubmitted',
        UNCOMMITTED_CHANGES: 'artemisApp.editor.unsubmittedChanges',
        COMMITTING: 'artemisApp.editor.submittingChanges',
    };

    Object.keys(CommitState).forEach((commitState) =>
        it(`should show exactly one status segment for CommitState ${commitState} with an icon and a non-empty description`, () => {
            comp.commitState = commitState as CommitState;
            fixture.changeDetectorRef.detectChanges();
            const commitStateSegment = fixture.debugElement.query(By.css('#commit_state'));
            showsExactlyOneStatusSegment(commitStateSegment, commitStateToTranslationKey[commitState as keyof typeof commitStateToTranslationKey]);
        }),
    );

    const showsExactlyOneStatusSegment = (stateSegment: DebugElement, expectedKey: string) => {
        expect(stateSegment.children).toHaveLength(1);
        const icon = stateSegment.query(By.css('fa-icon'));
        expect(icon).not.toBeNull();
        const text = stateSegment.query(By.css('span[jhiTranslate]'));
        expect(text).not.toBeNull();
        expect(text.attributes['jhiTranslate']).toBe(expectedKey);
    };
});
