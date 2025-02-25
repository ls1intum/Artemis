import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { LectureTranscriptionIngestionComponent } from 'app/admin/lecture-transcription-ingestion/lecture-transcription-ingestion.component';
import { LectureTranscriptionService } from 'app/admin/lecture-transcription-ingestion/lecture-transcription.service';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';

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
        comp.ingestCourseIdInput = courseId;

        const lectureId = '1';
        comp.ingestLectureIdInput = lectureId;

        const lectureUnitId = '1';
        comp.ingestLectureUnitIdInput = lectureUnitId;
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('#ingest-transcription-button'));

        button.triggerEventHandler('onClick', null);
        fixture.detectChanges();

        expect(lectureTranscriptionService.ingestTranscription).toHaveBeenCalledWith(Number(courseId), Number(lectureId), Number(lectureUnitId));
    }));

    it('should call createTranscription with correct parameters when create transcription button is clicked', waitForAsync(() => {
        const transcription = '{ "transcription": [] }';
        const courseId = '1';
        const lectureId = '1';
        const lectureUnitId = '1';

        comp.transcriptionInput = transcription;
        comp.createCourseIdInput = courseId;
        comp.createLectureIdInput = lectureId;
        comp.createLectureUnitIdInput = lectureUnitId;

        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('#create-transcription-button'));

        button.triggerEventHandler('onClick', null);
        fixture.detectChanges();

        expect(lectureTranscriptionService.createTranscription).toHaveBeenCalledWith(Number(courseId), Number(lectureId), Number(lectureUnitId), JSON.parse(transcription));
    }));
});
