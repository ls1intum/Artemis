import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { Component, OnInit, input, viewChildren } from '@angular/core';
import { faCompress, faExpand, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-structured-grading-instructions-assessment-layout',
    templateUrl: './structured-grading-instructions-assessment-layout.component.html',
    styleUrls: ['./structured-grading-instructions-assessment-layout.component.scss'],
    imports: [FaIconComponent, TranslateDirective, ExpandableSectionComponent, NgbTooltip, HelpIconComponent, HtmlForMarkdownPipe],
})
export class StructuredGradingInstructionsAssessmentLayoutComponent implements OnInit {
    public readonly criteria = input.required<GradingCriterion[]>();
    readonly readonly = input<boolean>();
    allowDrop: boolean;
    // Icons
    faInfoCircle = faInfoCircle;
    faExpand = faExpand;
    faCompress = faCompress;

    readonly expandableSections = viewChildren(ExpandableSectionComponent);

    /**
     * OnInit set the allowDrop property to allow drop of SGI if not in readOnly mode
     */
    ngOnInit(): void {
        this.allowDrop = !this.readonly();
    }

    collapseAll() {
        this.expandableSections().forEach((section) => {
            if (!section.isCollapsed) {
                section.toggleCollapsed();
            }
        });
    }

    expandAll() {
        this.expandableSections().forEach((section) => {
            if (section.isCollapsed) {
                section.toggleCollapsed();
            }
        });
    }

    /**
     * Set the tooltip of the draggable grading instruction to be equal to the feedback detail text
     * @param {GradingInstruction} instr - the instruction object from which the feedback detail text is retrieved
     */
    setTooltip(instr: GradingInstruction) {
        return 'Feedback: ' + instr.feedback;
    }

    /**
     * Set the color of the draggable grading instruction based on the credits of the instruction
     *  @param {GradingInstruction} instr - the instruction object we set its color based on its credits
     */
    setInstrColour(instr: GradingInstruction) {
        let colour;
        if (instr.credits === 0) {
            colour = 'var(--sgi-assessment-layout-zero-background)';
        } else if (instr.credits < 0) {
            colour = 'var(--sgi-assessment-layout-negative-background)';
        } else {
            colour = 'var(--sgi-assessment-layout-positive-background)';
        }
        return colour;
    }
    setScore(nr: number) {
        return nr + 'P';
    }
    /**
     * Connects the SGI with the Feedback of a Submission Element in assessment detail
     * @param {Event} event - The drag event
     * @param {Object} instruction - The SGI element that should be connected with the feedback on drop
     * the corresponding drop method is in AssessmentDetailComponent
     */
    drag(event: any, instruction: GradingInstruction) {
        // The mimetype has to be text/plain to enable dragging into an external application, e.g, Apollon
        event.dataTransfer.setData('text/plain', JSON.stringify(instruction));
    }
    /**
     * disables drag if on readOnly mode
     */
    disableDrag() {
        return this.allowDrop;
    }
}
