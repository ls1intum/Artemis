import { Component } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule } from '@angular/forms';
import { LectureTranscriptionService } from 'app/admin/lecture-transcription-ingestion/lecture-transcription.service';

@Component({
    selector: 'jhi-lecture-transcription-ingestion',
    templateUrl: './lecture-transcription-ingestion.component.html',
    styleUrl: './lecture-transcription-ingestion.component.scss',
    standalone: true,
    imports: [ArtemisSharedComponentModule, FormsModule],
})
export class LectureTranscriptionIngestionComponent {
    courseIdInput = '';
    lectureIdInput = '';

    transcriptionInput = '';

    faCheck = faCheck;

    constructor(private lectureTranscriptionService: LectureTranscriptionService) {}

    ingestTranscription(): void {
        this.lectureTranscriptionService.ingestTranscription(Number(this.courseIdInput), Number(this.lectureIdInput)).subscribe(() => {
            this.courseIdInput = '';
            this.lectureIdInput = '';
        });
    }

    createTranscription(): void {
        this.lectureTranscriptionService.createTranscription(JSON.parse(this.transcriptionInput)).subscribe(() => {
            this.transcriptionInput = '';
        });
    }
}
