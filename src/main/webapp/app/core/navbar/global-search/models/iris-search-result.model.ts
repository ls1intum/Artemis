import { GlobalSearchSource } from './global-search-source.model';
import { IrisSearchHandoff } from './iris-search-status-update.model';

export interface IrisSearchResult {
    answer: string | undefined;
    sources: GlobalSearchSource[];
    handoff?: IrisSearchHandoff;
}
