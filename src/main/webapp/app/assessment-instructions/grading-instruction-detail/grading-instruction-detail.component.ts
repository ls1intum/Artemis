import { Component, OnInit, ViewChildren, QueryList } from '@angular/core';
import { EditMultipleChoiceQuestionComponent } from 'app/quiz/edit';
import { EditStructuredGradingInstructionsComponent } from 'app/assessment-instructions/edit-structured-grading-instructions/edit-structured-grading-instructions.component';
import { AssessmentInstructions } from 'app/assessment-instructions/assessment-instructions.model';

@Component({
    selector: 'jhi-grading-instruction-detail',
    templateUrl: './grading-instruction-detail.component.html',
    styles: [],
})
export class GradingInstructionDetailComponent implements OnInit {
    @ViewChildren('editMultipleChoice')
    editStructuredGradingInstructionsComponent: QueryList<EditStructuredGradingInstructionsComponent>;
    constructor() {}

    ngOnInit() {
        this.addGradingInstruction();
    }

    addGradingInstruction() {
        const instruction = new AssessmentInstructions();
        instruction.credit = 0;
        instruction.gradingInstruction = 'Add grading instruction here (only visible for tutors)';
        instruction.feedback = 'Add feedback for students here (visible for students)';
        instruction.usageCount = 0;
    }
}
