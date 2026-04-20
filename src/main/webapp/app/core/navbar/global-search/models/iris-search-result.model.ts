import { LectureSearchResult } from './lecture-search-result.model';

export interface IrisSearchWebsocketDTO {
    cited: boolean;
    answer: string;
    sources: LectureSearchResult[];
}
