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
    forwardRef,
} from '@angular/core';
import { ViewContainerRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MetisService } from 'app/shared/metis/metis.service';
import { LectureService } from 'app/lecture/lecture.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { isCommunicationEnabled } from 'app/entities/course.model';
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
import { EmojiAction } from 'app/shared/monaco-editor/model/actions/emoji.action';
import { Overlay, OverlayPositionBuilder } from '@angular/cdk/overlay';

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
})
export class PostingMarkdownEditorComponent implements OnInit, ControlValueAccessor, AfterContentChecked, AfterViewInit {
    @ViewChild(MarkdownEditorMonacoComponent, { static: true }) markdownEditor: MarkdownEditorMonacoComponent;

    @Input() maxContentLength: number;
    @Input() editorHeight: MarkdownEditorHeight = MarkdownEditorHeight.INLINE;
    @Input() isInputLengthDisplayed = true;
    @Input() suppressNewlineOnEnter = true;
    @Output() valueChange = new EventEmitter();
    lectureAttachmentReferenceAction: LectureAttachmentReferenceAction;
    defaultActions: TextEditorAction[];
    content?: string;
    previewMode = false;

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

    constructor(
        private cdref: ChangeDetectorRef,
        private metisService: MetisService,
        private courseManagementService: CourseManagementService,
        private lectureService: LectureService,
        private channelService: ChannelService,
        public viewContainerRef: ViewContainerRef,
        private overlay: Overlay,
        private positionBuilder: OverlayPositionBuilder,
    ) {}

    /**
     * on initialization: sets commands that will be available as formatting buttons during creation/editing of postings
     */
    ngOnInit(): void {
        const messagingOnlyActions = isCommunicationEnabled(this.metisService.getCourse())
            ? [new UserMentionAction(this.courseManagementService, this.metisService), new ChannelReferenceAction(this.metisService, this.channelService)]
            : [];

        this.defaultActions = [
            new BoldAction(),
            new ItalicAction(),
            new UnderlineAction(),
            new EmojiAction(this.viewContainerRef, this.overlay, this.positionBuilder),
            new QuoteAction(),
            new CodeAction(),
            new CodeBlockAction(),
            ...messagingOnlyActions,
            new ExerciseReferenceAction(this.metisService),
        ];

        this.lectureAttachmentReferenceAction = new LectureAttachmentReferenceAction(this.metisService, this.lectureService);
    }

    ngAfterViewInit(): void {
        this.markdownEditor.enableTextFieldMode();
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
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    onChange = (val: string) => {};

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
