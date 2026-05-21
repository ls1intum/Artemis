import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { LectureUnitInformationDTO } from 'app/lecture/manage/lecture-units/attachment-video-units/attachment-video-units.component';

export class MockAttachmentVideoUnitsService {
    getSplitUnitsData = (lectureId: number, filename: string) =>
        of(
            new HttpResponse<LectureUnitInformationDTO>({
                body: { units: [], numberOfPages: 0, removeSlidesCommaSeparatedKeyPhrases: '' },
            }),
        );

    createUnits = (lectureId: number, filename: string, lectureUnitInformation: LectureUnitInformationDTO) => of(new HttpResponse({ body: {} }));

    uploadSlidesForProcessing = (lectureId: number, file: File) => of(new HttpResponse<string>({ body: 'test-filename' }));

    getSlidesToRemove = (lectureId: number, filename: string, keyPhrases: string) => of(new HttpResponse<number[]>({ body: [] }));
}
