import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    ViewEncapsulation,
    afterNextRender,
    afterRenderEffect,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { CdkConnectedOverlay, ConnectedPosition } from '@angular/cdk/overlay';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import {
    faAt,
    faBold,
    faChevronUp,
    faCircleQuestion,
    faCode,
    faEllipsis,
    faFileCode,
    faHashtag,
    faItalic,
    faLink,
    faListOl,
    faListUl,
    faQuoteLeft,
    faStrikethrough,
    faUnderline,
    faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiInputDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { MessageCompletion, MessageEditorDom } from './message-editor-dom';
import { MessageEditorSuggestions, MessageSuggestion } from './message-editor-suggestions';
import { revealMessageEditorOption } from './message-editor-scroll';
import { isMessageLinkSafe, normalizeMessageLink } from './message-editor-conversion';
import { MessageEditorToolsComponent, MessageResourceInsertion } from './message-editor-tools.component';
import { MESSAGE_CLIPBOARD_TYPE } from './message-editor-clipboard';
import { barNeedsRoomBelow, clippingTop, showsWideHint, visibleToolbarItemCount } from './message-editor-toolbar-layout';

type FormatTag = 'strong' | 'em' | 'ins' | 's' | 'code';

/** A button of the toolbar line. The ones that do not fit the line are shown in the bar of further options instead. */
type ToolbarItem = { id: string; kind: 'format'; tag: FormatTag; icon: IconDefinition; label: string } | { id: string; kind: 'link' | 'mention' | 'reference' };

@Component({
    selector: 'jhi-message-editor',
    templateUrl: './message-editor.component.html',
    styleUrl: './message-editor.component.scss',
    imports: [
        ArtemisTranslatePipe,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        TumAetUiTooltipDirective,
        CdkConnectedOverlay,
        NgTemplateOutlet,
        MessageEditorToolsComponent,
    ],
    providers: [MessageEditorSuggestions],
    encapsulation: ViewEncapsulation.None,
    host: { '(keydown)': 'toolbarKeydown($event)', '(document:selectionchange)': 'updateSelectionState()', '(window:resize)': 'dismissSuggestions()' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageEditorComponent {
    private readonly search = inject(MessageEditorSuggestions);
    private readonly destroyRef = inject(DestroyRef);
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

    /** Serialized message content; external replacements reset history, while emitted local edits retain it. */
    readonly content = input('');
    readonly courseId = input.required<number>();
    readonly conversation = input<ConversationDTO>();
    readonly editing = input(false);
    /** Names the conversation in the hint of the empty editor ("Message Katja"); the empty editor has no hint without it. */
    readonly conversationName = input<string>();
    /** Words the hint of the empty editor for a reply in a thread. */
    readonly reply = input(false);
    /** Distinguishes drafts with identical text so thread switches discard history and pending upload insertions. */
    readonly draftIdentity = input<string>();
    readonly suppressNewlineOnEnter = input(true);
    /** Emits serialized content immediately after an edit or history operation. */
    readonly contentChange = output<string>();
    /** Reports pending attachment work so the parent form can block submission. */
    readonly uploadingChange = output<boolean>();
    private readonly editable = viewChild<ElementRef<HTMLElement>>('editable');
    private readonly toolbar = viewChild('toolbar', { read: ElementRef<HTMLElement> });
    private readonly advancedBar = viewChild('advancedBar', { read: ElementRef<HTMLElement> });
    private readonly toolbarRow = viewChild('toolbarRow', { read: ElementRef<HTMLElement> });
    private readonly toolbarToggle = viewChild('toolbarToggle', { read: ElementRef<HTMLElement> });
    protected readonly tools = viewChild(MessageEditorToolsComponent);
    private readonly completionOverlay = viewChild(CdkConnectedOverlay);
    private readonly completionList = viewChild<ElementRef<HTMLElement>>('completionList');
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
    protected readonly linkComposing = signal(false);
    readonly activeFormatting = signal<string[]>([]);
    readonly linkAllowed = signal(true);
    protected readonly completionOrigin = signal({ x: 0, y: 0, width: 0, height: 0 });

    private readonly toolbarDisclosure = signal<'collapsed' | 'selection' | 'manual'>('collapsed');
    protected readonly toolbarExpanded = computed(() => this.toolbarDisclosure() !== 'collapsed');
    protected readonly icons = { faLink, faAt, faHashtag, faXmark, faEllipsis, faChevronUp, faCircleQuestion };
    /** In the order of the toolbar line; when the line is too narrow, the last items move to the bar of further options. */
    protected readonly toolbarItems: readonly ToolbarItem[] = [
        { id: 'strong', kind: 'format', tag: 'strong', icon: faBold, label: 'bold' },
        { id: 'em', kind: 'format', tag: 'em', icon: faItalic, label: 'italic' },
        { id: 'ins', kind: 'format', tag: 'ins', icon: faUnderline, label: 'underline' },
        { id: 's', kind: 'format', tag: 's', icon: faStrikethrough, label: 'strikethrough' },
        { id: 'code', kind: 'format', tag: 'code', icon: faCode, label: 'inlineCode' },
        { id: 'link', kind: 'link' },
        { id: 'mention', kind: 'mention' },
        { id: 'reference', kind: 'reference' },
    ];
    /** How many toolbar items fit the line. Every item until the line has been measured. */
    private readonly fittingItems = signal(Number.POSITIVE_INFINITY);
    protected readonly lineItems = computed(() => this.toolbarItems.slice(0, this.fittingItems()));
    protected readonly overflowItems = computed(() => this.toolbarItems.slice(this.fittingItems()));
    /** The longer hint below the editor needs a wide line; a narrow one gets the short hint, so that the hint never needs more than two lines. */
    protected readonly wideHint = signal(true);
    /** The bar of further options opens above the toolbar, and below it when the ancestors would clip it there. */
    protected readonly barBelow = signal(false);
    /** The empty editor shows a hint. A document with only spaces or line breaks is not empty, because the caret is not at the start of the hint then. */
    protected readonly empty = signal(true);
    /** Composed text is not part of the content yet, so the hint must not be drawn behind it. */
    protected readonly composingText = signal(false);
    protected readonly placeholderKey = computed(() => {
        if (this.editing() || !this.empty() || this.composingText()) return undefined;
        if (this.reply()) return 'artemisApp.communication.formattedEditor.replyPlaceholder';
        return this.conversationName() ? 'artemisApp.communication.formattedEditor.placeholder' : undefined;
    });
    protected readonly blocks = [
        { tag: 'ul' as const, icon: faListUl, label: 'bulletedList' },
        { tag: 'ol' as const, icon: faListOl, label: 'orderedList' },
        { tag: 'blockquote' as const, icon: faQuoteLeft, label: 'quote' },
        { tag: 'pre' as const, icon: faFileCode, label: 'codeBlock' },
    ];
    protected readonly positions: ConnectedPosition[] = [
        { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
        { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
    ];
    private static nextId = 0;
    readonly listId = `message-completions-${MessageEditorComponent.nextId++}`;
    readonly hintId = `${this.listId}-hint`;
    protected readonly advancedToolbarId = `${this.listId}-advanced-toolbar`;

    private toolbarObserver?: ResizeObserver;
    private editor?: MessageEditorDom;
    private lastValue?: string;
    private lastContext?: string;
    private completion?: MessageCompletion;
    private request = 0;
    private searchTimer?: ReturnType<typeof setTimeout>;
    private composing = false;
    private existingLink?: HTMLAnchorElement;
    private linkSelection?: Range;
    private disclosedSelection?: Range;

    constructor() {
        afterRenderEffect(() => {
            this.suggestions();
            this.loading();
            this.searchFailed();
            // Search results change the popup's size after attachment, so measure its rendered content again.
            if (this.suggestionsOpen()) this.completionOverlay()?.overlayRef?.updatePosition();
        });
        afterRenderEffect(() => {
            const index = this.activeIndex();
            this.suggestions();
            const popup = this.completionList()?.nativeElement;
            const option = popup?.querySelector<HTMLElement>(`[id="${this.listId}-${index}"]`);
            if (!this.suggestionsOpen() || !popup || !option) return;
            revealMessageEditorOption(popup, option);
        });
        const dismissOnAncestorScroll = (event: Event) => {
            const element = this.editable()?.nativeElement;
            if (element && (event.target === element.ownerDocument || (event.target instanceof Element && event.target.contains(element)))) this.dismissSuggestions();
        };
        // A viewport-point origin cannot follow ancestor scrolling. Scrolling the popup itself must remain possible.
        document.addEventListener('scroll', dismissOnAncestorScroll, true);
        effect(() => {
            const element = this.editable()?.nativeElement;
            const content = this.content();
            const context = JSON.stringify([this.courseId(), this.conversation()?.id, this.editing(), this.draftIdentity()]);
            const changedContext = context !== this.lastContext;
            if (changedContext) {
                this.lastContext = context;
                this.toolbarDisclosure.set('collapsed');
                this.disclosedSelection = undefined;
                this.dismissSuggestions();
            }
            if (element) {
                this.editor ??= new MessageEditorDom(element, (value) => {
                    this.linkOpen.set(false);
                    this.lastValue = value;
                    this.empty.set(value === '');
                    this.contentChange.emit(value);
                    this.refreshFormatState();
                });
                if (changedContext || content !== this.lastValue) {
                    this.editor.load(content);
                    this.editorRevision.update((revision) => revision + 1);
                    this.lastValue = content;
                    this.empty.set(content === '');
                    this.existingLink = undefined;
                    this.composing = false;
                    this.composingText.set(false);
                    this.uploading.set(false);
                    this.dismissSuggestions();
                    this.linkOpen.set(false);
                    this.refreshFormatState();
                }
            }
        });
        // The first measurement happens before the first paint, so a narrow toolbar never shows all buttons for a frame.
        afterNextRender(() => this.measureToolbarLine());
        afterRenderEffect(() => {
            // The height of the bar changes with its buttons, and the place above the toolbar with the scrolling of its ancestors.
            this.toolbarExpanded();
            this.fittingItems();
            const bar = this.advancedBar()?.nativeElement;
            const toolbar = this.toolbar()?.nativeElement;
            if (!bar || !toolbar || bar.hidden) return;
            this.barBelow.set(barNeedsRoomBelow(bar.offsetHeight, toolbar.getBoundingClientRect().top, clippingTop(bar)));
        });
        effect(() => {
            const row = this.toolbarRow()?.nativeElement;
            this.toolbarObserver?.disconnect();
            this.toolbarObserver = undefined;
            if (row && typeof ResizeObserver !== 'undefined') {
                this.toolbarObserver = new ResizeObserver(() => this.measureToolbarLine());
                this.toolbarObserver.observe(row);
            }
        });
        this.destroyRef.onDestroy(() => {
            document.removeEventListener('scroll', dismissOnAncestorScroll, true);
            this.toolbarObserver?.disconnect();
            clearTimeout(this.searchTimer);
            this.request++;
        });
    }

    /** Fits as many toolbar items on the line as its width allows next to the emoji and more options buttons. */
    private measureToolbarLine(): void {
        const row = this.toolbarRow()?.nativeElement;
        const toggle = this.toolbarToggle()?.nativeElement;
        if (!row || !toggle) return;
        const gap = Number.parseFloat(getComputedStyle(row).columnGap) || 0;
        // Layout widths, not the ones of getBoundingClientRect: an ancestor that is scaled while it opens, such as a dialog, must not change the count.
        this.fittingItems.set(visibleToolbarItemCount(row.clientWidth, toggle.offsetWidth, gap, 2, this.toolbarItems.length));
        this.wideHint.set(showsWideHint(row.clientWidth));
    }

    /** Keeps pointer toolbar activation from stealing the native editing selection. */
    protected remember(event: MouseEvent): void {
        event.preventDefault();
        this.editor?.rememberSelection();
    }

    /** Changes toolbar disclosure without losing the selection or interrupting pending uploads. */
    protected toggleToolbar(): void {
        this.toolbarDisclosure.set(this.toolbarExpanded() ? 'collapsed' : 'manual');
        if (!this.toolbarExpanded()) this.tools()?.close(false);
        this.dismissSuggestions();
    }

    /** Applies an inline mark after dismissing popup ranges that the DOM edit could invalidate. */
    protected applyFormat(tag: FormatTag): void {
        this.dismissSuggestions();
        this.linkOpen.set(false);
        this.editor?.format(tag);
        this.focus();
    }

    /** Changes selected blocks and returns focus to the preserved editing range. */
    protected applyBlock(tag: 'ul' | 'ol' | 'blockquote' | 'pre'): void {
        this.dismissSuggestions();
        this.linkOpen.set(false);
        this.editor?.block(tag);
        this.focus();
    }

    /** Routes native undo and managed text insertion through the adapter while leaving IME edits to the browser. */
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
        } else if (!this.composing && event.inputType === 'insertText' && event.cancelable && this.editor?.convertTypedListMarker(event.data ?? '')) {
            event.preventDefault();
            this.scheduleSuggestions();
        } else if (!this.composing && event.inputType === 'insertText' && event.data !== null && event.cancelable && this.editor?.needsManagedTextInsertion()) {
            // WebKit can normalize a valid caret to before the preceding line break during native insertion.
            event.preventDefault();
            this.editor.insertText(event.data);
            this.scheduleSuggestions();
        } else if (!this.composing) {
            this.editor?.beforeInput();
        }
    }

    /** Commits native edits and searches the new caret position once IME composition has finished. */
    protected onInput(event: Event): void {
        if (!(event instanceof InputEvent && event.isComposing) && !this.composing) {
            this.editor?.afterInput();
            this.scheduleSuggestions();
        }
    }

    /** Saves pending typing marks and defers ordinary input handling until the browser commits the IME text. */
    protected compositionStart(): void {
        this.editor?.beginComposition();
        this.composing = true;
        this.composingText.set(true);
        this.dismissSuggestions();
    }

    /** Commits the composed text as one editor update before resuming completion searches. */
    protected compositionEnd(): void {
        this.composing = false;
        this.composingText.set(false);
        this.editor?.endComposition();
        this.scheduleSuggestions();
    }

    /** Handles editing shortcuts and completion acceptance before allowing the parent form's send shortcut to run. */
    protected keydown(event: KeyboardEvent): void {
        // Safari may confirm a candidate after compositionend with keyCode 229 and isComposing=false.
        if (event.isComposing || this.composing || event.keyCode === 229) {
            event.stopPropagation();
            return;
        }
        if (event.key === 'Enter' && event.shiftKey) {
            event.preventDefault();
            event.stopPropagation();
            this.dismissSuggestions();
            if (!this.editor?.insertListItem()) this.editor?.insertText('\n');
            return;
        }
        const command = event.ctrlKey || event.metaKey;
        if (event.key === 'Enter' && command && this.suppressNewlineOnEnter()) {
            this.dismissSuggestions();
            event.preventDefault();
            if (this.uploading()) event.stopPropagation();
            return;
        }
        if (event.key.startsWith('Arrow') || ['Home', 'End', 'Escape', 'Tab'].includes(event.key) || (command && event.key.toLowerCase() === 'a')) {
            this.editor?.clearItemHighlight();
        }
        if (command && ['z', 'y', 'b', 'i', 'u', 'k'].includes(event.key.toLowerCase())) {
            event.preventDefault();
            event.stopPropagation();
            this.dismissSuggestions();
            if (event.key.toLowerCase() !== 'k') this.linkOpen.set(false);
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
            this.refreshFormatState();
            return;
        }
        if (event.key === 'Enter' && this.suggestionsOpen() && !this.loading() && !this.suggestions().length) {
            this.dismissSuggestions();
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
        if (event.key === 'Enter' && this.suppressNewlineOnEnter()) {
            // The parent form sends in every block style; Shift+Enter continues lists or inserts a soft newline.
            event.preventDefault();
            if (this.uploading()) event.stopPropagation();
            return;
        }
        if (!command && !event.shiftKey && (event.key === 'ArrowUp' || event.key === 'ArrowDown' || event.key === 'Enter') && this.editor?.leaveBlock(event.key)) {
            event.preventDefault();
            event.stopPropagation();
            return;
        }
        if (event.key === 'Backspace' || event.key === 'Delete') {
            if ((event.key === 'Backspace' && (this.editor?.exitEmptyList() || this.editor?.exitFirstListItem())) || this.editor?.deleteItem(event.key === 'Backspace')) {
                event.preventDefault();
                event.stopPropagation();
            }
        } else if (event.key === 'Enter') {
            const selection = window.getSelection();
            const node = selection?.anchorNode;
            const parent = node instanceof HTMLElement ? node : node?.parentElement;
            if (parent?.closest('pre')) {
                event.preventDefault();
                event.stopPropagation();
                this.editor?.insertText('\n');
            } else if (this.editor?.exitEmptyList()) {
                event.preventDefault();
                event.stopPropagation();
            } else {
                event.stopPropagation();
            }
        }
    }

    /** Keeps Enter used to activate toolbar or popup controls from reaching the message form's submit handler. */
    protected toolbarKeydown(event: KeyboardEvent): void {
        if (event.key === 'Enter' && event.target !== this.editable()?.nativeElement) {
            event.stopPropagation();
        }
    }

    /** Retains internal references and attachments on copy/cut without trusting their raw HTML attributes on paste. */
    protected copy(event: ClipboardEvent, cut = false): void {
        if (event.clipboardData && this.editor?.writeClipboard(event.clipboardData, cut)) {
            event.preventDefault();
            this.dismissSuggestions();
        }
    }

    /** Routes clipboard files through uploads and preserves the supported formatting of pasted text. */
    protected paste(event: ClipboardEvent): void {
        event.preventDefault();
        const files = Array.from(event.clipboardData?.files ?? []);
        if (files.length) {
            void this.tools()?.uploadFiles(files);
        } else {
            this.editor?.insertClipboard(
                event.clipboardData?.getData('text/html') ?? '',
                event.clipboardData?.getData('text/plain') ?? '',
                event.clipboardData?.getData(MESSAGE_CLIPBOARD_TYPE) ?? '',
            );
        }
        this.dismissSuggestions();
    }

    /** Uploads dropped files through the captured editor context instead of letting the browser insert their contents. */
    protected drop(event: DragEvent): void {
        event.preventDefault();
        void this.tools()?.uploadFiles(Array.from(event.dataTransfer?.files ?? []));
        this.dismissSuggestions();
    }

    /** Recovers an asynchronous insertion bookmark in the current document or reports an invalid position. */
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
        this.positionCompletion(completion.range);
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

    /** Invalidates pending searches and clears their replacement range so stale results cannot reopen the popup. */
    dismissSuggestions(): void {
        clearTimeout(this.searchTimer);
        this.request++;
        this.completion = undefined;
        this.suggestions.set([]);
        this.suggestionsOpen.set(false);
        this.loading.set(false);
    }

    /** An intentional close can detach the old overlay after the next query's debounce has already been scheduled. */
    protected completionDetached(): void {
        if (this.suggestionsOpen()) this.dismissSuggestions();
    }

    /** Replaces only the active completion range, then restores focus without submitting the surrounding form. */
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

    /** Drops selection-bound popups and refreshes formatting and completions for the new caret. */
    protected selectionChanged(): void {
        this.dismissSuggestions();
        this.linkOpen.set(false);
        this.editor?.rememberSelection();
        this.refreshFormatState();
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

    /** Opens the link form for an explicit or enclosing anchor; repeated toolbar activation closes the form. */
    protected openLink(link?: HTMLAnchorElement): void {
        const target = link ?? this.editor?.linkAtSelection();
        if (!this.editor?.canLinkSelection(target)) return;
        if (!link && this.linkOpen()) {
            this.closeLink();
            return;
        }
        this.editor?.rememberSelection();
        this.dismissSuggestions();
        const selection = this.editable()?.nativeElement.ownerDocument.getSelection();
        this.linkSelection = selection?.rangeCount ? selection.getRangeAt(0).cloneRange() : undefined;
        this.existingLink = target;
        this.editingLink.set(!!target);
        this.linkText.set(target?.textContent ?? selection?.toString() ?? '');
        this.linkUrl.set(target?.getAttribute('href') ?? '');
        this.linkInvalid.set(false);
        this.linkComposing.set(false);
        this.linkOpen.set(true);
    }

    protected updateLinkField(event: Event, url: boolean): void {
        if (event.target instanceof HTMLInputElement) {
            (url ? this.linkUrl : this.linkText).set(event.target.value);
        }
    }

    /** Validates the destination before updating the captured link or selection and prevents form submission. */
    protected saveLink(event?: Event): void {
        event?.stopPropagation();
        if (event instanceof KeyboardEvent && (this.linkComposing() || event.isComposing || event.keyCode === 229)) return;
        event?.preventDefault();
        const url = normalizeMessageLink(this.linkUrl());
        if (!isMessageLinkSafe(url, this.linkText())) {
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
        this.linkComposing.set(false);
        this.linkOpen.set(false);
        this.focus();
    }

    protected focus(): void {
        this.editor?.focus();
        this.refreshFormatState();
    }

    /** Tracks native caret movement without closing a link form while focus is in its inputs. */
    protected updateSelectionState(): void {
        const element = this.editable()?.nativeElement;
        const selection = element?.ownerDocument.getSelection();
        if (selection?.anchorNode && element?.contains(selection.anchorNode)) {
            const range = selection.rangeCount ? selection.getRangeAt(0) : undefined;
            if (range && !range.collapsed) {
                const previous = this.disclosedSelection;
                if (
                    !previous ||
                    previous.startContainer !== range.startContainer ||
                    previous.startOffset !== range.startOffset ||
                    previous.endContainer !== range.endContainer ||
                    previous.endOffset !== range.endOffset
                ) {
                    if (this.toolbarDisclosure() !== 'manual') this.toolbarDisclosure.set('selection');
                    this.disclosedSelection = range.cloneRange();
                }
            } else {
                this.collapseSelectionToolbar();
            }
            if (this.linkOpen() && element.ownerDocument.activeElement === element && this.linkSelection && selection.rangeCount) {
                const range = selection.getRangeAt(0);
                if (
                    range.startContainer !== this.linkSelection.startContainer ||
                    range.startOffset !== this.linkSelection.startOffset ||
                    range.endContainer !== this.linkSelection.endContainer ||
                    range.endOffset !== this.linkSelection.endOffset
                ) {
                    this.linkOpen.set(false);
                }
            }
            this.refreshFormatState();
        } else {
            const activeElement = element?.ownerDocument.activeElement;
            const focusedControl = activeElement !== element && this.host.nativeElement.contains(activeElement ?? null);
            // Picker overlays live outside the composer host; moving focus to their inputs must retain the editing selection.
            if (!focusedControl && !this.tools()?.picker() && !this.linkOpen()) this.collapseSelectionToolbar();
        }
    }

    private collapseSelectionToolbar(): void {
        this.disclosedSelection = undefined;
        if (this.toolbarDisclosure() === 'selection') this.toolbarDisclosure.set('collapsed');
    }

    private refreshFormatState(): void {
        this.activeFormatting.set(this.editor?.activeFormatting() ?? []);
        this.linkAllowed.set(this.editor?.canLinkSelection() ?? true);
    }

    private positionCompletion(range: Range): void {
        const caret = range.cloneRange();
        caret.collapse(false);
        // A caret range follows wrapping and multiline text; the composer box can be several lines away.
        const rect = caret.getBoundingClientRect?.();
        if (rect?.height) {
            this.completionOrigin.set({ x: rect.left, y: rect.top, width: 0, height: rect.height });
        } else {
            const editor = this.editable()!.nativeElement.getBoundingClientRect();
            this.completionOrigin.set({ x: editor.left, y: editor.top, width: 0, height: editor.height });
        }
    }
}
