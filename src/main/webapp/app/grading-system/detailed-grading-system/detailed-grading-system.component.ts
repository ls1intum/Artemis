import { Component } from '@angular/core';
import { BaseGradingSystemComponent, CsvGradeStep } from 'app/grading-system/base-grading-system/base-grading-system.component';
import { parse } from 'papaparse';

@Component({
    selector: 'jhi-detailed-grading-system',
    templateUrl: './detailed-grading-system.component.html',
    styleUrls: ['./detailed-grading-system.component.scss'],
})
export class DetailedGradingSystemComponent extends BaseGradingSystemComponent {
    /**
     * Sets the inclusivity for all grade steps based on the lowerBoundInclusivity property
     * Called before a post/put request
     * @override
     */
    setInclusivity(): void {
        const gradeSteps = this.gradingScale.gradeSteps;
        // copy the grade steps in a separate array, so they don't get dynamically updated when sorting
        let sortedGradeSteps = gradeSteps.slice();
        sortedGradeSteps = this.gradingSystemService.sortGradeSteps(sortedGradeSteps);

        gradeSteps.forEach((gradeStep) => {
            if (this.lowerBoundInclusivity) {
                gradeStep.lowerBoundInclusive = true;
                gradeStep.upperBoundInclusive = sortedGradeSteps.last()!.gradeName === gradeStep.gradeName;
            } else {
                gradeStep.lowerBoundInclusive = sortedGradeSteps.first()!.gradeName === gradeStep.gradeName;
                gradeStep.upperBoundInclusive = true;
            }
        });
    }

    /**
     * Parse CSV file to a list of CsvGradeStep objects
     * @param csvFile the read csv file
     * @override
     */
    parseCSVFile(csvFile: File): Promise<CsvGradeStep[]> {
        return new Promise(async (resolve, reject) => {
            parse(csvFile, {
                header: true,
                skipEmptyLines: true,
                dynamicTyping: false,
                complete: (results) => resolve(results.data as CsvGradeStep[]),
                error: (error) => reject(error),
            });
        });
    }
}
