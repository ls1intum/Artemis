import { Component, Input } from '@angular/core';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-resizable-instructions',
    templateUrl: './resizable-instructions.component.html',
    styleUrls: ['./resizable-instructions.component.scss'],
    imports: [FaIconComponent, TranslateDirective, StructuredGradingInstructionsAssessmentLayoutComponent, HtmlForMarkdownPipe],
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
}
