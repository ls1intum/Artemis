import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Exercise } from 'app/entities/exercise.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { GradingInstructionsDetailsComponent } from 'app/exercises/shared/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { MonacoGradingInstructionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-instruction.action';
import { MonacoGradingCreditsAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-credits.action';
import { MonacoGradingScaleAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-scale.action';
import { MonacoGradingDescriptionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-description.action';
import { MonacoGradingFeedbackAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-feedback.action';
import { MonacoGradingUsageCountAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-usage-count.action';
import { MonacoGradingCriterionAction } from 'app/shared/monaco-editor/model/actions/grading-criteria/monaco-grading-criterion.action';
import { TextWithDomainAction } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

describe('GradingInstructionsDetailsComponent', () => {
    let component: GradingInstructionsDetailsComponent;
    let fixture: ComponentFixture<GradingInstructionsDetailsComponent>;
    let gradingInstruction: GradingInstruction;
    let gradingCriterion: GradingCriterion;
    let gradingInstructionWithoutId: GradingInstruction;
    let gradingCriterionWithoutId: GradingCriterion;
    const exercise = { id: 1 } as Exercise;
    const backupExercise = { id: 1 } as Exercise;

    const criterionMarkdownText =
        '[criterion] testCriteria\n' +
        '\t[instruction]\n' +
        '\t[credits] 1\n' +
        '\t[gradingScale] scale\n' +
        '\t[description] description\n' +
        '\t[feedback] feedback\n' +
        '\t[maxCountInScore] 0\n\n';

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
        gradingInstruction = { id: 1, credits: 1, gradingScale: 'scale', instructionDescription: 'description', feedback: 'feedback', usageCount: 0 };
        gradingCriterion = { id: 1, title: 'testCriteria', structuredGradingInstructions: [gradingInstruction] };
        gradingInstructionWithoutId = { credits: 1, gradingScale: 'scale', instructionDescription: 'description', feedback: 'feedback', usageCount: 0 };
        gradingCriterionWithoutId = { title: 'testCriteria', structuredGradingInstructions: [gradingInstructionWithoutId] };
    });

    describe('onInit', () => {
        it('should initialize the component', fakeAsync(() => {
            // WHEN
            component.ngOnInit();

            // THEN
            expect(component).toBeTruthy();
        }));
        it('should set the grading criteria based on the exercise', fakeAsync(() => {
            component.exercise.gradingCriteria = [gradingCriterion];
            // WHEN
            component.ngOnInit();
            tick(); // simulate async
            // THEN
            expect(component.markdownEditorText).toEqual('Add Assessment Instruction text here\n\n' + criterionMarkdownText);
        }));
    });

    it('should return grading criteria index', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        const index = component.findCriterionIndex(gradingCriterion, component.exercise);
        fixture.detectChanges();

        expect(index).toBe(0);
    });

    it('should return grading instruction index', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        const index = component.findInstructionIndex(gradingInstruction, component.exercise, 0);
        fixture.detectChanges();

        expect(index).toBe(0);
    });

    it('should add new grading instruction to criteria', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.addNewInstruction(gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions).toHaveLength(2);
    });

    it('should delete the grading criterion', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.deleteGradingCriterion(gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria).toHaveLength(0);
    });

    it('should reset the grading criterion', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.backupExercise.gradingCriteria = [gradingCriterion];
        component.resetCriterionTitle(gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria).toEqual(component.backupExercise.gradingCriteria);
    });

    it('should add new grading criteria to corresponding exercise', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.addNewGradingCriterion();
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria).toHaveLength(2);
    });

    it('should change grading criteria title', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        const event = { target: { value: 'changed Title' } };
        component.onCriterionTitleChange(event, gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].title).toEqual(event.target.value);
    });

    it('should change grading instruction', () => {
        const newDescription = 'new text';
        const domainActions = [{ text: newDescription, action: new MonacoGradingDescriptionAction() }] as TextWithDomainAction[];

        component.exercise.gradingCriteria = [gradingCriterion];
        component.onInstructionChange(domainActions, gradingInstruction);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions[0].instructionDescription).toEqual(newDescription);
    });

    it('should delete a grading instruction', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        component.deleteInstruction(gradingInstruction, gradingCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions[0].id).toBeUndefined();
    });

    it('should set grading instruction text for exercise', () => {
        const markdownText = 'new text';
        const domainActions = [{ text: markdownText, action: undefined }] as TextWithDomainAction[];

        component.setExerciseGradingInstructionText(domainActions);
        fixture.detectChanges();

        expect(component.exercise.gradingInstructions).toEqual(markdownText);
    });

    const getDomainActionArray = () => {
        const creditsAction = new MonacoGradingCreditsAction();
        const scaleAction = new MonacoGradingScaleAction();
        const descriptionAction = new MonacoGradingDescriptionAction();
        const feedbackAction = new MonacoGradingFeedbackAction();
        const usageCountAction = new MonacoGradingUsageCountAction();
        const instructionAction = new MonacoGradingInstructionAction(creditsAction, scaleAction, descriptionAction, feedbackAction, usageCountAction);
        const criterionAction = new MonacoGradingCriterionAction(instructionAction);

        return [
            { text: 'testCriteria', action: criterionAction },
            { text: '', action: instructionAction },
            { text: '1', action: creditsAction },
            { text: 'scale', action: scaleAction },
            { text: 'description', action: descriptionAction },
            { text: 'feedback', action: feedbackAction },
            { text: '0', action: usageCountAction },
        ] as TextWithDomainAction[];
    };

    it('should set grading instruction without criterion action when markdown-change triggered', () => {
        const domainActionsWithoutCriterion = getDomainActionArray().slice(1);

        component.onDomainActionsFound(domainActionsWithoutCriterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria).toBeDefined();
        const gradingCriteria = component.exercise.gradingCriteria![0];
        expect(gradingCriteria.structuredGradingInstructions[0]).toEqual(gradingInstructionWithoutId);
    });

    it('should set grading instruction with criterion action when markdown-change triggered', () => {
        const domainActions = getDomainActionArray();

        component.onDomainActionsFound(domainActions);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria).toBeDefined();
        const gradingCriteria = component.exercise.gradingCriteria![0];
        expect(gradingCriteria).toEqual(gradingCriterionWithoutId);
    });

    it('should update properties for grading instruction', () => {
        component.exercise.gradingCriteria = [gradingCriterion];
        const instruction = gradingInstruction;
        const criterion = gradingCriterion;

        instruction.credits = 5;
        component.updateGradingInstruction(instruction, criterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions[0].credits).toBe(5);

        instruction.gradingScale = 'changed grading scale';
        component.updateGradingInstruction(instruction, criterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions[0].gradingScale).toBe('changed grading scale');

        instruction.instructionDescription = 'changed instruction description';
        component.updateGradingInstruction(instruction, criterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions[0].instructionDescription).toBe('changed instruction description');

        instruction.feedback = 'changed feedback';
        component.updateGradingInstruction(instruction, criterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions[0].feedback).toBe('changed feedback');

        instruction.usageCount = 2;
        component.updateGradingInstruction(instruction, criterion);
        fixture.detectChanges();

        expect(component.exercise.gradingCriteria[0].structuredGradingInstructions[0].usageCount).toBe(2);
    });
});
