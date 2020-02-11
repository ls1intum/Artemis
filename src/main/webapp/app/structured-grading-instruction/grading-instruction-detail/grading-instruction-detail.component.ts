import { Component, OnInit, ViewChildren, QueryList } from '@angular/core';
import { EditStructuredGradingInstructionComponent } from 'app/structured-grading-instruction/edit-structured-grading-instruction/edit-structured-grading-instruction.component';
import { GradingInstruction } from 'app/structured-grading-instruction/grading-instruction.model';

@Component({
    selector: 'jhi-grading-instruction-detail',
    templateUrl: './grading-instruction-detail.component.html',
    styles: [],
})
export class GradingInstructionDetailComponent implements OnInit {
    @ViewChildren('editMultipleChoice')
    editStructuredGradingInstructionsComponent: QueryList<EditStructuredGradingInstructionComponent>;
    constructor() {}

    ngOnInit() {
        this.addGradingInstruction();
    }

    addGradingInstruction() {
        const instruction = new GradingInstruction();
        instruction.credit = 0;
        instruction.instructionDescription = 'Add grading instruction here (only visible for tutors)';
        instruction.feedback = 'Add feedback for students here (visible for students)';
        instruction.usageCount = 0;
    }
}
