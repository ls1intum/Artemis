import { AfterContentChecked, AfterViewInit, ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, ViewChild, ViewEncapsulation, forwardRef } from '@angular/core';
import { Command } from 'app/shared/markdown-editor/commands/command';
import { BoldCommand } from 'app/shared/markdown-editor/commands/bold.command';
import { ItalicCommand } from 'app/shared/markdown-editor/commands/italic.command';
import { ReferenceCommand } from 'app/shared/markdown-editor/commands/reference.command';
import { UnderlineCommand } from 'app/shared/markdown-editor/commands/underline.command';
import { CodeBlockCommand } from 'app/shared/markdown-editor/commands/codeblock.command';
import { CodeCommand } from 'app/shared/markdown-editor/commands/code.command';
import { LinkCommand } from 'app/shared/markdown-editor/commands/link.command';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { ExerciseReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/exerciseReferenceCommand';
import { LectureAttachmentReferenceCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/lectureAttachmentReferenceCommand';
import { LectureService } from 'app/lecture/lecture.service';
import { UserMentionCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/userMentionCommand';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ChannelMentionCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/channelMentionCommand';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { isCommunicationEnabled } from 'app/entities/course.model';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import { MonacoBoldAction } from 'app/shared/monaco-editor/model/actions/monaco-bold.action';
import { MonacoItalicAction } from 'app/shared/monaco-editor/model/actions/monaco-italic.action';
import { MonacoUnderlineAction } from 'app/shared/monaco-editor/model/actions/monaco-underline.action';
import { MonacoQuoteAction } from 'app/shared/monaco-editor/model/actions/monaco-quote.action';
import { MonacoCodeAction } from 'app/shared/monaco-editor/model/actions/monaco-code.action';
import { MonacoCodeBlockAction } from 'app/shared/monaco-editor/model/actions/monaco-code-block.action';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

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
})
export class PostingMarkdownEditorComponent implements OnInit, ControlValueAccessor, AfterContentChecked, AfterViewInit {
    @ViewChild(MarkdownEditorMonacoComponent, { static: true }) markdownEditor: MarkdownEditorMonacoComponent;

    @Input() maxContentLength: number;
    @Input() editorHeight: MarkdownEditorHeight = MarkdownEditorHeight.INLINE;
    @Input() isInputLengthDisplayed = true;
    @Output() valueChange = new EventEmitter();
    defaultCommands: Command[];
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
        const messagingOnlyCommands = isCommunicationEnabled(this.metisService.getCourse())
            ? [new UserMentionCommand(this.courseManagementService, this.metisService), new ChannelMentionCommand(this.channelService, this.metisService)]
            : [];

        this.defaultCommands = [
            new BoldCommand(),
            new ItalicCommand(),
            new UnderlineCommand(),
            new ReferenceCommand(),
            new CodeCommand(),
            new CodeBlockCommand(),
            new LinkCommand(),
            ...messagingOnlyCommands,
            new ExerciseReferenceCommand(this.metisService),
            new LectureAttachmentReferenceCommand(this.metisService, this.lectureService),
        ];

        this.defaultActions = [
            new MonacoBoldAction(),
            new MonacoItalicAction(),
            new MonacoUnderlineAction(),
            new MonacoQuoteAction(),
            new MonacoCodeAction(),
            new MonacoCodeBlockAction(),
            /* TODO: Exercise/Lecture reference & messaging */
        ];
    }

    ngAfterViewInit(): void {
        this.markdownEditor.setTextFieldMode();
        // this.markdownEditor.monacoEditor.addOverlayWidget('markdown-send-btn', this.sendButton.nativeElement, { preference: 1 });
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
        // Prevent a newline from being added when pressing enter
        if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
        }
    }
}
