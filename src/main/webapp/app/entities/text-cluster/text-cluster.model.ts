import { TextBlock } from 'app/entities/text-block/text-block.model';

export class TextCluster {
    id?: number;
    probabilitiesContentType?: string;
    probabilities?: any;
    distanceMatrixContentType?: string;
    distanceMatrix?: any;
    blocks?: TextBlock[];
}
