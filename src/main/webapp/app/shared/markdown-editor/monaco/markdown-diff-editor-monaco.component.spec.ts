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
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { MarkdownEditorHeight } from './markdown-editor-monaco.component';
import * as monaco from 'monaco-editor';

describe('MarkdownDiffEditorMonacoComponent', () => {
    let fixture: ComponentFixture<MarkdownDiffEditorMonacoComponent>;
    let comp: MarkdownDiffEditorMonacoComponent;
    let mockDiffEditor: jest.Mocked<monaco.editor.IStandaloneDiffEditor>;
    let mockOriginalEditor: jest.Mocked<monaco.editor.IStandaloneCodeEditor>;
    let mockModifiedEditor: jest.Mocked<monaco.editor.IStandaloneCodeEditor>;
    let mockDiffEditorComponent: Partial<MonacoDiffEditorComponent>;

    beforeEach(() => {
        // Mock ResizeObserver which is not available in Jest environment
        global.ResizeObserver = jest.fn().mockImplementation(() => ({
            observe: jest.fn(),
            unobserve: jest.fn(),
            disconnect: jest.fn(),
        }));

        // Create mock editors
        mockOriginalEditor = {
            getValue: jest.fn().mockReturnValue(''),
            onDidContentSizeChange: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            onDidChangeHiddenAreas: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            getContentHeight: jest.fn().mockReturnValue(100),
            getScrollHeight: jest.fn().mockReturnValue(100),
            getModel: jest.fn().mockReturnValue(null),
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
            updateOptions: jest.fn(),
            onMouseMove: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            onMouseDown: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            onKeyDown: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            onMouseLeave: jest.fn().mockReturnValue({ dispose: jest.fn() }),
            createDecorationsCollection: jest.fn().mockReturnValue({ set: jest.fn(), clear: jest.fn() }),
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

        // Create mock MonacoDiffEditorComponent
        mockDiffEditorComponent = {
            setFileContents: jest.fn(),
            getText: jest.fn().mockReturnValue({ original: '', modified: '' }),
            getModifiedEditor: jest.fn().mockReturnValue(mockModifiedEditor),
            getOriginalEditor: jest.fn().mockReturnValue(mockOriginalEditor),
            getDiffEditor: jest.fn().mockReturnValue(mockDiffEditor),
            getLineChanges: jest.fn().mockReturnValue([]),
            fillContainer: jest.fn(),
            layout: jest.fn(),
            monacoDiffEditorContainerElement: document.createElement('div'),
        };

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
        })
            .overrideComponent(MarkdownDiffEditorMonacoComponent, {
                remove: { imports: [ColorSelectorComponent, MonacoDiffEditorComponent] },
                add: { imports: [MockComponent(ColorSelectorComponent), MockComponent(MonacoDiffEditorComponent)] },
            })
            .compileComponents();

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
        // Default is now MarkdownEditorHeight.MEDIUM (500)
        expect(comp.targetWrapperHeight).toBe(MarkdownEditorHeight.MEDIUM);
        expect(comp.minWrapperHeight).toBe(MarkdownEditorHeight.SMALL);
        expect(comp.isResizing).toBeFalse();
    });

    it('should set file contents via embedded component', () => {
        const originalText = '# Original';
        const modifiedText = '# Modified';
        const originalFileName = 'original.md';
        const modifiedFileName = 'modified.md';

        fixture.detectChanges();

        // Mock the diffEditorComponent viewChild
        jest.spyOn(comp, 'diffEditorComponent').mockReturnValue(mockDiffEditorComponent as MonacoDiffEditorComponent);

        comp.setFileContents(originalText, modifiedText, originalFileName, modifiedFileName);

        expect(mockDiffEditorComponent.setFileContents).toHaveBeenCalledWith(originalText, modifiedText, originalFileName, modifiedFileName);
    });

    it('should get text from embedded component', () => {
        (mockDiffEditorComponent.getText as jest.Mock).mockReturnValue({
            original: 'original text',
            modified: 'modified text',
        });

        fixture.detectChanges();
        jest.spyOn(comp, 'diffEditorComponent').mockReturnValue(mockDiffEditorComponent as MonacoDiffEditorComponent);

        const result = comp.getText();

        expect(result).toEqual({
            original: 'original text',
            modified: 'modified text',
        });
    });

    it('should return empty text when diffEditorComponent is not available', () => {
        fixture.detectChanges();
        jest.spyOn(comp, 'diffEditorComponent').mockReturnValue(undefined);

        const result = comp.getText();

        expect(result).toEqual({ original: '', modified: '' });
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
        const pointerY = 400;
        const expectedHeight = pointerY - wrapperTop - dragElemHeight / 2;

        fixture.componentRef.setInput('resizableMinHeight', 100);

        comp.targetWrapperHeight = 500;

        const cdkDragMove = {
            source: { reset: jest.fn(), element: { nativeElement: { clientHeight: dragElemHeight } } },
            pointerPosition: { y: pointerY },
        } as unknown as CdkDragMove;

        fixture.detectChanges();
        jest.spyOn(comp.wrapper().nativeElement, 'getBoundingClientRect').mockReturnValue({ top: wrapperTop } as DOMRect);

        comp.onResizeMoved(cdkDragMove);

        expect(comp.targetWrapperHeight).toBe(expectedHeight);
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
        jest.spyOn(comp, 'diffEditorComponent').mockReturnValue(mockDiffEditorComponent as MonacoDiffEditorComponent);

        comp.executeAction(action);

        expect(runSpy).toHaveBeenCalledWith(expect.any(Object));
    });

    it('should execute domain action with value on modified editor', () => {
        const action = new TestCaseAction();
        const runSpy = jest.spyOn(action, 'run');
        const value = { id: 'test', value: 'Test Case' };

        fixture.detectChanges();
        jest.spyOn(comp, 'diffEditorComponent').mockReturnValue(mockDiffEditorComponent as MonacoDiffEditorComponent);

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
        jest.spyOn(comp, 'diffEditorComponent').mockReturnValue(mockDiffEditorComponent as MonacoDiffEditorComponent);

        const color = '#ca2024';
        comp.onSelectColor(color);

        expect(runSpy).toHaveBeenCalledWith(expect.any(Object), { color: 'red' });
    });

    it('should pass fullscreen element to fullscreen action', () => {
        const fullscreenAction = new FullscreenAction();
        fixture.componentRef.setInput('metaActions', [fullscreenAction]);

        fixture.detectChanges();

        expect(fullscreenAction.element).toBe(comp.fullElement().nativeElement);
    });

    it('should dispose listeners on destroy', () => {
        const disposeSpy = jest.fn();
        comp.listeners = [{ dispose: disposeSpy }, { dispose: disposeSpy }];

        comp.ngOnDestroy();

        expect(disposeSpy).toHaveBeenCalledTimes(2);
    });

    it('should update height from input signals in ngAfterViewInit', () => {
        fixture.componentRef.setInput('initialEditorHeight', 600);
        fixture.componentRef.setInput('resizableMinHeight', 250);

        fixture.detectChanges();

        expect(comp.targetWrapperHeight).toBe(600);
        expect(comp.minWrapperHeight).toBe(250);
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

        fixture.componentRef.setInput('domainActions', [actionWithOptions, actionWithoutOptions]);
        fixture.detectChanges();

        expect(comp.domainActionsWithoutOptions()).toContain(actionWithoutOptions);
        expect(comp.domainActionsWithOptions()).toContain(actionWithOptions);
    });

    it('should emit onDiffEditorReady event', () => {
        const emitSpy = jest.spyOn(comp.onReadyForDisplayChange, 'emit');
        const event = { ready: true, lineChange: { addedLineCount: 5, removedLineCount: 3 } };

        comp.onDiffEditorReady(event);

        expect(emitSpy).toHaveBeenCalledWith(event);
    });

    it('should not throw when destroy is called without initialization', () => {
        // Create fresh component without initializing
        const newFixture = TestBed.createComponent(MarkdownDiffEditorMonacoComponent);
        const newComp = newFixture.componentInstance;

        // Should not throw when destroy is called without initialization
        expect(() => newComp.ngOnDestroy()).not.toThrow();
    });

    it('should have correct initial input values', () => {
        expect(comp.allowSplitView()).toBeTrue();
        expect(comp.enableResize()).toBeTrue();
        expect(comp.fillHeight()).toBeFalse();
        expect(comp.readOnly()).toBeFalse();
        expect(comp.initialEditorHeight()).toBe(MarkdownEditorHeight.MEDIUM);
        expect(comp.resizableMinHeight()).toBe(MarkdownEditorHeight.SMALL);
        expect(comp.resizableMaxHeight()).toBe(MarkdownEditorHeight.LARGE);
    });

    it('should return correct host bindings when fillHeight is true', () => {
        fixture.componentRef.setInput('fillHeight', true);
        fixture.detectChanges();

        expect(comp.hostDisplay).toBe('flex');
        expect(comp.hostFlexDirection).toBe('column');
        expect(comp.hostHeight).toBe('100%');
    });

    it('should return null host bindings when fillHeight is false', () => {
        fixture.componentRef.setInput('fillHeight', false);
        fixture.detectChanges();

        expect(comp.hostDisplay).toBeNull();
        expect(comp.hostFlexDirection).toBeNull();
        expect(comp.hostHeight).toBeNull();
    });
});
