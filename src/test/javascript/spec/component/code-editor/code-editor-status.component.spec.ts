import { DebugElement } from '@angular/core/';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { CommitState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockModule } from 'ng-mocks';

describe('CodeEditorStatusComponent', () => {
    let comp: CodeEditorStatusComponent;
    let fixture: ComponentFixture<CodeEditorStatusComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, MockModule(NgbTooltipModule)],
            declarations: [CodeEditorStatusComponent, TranslatePipeMock],
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
        expect(icon).not.toBeNull();
        const text = stateSegment.query(By.css('span'));
        expect(text).not.toBeNull();
        expect(text.nativeElement.textContent).not.toBe('');
    };
});
