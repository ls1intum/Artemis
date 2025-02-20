import { Component, inject } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { LectureTranscriptionService } from 'app/admin/lecture-transcription-ingestion/lecture-transcription.service';
import { AlertService } from 'app/core/util/alert.service';
import { ButtonComponent } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-lecture-transcription-ingestion',
    templateUrl: './lecture-transcription-ingestion.component.html',
    styleUrl: './lecture-transcription-ingestion.component.scss',
    standalone: true,
    imports: [FormsModule, ButtonComponent],
})
export class LectureTranscriptionIngestionComponent {
    private lectureTranscriptionService = inject(LectureTranscriptionService);
    private alertService = inject(AlertService);

    ingestCourseIdInput = '';
    ingestLectureIdInput = '';
    ingestLectureUnitIdInput = '';

    createCourseIdInput = '';
    createLectureIdInput = '';
    createLectureUnitIdInput = '';

    transcriptionInput = '';

    faCheck = faCheck;

    ingestTranscription(): void {
        this.lectureTranscriptionService
            .ingestTranscription(Number(this.ingestCourseIdInput), Number(this.ingestLectureIdInput), Number(this.ingestLectureUnitIdInput))
            .subscribe((successful) => {
                if (successful) {
                    this.alertService.success('Ingested transcription');
                } else {
                    this.alertService.error('Unknown error while ingesting transcription');
                }
                this.ingestCourseIdInput = '';
                this.ingestLectureIdInput = '';
                this.ingestLectureUnitIdInput = '';
            });
    }

    createTranscription(): void {
        this.lectureTranscriptionService
            .createTranscription(Number(this.createCourseIdInput), Number(this.createLectureIdInput), Number(this.createLectureUnitIdInput), JSON.parse(this.transcriptionInput))
            .subscribe((successful) => {
                if (successful) {
                    this.alertService.success('Created transcription');
                } else {
                    this.alertService.error('Unknown error while creating transcription');
                }
                this.transcriptionInput = '';
                this.createCourseIdInput = '';
                this.createLectureIdInput = '';
                this.createLectureUnitIdInput = '';
            });
    }
}
