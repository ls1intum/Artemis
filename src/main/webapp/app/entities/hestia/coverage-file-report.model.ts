import { BaseEntity } from 'app/shared/model/base-entity';
import { TestwiseCoverageReportEntry } from 'app/entities/hestia/testwise-coverage-report-entry.model';

export class CoverageFileReport implements BaseEntity {
    public id?: number;

    public filePath?: string;
    public lineCount?: number;
    public coveredLineCount?: number;
    public testwiseCoverageEntries?: TestwiseCoverageReportEntry[];

    constructor() {}
}
