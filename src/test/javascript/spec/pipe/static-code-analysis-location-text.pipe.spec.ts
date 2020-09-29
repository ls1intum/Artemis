import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { StaticCodeAnalysisLocationTextPipe } from 'app/shared/pipes/static-code-analysis-location-text.pipe';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('StaticCodeAnalysisLocationTextPipe', () => {
    const pipe = new StaticCodeAnalysisLocationTextPipe();
    let issue: StaticCodeAnalysisIssue;

    beforeEach(() => {
        issue = { filePath: 'filePath', startLine: 1, endLine: 1 } as StaticCodeAnalysisIssue;
    });

    it('Create location text without columns', () => {
        const locationText = pipe.transform(issue);
        expect(locationText).equals(`${issue.filePath} at line ${issue.startLine}`);
    });

    it('Create location text with start and end line without columns', () => {
        issue.endLine = 3;
        const locationText = pipe.transform(issue);
        expect(locationText).equals(`${issue.filePath} at lines ${issue.startLine}-${issue.endLine}`);
    });

    it('Create location text with columns', () => {
        issue.startColumn = 1;
        issue.endColumn = 1;
        const locationText = pipe.transform(issue);
        expect(locationText).equals(`${issue.filePath} at line ${issue.startLine} column ${issue.startColumn}`);
    });

    it('Create location text with start and end column', () => {
        issue.startColumn = 1;
        issue.endColumn = 3;
        const locationText = pipe.transform(issue);
        expect(locationText).equals(`${issue.filePath} at line ${issue.startLine} columns ${issue.startColumn}-${issue.endColumn}`);
    });
});
