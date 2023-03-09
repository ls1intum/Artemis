import { TestwiseCoverageReportEntry } from 'app/entities/hestia/testwise-coverage-report-entry.model';
import { BaseEntity } from 'app/shared/model/base-entity';

export class CoverageFileReport implements BaseEntity {
    public id?: number;

    public filePath?: string;
    public lineCount?: number;
    public coveredLineCount?: number;
    public testwiseCoverageEntries?: TestwiseCoverageReportEntry[];

    constructor() {}
}
