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
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MetisService } from 'app/shared/metis/metis.service';
import { LectureService } from 'app/lecture/lecture.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { isCommunicationEnabled } from 'app/entities/course.model';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { MonacoBoldAction } from 'app/shared/monaco-editor/model/actions/monaco-bold.action';
import { MonacoItalicAction } from 'app/shared/monaco-editor/model/actions/monaco-italic.action';
import { MonacoUnderlineAction } from 'app/shared/monaco-editor/model/actions/monaco-underline.action';
import { MonacoQuoteAction } from 'app/shared/monaco-editor/model/actions/monaco-quote.action';
import { MonacoCodeAction } from 'app/shared/monaco-editor/model/actions/monaco-code.action';
import { MonacoCodeBlockAction } from 'app/shared/monaco-editor/model/actions/monaco-code-block.action';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoChannelReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/monaco-channel-reference.action';
import { MonacoUserMentionAction } from 'app/shared/monaco-editor/model/actions/communication/monaco-user-mention.action';
import { MonacoExerciseReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/monaco-exercise-reference.action';
import { MonacoLectureAttachmentReferenceAction } from 'app/shared/monaco-editor/model/actions/communication/monaco-lecture-attachment-reference.action';

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
    lectureAttachmentReferenceAction: MonacoLectureAttachmentReferenceAction;
    defaultActions: MonacoEditorAction[];
    content?: string;
    previewMode = false;

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

    constructor(
        private cdref: ChangeDetectorRef,
        private metisService: MetisService,
        private courseManagementService: CourseManagementService,
        private lectureService: LectureService,
        private channelService: ChannelService,
    ) {}

    /**
     * on initialization: sets commands that will be available as formatting buttons during creation/editing of postings
     */
    ngOnInit(): void {
        const messagingOnlyActions = isCommunicationEnabled(this.metisService.getCourse())
            ? [new MonacoUserMentionAction(this.courseManagementService, this.metisService), new MonacoChannelReferenceAction(this.metisService, this.channelService)]
            : [];

        this.defaultActions = [
            new MonacoBoldAction(),
            new MonacoItalicAction(),
            new MonacoUnderlineAction(),
            new MonacoQuoteAction(),
            new MonacoCodeAction(),
            new MonacoCodeBlockAction(),
            ...messagingOnlyActions,
            new MonacoExerciseReferenceAction(this.metisService),
        ];

        this.lectureAttachmentReferenceAction = new MonacoLectureAttachmentReferenceAction(this.metisService, this.lectureService);
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
