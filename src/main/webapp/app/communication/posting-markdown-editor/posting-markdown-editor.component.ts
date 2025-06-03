import {
    AfterContentChecked,
    AfterViewInit,
    ChangeDetectionStrategy,
    ChangeDetectorRef,
    Component,
    EventEmitter,
    Input,
    OnInit,
    Output,
    ViewChild,
    ViewContainerRef,
    ViewEncapsulation,
    computed,
    forwardRef,
    inject,
    input,
    output,
} from '@angular/core';
import * as monaco from 'monaco-editor';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MetisService } from 'app/communication/service/metis.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { Course, isCommunicationEnabled, isFaqEnabled } from 'app/core/course/shared/entities/course.model';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { BoldAction } from 'app/shared/monaco-editor/model/actions/bold.action';
import { ItalicAction } from 'app/shared/monaco-editor/model/actions/italic.action';
import { UnderlineAction } from 'app/shared/monaco-editor/model/actions/underline.action';
import { QuoteAction } from 'app/shared/monaco-editor/model/actions/quote.action';
import { CodeAction } from 'app/shared/monaco-editor/model/actions/code.action';
import { CodeBlockAction } from 'app/shared/monaco-editor/model/actions/code-block.action';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ChannelReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/channel-reference.action';
import { UserMentionAction } from 'app/shared/monaco-editor/model/actions/communication/user-mention.action';
import { ExerciseReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/exercise-reference.action';
import { LectureAttachmentReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/lecture-attachment-reference.action';
import { FaqReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/faq-reference.action';
import { UrlAction } from 'app/shared/monaco-editor/model/actions/url.action';
import { AttachmentAction } from 'app/shared/monaco-editor/model/actions/attachment.action';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { EmojiAction } from 'app/shared/monaco-editor/model/actions/emoji.action';
import { Overlay, OverlayPositionBuilder } from '@angular/cdk/overlay';
import { BulletedListAction } from 'app/shared/monaco-editor/model/actions/bulleted-list.action';
import { OrderedListAction } from 'app/shared/monaco-editor/model/actions/ordered-list.action';
import { StrikethroughAction } from 'app/shared/monaco-editor/model/actions/strikethrough.action';
import { PostingContentComponent } from '../posting-content/posting-content.components';
import { NgStyle } from '@angular/common';
import { PostingEditType } from '../metis.util';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { FileService } from 'app/shared/service/file.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { EmojiSuggestionDropdownComponent, getEmojiSuggestions } from '../emoji/emoji-suggestion-dropdown.component';

@Component({
    selector: 'jhi-posting-markdown-editor',
    templateUrl: './posting-markdown-editor.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => PostingMarkdownEditorComponent),
            multi: true,
        },
    ],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MarkdownEditorMonacoComponent, PostingContentComponent, NgStyle, EmojiSuggestionDropdownComponent],
})
export class PostingMarkdownEditorComponent implements OnInit, ControlValueAccessor, AfterContentChecked, AfterViewInit {
    private cdref = inject(ChangeDetectorRef);
    private metisService = inject(MetisService);
    private fileService = inject(FileService);
    private courseManagementService = inject(CourseManagementService);
    private lectureService = inject(LectureService);
    private channelService = inject(ChannelService);
    viewContainerRef = inject(ViewContainerRef);
    private positionBuilder = inject(OverlayPositionBuilder);

    @ViewChild(MarkdownEditorMonacoComponent, { static: true }) markdownEditor: MarkdownEditorMonacoComponent;

    @Input() maxContentLength: number;
    @Input() editorHeight: MarkdownEditorHeight = MarkdownEditorHeight.INLINE;
    @Input() isInputLengthDisplayed = true;
    @Input() suppressNewlineOnEnter = true;

    showCloseButton = input<boolean>(false);
    closeEditor = output<void>();
    isButtonLoading = input<boolean>(false);
    isFormGroupValid = input<boolean>(false);
    editType = input<PostingEditType>();
    course = input<Course>();

    readonly EditType = PostingEditType.CREATE;
    /**
     * For AnswerPosts, the MetisService may not always have an active conversation (e.g. when in the 'all messages' view).
     * In this case, file uploads have to rely on the parent post to determine the course.
     */
    readonly activeConversation = input<ConversationDTO>();
    @Output() valueChange = new EventEmitter();
    lectureAttachmentReferenceAction: LectureAttachmentReferenceAction;
    defaultActions: TextEditorAction[];
    content?: string;
    previewMode = false;
    fallbackConversationId = computed<number | undefined>(() => this.activeConversation()?.id);

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    private overlay = inject(Overlay);

    // --- Emoji dropdown state ---
    emojiSuggestions: { name: string; emoji: string }[] = [];
    showEmojiDropdown = false;
    emojiDropdownStyle: { [key: string]: string } = {};
    lastEmojiMatch: { match: string; index: number } | null = null;
    // Add keyboard navigation for emoji suggestion dropdown
    emojiActiveIndex = 0;

    /**
     * on initialization: sets commands that will be available as formatting buttons during creation/editing of postings
     */
    ngOnInit(): void {
        const messagingOnlyActions = isCommunicationEnabled(this.metisService.getCourse())
            ? [new UserMentionAction(this.courseManagementService, this.metisService), new ChannelReferenceAction(this.metisService, this.channelService)]
            : [];

        const faqAction = isFaqEnabled(this.metisService.getCourse()) ? [new FaqReferenceAction(this.metisService)] : [];

        this.defaultActions = [
            new BoldAction(),
            new ItalicAction(),
            new UnderlineAction(),
            new StrikethroughAction(),
            new EmojiAction(this.viewContainerRef, this.overlay, this.positionBuilder),
            new BulletedListAction(),
            new OrderedListAction(),
            new QuoteAction(),
            new CodeAction(),
            new CodeBlockAction(),
            new UrlAction(),
            new AttachmentAction(),
            ...messagingOnlyActions,
            new ExerciseReferenceAction(this.metisService),
            ...faqAction,
        ];

        this.lectureAttachmentReferenceAction = new LectureAttachmentReferenceAction(this.metisService, this.lectureService, this.fileService);
    }

    ngAfterViewInit(): void {
        this.markdownEditor.enableTextFieldMode();
        const editor = this.markdownEditor.monacoEditor;
        if (editor && (editor as any)._editor) {
            (editor as any)._editor.onKeyDown((event: any) => {
                const domEvent = event.browserEvent as KeyboardEvent;
                this.onKeyDown(domEvent);
            });
        }
        if (editor) {
            editor.onDidChangeModelContent((event: monaco.editor.IModelContentChangedEvent) => {
                const position = editor.getPosition();
                if (!position) {
                    return;
                }

                const model = editor.getModel();
                if (!model) {
                    return;
                }

                const lineContent = model.getLineContent(position.lineNumber).trimStart();
                const hasPrefix = lineContent.startsWith('- ') || /^\s*1\. /.test(lineContent);
                if (hasPrefix && event.changes.length === 1 && (event.changes[0].text.startsWith('- ') || event.changes[0].text.startsWith('1. '))) {
                    return;
                }

                if (hasPrefix) {
                    this.handleKeyDown(model, position.lineNumber);
                }
            });
        }
    }

    private handleKeyDown(model: monaco.editor.ITextModel, lineNumber: number): void {
        const lineContent = model.getLineContent(lineNumber).trimStart();

        if (lineContent.startsWith('- ')) {
            this.markdownEditor.handleActionClick(new MouseEvent('click'), this.defaultActions.find((action) => action instanceof BulletedListAction)!);
        } else if (/^\d+\. /.test(lineContent)) {
            this.markdownEditor.handleActionClick(new MouseEvent('click'), this.defaultActions.find((action) => action instanceof OrderedListAction)!);
        }
    }

    /**
     * this lifecycle hook is required to avoid causing "Expression has changed after it was checked"-error when dismissing all changes in the markdown editor
     * on dismissing the edit-create-modal -> we do not want to store changes in the create-edit-modal that are not saved
     */
    ngAfterContentChecked() {
        this.cdref.detectChanges();
    }

    /**
     * the callback function to register on UI change
     */
    onChange = (_val: string) => {};

    /**
     * emits the value change from component
     */
    valueChanged() {
        this.valueChange.emit();
    }

    /**
     * writes the current value of a form group into the `content` variable,
     * i.e. sets the value programmatically
     * @param value
     */
    writeValue(value: any): void {
        this.content = value ?? '';
    }

    /**
     * upon UI element value changes, this method is triggered (required)
     * @param fn
     */
    registerOnChange(fn: any): void {
        this.onChange = fn;
    }

    /**
     * upon touching the element, this method gets triggered (required)
     */
    registerOnTouched(): void {}

    /**
     * changes in bound markdown content
     * @param newValue
     */
    updateField(newValue: string) {
        this.content = newValue;
        // Emoji suggestion logic
        const matches = newValue.match(/:([a-zA-Z0-9_+-]*)/g);
        let query = '';
        this.showEmojiDropdown = false;
        this.emojiSuggestions = [];
        this.emojiDropdownStyle = {};
        this.lastEmojiMatch = null;
        // Update emojiActiveIndex
        this.emojiActiveIndex = 0;
        if (matches) {
            const lastMatch = matches[matches.length - 1];
            const lastIndex = newValue.lastIndexOf(lastMatch);
            query = lastMatch.slice(1); // Remove the leading colon
            if (query.length > 0) {
                const suggestions = getEmojiSuggestions(query, 3);
                if (suggestions.length > 0 && this.markdownEditor && this.markdownEditor.monacoEditor && (this.markdownEditor.monacoEditor as any)._editor) {
                    const editor = (this.markdownEditor.monacoEditor as any)._editor;
                    const lines = newValue.substring(0, lastIndex).split('\n');
                    const line = lines.length;
                    const column = lines[lines.length - 1].length + 1;
                    const coords = editor.getScrolledVisiblePosition({ lineNumber: line, column });
                    // In updateField, adjust the calculated top position by subtracting a few pixels
                    if (coords) {
                        this.emojiDropdownStyle = {
                            display: 'block',
                            position: 'absolute',
                            left: `${coords.left}px`,
                            top: `${coords.top + coords.height + 28}px`,
                            zIndex: '1000',
                        };
                        this.emojiSuggestions = suggestions;
                        this.showEmojiDropdown = true;
                        this.lastEmojiMatch = { match: lastMatch, index: lastIndex };
                    }
                }
            }
        }
        this.onChange(this.content);
        this.valueChanged();
    }

    onEmojiSuggestionSelect(selected: { name: string; emoji: string }) {
        if (!this.lastEmojiMatch) return;
        const { match, index } = this.lastEmojiMatch;
        const before = this.content?.substring(0, index) ?? '';
        const after = this.content?.substring(index + match.length) ?? '';
        const newText = before + selected.emoji + after;
        this.content = newText;
        this.showEmojiDropdown = false;
        this.emojiSuggestions = [];
        this.emojiDropdownStyle = {};
        this.lastEmojiMatch = null;
        if (this.markdownEditor && this.markdownEditor.monacoEditor && (this.markdownEditor.monacoEditor as any)._editor) {
            const editor = (this.markdownEditor.monacoEditor as any)._editor;
            const model = this.markdownEditor.monacoEditor.getModel();
            if (editor && model) {
                model.setValue(newText);
                const pos = before.length + selected.emoji.length;
                const { lineNumber, column } = model.getPositionAt(pos);
                editor.setPosition({ lineNumber, column });
            }
        }
        this.onChange(this.content);
        this.valueChanged();
    }

    // Add keyboard navigation for emoji suggestion dropdown
    onEmojiSuggestionKeyDown(event: KeyboardEvent) {
        if (!this.showEmojiDropdown || !this.emojiSuggestions.length) return;
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            this.emojiActiveIndex = (this.emojiActiveIndex + 1) % this.emojiSuggestions.length;
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            this.emojiActiveIndex = (this.emojiActiveIndex - 1 + this.emojiSuggestions.length) % this.emojiSuggestions.length;
        } else if (event.key === 'Enter') {
            event.preventDefault();
            if (this.emojiSuggestions[this.emojiActiveIndex]) {
                this.onEmojiSuggestionSelect(this.emojiSuggestions[this.emojiActiveIndex]);
            }
        } else if (event.key === 'Escape') {
            this.showEmojiDropdown = false;
        }
    }

    onKeyDown(event: KeyboardEvent) {
        if (this.showEmojiDropdown && ['ArrowDown', 'ArrowUp', 'Enter', 'Escape'].includes(event.key)) {
            this.onEmojiSuggestionKeyDown(event);
            // Prevent further handling of Enter when dropdown is open
            if (event.key === 'Enter') {
                event.stopPropagation();
                return;
            }
            return;
        }
        // Prevent a newline from being added to the text when pressing enter
        if (this.suppressNewlineOnEnter && event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
        }
    }
}
