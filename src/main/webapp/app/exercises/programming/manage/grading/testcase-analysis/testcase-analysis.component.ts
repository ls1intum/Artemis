import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-testcase-analysis',
    templateUrl: './testcase-analysis.component.html',
    styleUrls: ['./testcase-analysis.component.scss'],
})
export class TestcaseAnalysisComponent {
    @Input() exerciseTitle?: string;

    exampleArray = [
        {
            occurrence: '39 (39%)',
            feedback: "The expected method 'insert' of the class 'RecursiveNode' with the parameters: [\"int\"] was not found...",
            task: 1,
            testcase: 'testMethods[RecursiveNode]',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '20 (20%)',
            feedback: "The expected method 'getLeftNode' of the class 'RecursiveNode' with no parameters was not found ...",
            task: 1,
            testcase: 'testMethods[RecursiveNode]',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '20 (20%)',
            feedback: 'Could not find the constructor with the parameters: [ int ] in the class RecursiveTree because the ...',
            task: 5,
            testcase: 'testRecursiveTreeAdd()',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '20 (20%)',
            feedback: 'Could not instantiate the class RecursiveTree because access to its constructor with the parameters: ...',
            task: 5,
            testcase: 'testRecursiveTreeAdd()',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '10 (10%)',
            feedback: 'Could not find the constructor with the parameters: [ int ] in the class RecursiveTree because the ...',
            task: 3,
            testcase: 'testRecursiveTreeIsEmpty()',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '10 (10%)',
            feedback: 'Could not instantiate the class RecursiveTree because access to its constructor with the parameters: ...',
            task: 3,
            testcase: 'testRecursiveTreeIsEmpty()',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '10 (10%)',
            feedback: 'Could not find the constructor with the parameters: [ int ] in the class RecursiveNode because the ...',
            task: 6,
            testcase: 'testContainsMethodRecursiveNode()',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '10 (10%)',
            feedback: 'Could not instantiate the class RecursiveNode because access to its constructor with the parameters: ...',
            task: 6,
            testcase: 'testContainsMethodRecursiveNode()',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '10 (10%)',
            feedback: 'Could not find the constructor with the parameters: [ int ] in the class RecursiveTree because the ...',
            task: 7,
            testcase: 'testRecursiveTreeSize()',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '10 (10%)',
            feedback: 'Could not instantiate the class RecursiveNode because access to its constructor with the parameters: ...',
            task: 7,
            testcase: 'testRecursiveTreeSize()',
            errorCategory: 'Student Error',
        },
        {
            occurrence: '1 (1%)',
            feedback: 'Failed: "Unwanted Statement Found. For Each Statement was found."',
            task: 7,
            testcase: 'testRecursiveTreeSize()',
            errorCategory: 'AST Error',
        },
        { occurrence: '1 (1%)', feedback: 'Security Exception: “.....”', task: 7, testcase: 'testRecursiveTreeSize()', errorCategory: 'ARES Error' },
    ];
}
