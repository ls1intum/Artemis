import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LectureTranscriptionIngestionComponent } from 'app/admin/lecture-transcription-ingestion/lecture-transcription-ingestion.component';
import { LectureTranscriptionService } from 'app/admin/lecture-transcription-ingestion/lecture-transcription.service';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../../helpers/mocks/service/mock-feature-toggle.service';

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
            imports: [LectureTranscriptionIngestionComponent],
            providers: [
                { provide: LectureTranscriptionService, useValue: mockLectureTranscriptionService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        }).compileComponents();

        lectureTranscriptionService = TestBed.inject(LectureTranscriptionService);

        fixture = TestBed.createComponent(LectureTranscriptionIngestionComponent);
        comp = fixture.componentInstance;
    });

    it('should call ingestTranscription with correct parameters when ingest button is clicked', fakeAsync(() => {
        const courseId = '1';
        comp.ingestCourseIdInput = courseId;

        const lectureId = '1';
        comp.ingestLectureIdInput = lectureId;

        const lectureUnitId = '1';
        comp.ingestLectureUnitIdInput = lectureUnitId;
        fixture.detectChanges();
        tick();

        const button = fixture.debugElement.query(By.css('#ingest-transcription-button'));
        button.triggerEventHandler('onClick');

        fixture.detectChanges();

        expect(lectureTranscriptionService.ingestTranscription).toHaveBeenCalledWith(Number(courseId), Number(lectureId), Number(lectureUnitId));
    }));

    it('should call createTranscription with correct parameters when create transcription button is clicked', fakeAsync(() => {
        const transcription = '{ "transcription": [] }';
        const lectureId = '1';
        const lectureUnitId = '1';

        comp.transcriptionInput = transcription;
        comp.createLectureIdInput = lectureId;
        comp.createLectureUnitIdInput = lectureUnitId;

        fixture.detectChanges();
        tick();

        const button = fixture.debugElement.query(By.css('#create-transcription-button'));

        button.triggerEventHandler('onClick', null);
        fixture.detectChanges();

        expect(lectureTranscriptionService.createTranscription).toHaveBeenCalledWith(Number(lectureId), Number(lectureUnitId), JSON.parse(transcription));
    }));
});
