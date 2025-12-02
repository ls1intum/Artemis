import { WindFile } from 'app/programming/shared/entities/wind.file';

export class DockerContainerConfig {
    public id: number;
    public name: string; // TODO: passing this through duplicately is ugly
    public buildPlanConfiguration?: string;
    public buildScript?: string;
    public dockerFlags?: string;
    public windfile?: WindFile;
}
