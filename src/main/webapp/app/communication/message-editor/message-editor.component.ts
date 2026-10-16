import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, ViewEncapsulation, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faAt, faBold, faCode, faHashtag, faItalic, faLink, faListOl, faListUl, faQuoteLeft, faStrikethrough, faUnderline, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiInputDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { MessageCompletion, MessageEditorDom } from './message-editor-dom';
import { MessageEditorSuggestions, MessageSuggestion } from './message-editor-suggestions';
import { isMessageLinkSafe } from './message-editor-conversion';
import { MessageEditorToolsComponent, MessageResourceInsertion } from './message-editor-tools.component';

@Component({
    selector: 'jhi-message-editor',
    templateUrl: './message-editor.component.html',
    styleUrl: './message-editor.component.scss',
    imports: [ArtemisTranslatePipe, FaIconComponent, TumAetUiButtonDirective, TumAetUiInputDirective, CdkOverlayOrigin, CdkConnectedOverlay, MessageEditorToolsComponent],
    providers: [MessageEditorSuggestions],
    encapsulation: ViewEncapsulation.None,
    host: { '(keydown)': 'toolbarKeydown($event)' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageEditorComponent {
    private readonly search = inject(MessageEditorSuggestions);
    private readonly destroyRef = inject(DestroyRef);

    readonly content = input('');
    readonly courseId = input.required<number>();
    readonly conversation = input<ConversationDTO>();
    readonly editing = input(false);
    readonly suppressNewlineOnEnter = input(true);
    readonly contentChange = output<string>();
    readonly uploadingChange = output<boolean>();
    private readonly editable = viewChild<ElementRef<HTMLElement>>('editable');
    private readonly tools = viewChild(MessageEditorToolsComponent);
    protected readonly uploading = signal(false);
    protected readonly editorRevision = signal(0);
    protected readonly captureEntitySelection = () => this.editor?.captureEntitySelection();

    readonly suggestions = signal<MessageSuggestion[]>([]);
    readonly suggestionsOpen = signal(false);
    readonly loading = signal(false);
    readonly searchFailed = signal(false);
    readonly activeIndex = signal(0);
    readonly linkOpen = signal(false);
    readonly linkText = signal('');
    readonly linkUrl = signal('');
    readonly linkInvalid = signal(false);
    protected readonly editingLink = signal(false);

    protected readonly icons = { faLink, faAt, faHashtag, faXmark };
    protected readonly formats = [
        { tag: 'strong' as const, icon: faBold, label: 'bold' },
        { tag: 'em' as const, icon: faItalic, label: 'italic' },
        { tag: 'ins' as const, icon: faUnderline, label: 'underline' },
        { tag: 's' as const, icon: faStrikethrough, label: 'strikethrough' },
        { tag: 'code' as const, icon: faCode, label: 'inlineCode' },
    ];
    protected readonly blocks = [
        { tag: 'ul' as const, icon: faListUl, label: 'bulletedList' },
        { tag: 'ol' as const, icon: faListOl, label: 'orderedList' },
        { tag: 'blockquote' as const, icon: faQuoteLeft, label: 'quote' },
        { tag: 'pre' as const, icon: faCode, label: 'codeBlock' },
    ];
    protected readonly positions: ConnectedPosition[] = [
        { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top' },
        { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom' },
    ];
    private static nextId = 0;
    readonly listId = `message-completions-${MessageEditorComponent.nextId++}`;
    readonly hintId = `${this.listId}-hint`;

    private editor?: MessageEditorDom;
    private lastValue?: string;
    private lastContext?: string;
    private completion?: MessageCompletion;
    private request = 0;
    private searchTimer?: ReturnType<typeof setTimeout>;
    private composing = false;
    private existingLink?: HTMLAnchorElement;

    constructor() {
        effect(() => {
            const element = this.editable()?.nativeElement;
            const content = this.content();
            const context = `${this.courseId()}:${this.conversation()?.id}:${this.editing()}`;
            if (context !== this.lastContext) {
                this.lastContext = context;
                this.dismissSuggestions();
            }
            if (element) {
                this.editor ??= new MessageEditorDom(element, (value) => {
                    this.lastValue = value;
                    this.contentChange.emit(value);
                });
                if (content !== this.lastValue) {
                    this.editor.load(content);
                    this.editorRevision.update((revision) => revision + 1);
                    this.lastValue = content;
                    this.dismissSuggestions();
                    this.linkOpen.set(false);
                }
            }
        });
        this.destroyRef.onDestroy(() => {
            clearTimeout(this.searchTimer);
            this.request++;
        });
    }

    protected remember(event: MouseEvent): void {
        event.preventDefault();
        this.editor?.rememberSelection();
    }

    protected applyFormat(tag: 'strong' | 'em' | 'ins' | 's' | 'code'): void {
        this.dismissSuggestions();
        this.editor?.format(tag);
        this.focus();
    }

    protected applyBlock(tag: 'ul' | 'ol' | 'blockquote' | 'pre'): void {
        this.dismissSuggestions();
        this.editor?.block(tag);
        this.focus();
    }

    protected beforeInput(event: Event): void {
        if (!(event instanceof InputEvent)) {
            return;
        }
        if (event.inputType === 'historyUndo' || event.inputType === 'historyRedo') {
            event.preventDefault();
            if (event.inputType === 'historyUndo') {
                this.editor?.undo();
            } else {
                this.editor?.redo();
            }
        } else if (!this.composing && event.inputType === 'insertText' && event.data !== null && event.cancelable && this.editor?.needsManagedTextInsertion()) {
            // WebKit can normalize a valid caret to before the preceding line break during native insertion.
            event.preventDefault();
            this.editor.insertText(event.data);
        } else if (!this.composing) {
            this.editor?.beforeInput();
        }
    }

    protected onInput(event: Event): void {
        if (!(event instanceof InputEvent && event.isComposing) && !this.composing) {
            this.editor?.afterInput();
            this.scheduleSuggestions();
        }
    }

    protected compositionStart(): void {
        this.editor?.beforeInput();
        this.composing = true;
        this.dismissSuggestions();
    }

    protected compositionEnd(): void {
        this.composing = false;
        this.editor?.afterInput();
        this.scheduleSuggestions();
    }

    protected keydown(event: KeyboardEvent): void {
        if (event.isComposing || this.composing) {
            event.stopPropagation();
            return;
        }
        const command = event.ctrlKey || event.metaKey;
        if (event.key.startsWith('Arrow') || ['Home', 'End', 'Escape', 'Tab'].includes(event.key) || (command && event.key.toLowerCase() === 'a')) {
            this.editor?.clearItemHighlight();
        }
        if (command && ['z', 'y', 'b', 'i', 'u', 'k'].includes(event.key.toLowerCase())) {
            event.preventDefault();
            event.stopPropagation();
            this.dismissSuggestions();
            switch (event.key.toLowerCase()) {
                case 'z':
                    if (event.shiftKey) {
                        this.editor?.redo();
                    } else {
                        this.editor?.undo();
                    }
                    break;
                case 'y':
                    this.editor?.redo();
                    break;
                case 'b':
                    this.editor?.format('strong');
                    break;
                case 'i':
                    this.editor?.format('em');
                    break;
                case 'u':
                    this.editor?.format('ins');
                    break;
                case 'k':
                    this.openLink();
                    break;
                default:
                    break;
            }
            return;
        }
        if (this.suggestionsOpen()) {
            if (['ArrowDown', 'ArrowUp', 'Enter', 'Tab', 'Escape'].includes(event.key)) {
                event.stopPropagation();
                if (event.key !== 'Tab' || this.suggestions().length) {
                    event.preventDefault();
                }
                if (event.key === 'Escape' || (event.key === 'Tab' && !this.suggestions().length)) {
                    this.dismissSuggestions();
                } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                    const length = this.suggestions().length;
                    this.activeIndex.set(length ? (this.activeIndex() + (event.key === 'ArrowDown' ? 1 : length - 1)) % length : 0);
                } else {
                    const suggestion = this.suggestions()[this.activeIndex()];
                    if (suggestion) {
                        this.accept(suggestion);
                    }
                }
                return;
            }
            // A changed query invalidates the old rows immediately, before the debounce elapses.
            this.dismissSuggestions();
        }
        if (event.key === 'Backspace' || event.key === 'Delete') {
            if (this.editor?.deleteItem(event.key === 'Backspace')) {
                event.preventDefault();
                event.stopPropagation();
            }
        } else if (event.key === 'Enter') {
            const selection = window.getSelection();
            const node = selection?.anchorNode;
            const parent = node instanceof HTMLElement ? node : node?.parentElement;
            if (event.shiftKey) {
                event.preventDefault();
                event.stopPropagation();
                this.editor?.insertText('\n');
            } else if (parent?.closest('pre')) {
                event.preventDefault();
                event.stopPropagation();
                this.editor?.insertText('\n');
            } else if (parent?.closest('li, blockquote') || !this.suppressNewlineOnEnter()) {
                event.stopPropagation();
            } else {
                // The existing parent form handles the send shortcut after the synchronous input update.
                event.preventDefault();
                if (this.uploading()) {
                    event.stopPropagation();
                }
            }
        }
    }

    protected toolbarKeydown(event: KeyboardEvent): void {
        if (event.key === 'Enter' && event.target !== this.editable()?.nativeElement) {
            event.stopPropagation();
        }
    }

    protected paste(event: ClipboardEvent): void {
        event.preventDefault();
        const files = Array.from(event.clipboardData?.files ?? []);
        if (files.length) {
            void this.tools()?.uploadFiles(files);
        } else {
            this.editor?.insertText(event.clipboardData?.getData('text/plain') ?? '');
        }
        this.dismissSuggestions();
    }

    protected drop(event: DragEvent): void {
        event.preventDefault();
        void this.tools()?.uploadFiles(Array.from(event.dataTransfer?.files ?? []));
        this.dismissSuggestions();
    }

    protected insertResource(insertion: MessageResourceInsertion): void {
        const range = this.editor?.resolveEntitySelection(insertion.range);
        if (range && this.editable()?.nativeElement.contains(range.commonAncestorContainer)) {
            this.dismissSuggestions();
            this.editor?.insertMarkdown(insertion.markdown, range);
            this.focus();
        } else {
            this.tools()?.error.set('position');
        }
    }

    protected uploadStateChanged(uploading: boolean): void {
        this.uploading.set(uploading);
        this.uploadingChange.emit(uploading);
    }

    private scheduleSuggestions(): void {
        this.dismissSuggestions();
        this.searchTimer = setTimeout(() => void this.refreshSuggestions(), 150);
    }

    /** Searches the current caret range and rejects responses from previous queries or destroyed composers. */
    async refreshSuggestions(): Promise<void> {
        const completion = this.editor?.completion();
        if (!completion) {
            this.dismissSuggestions();
            return;
        }
        const request = ++this.request;
        this.completion = completion;
        this.suggestionsOpen.set(true);
        this.loading.set(true);
        this.searchFailed.set(false);
        this.suggestions.set([]);
        this.activeIndex.set(0);
        try {
            const items = await this.search.search(completion.trigger, completion.query, this.courseId(), this.conversation(), this.editing());
            if (request === this.request) {
                this.suggestions.set(items);
            }
        } catch {
            if (request === this.request) {
                this.searchFailed.set(true);
            }
        } finally {
            if (request === this.request) {
                this.loading.set(false);
            }
        }
    }

    dismissSuggestions(): void {
        clearTimeout(this.searchTimer);
        this.request++;
        this.completion = undefined;
        this.suggestions.set([]);
        this.suggestionsOpen.set(false);
        this.loading.set(false);
    }

    protected accept(suggestion: MessageSuggestion): void {
        const range = this.completion?.range;
        this.dismissSuggestions();
        if (range && this.editable()?.nativeElement.contains(range.commonAncestorContainer)) {
            this.editor?.insertMarkdown(suggestion.markdown + ' ', range);
            this.focus();
        }
    }

    protected insertTrigger(trigger: '@' | '#'): void {
        this.editor?.insertText(trigger);
        this.focus();
        void this.refreshSuggestions();
    }

    protected selectionChanged(): void {
        this.dismissSuggestions();
        this.editor?.rememberSelection();
        this.scheduleSuggestions();
    }

    protected clearItemHighlight(): void {
        this.editor?.clearItemHighlight();
    }

    protected clicked(event: MouseEvent): void {
        const link = event.target instanceof Element ? event.target.closest('a') : undefined;
        if (link instanceof HTMLAnchorElement) {
            event.preventDefault();
            this.openLink(link);
        }
    }

    protected openLink(link?: HTMLAnchorElement): void {
        this.editor?.rememberSelection();
        this.dismissSuggestions();
        this.existingLink = link;
        this.editingLink.set(!!link);
        this.linkText.set(link?.textContent ?? window.getSelection()?.toString() ?? '');
        this.linkUrl.set(link?.getAttribute('href') ?? '');
        this.linkInvalid.set(false);
        this.linkOpen.set(true);
    }

    protected updateLinkField(event: Event, url: boolean): void {
        if (event.target instanceof HTMLInputElement) {
            (url ? this.linkUrl : this.linkText).set(event.target.value);
        }
    }

    protected saveLink(event?: Event): void {
        event?.preventDefault();
        event?.stopPropagation();
        const url = this.linkUrl().trim();
        if (!isMessageLinkSafe(url)) {
            this.linkInvalid.set(true);
            return;
        }
        this.editor?.setLink(this.linkText(), url, this.existingLink);
        this.closeLink();
    }

    protected removeLink(): void {
        if (this.existingLink) {
            this.editor?.unlink(this.existingLink);
        }
        this.closeLink();
    }

    protected closeLink(): void {
        this.linkOpen.set(false);
        this.focus();
    }

    protected focus(): void {
        this.editor?.focus();
    }
}
