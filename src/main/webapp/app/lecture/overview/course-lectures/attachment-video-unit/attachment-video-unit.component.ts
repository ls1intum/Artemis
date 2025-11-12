import { Component, OnInit, computed, inject } from '@angular/core';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { AttachmentVideoUnit, TranscriptionStatus, TranscriptionStatusDTO } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import urlParser from 'js-video-url-parser';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import {
    faDownload,
    faExclamationTriangle,
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
    faSpinner,
} from '@fortawesome/free-solid-svg-icons';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { FileService } from 'app/shared/service/file.service';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { LectureTranscriptionService } from 'app/lecture/manage/services/lecture-transcription.service';
import { AccountService } from 'app/core/auth/account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-attachment-video-unit',
    imports: [LectureUnitComponent, ArtemisDatePipe, TranslateDirective, SafeResourceUrlPipe, FaIconComponent],
    templateUrl: './attachment-video-unit.component.html',
    styleUrl: './attachment-video-unit.component.scss',
})
export class AttachmentVideoUnitComponent extends LectureUnitDirective<AttachmentVideoUnit> implements OnInit {
    protected readonly faDownload = faDownload;
    protected readonly faSpinner = faSpinner;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly TranscriptionStatus = TranscriptionStatus;

    private readonly fileService = inject(FileService);
    private readonly scienceService = inject(ScienceService);
    private readonly lectureTranscriptionService = inject(LectureTranscriptionService);
    private readonly accountService = inject(AccountService);

    transcriptionStatus?: TranscriptionStatusDTO;
    isLoadingTranscriptionStatus = false;

    private readonly videoUrlAllowList = [
        // TUM-Live. Example: 'https://live.rbg.tum.de/w/test/26?video_only=1'
        RegExp('^https://live\\.rbg\\.tum\\.de/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1$'),
    ];

    ngOnInit() {
        // Load transcription status if user is instructor or admin and video source exists
        if ((this.accountService.isAtLeastInstructorInCourse(this.lectureUnit().lecture?.course) || this.accountService.isAdmin()) && this.hasVideo() && this.lectureUnit().id) {
            this.loadTranscriptionStatus();
        }
    }

    loadTranscriptionStatus() {
        this.isLoadingTranscriptionStatus = true;
        this.lectureTranscriptionService.getTranscriptionStatus(this.lectureUnit().id!).subscribe({
            next: (status) => {
                this.transcriptionStatus = status;
                this.isLoadingTranscriptionStatus = false;
            },
            error: () => {
                this.isLoadingTranscriptionStatus = false;
            },
        });
    }

    shouldShowTranscriptionStatus(): boolean {
        const status = this.transcriptionStatus?.status;
        return (
            !this.isLoadingTranscriptionStatus &&
            !!status &&
            (status === TranscriptionStatus.PENDING || status === TranscriptionStatus.PROCESSING || status === TranscriptionStatus.FAILED)
        );
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
     * Return the URL of the video source
     */
    readonly videoUrl = computed(() => {
        if (this.lectureUnit().videoSource) {
            const source = this.lectureUnit().videoSource!;
            if (this.videoUrlAllowList.some((r) => r.test(source)) || !urlParser || urlParser.parse(source)) {
                return source;
            }
        }
        return undefined;
    });

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
