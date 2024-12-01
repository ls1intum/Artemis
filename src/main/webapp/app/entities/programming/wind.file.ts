import { BuildAction } from 'app/entities/programming/build.action';
import { WindMetadata } from 'app/entities/programming/wind.metadata';

export class WindFile {
    api: string;
    metadata: WindMetadata;
    actions: BuildAction[];
}
