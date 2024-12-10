import { Component, Input } from '@angular/core';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@Component({
    selector: 'jhi-resizable-instructions',
    templateUrl: './resizable-instructions.component.html',
    styleUrls: ['./resizable-instructions.component.scss'],
    standalone: true,
    imports: [FaIconComponent, TranslateDirective, AssessmentInstructionsModule, ArtemisMarkdownModule],
})
export class ResizableInstructionsComponent {
    @Input() public criteria: GradingCriterion[];
    @Input() public problemStatement?: string;
    @Input() public sampleSolution?: string;
    @Input() public gradingInstructions?: string;
    @Input() public toggleCollapse: (event: any, type?: string) => void;
    @Input() public toggleCollapseId?: string;
    @Input() readOnly: boolean;

    // Icons
    faChevronRight = faChevronRight;
    farListAlt = faListAlt;

    constructor() {}
}
