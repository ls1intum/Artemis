import { Component, computed, inject, signal } from '@angular/core';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import urlParser from 'js-video-url-parser';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { VideoPlayerComponent } from 'app/lecture/shared/video-player/video-player.component';
import { HttpClient, HttpParams } from '@angular/common/http';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
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
import { TranscriptSegment } from 'app/lecture/shared/video-player/video-player.component';
import { firstValueFrom, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [LectureUnitComponent, ArtemisDatePipe, TranslateDirective, SafeResourceUrlPipe, VideoPlayerComponent],
    templateUrl: './attachment-video-unit.component.html',
    styleUrl: './attachment-video-unit.component.scss',
})
export class AttachmentVideoUnitComponent extends LectureUnitDirective<AttachmentVideoUnit> {
    protected readonly faDownload = faDownload;

    private readonly fileService = inject(FileService);
    private readonly scienceService = inject(ScienceService);
    private readonly http = inject(HttpClient);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);

    readonly transcriptSegments = signal<TranscriptSegment[]>([]);
    readonly playlistUrl = signal<string | undefined>(undefined);
    readonly hasTranscript = computed(() => this.transcriptSegments().length > 0);

    private readonly videoUrlAllowList = [RegExp('^https://live\\.rbg\\.tum\\.de/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1$'), RegExp('^https://.+\\.m3u8($|\\?.*)')];

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
        if (this.videoUrlAllowList.some((r) => r.test(source)) || !urlParser || urlParser.parse(source)) {
            return source;
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

            const src = this.lectureUnit().videoSource;
            if (!src) {
                return;
            }

            // Direct .m3u8 playlist URLs - use without resolver
            const url = new URL(src);
            if (url.pathname.endsWith('.m3u8')) {
                this.playlistUrl.set(src);
                this.fetchTranscript();
                return;
            }
            // For non-playlist URLs (e.g., TUM Live), try to resolve a .m3u8 playlist
            this.resolveTumLivePlaylist(src).then((url) => {
                if (url) {
                    this.playlistUrl.set(url);
                    this.fetchTranscript();
                }
            });
        }
    }

    private async resolveTumLivePlaylist(pageUrl: string): Promise<string | undefined> {
        const params = new HttpParams().set('url', pageUrl);
        try {
            const res = await firstValueFrom(this.http.get('/api/nebula/video-utils/tum-live-playlist', { params, responseType: 'text' }).pipe(catchError(() => of(null))));
            return (res || undefined) as string | undefined;
        } catch {
            return undefined;
        }
    }

    private fetchTranscript(): void {
        const id = this.lectureUnit().id!;

        void firstValueFrom(
            this.lectureTranscriptionService.getTranscription(id).pipe(
                map((dto) => {
                    if (!dto || !dto.segments) {
                        return [];
                    }
                    // Filter and map to ensure all required fields are present
                    return dto.segments.filter((seg): seg is TranscriptSegment => seg.startTime != null && seg.endTime != null && seg.text != null) as TranscriptSegment[];
                }),
            ),
        ).then((segments) => {
            this.transcriptSegments.set(segments);
        });
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
