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
    ViewEncapsulation,
    computed,
    forwardRef,
    inject,
    input,
} from '@angular/core';
import monaco from 'monaco-editor';
import { ViewContainerRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MetisService } from 'app/communication/metis.service';
import { LectureService } from 'app/lecture/manage/lecture.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { Course, isCommunicationEnabled, isFaqEnabled } from 'app/entities/course.model';
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
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { EmojiAction } from 'app/shared/monaco-editor/model/actions/emoji.action';
import { Overlay, OverlayPositionBuilder } from '@angular/cdk/overlay';
import { BulletedListAction } from 'app/shared/monaco-editor/model/actions/bulleted-list.action';
import { OrderedListAction } from 'app/shared/monaco-editor/model/actions/ordered-list.action';
import { StrikethroughAction } from 'app/shared/monaco-editor/model/actions/strikethrough.action';
import { PostingContentComponent } from '../posting-content.components';
import { NgStyle } from '@angular/common';
import { FileService } from 'app/shared/http/file.service';
import { PostingEditType } from '../metis.util';

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
    imports: [MarkdownEditorMonacoComponent, PostingContentComponent, NgStyle],
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
        this.onChange(this.content);
        this.valueChanged();
    }

    onKeyDown(event: KeyboardEvent) {
        // Prevent a newline from being added to the text when pressing enter
        if (this.suppressNewlineOnEnter && event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
        }
    }
}
