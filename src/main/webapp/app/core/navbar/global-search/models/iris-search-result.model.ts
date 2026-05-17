import { GlobalSearchSource } from './global-search-source.model';

export interface IrisSearchResult {
    answer: string | undefined;
    sources: GlobalSearchSource[];
}
