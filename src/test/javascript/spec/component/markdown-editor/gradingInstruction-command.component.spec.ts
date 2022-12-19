import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { GradingInstructionCommand } from 'app/shared/markdown-editor/domainCommands/gradingInstruction.command';
import { CreditsCommand } from 'app/shared/markdown-editor/domainCommands/credits.command';
import { GradingScaleCommand } from 'app/shared/markdown-editor/domainCommands/gradingScaleCommand';
import { InstructionDescriptionCommand } from 'app/shared/markdown-editor/domainCommands/instructionDescription.command';
import { FeedbackCommand } from 'app/shared/markdown-editor/domainCommands/feedback.command';
import { UsageCountCommand } from 'app/shared/markdown-editor/domainCommands/usageCount.command';
import { GradingCriterionCommand } from 'app/shared/markdown-editor/domainCommands/gradingCriterionCommand';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgModel } from '@angular/forms';

describe('Grading Instruction Command', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, ArtemisMarkdownEditorModule],
            declarations: [MockPipe(ArtemisTranslatePipe), MockDirective(NgModel)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should add instruction identifiers and parameters on execute', () => {
        const gradingInstructionCommand = new GradingInstructionCommand();
        comp.domainCommands = [gradingInstructionCommand];
        fixture.detectChanges();
        comp.ngAfterViewInit();

        gradingInstructionCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe(
            '[instruction]' +
                '\n' +
                '\t' +
                ('[credits]' +
                    CreditsCommand.text +
                    '\n' +
                    '\t' +
                    '[gradingScale]' +
                    GradingScaleCommand.text +
                    '\n' +
                    '\t' +
                    '[description]' +
                    InstructionDescriptionCommand.text +
                    '\n' +
                    '\t' +
                    '[feedback]' +
                    FeedbackCommand.text +
                    '\n' +
                    '\t' +
                    '[maxCountInScore]' +
                    UsageCountCommand.text) +
                '\n',
        );
    });

    it('should add CriteriaCommand identifier on execute', () => {
        const criterionCommand = new GradingCriterionCommand();
        const gradingInstructionCommand = new GradingInstructionCommand();
        comp.domainCommands = [criterionCommand, gradingInstructionCommand];
        fixture.detectChanges();
        comp.ngAfterViewInit();

        criterionCommand.execute();
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('\n' + '[criterion]' + GradingCriterionCommand.text + '\n' + gradingInstructionCommand.instructionText());
    });
});
