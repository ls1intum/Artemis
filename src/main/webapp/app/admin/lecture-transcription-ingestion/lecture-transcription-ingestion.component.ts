import { Component, inject } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule } from '@angular/forms';
import { LectureTranscriptionService } from 'app/admin/lecture-transcription-ingestion/lecture-transcription.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-lecture-transcription-ingestion',
    templateUrl: './lecture-transcription-ingestion.component.html',
    styleUrl: './lecture-transcription-ingestion.component.scss',
    standalone: true,
    imports: [ArtemisSharedComponentModule, FormsModule],
})
export class LectureTranscriptionIngestionComponent {
    private lectureTranscriptionService = inject(LectureTranscriptionService);
    private alertService = inject(AlertService);

    courseIdInput = '';
    lectureIdInput = '';

    transcriptionInput = '';

    faCheck = faCheck;

    ingestTranscription(): void {
        this.lectureTranscriptionService.ingestTranscription(Number(this.courseIdInput), Number(this.lectureIdInput)).subscribe((successful) => {
            if (successful) {
                this.alertService.success('Ingested transcription');
            } else {
                this.alertService.error('Unknown error while ingesting transcription');
            }
            this.courseIdInput = '';
            this.lectureIdInput = '';
        });
    }

    createTranscription(): void {
        this.lectureTranscriptionService.createTranscription(JSON.parse(this.transcriptionInput)).subscribe((successful) => {
            if (successful) {
                this.alertService.success('Created transcription');
            } else {
                this.alertService.error('Unknown error while creating transcription');
            }
            this.transcriptionInput = '';
        });
    }
}
