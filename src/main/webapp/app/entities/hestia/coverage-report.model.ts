import { BaseEntity } from 'app/shared/model/base-entity';
import { CoverageFileReport } from 'app/entities/hestia/coverage-file-report.model';

export class CoverageReport implements BaseEntity {
    public id?: number;

    public fileReports?: CoverageFileReport[];
    public coveredLineRatio?: number;

    constructor() {}
}
