import { of } from 'rxjs';
import type { LectureUnitSplitInformation } from 'app/openapi/model/lecture-unit-split-information';

export class MockAttachmentVideoUnitApi {
    getAttachmentVideoUnitsData = (lectureId: number, filename: string) =>
        of<LectureUnitSplitInformation>({ units: [], numberOfPages: 0, removeSlidesCommaSeparatedKeyPhrases: '' });

    createAttachmentVideoUnits = (lectureId: number, filename: string, lectureUnitSplitInformation: LectureUnitSplitInformation) => of([]);

    uploadSlidesForProcessing = (lectureId: number, file: File) => of('test-filename');

    getSlidesToRemove = (lectureId: number, filename: string, commaSeparatedKeyPhrases: string) => of<number[]>([]);
}
