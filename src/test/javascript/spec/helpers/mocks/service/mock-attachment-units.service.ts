import { of } from 'rxjs';
import { LectureUnitInformationDTO } from 'app/lecture/manage/lecture-units/attachment-units/attachment-units.component';

export class MockAttachmentUnitsService {
    getSplitUnitsData = (lectureId: number, filename: string) => of({});

    createUnits = (lectureId: number, filename: string, lectureUnitInformation: LectureUnitInformationDTO) => of({});

    uploadSlidesForProcessing = (lectureId: number, file: File) => of({});

    getSlidesToRemove = (lectureId: number, filename: string, keyPhrases: string) => of({});
}
