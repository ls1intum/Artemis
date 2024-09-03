import { BaseEntity } from 'app/shared/model/base-entity';

export class AuxiliaryRepository implements BaseEntity {
    public id?: number;
    public name?: string;
    public checkoutDirectory?: string;
    public repositoryUri?: string;
    public description?: string;
}
