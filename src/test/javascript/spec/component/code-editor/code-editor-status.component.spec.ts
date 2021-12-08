import { DebugElement } from '@angular/core/';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { CommitState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockDirective } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

describe('CodeEditorStatusComponent', () => {
    let comp: CodeEditorStatusComponent;
    let fixture: ComponentFixture<CodeEditorStatusComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule],
            declarations: [CodeEditorStatusComponent, TranslatePipeMock, MockDirective(NgbTooltip)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorStatusComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should show an empty status segment for CommitState if no EditorState is given', () => {
        const commitStateSegment = fixture.debugElement.query(By.css('#commit_state'));
        expect(commitStateSegment.children).toBeEmpty();
    });

    Object.keys(CommitState).map((commitState) =>
        it(`should show exactly one status segment for CommitState ${commitState} with an icon and a non empty description`, () => {
            comp.commitState = commitState as CommitState;
            fixture.detectChanges();
            const commitStateSegment = fixture.debugElement.query(By.css('#commit_state'));
            showsExactlyOneStatusSegment(commitStateSegment);
        }),
    );

    const showsExactlyOneStatusSegment = (stateSegment: DebugElement) => {
        expect(stateSegment.children).toHaveLength(1);
        const icon = stateSegment.query(By.css('fa-icon'));
        expect(icon).not.toBe(null);
        const text = stateSegment.query(By.css('span'));
        expect(text).not.toBe(null);
        expect(text.nativeElement.textContent).not.toBe('');
    };
});
