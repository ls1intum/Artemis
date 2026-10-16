import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, effect, inject, input, output, signal, untracked, viewChild, viewChildren } from '@angular/core';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFaceSmile, faFileArrowUp, faFileCircleQuestion, faFilePowerpoint } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiInputDirective } from '@tumaet/ui-angular';
import { EmojiEvent } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { EmojiPickerComponent } from 'app/communication/emoji/emoji-picker.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { MAX_FILE_SIZE_COMMUNICATION } from 'app/foundation/constants/input.constants';
import { sanitizeStringForMarkdownEditor } from 'app/foundation/util/markdown.util';
import { MessageEditorResources, MessageResourceKind } from './message-editor-resources';
import { MessageSuggestion } from './message-editor-suggestions';

export interface MessageResourceInsertion {
    markdown: string;
    range: Range;
}

/** Existing upload and emoji services, with searchable course resources independent of Monaco. */
@Component({
    selector: 'jhi-message-editor-tools',
    templateUrl: './message-editor-tools.component.html',
    imports: [ArtemisTranslatePipe, FaIconComponent, TumAetUiButtonDirective, TumAetUiInputDirective, CdkConnectedOverlay, CdkOverlayOrigin, CdkTrapFocus, EmojiPickerComponent],
    providers: [MessageEditorResources],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageEditorToolsComponent {
    private readonly resources = inject(MessageEditorResources);
    private readonly files = inject(FileUploaderService);
    private readonly destroyRef = inject(DestroyRef);

    readonly courseId = input.required<number>();
    readonly conversationId = input<number>();
    readonly editorRevision = input(0);
    readonly captureSelection = input.required<() => Range | undefined>();
    readonly insert = output<MessageResourceInsertion>();
    readonly uploadingChange = output<boolean>();
    readonly closed = output<void>();
    private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');
    private readonly queryInput = viewChild<ElementRef<HTMLInputElement>>('queryInput');
    private readonly optionOrigins = viewChildren<CdkOverlayOrigin>('optionOrigin');
    protected readonly preview = signal<{ item: MessageSuggestion; origin: CdkOverlayOrigin } | undefined>(undefined);
    protected readonly previewFailed = signal(false);
    protected readonly previewPositions = [
        { originX: 'end' as const, originY: 'center' as const, overlayX: 'start' as const, overlayY: 'center' as const, offsetX: 8 },
        { originX: 'start' as const, originY: 'center' as const, overlayX: 'end' as const, overlayY: 'center' as const, offsetX: -8 },
        { originX: 'center' as const, originY: 'bottom' as const, overlayX: 'center' as const, overlayY: 'top' as const, offsetY: 8 },
    ];

    readonly picker = signal<MessageResourceKind | 'emoji' | undefined>(undefined);
    readonly uploading = signal(false);
    readonly error = signal<'position' | 'upload' | 'conversation' | 'busy' | undefined>(undefined);
    protected composing = false;
    protected readonly errorFile = signal('');
    protected readonly query = signal('');
    protected readonly loading = signal(false);
    protected readonly failed = signal(false);
    protected readonly results = signal<MessageSuggestion[]>([]);
    protected readonly active = signal(0);
    protected readonly icons = { faFileArrowUp, faFileCircleQuestion, faFilePowerpoint, faFaceSmile };
    protected readonly acceptedFiles = this.files.acceptedMarkdownFileExtensions.map((extension) => `.${extension}`).join(',');
    protected readonly maxFileSize = MAX_FILE_SIZE_COMMUNICATION / (1024 * 1024);
    protected readonly positions = [
        { originX: 'end' as const, originY: 'top' as const, overlayX: 'end' as const, overlayY: 'bottom' as const, offsetY: -8 },
        { originX: 'end' as const, originY: 'bottom' as const, overlayX: 'end' as const, overlayY: 'top' as const, offsetY: 8 },
    ];
    private range?: Range;
    private request = 0;
    private context = 0;
    private static nextId = 0;
    protected readonly listId = `message-resource-results-${MessageEditorToolsComponent.nextId++}`;

    constructor() {
        effect(() => {
            this.courseId();
            this.conversationId();
            this.editorRevision();
            untracked(() => {
                this.context++;
                this.close(false);
                this.setUploading(false);
                this.error.set(undefined);
            });
        });
        effect(() => this.queryInput()?.nativeElement.focus());
    }

    protected remember(event: MouseEvent): void {
        event.preventDefault();
        this.range = this.captureSelection()();
    }

    protected open(kind: MessageResourceKind | 'emoji'): void {
        this.range = this.captureSelection()();
        if (!this.range) {
            this.error.set('position');
            return;
        }
        this.error.set(undefined);
        this.query.set('');
        this.picker.set(kind);
        if (kind !== 'emoji') {
            void this.search();
        }
    }

    protected close(restoreFocus = true): void {
        this.request++;
        this.picker.set(undefined);
        this.preview.set(undefined);
        this.results.set([]);
        this.failed.set(false);
        if (restoreFocus) {
            this.closed.emit();
        }
    }

    protected async search(event?: Event): Promise<void> {
        if (event?.target instanceof HTMLInputElement) {
            this.query.set(event.target.value);
        }
        const kind = this.picker();
        if (!kind || kind === 'emoji') {
            return;
        }
        const request = ++this.request;
        this.preview.set(undefined);
        this.loading.set(true);
        this.failed.set(false);
        this.active.set(0);
        try {
            const results = await this.resources.search(kind, this.query(), this.courseId());
            if (request === this.request && !this.destroyRef.destroyed) {
                this.results.set(results);
            }
        } catch {
            if (request === this.request && !this.destroyRef.destroyed) {
                this.failed.set(true);
                this.results.set([]);
            }
        } finally {
            if (request === this.request && !this.destroyRef.destroyed) {
                this.loading.set(false);
            }
        }
    }

    protected choose(item: MessageSuggestion): void {
        if (this.range) {
            this.insert.emit({ markdown: item.markdown + ' ', range: this.range });
        }
        this.close();
    }

    protected chooseEmoji(event: EmojiEvent): void {
        if (event.emoji.native && this.range) {
            this.insert.emit({ markdown: event.emoji.native, range: this.range });
        }
        this.close();
    }

    protected keydown(event: Event): void {
        event.stopPropagation();
        if (!(event instanceof KeyboardEvent) || event.isComposing || this.composing) {
            return;
        }
        if (event.key === 'Escape') {
            event.preventDefault();
            this.close();
        } else if (event.key === 'Enter') {
            event.preventDefault();
            const item = this.results()[this.active()];
            if (item) {
                this.choose(item);
            }
        } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            const length = this.results().length;
            this.activate(length ? (this.active() + (event.key === 'ArrowDown' ? 1 : length - 1)) % length : 0);
        }
    }

    /** Loads a single preview on pointer hover or keyboard navigation, using the existing authorized slide endpoint. */
    protected activate(index: number): void {
        this.active.set(index);
        const item = this.results()[index];
        const origin = this.optionOrigins()[index];
        this.previewFailed.set(false);
        this.preview.set(item?.previewUrl && origin ? { item, origin } : undefined);
    }

    protected openFiles(): void {
        this.range = this.captureSelection()();
        this.fileInput()?.nativeElement.click();
    }

    protected filesSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        void this.uploadFiles(Array.from(input.files ?? []), this.range);
        input.value = '';
    }

    /** Uploads pasted, dropped or selected files using the existing conversation-scoped validation and endpoint. */
    async uploadFiles(files: File[], range = this.captureSelection()()): Promise<void> {
        if (!files.length) {
            return;
        }
        if (this.uploading()) {
            this.error.set('busy');
            return;
        }
        if (!this.conversationId()) {
            this.error.set('conversation');
            return;
        }
        if (!range) {
            this.error.set('position');
            return;
        }
        this.error.set(undefined);
        const context = this.context;
        const courseId = this.courseId();
        const conversationId = this.conversationId();
        this.setUploading(true);
        for (const file of files) {
            try {
                const result = await this.files.uploadMarkdownFileInCurrentConversation(file, courseId, conversationId);
                if (context !== this.context || this.destroyRef.destroyed) {
                    return;
                }
                if (!result.path) {
                    throw new Error('Missing file path');
                }
                const label = sanitizeStringForMarkdownEditor(file.name);
                this.insert.emit({ markdown: `${file.name.toLowerCase().endsWith('.pdf') ? '' : '!'}[${label}](${result.path})`, range });
                range = this.captureSelection()() ?? range;
            } catch {
                if (context === this.context && !this.destroyRef.destroyed) {
                    this.errorFile.set(file.name);
                    this.error.set('upload');
                }
            }
            if (context !== this.context || this.destroyRef.destroyed) {
                return;
            }
        }
        this.setUploading(false);
    }

    private setUploading(value: boolean): void {
        if (this.uploading() !== value) {
            this.uploading.set(value);
            this.uploadingChange.emit(value);
        }
    }
}
