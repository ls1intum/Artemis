import { Component, DestroyRef, OnDestroy, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import urlParser from 'js-video-url-parser';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { VideoPlayerComponent } from 'app/lecture/shared/video-player/video-player.component';
import { PdfViewerComponent } from 'app/lecture/shared/pdf-viewer/pdf-viewer.component';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import {
    faDownload,
    faFile,
    faFileArchive,
    faFileCode,
    faFileCsv,
    faFileExcel,
    faFileImage,
    faFileLines,
    faFilePdf,
    faFilePen,
    faFilePowerpoint,
    faFileVideo,
    faFileWord,
} from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { FileService } from 'app/shared/service/file.service';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';
import { map } from 'rxjs/operators';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [LectureUnitComponent, TranslateDirective, SafeResourceUrlPipe, VideoPlayerComponent, PdfViewerComponent, FaIconComponent],
    templateUrl: './attachment-video-unit.component.html',
    styleUrl: './attachment-video-unit.component.scss',
})
export class AttachmentVideoUnitComponent extends LectureUnitDirective<AttachmentVideoUnit> implements OnDestroy {
    protected readonly faDownload = faDownload;
    protected readonly faFileLines = faFileLines;

    private readonly destroyRef = inject(DestroyRef);
    private readonly fileService = inject(FileService);
    private readonly scienceService = inject(ScienceService);
    private readonly attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);
    private readonly httpClient = inject(HttpClient);

    targetPdfPage = input<number | undefined>(undefined);

    readonly transcriptSegments = signal<TranscriptSegment[]>([]);
    readonly playlistUrl = signal<string | undefined>(undefined);
    readonly isLoading = signal<boolean>(false);

    readonly pdfUrl = signal<string | undefined>(undefined);
    readonly isPdfLoading = signal<boolean>(false);
    readonly pdfLoadError = signal<boolean>(false);

    private currentLoadSession = 0;

    readonly hasTranscript = computed(() => this.transcriptSegments().length > 0);

    readonly hasPdf = computed(() => {
        const link = this.lectureUnit().attachment?.link;
        return this.hasAttachment() && link ? link.toLowerCase().endsWith('.pdf') : false;
    });

    // TODO: This must use a server configuration to make it compatible with deployments other than TUM
    private readonly videoUrlAllowList = [RegExp('^https://(?:live\\.rbg\\.tum\\.de|tum\\.live)/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1')];

    readonly videoUrl = computed(() => this.computeVideoUrl());

    private computeVideoUrl(): string | undefined {
        const source = this.lectureUnit().videoSource;
        if (!source) {
            return undefined;
        }
        if (this.videoUrlAllowList.some((r) => r.test(source))) {
            return source;
        }
        if (urlParser) {
            const parsed = urlParser.parse(source);
            if (parsed) {
                return source;
            }
        }
        return undefined;
    }

    private logUnitOpenedEvent(): void {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);
    }

    private clearLoadedContent(): void {
        this.transcriptSegments.set([]);
        this.playlistUrl.set(undefined);
        this.revokePdfUrl();
        this.pdfUrl.set(undefined);
        this.pdfLoadError.set(false);
    }

    override toggleCollapse(isCollapsed: boolean): void {
        super.toggleCollapse(isCollapsed);

        if (!isCollapsed) {
            this.logUnitOpenedEvent();
            this.clearLoadedContent();

            // Rotate session token to invalidate any pending async operations
            const sessionToken = ++this.currentLoadSession;

            const src = this.lectureUnit().videoSource;

            if (src) {
                this.isLoading.set(true);

                this.attachmentVideoUnitService
                    .getPlaylistUrl(src)
                    .pipe(takeUntilDestroyed(this.destroyRef))
                    .subscribe({
                        next: (resolvedUrl) => {
                            // Only update state if this is still the active session
                            if (sessionToken !== this.currentLoadSession) {
                                return;
                            }
                            if (resolvedUrl) {
                                this.playlistUrl.set(resolvedUrl);
                                this.fetchTranscript(sessionToken);
                            }
                            this.isLoading.set(false);
                        },
                        error: () => {
                            // Only update state if this is still the active session
                            if (sessionToken !== this.currentLoadSession) {
                                return;
                            }
                            this.playlistUrl.set(undefined);
                            this.isLoading.set(false);
                        },
                    });
            } else {
                this.isLoading.set(false);
            }

            if (this.hasPdf()) {
                this.loadPdf(sessionToken);
            }
        } else {
            // Invalidate any pending async operations by rotating the session token
            this.currentLoadSession++;
            // Clear loaded content and reset state
            this.clearLoadedContent();
            this.isLoading.set(false);
            this.isPdfLoading.set(false);
        }
    }

    private fetchTranscript(sessionToken: number): void {
        const id = this.lectureUnit().id!;

        this.lectureTranscriptionService
            .getTranscription(id)
            .pipe(
                map((dto) => {
                    if (!dto || !dto.segments) {
                        return [];
                    }
                    // Filter segments with required fields
                    return dto.segments.filter((seg): seg is TranscriptSegment => seg.startTime != null && seg.endTime != null && seg.text != null) as TranscriptSegment[];
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (segments) => {
                    // Only update state if this is still the active session
                    if (sessionToken !== this.currentLoadSession) {
                        return;
                    }
                    this.transcriptSegments.set(segments);
                },
                error: () => {
                    // Only update state if this is still the active session
                    if (sessionToken !== this.currentLoadSession) {
                        return;
                    }
                    // Failed to fetch transcript, video player will work without it
                    this.transcriptSegments.set([]);
                },
            });
    }

    private loadPdf(sessionToken: number): void {
        this.isPdfLoading.set(true);
        this.pdfLoadError.set(false);

        const link = this.getAttachmentLink();

        if (!link) {
            this.pdfLoadError.set(true);
            this.isPdfLoading.set(false);
            return;
        }

        this.httpClient
            .get(link, { responseType: 'blob' })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (blob) => {
                    // Only update state if this is still the active session
                    if (sessionToken !== this.currentLoadSession) {
                        return;
                    }
                    if (blob) {
                        this.pdfUrl.set(URL.createObjectURL(blob));
                        this.pdfLoadError.set(false);
                    }
                    this.isPdfLoading.set(false);
                },
                error: () => {
                    // Only update state if this is still the active session
                    if (sessionToken !== this.currentLoadSession) {
                        return;
                    }
                    this.pdfUrl.set(undefined);
                    this.pdfLoadError.set(true);
                    this.isPdfLoading.set(false);
                },
            });
    }

    ngOnDestroy(): void {
        this.revokePdfUrl();
    }

    private revokePdfUrl(): void {
        const url = this.pdfUrl();
        if (url) {
            URL.revokeObjectURL(url);
        }
    }

    getFileName(): string {
        if (this.lectureUnit().attachment?.link) {
            const link = this.lectureUnit().attachment!.link!;
            const filename = link.substring(link.lastIndexOf('/') + 1);
            return this.fileService.replaceAttachmentPrefixAndUnderscores(filename);
        }
        return '';
    }

    /** Downloads student version if available, otherwise instructor version. */
    handleDownload() {
        this.logUnitOpenedEvent();

        const link = this.getAttachmentLink();

        if (link) {
            this.fileService.downloadFileByAttachmentName(link, this.lectureUnit().attachment!.name!);
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }

    private getAttachmentLink(): string | undefined {
        const attachment = this.lectureUnit().attachment;
        if (!attachment) {
            return undefined;
        }
        const link = attachment.studentVersion ?? (attachment.link ? this.fileService.createStudentLink(attachment.link) : undefined);
        return link ? addPublicFilePrefix(link) : undefined;
    }

    handleOriginalVersion() {
        this.logUnitOpenedEvent();

        const link = addPublicFilePrefix(this.lectureUnit().attachment!.link!);

        if (link) {
            this.fileService.downloadFileByAttachmentName(link, this.lectureUnit().attachment!.name!);
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }

    hasAttachment(): boolean {
        return !!this.lectureUnit().attachment;
    }

    hasVideo(): boolean {
        return !!this.lectureUnit().videoSource;
    }

    getAttachmentIcon(): IconDefinition {
        if (this.hasVideo()) {
            return faFileVideo;
        }

        if (this.lectureUnit().attachment?.link) {
            const fileExtension = this.lectureUnit().attachment?.link?.split('.').pop()!.toLocaleLowerCase();
            switch (fileExtension) {
                case 'png':
                case 'jpg':
                case 'jpeg':
                case 'gif':
                case 'svg':
                    return faFileImage;
                case 'pdf':
                    return faFilePdf;
                case 'zip':
                case 'tar':
                    return faFileArchive;
                case 'txt':
                case 'rtf':
                case 'md':
                    return faFileLines;
                case 'htm':
                case 'html':
                case 'json':
                    return faFileCode;
                case 'doc':
                case 'docx':
                case 'pages':
                case 'pages-tef':
                case 'odt':
                    return faFileWord;
                case 'csv':
                    return faFileCsv;
                case 'xls':
                case 'xlsx':
                case 'numbers':
                case 'ods':
                    return faFileExcel;
                case 'ppt':
                case 'pptx':
                case 'key':
                case 'odp':
                    return faFilePowerpoint;
                case 'odg':
                case 'odc':
                case 'odi':
                case 'odf':
                    return faFilePen;
                default:
                    return faFile;
            }
        }
        return faFile;
    }
}
