export interface UniqueElementStatistic {
    name: string;
    apollonId: string;
    conflict: boolean;
}

export interface ModelStatistic {
    confidence: number;
    coverage: number;
    conflicts: number;
}

export interface ModelingStatistic {
    numberModels: number;
    numberConflicts: number;
    totalConfidence: number;
    totalCoverage: number;
    uniqueElements: [{ [id: string]: UniqueElementStatistic }];
    models: [{ [id: string]: ModelStatistic }];
}
