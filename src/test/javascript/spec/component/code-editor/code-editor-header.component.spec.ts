import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CodeEditorHeaderComponent } from 'app/exercises/programming/shared/code-editor/header/code-editor-header.component';
import { ArtemisTestModule } from '../../test.module';
import { NgbDropdownMocksModule } from '../../helpers/mocks/directive/ngbDropdownMocks.module';

import { MAX_TAB_SIZE } from 'app/shared/monaco-editor/monaco-editor.component';

describe('CodeEditorHeaderComponent', () => {
    let fixture: ComponentFixture<CodeEditorHeaderComponent>;
    let comp: CodeEditorHeaderComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbDropdownMocksModule],
            declarations: [],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorHeaderComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should only allow tab sizes between 1 and the maximum size', () => {
        fixture.componentRef.setInput('tabSize', 4);
        comp.validateTabSize();
        expect(comp.tabSize()).toBe(4);

        fixture.componentRef.setInput('tabSize', -1);
        comp.validateTabSize();
        expect(comp.tabSize()).toBe(1);

        fixture.componentRef.setInput('tabSize', MAX_TAB_SIZE + 10);
        comp.validateTabSize();
        expect(comp.tabSize()).toBe(MAX_TAB_SIZE);
    });

    it('should notify when the tab size changed', fakeAsync(() => {
        comp.tabSizeChanged.subscribe((tabSize) => {
            expect(tabSize).toBe(5);
        });

        fixture.componentRef.setInput('tabSize', 5);
        comp.validateTabSize();

        tick();
    }));
});
