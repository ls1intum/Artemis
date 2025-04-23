import { BuildAction } from 'app/programming/shared/entities/build.action';
import { WindMetadata } from 'app/programming/shared/entities/wind.metadata';

export class WindFile {
    api: string;
    metadata: WindMetadata;
    actions: BuildAction[];
}
