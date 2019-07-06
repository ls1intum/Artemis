import { TextCluster } from 'app/entities/text-cluster/text-cluster.model';
import { TextSubmission } from 'app/entities/text-submission';

export class TextBlock {
    id?: number;
    text?: string;
    submission?: TextSubmission;
    cluster: TextCluster;
}
