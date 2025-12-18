import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { MarkdownDiffEditorMonacoComponent } from './markdown-diff-editor-monaco.component';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CdkDragMove, DragDropModule } from '@angular/cdk/drag-drop';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import { ColorAction } from 'app/shared/monaco-editor/model/actions/color.action';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MonacoEditorService } from 'app/shared/monaco-editor/service/monaco-editor.service';
import * as monaco from 'monaco-editor';

describe('MarkdownDiffEditorMonacoComponent', () => {
    let fixture: ComponentFixture<MarkdownDiffEditorMonacoComponent>;
    let comp: MarkdownDiffEditorMonacoComponent;
    let mockDiffEditor: jest.Mocked<monaco.editor.IStandaloneDiffEditor>;
    let mockOriginalEditor: jest.Mocked<monaco.editor.IStandaloneCodeEditor>;
    let mockModifiedEditor: jest.Mocked<monaco.editor.IStandaloneCodeEditor>;

    beforeEach(() => {
        // Create mock editors
        mockOriginalEditor = {
            getValue: jest.fn().mockReturnValue(''),
            onDidContentSizeChange: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            onDidChangeHiddenAreas: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            getContentHeight: jest.fn().mockReturnValue(100),
            getScrollHeight: jest.fn().mockReturnValue(100),
        } as any;

        mockModifiedEditor = {
            getValue: jest.fn().mockReturnValue(''),
            onDidContentSizeChange: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            onDidChangeHiddenAreas: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            getContentHeight: jest.fn().mockReturnValue(100),
            getScrollHeight: jest.fn().mockReturnValue(100),
            getSelection: jest.fn().mockReturnValue({ startLineNumber: 1, endLineNumber: 1 }),
            getModel: jest.fn().mockReturnValue({
                getValueInRange: jest.fn().mockReturnValue(''),
            }),
            getPosition: jest.fn().mockReturnValue({ lineNumber: 1, column: 1 }),
            executeEdits: jest.fn(),
            setPosition: jest.fn(),
            focus: jest.fn(),
        } as any;

        mockDiffEditor = {
            getOriginalEditor: jest.fn().mockReturnValue(mockOriginalEditor),
            getModifiedEditor: jest.fn().mockReturnValue(mockModifiedEditor),
            onDidUpdateDiff: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            updateOptions: jest.fn(),
            layout: jest.fn(),
            setModel: jest.fn(),
            getId: jest.fn().mockReturnValue('test-diff-editor-id'),
            dispose: jest.fn(),
            getLineChanges: jest.fn().mockReturnValue([]),
        } as any;

        const mockMonacoEditorService = {
            createStandaloneDiffEditor: jest.fn().mockReturnValue(mockDiffEditor),
        };

        TestBed.configureTestingModule({
            providers: [
                { provide: MonacoEditorService, useValue: mockMonacoEditorService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
            imports: [MarkdownDiffEditorMonacoComponent, DragDropModule, MockDirective(NgbTooltip), MockPipe(ArtemisTranslatePipe)],
        }).overrideComponent(MarkdownDiffEditorMonacoComponent, {
            remove: { imports: [ColorSelectorComponent] },
            add: { imports: [MockComponent(ColorSelectorComponent)] },
        });

        fixture = TestBed.createComponent(MarkdownDiffEditorMonacoComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create the component', () => {
        expect(comp).toBeTruthy();
    });

    it('should initialize with default values', () => {
        expect(comp.targetWrapperHeight).toBe(300);
        expect(comp.minWrapperHeight).toBe(200);
        expect(comp.isResizing).toBeFalse();
    });

    it('should set file contents and create models', () => {
        const originalText = '# Original';
        const modifiedText = '# Modified';
        const originalFileName = 'original.md';
        const modifiedFileName = 'modified.md';

        const createModelSpy = jest.spyOn(monaco.editor, 'createModel');

        fixture.detectChanges();
        comp.setFileContents(originalText, modifiedText, originalFileName, modifiedFileName);

        expect(createModelSpy).toHaveBeenCalledTimes(2);
        expect(mockDiffEditor.setModel).toHaveBeenCalledWith({
            original: expect.any(Object),
            modified: expect.any(Object),
        });
    });

    it('should get text from both editors', () => {
        mockOriginalEditor.getValue.mockReturnValue('original text');
        mockModifiedEditor.getValue.mockReturnValue('modified text');

        const result = comp.getText();

        expect(result).toEqual({
            original: 'original text',
            modified: 'modified text',
        });
    });

    it('should limit the vertical drag position based on input values', () => {
        fixture.detectChanges();
        const wrapperTop = comp.wrapper().nativeElement.getBoundingClientRect().top;

        const minPoint = comp.constrainDragPosition({ x: 0, y: wrapperTop - 10000 });
        expect(minPoint.y).toBe(wrapperTop + comp.resizableMinHeight());

        const maxPoint = comp.constrainDragPosition({ x: 0, y: wrapperTop + 10000 });
        expect(maxPoint.y).toBe(wrapperTop + comp.resizableMaxHeight());
    });

    it('should adjust wrapper height when resized manually', () => {
        const wrapperTop = 100;
        const dragElemHeight = 20;
        const pointerY = 300;
        const expectedHeight = pointerY - wrapperTop - dragElemHeight / 2;

        fixture.componentRef.setInput('resizableMinHeight', 100);

        comp.targetWrapperHeight = 400;

        const cdkDragMove = {
            source: { reset: jest.fn(), element: { nativeElement: { clientHeight: dragElemHeight } } },
            pointerPosition: { y: pointerY },
        } as unknown as CdkDragMove;

        fixture.detectChanges();
        jest.spyOn(comp.wrapper().nativeElement, 'getBoundingClientRect').mockReturnValue({ top: wrapperTop } as DOMRect);

        comp.onResizeMoved(cdkDragMove);

        expect(comp.targetWrapperHeight).toBe(expectedHeight);
        expect(mockDiffEditor.layout).toHaveBeenCalled();
        expect(cdkDragMove.source.reset).toHaveBeenCalled();
    });

    it('should not update height if new height is outside bounds', () => {
        const initialHeight = comp.targetWrapperHeight;
        const cdkDragMove = {
            source: { reset: jest.fn(), element: { nativeElement: { clientHeight: 20 } } },
            pointerPosition: { y: 50 }, // Too small
        } as unknown as CdkDragMove;

        fixture.detectChanges();
        jest.spyOn(comp.wrapper().nativeElement, 'getBoundingClientRect').mockReturnValue({ top: 100 } as DOMRect);

        comp.onResizeMoved(cdkDragMove);

        expect(comp.targetWrapperHeight).toBe(initialHeight);
    });

    it('should execute action on modified editor', () => {
        const action = new FormulaAction();
        const runSpy = jest.spyOn(action, 'run');

        fixture.detectChanges();
        comp.executeAction(action);

        expect(runSpy).toHaveBeenCalledWith(expect.any(Object));
    });

    it('should execute domain action with value on modified editor', () => {
        const action = new TestCaseAction();
        const runSpy = jest.spyOn(action, 'run');
        const value = { id: 'test', value: 'Test Case' };

        fixture.detectChanges();
        comp.executeDomainAction(action, value);

        expect(runSpy).toHaveBeenCalledWith(expect.any(Object), { selectedItem: value });
    });

    it('should open color selector', () => {
        fixture.detectChanges();
        const selector = comp.colorSelector();
        expect(selector).toBeDefined();

        const openSpy = jest.spyOn(selector!, 'openColorSelector');
        const event = new MouseEvent('click');

        comp.openColorSelector(event);

        expect(openSpy).toHaveBeenCalledWith(event, comp.colorPickerMarginTop, comp.colorPickerHeight);
    });

    it('should execute color action with correct color class', () => {
        const colorAction = new ColorAction();
        const runSpy = jest.spyOn(colorAction, 'run');
        fixture.componentRef.setInput('colorAction', colorAction);

        fixture.detectChanges();

        const color = '#ca2024';
        comp.onSelectColor(color);

        expect(runSpy).toHaveBeenCalledWith(expect.any(Object), { color: '#ca2024' });
    });

    it('should pass fullscreen element to fullscreen action', () => {
        const fullscreenAction = new FullscreenAction();
        fixture.componentRef.setInput('metaActions', [fullscreenAction]);

        fixture.detectChanges();

        expect(fullscreenAction.element).toBe(comp.fullElement().nativeElement);
    });

    it('should update editor options when allowSplitView changes', () => {
        fixture.componentRef.setInput('allowSplitView', false);
        fixture.detectChanges();

        expect(mockDiffEditor.updateOptions).toHaveBeenCalledWith(
            expect.objectContaining({
                renderSideBySide: false,
            }),
        );
    });

    it('should layout editor after view init', () => {
        fixture.detectChanges();

        expect(mockDiffEditor.layout).toHaveBeenCalledWith({
            width: expect.any(Number),
            height: expect.any(Number),
        });
    });

    it('should adjust container height', () => {
        const newHeight = 500;

        fixture.detectChanges();
        comp.adjustContainerHeight(newHeight);

        expect(comp.monacoDiffEditorContainerElement.style.height).toBe(`${newHeight}px`);
        expect(mockDiffEditor.layout).toHaveBeenCalled();
    });

    it('should get maximum content height from both editors', () => {
        mockOriginalEditor.getScrollHeight.mockReturnValue(300);
        mockModifiedEditor.getScrollHeight.mockReturnValue(500);

        const maxHeight = comp.getMaximumContentHeight();

        expect(maxHeight).toBe(500);
    });

    it('should get content height of specific editor', () => {
        mockOriginalEditor.getScrollHeight.mockReturnValue(400);

        const height = comp.getContentHeightOfEditor(mockOriginalEditor);

        expect(height).toBe(400);
    });

    it('should dispose listeners on destroy', () => {
        const disposeSpy = jest.fn();
        comp.listeners = [{ dispose: disposeSpy }, { dispose: disposeSpy }];

        comp.ngOnDestroy();

        expect(disposeSpy).toHaveBeenCalledTimes(2);
    });

    it('should not auto-adjust height when resize is enabled', () => {
        fixture.componentRef.setInput('enableResize', true);
        fixture.detectChanges();

        const adjustSpy = jest.spyOn(comp, 'adjustContainerHeight');

        // Trigger diff update (which would normally adjust height)
        const diffCallback = (mockDiffEditor.onDidUpdateDiff as jest.Mock).mock.calls[0][0];
        diffCallback();

        expect(adjustSpy).not.toHaveBeenCalled();
    });

    it('should auto-adjust height when resize is disabled', () => {
        fixture.componentRef.setInput('enableResize', false);
        fixture.detectChanges();

        const adjustSpy = jest.spyOn(comp, 'adjustContainerHeight');

        // Trigger diff update
        const diffCallback = (mockDiffEditor.onDidUpdateDiff as jest.Mock).mock.calls[0][0];
        diffCallback();

        expect(adjustSpy).toHaveBeenCalled();
    });

    it('should update height from input signals in ngAfterViewInit', () => {
        fixture.componentRef.setInput('initialEditorHeight', 400);
        fixture.componentRef.setInput('resizableMinHeight', 150);

        fixture.detectChanges();

        expect(comp.targetWrapperHeight).toBe(400);
        expect(comp.minWrapperHeight).toBe(150);
    });

    it('should compute element client height', () => {
        const element = document.createElement('div');
        Object.defineProperty(element, 'clientHeight', { value: 250, writable: false });

        const height = comp.getElementClientHeight(element);

        expect(height).toBe(250);
    });

    it('should split domain actions by type', () => {
        const actionWithoutOptions = new FormulaAction();
        const actionWithOptions = new TestCaseAction();

        fixture.componentRef.setInput('domainActions', [actionWithoutOptions, actionWithOptions]);
        fixture.detectChanges();

        expect(comp.domainActionsWithoutOptions()).toContain(actionWithoutOptions);
        expect(comp.domainActionsWithOptions()).toContain(actionWithOptions);
    });
});
