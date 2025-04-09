import { Component, inject } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { LectureTranscriptionService } from 'app/admin/lecture-transcription-ingestion/service/lecture-transcription.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ButtonComponent } from 'app/shared/components/button/button.component';

@Component({
    selector: 'jhi-lecture-transcription-ingestion',
    templateUrl: './lecture-transcription-ingestion.component.html',
    styleUrl: './lecture-transcription-ingestion.component.scss',
    imports: [FormsModule, ButtonComponent],
})
export class LectureTranscriptionIngestionComponent {
    lectureTranscriptionService = inject(LectureTranscriptionService);
    alertService = inject(AlertService);

    ingestCourseIdInput = '';
    ingestLectureIdInput = '';
    ingestLectureUnitIdInput = '';

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
            .createTranscription(Number(this.createLectureIdInput), Number(this.createLectureUnitIdInput), JSON.parse(this.transcriptionInput))
            .subscribe((successful) => {
                if (successful) {
                    this.alertService.success('Created transcription');
                } else {
                    this.alertService.error('Unknown error while creating transcription');
                }
                this.transcriptionInput = '';
                this.createLectureIdInput = '';
                this.createLectureUnitIdInput = '';
            });
    }
}
