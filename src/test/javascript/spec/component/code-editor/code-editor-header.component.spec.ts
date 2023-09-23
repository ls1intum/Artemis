import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CodeEditorHeaderComponent } from 'app/exercises/programming/shared/code-editor/header/code-editor-header.component';
import { ArtemisTestModule } from '../../test.module';
import { NgbDropdownMocksModule } from '../../helpers/mocks/directive/ngbDropdownMocks.module';
import { MAX_TAB_SIZE } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';

describe('CodeEditorHeaderComponent', () => {
    let fixture: ComponentFixture<CodeEditorHeaderComponent>;
    let comp: CodeEditorHeaderComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbDropdownMocksModule],
            declarations: [CodeEditorHeaderComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorHeaderComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should only allow tab sizes between 1 and the maximum size', () => {
        comp.tabSize = 4;
        comp.validateTabSize();
        expect(comp.tabSize).toBe(4);

        comp.tabSize = -1;
        comp.validateTabSize();
        expect(comp.tabSize).toBe(1);

        comp.tabSize = MAX_TAB_SIZE + 10;
        comp.validateTabSize();
        expect(comp.tabSize).toBe(MAX_TAB_SIZE);
    });

    it('should notify when the tab size changed', fakeAsync(() => {
        comp.tabSizeChanged.subscribe((tabSize) => {
            expect(tabSize).toBe(5);
        });

        comp.tabSize = 5;
        comp.validateTabSize();

        tick();
    }));
});
