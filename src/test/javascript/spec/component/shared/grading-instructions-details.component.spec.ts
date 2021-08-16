import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Exercise } from 'app/entities/exercise.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { InstructionDescriptionCommand } from 'app/shared/markdown-editor/domainCommands/instructionDescription.command';

chai.use(sinonChai);
const expect = chai.expect;

describe('Grading Instructions Management Component', () => {
    let component: GradingInstructionsDetailsComponent;
    let fixture: ComponentFixture<GradingInstructionsDetailsComponent>;
    const gradingInstruction = { id: 1, credits: 1, gradingScale: 'scale', instructionDescription: 'description', feedback: 'feedback', usageCount: 0 } as GradingInstruction;
    const gradingCriterion = { id: 1, title: 'testCriteria', structuredGradingInstructions: [gradingInstruction] } as GradingCriterion;
    const exercise = { id: 1 } as Exercise;
    const backupExercise = { id: 1 } as Exercise;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [GradingInstructionsDetailsComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(GradingInstructionsDetailsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(GradingInstructionsDetailsComponent);
        component = fixture.componentInstance;
        component.exercise = exercise;
        component.backupExercise = backupExercise;
    });

    describe('OnInit', function () {
        it('should Set the default grading criteria', fakeAsync(() => {
            // WHEN
            component.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(component.backupExercise.id).to.equal(component.exercise.id);
            expect(component.markdownEditorText).to.equal(
                'Add Assessment Instruction text here\n\n' +
                    '[instruction]\n' +
                    '\t[credits] 0\n' +
                    '\t[gradingScale] Add instruction grading scale here (only visible for tutors)\n' +
                    '\t[description] Add grading instruction here (only visible for tutors)\n' +
                    '\t[feedback] Add feedback for students here (visible for students)\n' +
                    '\t[maxCountInScore] 0\n' +
                    '\n' +
                    '[criterion]This is an example criterion\n' +
                    '\t[instruction]\n' +
                    '\t[credits] 0\n' +
                    '\t[gradingScale] Add instruction grading scale here (only visible for tutors)\n' +
                    '\t[description] Add grading instruction here (only visible for tutors)\n' +
                    '\t[feedback] Add feedback for students here (visible for students)\n' +
                    '\t[maxCountInScore] 0\n' +
                    '\n' +
                    '[instruction]\n' +
                    '\t[credits] 0\n' +
                    '\t[gradingScale] Add instruction grading scale here (only visible for tutors)\n' +
                    '\t[description] Add grading instruction here (only visible for tutors)\n' +
                    '\t[feedback] Add feedback for students here (visible for students)\n' +
                    '\t[maxCountInScore] 0\n' +
                    '\n',
            );
        }));
        it('should set the grading criteria based on the exercise', fakeAsync(() => {
            component.exercise.gradingCriteria = [gradingCriterion];
            // WHEN
            component.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(component.markdownEditorText).to.equal(
                'Add Assessment Instruction text here\n\n' +
                    '[criterion]' +
                    'testCriteria' +
                    '\n' +
                    '\t' +
                    '[instruction]\n' +
                    '\t' +
                    '[credits]' +
                    ' 1\n' +
                    '\t' +
                    '[gradingScale] scale\n' +
                    '\t' +
                    '[description] description\n' +
                    '\t' +
                    '[feedback] feedback\n' +
                    '\t' +
                    '[maxCountInScore] 0\n\n',
            );
        }));
    });

    it('should return grading criteria index', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        const index = component.findCriterionIndex(gradingCriterion, component.exercise);
        fixture.detectChanges();

        expect(index).to.equal(0);
    });

    it('should return grading instruction index', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        const index = component.findInstructionIndex(gradingInstruction, component.exercise, 0);
        fixture.detectChanges();

        expect(index).to.equal(0);
    });

    it('should add new grading instruction to criteria', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.addNewInstruction(gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions.length).to.equal(2);
    });

    it('should delete the grading criterion', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.deleteGradingCriterion(gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria.length).to.equal(0);
    });

    it('should reset the grading criterion', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.backupExercise.gradingCriteria = [gradingCriterion];
        component.resetCriterionTitle(gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria).to.deep.equal(component.backupExercise.gradingCriteria);
    });

    it('should add new grading criteria to corresponding exercise', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.addNewGradingCriterion();
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria.length).to.equal(2);
    });

    it('should change grading criteria title', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        const event = { target: { value: 'changed Title' } };
        component.onCriterionTitleChange(event, gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].title).to.equal(event.target.value);
    });

    it('should change grading instruction', () => {
        const newDescription = 'new text';
        const domainCommands = [[newDescription, new InstructionDescriptionCommand()]] as [string, DomainCommand | null][];

        component.exercise.gradingCriteria = [gradingCriterion];
        component.onInstructionChange(domainCommands, gradingInstruction);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions[0].instructionDescription).to.equal(newDescription);
    });

    it('should delete a grading instruction', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.deleteInstruction(gradingInstruction, gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions[0].id).to.equal(undefined);
    });

    it('should set grading instruction text for exercise', () => {
        const markdownText = 'new text';
        const domainCommands = [[markdownText, null]] as [string, DomainCommand | null][];

        component.setExerciseGradingInstructionText(domainCommands);
        fixture.detectChanges();

        expect(component.exercise.gradingInstructions).to.equal(markdownText);
    });
});
