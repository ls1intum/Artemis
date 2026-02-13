import { Component, DestroyRef, OnDestroy, computed, inject, signal } from '@angular/core';
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
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { FileService } from 'app/shared/service/file.service';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';
import { map } from 'rxjs/operators';
@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [LectureUnitComponent, ArtemisDatePipe, TranslateDirective, SafeResourceUrlPipe, VideoPlayerComponent, PdfViewerComponent],
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

    readonly transcriptSegments = signal<TranscriptSegment[]>([]);
    readonly playlistUrl = signal<string | undefined>(undefined);
    readonly isLoading = signal<boolean>(false);

    readonly pdfUrl = signal<string | undefined>(undefined);
    readonly isPdfLoading = signal<boolean>(false);

    readonly hasTranscript = computed(() => this.transcriptSegments().length > 0);

    readonly isPdf = computed(() => {
        const link = this.lectureUnit().attachment?.link;
        const result = link && link.toLowerCase().endsWith('.pdf');
        // eslint-disable-next-line no-undef
        console.log('[AttachmentVideoUnit] isPdf computed - link:', link, 'result:', result);
        return result;
    });

    readonly hasPdf = computed(() => {
        const result = this.hasAttachment() && this.isPdf();
        // eslint-disable-next-line no-undef
        console.log('[AttachmentVideoUnit] hasPdf computed - hasAttachment:', this.hasAttachment(), 'isPdf:', this.isPdf(), 'result:', result);
        return result;
    });

    // TODO: This must use a server configuration to make it compatible with deployments other than TUM
    private readonly videoUrlAllowList = [RegExp('^https://(?:live\\.rbg\\.tum\\.de|tum\\.live)/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1')];

    /**
     * Return the URL of the video source
     */
    readonly videoUrl = computed(() => this.computeVideoUrl());

    /**
     * Computes the video URL based on the video source.
     * Returns undefined if the source is invalid or doesn't match the allow list.
     */
    private computeVideoUrl(): string | undefined {
        const source = this.lectureUnit().videoSource;
        if (!source) {
            return undefined;
        }
        // Check if it matches the allow list (e.g., TUM Live URLs)
        if (this.videoUrlAllowList.some((r) => r.test(source))) {
            return source;
        }
        // Check if urlParser can parse it (e.g., YouTube, Vimeo, etc.)
        if (urlParser) {
            const parsed = urlParser.parse(source);
            if (parsed) {
                return source;
            }
        }
        return undefined;
    }

    override toggleCollapse(isCollapsed: boolean): void {
        super.toggleCollapse(isCollapsed);

        if (!isCollapsed) {
            this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

            // reset stale state
            this.transcriptSegments.set([]);
            this.playlistUrl.set(undefined);
            this.pdfUrl.set(undefined);

            const src = this.lectureUnit().videoSource;

            // Handle video
            if (src) {
                this.isLoading.set(true);

                // Try to resolve a .m3u8 playlist URL from the server
                this.attachmentVideoUnitService
                    .getPlaylistUrl(src)
                    .pipe(takeUntilDestroyed(this.destroyRef))
                    .subscribe({
                        next: (resolvedUrl) => {
                            if (resolvedUrl) {
                                this.playlistUrl.set(resolvedUrl);
                                this.fetchTranscript();
                            }
                            this.isLoading.set(false);
                        },
                        error: () => {
                            // Failed to resolve playlist URL, will fall back to iframe
                            this.playlistUrl.set(undefined);
                            this.isLoading.set(false);
                        },
                    });
            } else {
                this.isLoading.set(false);
            }

            // Handle PDF (load independently)
            // eslint-disable-next-line no-undef
            console.log('[AttachmentVideoUnit] hasPdf():', this.hasPdf(), 'isPdf():', this.isPdf(), 'hasAttachment():', this.hasAttachment());
            if (this.hasPdf()) {
                // eslint-disable-next-line no-undef
                console.log('[AttachmentVideoUnit] Calling loadPdf()');
                this.loadPdf();
            } else {
                // eslint-disable-next-line no-undef
                console.log('[AttachmentVideoUnit] NOT calling loadPdf because hasPdf() is false');
            }
        }
    }

    private fetchTranscript(): void {
        const id = this.lectureUnit().id!;

        this.lectureTranscriptionService
            .getTranscription(id)
            .pipe(
                map((dto) => {
                    if (!dto || !dto.segments) {
                        return [];
                    }
                    // Filter and map to ensure all required fields are present
                    return dto.segments.filter((seg): seg is TranscriptSegment => seg.startTime != null && seg.endTime != null && seg.text != null) as TranscriptSegment[];
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (segments) => {
                    this.transcriptSegments.set(segments);
                },
                error: () => {
                    // Failed to fetch transcript, video player will work without it
                    this.transcriptSegments.set([]);
                },
            });
    }

    private loadPdf(): void {
        // eslint-disable-next-line no-undef
        console.log('[AttachmentVideoUnit] loadPdf called');
        // eslint-disable-next-line no-undef
        console.log('[AttachmentVideoUnit] Setting isPdfLoading to TRUE');
        this.isPdfLoading.set(true);

        // Use the same link as download - works for both student and instructor versions
        const link = addPublicFilePrefix(this.lectureUnit().attachment!.studentVersion || this.fileService.createStudentLink(this.lectureUnit().attachment!.link!));

        if (!link) {
            // eslint-disable-next-line no-undef
            console.error('[AttachmentVideoUnit] No link available for PDF');
            // eslint-disable-next-line no-undef
            console.log('[AttachmentVideoUnit] Setting isPdfLoading to FALSE (no link)');
            this.isPdfLoading.set(false);
            return;
        }

        // eslint-disable-next-line no-undef
        console.log('[AttachmentVideoUnit] Fetching PDF from link:', link);

        // Fetch the PDF as blob using HttpClient with proper typing
        this.httpClient
            .get(link, {
                responseType: 'blob',
                observe: 'response',
            })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (response) => {
                    const blob = response.body;
                    if (!blob) {
                        // eslint-disable-next-line no-undef
                        console.error('[AttachmentVideoUnit] No blob in response');
                        // eslint-disable-next-line no-undef
                        console.log('[AttachmentVideoUnit] Setting isPdfLoading to FALSE (no blob)');
                        this.isPdfLoading.set(false);
                        return;
                    }
                    // eslint-disable-next-line no-undef
                    console.log('[AttachmentVideoUnit] PDF blob received, size:', blob.size);
                    const url = URL.createObjectURL(blob);
                    // eslint-disable-next-line no-undef
                    console.log('[AttachmentVideoUnit] Object URL created:', url);
                    // eslint-disable-next-line no-undef
                    console.log('[AttachmentVideoUnit] Setting pdfUrl to:', url);
                    this.pdfUrl.set(url);
                    // eslint-disable-next-line no-undef
                    console.log('[AttachmentVideoUnit] Setting isPdfLoading to FALSE (success)');
                    this.isPdfLoading.set(false);
                    // eslint-disable-next-line no-undef
                    console.log('[AttachmentVideoUnit] FINAL STATE - isPdfLoading:', this.isPdfLoading(), 'pdfUrl:', this.pdfUrl());
                },
                error: (err) => {
                    // eslint-disable-next-line no-undef
                    console.error('[AttachmentVideoUnit] Error loading PDF:', err);
                    // eslint-disable-next-line no-undef
                    console.log('[AttachmentVideoUnit] Setting isPdfLoading to FALSE (HTTP error)');
                    this.pdfUrl.set(undefined);
                    this.isPdfLoading.set(false);
                },
            });
    }

    ngOnDestroy(): void {
        // Revoke PDF object URL to prevent memory leak
        const url = this.pdfUrl();
        if (url) {
            URL.revokeObjectURL(url);
        }
    }

    /**
     * Returns the name of the attachment file (including its file extension)
     */
    getFileName(): string {
        if (this.lectureUnit().attachment?.link) {
            const link = this.lectureUnit().attachment!.link!;
            const filename = link.substring(link.lastIndexOf('/') + 1);
            return this.fileService.replaceAttachmentPrefixAndUnderscores(filename);
        }
        return '';
    }

    /**
     * Downloads the file as the student version if available, otherwise the instructor version
     * If it is not the student view, it always downloads the original version
     */
    handleDownload() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

        // Determine the link based on the availability of a student version
        const link = addPublicFilePrefix(this.lectureUnit().attachment!.studentVersion || this.fileService.createStudentLink(this.lectureUnit().attachment!.link!));

        if (link) {
            this.fileService.downloadFileByAttachmentName(link, this.lectureUnit().attachment!.name!);
            this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
        }
    }

    handleOriginalVersion() {
        this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);

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

    /**
     * Returns the matching icon for the file extension of the attachment
     */
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
