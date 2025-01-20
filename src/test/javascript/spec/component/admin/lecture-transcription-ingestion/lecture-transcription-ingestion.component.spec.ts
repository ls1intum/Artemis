import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { LectureTranscriptionIngestionComponent } from 'app/admin/lecture-transcription-ingestion/lecture-transcription-ingestion.component';
import { LectureTranscriptionService } from 'app/admin/lecture-transcription-ingestion/lecture-transcription.service';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';

describe('LectureTranscriptionIngestionComponent', () => {
    let comp: LectureTranscriptionIngestionComponent;
    let fixture: ComponentFixture<LectureTranscriptionIngestionComponent>;
    let lectureTranscriptionService: LectureTranscriptionService;

    beforeEach(() => {
        const mockLectureTranscriptionService = {
            ingestTranscription: jest.fn().mockReturnValue(of({})),
            createTranscription: jest.fn().mockReturnValue(of({})),
        };

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, LectureTranscriptionIngestionComponent],
            providers: [{ provide: LectureTranscriptionService, useValue: mockLectureTranscriptionService }],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureTranscriptionIngestionComponent);
        comp = fixture.componentInstance;
        lectureTranscriptionService = TestBed.inject(LectureTranscriptionService);
    });

    it('should call ingestTranscription with correct parameters when ingest button is clicked', waitForAsync(() => {
        const courseId = '1';
        comp.courseIdInput = courseId;

        const lectureId = '1';
        comp.lectureIdInput = lectureId;
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('#ingest-transcription-button'));

        button.triggerEventHandler('onClick', null);
        fixture.detectChanges();

        expect(lectureTranscriptionService.ingestTranscription).toHaveBeenCalledWith(Number(courseId), Number(lectureId));
    }));

    it('should call createTranscription with correct parameters when create transcription button is clicked', waitForAsync(() => {
        const transcription = '{ "transcription": [] }';
        comp.transcriptionInput = transcription;
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('#create-transcription-button'));

        button.triggerEventHandler('onClick', null);
        fixture.detectChanges();

        expect(lectureTranscriptionService.createTranscription).toHaveBeenCalledWith(JSON.parse(transcription));
    }));
});
