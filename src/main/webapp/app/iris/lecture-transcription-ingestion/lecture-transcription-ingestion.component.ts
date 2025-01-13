import { Component, Input } from '@angular/core';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { IrisTranscriptionService } from 'app/iris/iris-transcription.service';

@Component({
    selector: 'jhi-lecture-transcription-ingestion',
    templateUrl: './lecture-transcription-ingestion.component.html',
    styleUrl: './lecture-transcription-ingestion.component.scss',
})
export class LectureTranscriptionIngestionComponent {
    @Input()
    public courseId: number;

    lectureIdInput = '';

    isIngestionLoading = false;

    faCheck = faCheck;

    constructor(private irisTranscriptionService: IrisTranscriptionService) {}

    ingestTranscription(): void {
        this.irisTranscriptionService.ingestTranscription(this.courseId, Number(this.lectureIdInput)).subscribe((response) => {
            console.log('Response received:', response);
        });
    }
}
