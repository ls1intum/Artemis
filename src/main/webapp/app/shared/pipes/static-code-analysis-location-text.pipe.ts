import { Pipe, PipeTransform } from '@angular/core';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';

@Pipe({
    name: 'staticCodeAnalysisLocationText',
})
export class StaticCodeAnalysisLocationTextPipe implements PipeTransform {
    /**
     * Creates the location text from a static code analysis issue instance.
     * Not every issue has a start and end column.
     *
     * @param {StaticCodeAnalysisIssue} issue - Issue for which the location text is created
     * @returns {string} Static Code analysis issue location
     */
    transform(issue: StaticCodeAnalysisIssue): string {
        const lineText = issue.startLine === issue.endLine ? ` at line ${issue.startLine}` : ` at lines ${issue.startLine}-${issue.endLine}`;
        let columnText = '';
        if (issue.startColumn) {
            columnText = issue.startColumn === issue.endColumn ? ` column ${issue.startColumn}` : ` columns ${issue.startColumn}-${issue.endColumn}`;
        }
        return issue.filePath + lineText + columnText;
    }
}
