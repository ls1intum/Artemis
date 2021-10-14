import { DebugElement } from '@angular/core/';
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { AceEditorModule } from 'ng2-ace-editor';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import * as chai from 'chai';
import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { CommitState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';

const expect = chai.expect;

describe('CodeEditorStatusComponent', () => {
    let comp: CodeEditorStatusComponent;
    let fixture: ComponentFixture<CodeEditorStatusComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, NgbModule],
            declarations: [CodeEditorStatusComponent, TranslatePipeMock],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorStatusComponent);
                comp = fixture.componentInstance;
            });
    }));

    it('should show an empty status segment for CommitState if no EditorState is given', () => {
        const commitStateSegment = fixture.debugElement.query(By.css('#commit_state'));
        expect(commitStateSegment.children).to.be.empty;
    });

    Object.keys(CommitState).map((commitState) =>
        it(`should show exactly one status segment for CommitState ${commitState} with an icon and a non empty description`, function () {
            comp.commitState = commitState as CommitState;
            fixture.detectChanges();
            const commitStateSegment = fixture.debugElement.query(By.css('#commit_state'));
            showsExactlyOneStatusSegment(commitStateSegment);
        }),
    );

    const showsExactlyOneStatusSegment = (stateSegment: DebugElement) => {
        expect(stateSegment.children).to.have.length(1);
        const icon = stateSegment.query(By.css('fa-icon'));
        expect(icon).to.exist;
        const text = stateSegment.query(By.css('span'));
        expect(text).to.exist;
        expect(text.nativeElement.textContent).not.to.equal('');
    };
});
