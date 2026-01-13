import { Injectable } from '@angular/core';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
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
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { TextEditorDomainActionWithOptions } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action-with-options.model';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';

/**
 * Service providing shared configuration and utilities for markdown editors.
 * Used by both MarkdownEditorMonacoComponent and MarkdownDiffEditorMonacoComponent.
 */
@Injectable({
    providedIn: 'root',
})
export class MarkdownEditorToolbarService {
    /**
     * Color mapping from hex codes to CSS class names.
     */
    readonly colorToClassMap = new Map<string, string>([
        ['#ca2024', 'red'],
        ['#3ea119', 'green'],
        ['#ffffff', 'white'],
        ['#000000', 'black'],
        ['#fffa5c', 'yellow'],
        ['#0d3cc2', 'blue'],
        ['#b05db8', 'lila'],
        ['#d86b1f', 'orange'],
    ]);

    /**
     * Creates a new array of default markdown actions.
     * Each call returns fresh instances to avoid shared state between editors.
     * @param codeBlockLanguage The language for code blocks (default: 'markdown')
     */
    createDefaultActions(codeBlockLanguage = 'markdown'): TextEditorAction[] {
        return [
            new BoldAction(),
            new ItalicAction(),
            new UnderlineAction(),
            new StrikethroughAction(),
            new QuoteAction(),
            new CodeAction(),
            new CodeBlockAction(codeBlockLanguage),
            new UrlAction(),
            new AttachmentAction(),
            new OrderedListAction(),
            new BulletedListAction(),
        ];
    }

    /**
     * Creates default meta actions (e.g., fullscreen).
     */
    createMetaActions(): TextEditorAction[] {
        return [new FullscreenAction()];
    }

    /**
     * Returns the list of available colors for the color picker.
     */
    getColors(): string[] {
        return [...this.colorToClassMap.keys()];
    }

    /**
     * Gets the CSS class name for a given color hex code.
     * @param hexColor The hex color code
     */
    getColorClass(hexColor: string): string | undefined {
        return this.colorToClassMap.get(hexColor);
    }

    /**
     * Filters actions to only include those that should be displayed in the editor toolbar.
     * @param actions The actions to filter
     */
    filterDisplayedActions<T extends TextEditorAction>(actions: T[]): T[] {
        return actions.filter((action) => !action.hideInEditor);
    }

    /**
     * Filters a single action, returning undefined if it should be hidden.
     * @param action The action to filter
     */
    filterDisplayedAction<T extends TextEditorAction>(action?: T): T | undefined {
        return action?.hideInEditor ? undefined : action;
    }

    /**
     * Splits domain actions into those with and without options.
     * @param domainActions The domain actions to split
     */
    splitDomainActions(domainActions: TextEditorDomainAction[]): {
        withoutOptions: TextEditorDomainAction[];
        withOptions: TextEditorDomainActionWithOptions[];
    } {
        return {
            withoutOptions: domainActions.filter((action) => !(action instanceof TextEditorDomainActionWithOptions)),
            withOptions: domainActions.filter((action) => action instanceof TextEditorDomainActionWithOptions) as TextEditorDomainActionWithOptions[],
        };
    }
}
