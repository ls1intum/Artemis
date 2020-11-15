import { JPlagMatch } from './JPlagMatch';
import { JPlagComparisonElement } from './JPlagComparisonElement';
import { PlagiarismComparison } from '../PlagiarismComparison';

export class JPlagComparison extends PlagiarismComparison {
    matches: JPlagMatch[];
    elementA: JPlagComparisonElement;
    elementB: JPlagComparisonElement;
}
