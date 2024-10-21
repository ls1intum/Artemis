import Comparable from './comparable';

export default interface SortStrategy<T extends Comparable = Date> {
    performSort(dates: Array<T>): void;
}
