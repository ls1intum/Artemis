import { TestBed } from '@angular/core/testing';
import { MarkdownEditorToolbarService } from './markdown-editor-toolbar.service';
import { BoldAction } from 'app/shared/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/shared/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/shared/monaco-editor/model/actions/underline.action';
import { StrikethroughAction } from 'app/shared/monaco-editor/model/actions/strikethrough.action';
import { QuoteAction } from 'app/shared/monaco-editor/model/actions/quote.action';
import { CodeAction } from 'app/shared/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/shared/monaco-editor/model/actions/code-block.action';
import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/shared/monaco-editor/model/actions/attachment.action';
import { OrderedListAction } from 'app/shared/monaco-editor/model/actions/ordered-list.action';
import { BulletedListAction } from 'app/shared/monaco-editor/model/actions/bulleted-list.action';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';

describe('MarkdownEditorToolbarService', () => {
    let service: MarkdownEditorToolbarService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MarkdownEditorToolbarService],
        });
        service = TestBed.inject(MarkdownEditorToolbarService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('colorToClassMap', () => {
        it('should have 8 color mappings', () => {
            expect(service.colorToClassMap.size).toBe(8);
        });

        it('should map red color correctly', () => {
            expect(service.colorToClassMap.get('#ca2024')).toBe('red');
        });

        it('should map green color correctly', () => {
            expect(service.colorToClassMap.get('#3ea119')).toBe('green');
        });

        it('should map white color correctly', () => {
            expect(service.colorToClassMap.get('#ffffff')).toBe('white');
        });

        it('should map black color correctly', () => {
            expect(service.colorToClassMap.get('#000000')).toBe('black');
        });

        it('should map yellow color correctly', () => {
            expect(service.colorToClassMap.get('#fffa5c')).toBe('yellow');
        });

        it('should map blue color correctly', () => {
            expect(service.colorToClassMap.get('#0d3cc2')).toBe('blue');
        });

        it('should map lila color correctly', () => {
            expect(service.colorToClassMap.get('#b05db8')).toBe('lila');
        });

        it('should map orange color correctly', () => {
            expect(service.colorToClassMap.get('#d86b1f')).toBe('orange');
        });
    });

    describe('createDefaultActions', () => {
        it('should return 11 default actions', () => {
            const actions = service.createDefaultActions();
            expect(actions).toHaveLength(11);
        });

        it('should create actions in correct order', () => {
            const actions = service.createDefaultActions();
            expect(actions[0]).toBeInstanceOf(BoldAction);
            expect(actions[1]).toBeInstanceOf(ItalicAction);
            expect(actions[2]).toBeInstanceOf(UnderlineAction);
            expect(actions[3]).toBeInstanceOf(StrikethroughAction);
            expect(actions[4]).toBeInstanceOf(QuoteAction);
            expect(actions[5]).toBeInstanceOf(CodeAction);
            expect(actions[6]).toBeInstanceOf(CodeBlockAction);
            expect(actions[7]).toBeInstanceOf(UrlAction);
            expect(actions[8]).toBeInstanceOf(AttachmentAction);
            expect(actions[9]).toBeInstanceOf(OrderedListAction);
            expect(actions[10]).toBeInstanceOf(BulletedListAction);
        });

        it('should create new instances on each call', () => {
            const actions1 = service.createDefaultActions();
            const actions2 = service.createDefaultActions();
            expect(actions1[0]).not.toBe(actions2[0]);
        });

        it('should use default language "markdown" for CodeBlockAction', () => {
            const actions = service.createDefaultActions();
            const codeBlockAction = actions[6] as CodeBlockAction;
            expect(codeBlockAction).toBeInstanceOf(CodeBlockAction);
        });

        it('should accept custom language for CodeBlockAction', () => {
            const actions = service.createDefaultActions('java');
            const codeBlockAction = actions[6] as CodeBlockAction;
            expect(codeBlockAction).toBeInstanceOf(CodeBlockAction);
        });
    });

    describe('createMetaActions', () => {
        it('should return 1 meta action', () => {
            const actions = service.createMetaActions();
            expect(actions).toHaveLength(1);
        });

        it('should return FullscreenAction', () => {
            const actions = service.createMetaActions();
            expect(actions[0]).toBeInstanceOf(FullscreenAction);
        });

        it('should create new instances on each call', () => {
            const actions1 = service.createMetaActions();
            const actions2 = service.createMetaActions();
            expect(actions1[0]).not.toBe(actions2[0]);
        });
    });

    describe('getColors', () => {
        it('should return 8 colors', () => {
            const colors = service.getColors();
            expect(colors).toHaveLength(8);
        });

        it('should return all hex color codes', () => {
            const colors = service.getColors();
            expect(colors).toContain('#ca2024');
            expect(colors).toContain('#3ea119');
            expect(colors).toContain('#ffffff');
            expect(colors).toContain('#000000');
            expect(colors).toContain('#fffa5c');
            expect(colors).toContain('#0d3cc2');
            expect(colors).toContain('#b05db8');
            expect(colors).toContain('#d86b1f');
        });

        it('should return a new array on each call', () => {
            const colors1 = service.getColors();
            const colors2 = service.getColors();
            expect(colors1).not.toBe(colors2);
            expect(colors1).toEqual(colors2);
        });
    });

    describe('getColorClass', () => {
        it('should return correct class for valid hex color', () => {
            expect(service.getColorClass('#ca2024')).toBe('red');
            expect(service.getColorClass('#3ea119')).toBe('green');
        });

        it('should return undefined for unknown hex color', () => {
            expect(service.getColorClass('#123456')).toBeUndefined();
        });

        it('should return undefined for empty string', () => {
            expect(service.getColorClass('')).toBeUndefined();
        });
    });

    describe('filterDisplayedActions', () => {
        it('should return all actions when none are hidden', () => {
            const actions = [new BoldAction(), new ItalicAction()];
            const filtered = service.filterDisplayedActions(actions);
            expect(filtered).toHaveLength(2);
        });

        it('should filter out hidden actions', () => {
            const visibleAction = new BoldAction();
            // Create a mock action with hideInEditor = true
            const hiddenAction = { hideInEditor: true } as any;

            const actions = [visibleAction, hiddenAction];
            const filtered = service.filterDisplayedActions(actions);

            expect(filtered).toHaveLength(1);
            expect(filtered[0]).toBe(visibleAction);
        });

        it('should return empty array when all actions are hidden', () => {
            const hiddenAction1 = { hideInEditor: true } as any;
            const hiddenAction2 = { hideInEditor: true } as any;

            const filtered = service.filterDisplayedActions([hiddenAction1, hiddenAction2]);
            expect(filtered).toHaveLength(0);
        });

        it('should return empty array for empty input', () => {
            const filtered = service.filterDisplayedActions([]);
            expect(filtered).toHaveLength(0);
        });
    });

    describe('filterDisplayedAction', () => {
        it('should return action when not hidden', () => {
            const action = new BoldAction();
            const result = service.filterDisplayedAction(action);
            expect(result).toBe(action);
        });

        it('should return undefined when action is hidden', () => {
            // Create a mock action with hideInEditor = true
            const hiddenAction = { hideInEditor: true } as any;
            const result = service.filterDisplayedAction(hiddenAction);
            expect(result).toBeUndefined();
        });

        it('should return undefined for undefined input', () => {
            const result = service.filterDisplayedAction(undefined);
            expect(result).toBeUndefined();
        });
    });

    describe('splitDomainActions', () => {
        it('should correctly split domain actions', () => {
            const actionWithoutOptions = new FormulaAction();
            const actionWithOptions = new TestCaseAction();

            const result = service.splitDomainActions([actionWithoutOptions, actionWithOptions]);

            expect(result.withoutOptions).toContain(actionWithoutOptions);
            expect(result.withOptions).toContain(actionWithOptions);
        });

        it('should handle empty array', () => {
            const result = service.splitDomainActions([]);

            expect(result.withoutOptions).toHaveLength(0);
            expect(result.withOptions).toHaveLength(0);
        });

        it('should handle only actions without options', () => {
            const action = new FormulaAction();
            const result = service.splitDomainActions([action]);

            expect(result.withoutOptions).toHaveLength(1);
            expect(result.withOptions).toHaveLength(0);
        });

        it('should handle only actions with options', () => {
            const action = new TestCaseAction();
            const result = service.splitDomainActions([action]);

            expect(result.withoutOptions).toHaveLength(0);
            expect(result.withOptions).toHaveLength(1);
        });
    });
});
