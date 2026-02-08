import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChecklistPanelComponent } from './checklist-panel.component';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { of, throwError } from 'rxjs';
import { ChecklistAnalysisResponse } from 'app/openapi/model/checklistAnalysisResponse';
import { ChecklistActionResponse } from 'app/openapi/model/checklistActionResponse';
import { ChecklistActionRequest } from 'app/openapi/model/checklistActionRequest';
import { By } from '@angular/platform-browser';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ChecklistPanelComponent', () => {
    let component: ChecklistPanelComponent;
    let fixture: ComponentFixture<ChecklistPanelComponent>;
    let apiService: HyperionProblemStatementApiService;
    let alertService: AlertService;

    const exercise = new ProgrammingExercise(undefined, undefined);
    exercise.id = 123;
    exercise.problemStatement = 'Problem statement';
    exercise.difficulty = DifficultyLevel.EASY; // Changed 'EASY' to DifficultyLevel.EASY for consistency with mockExercise

    const mockResponse: ChecklistAnalysisResponse = {
        inferredCompetencies: [{ competencyTitle: 'Loops', taxonomyLevel: 'APPLY', confidence: 0.9, whyThisMatches: 'Explanation' }],
        difficultyAssessment: { suggested: 'EASY', reasoning: 'Reason', matchesDeclared: true },
        qualityIssues: [],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, ChecklistPanelComponent],
            providers: [MockProvider(HyperionProblemStatementApiService), MockProvider(AlertService), MockProvider(TranslateService)],
        })
            .overrideComponent(ChecklistPanelComponent, {
                remove: { imports: [ArtemisTranslatePipe, TranslateDirective] },
                add: { imports: [MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ChecklistPanelComponent);
        component = fixture.componentInstance;
        apiService = TestBed.inject(HyperionProblemStatementApiService);
        alertService = TestBed.inject(AlertService);

        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('problemStatement', 'Problem statement');
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call analyzeChecklist on button click', () => {
        const analyzeSpy = jest.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);

        // Find analyze button
        const button = fixture.debugElement.query(By.css('button'));
        expect(button).toBeTruthy();
        button.nativeElement.click();

        expect(analyzeSpy).toHaveBeenCalledWith(exercise.id!, expect.objectContaining({ problemStatementMarkdown: 'Problem statement' }));

        // Wait for observable
        fixture.detectChanges();

        expect(component.isLoading()).toBeFalse();
        expect(component.analysisResult()).toEqual(mockResponse);
        expect(component.analysisResult()).toBeDefined();
    });

    it('should handle analysis error', () => {
        jest.spyOn(apiService, 'analyzeChecklist').mockReturnValue(throwError(() => new Error('Error')) as any);
        const errorSpy = jest.spyOn(alertService, 'error');

        component.analyze();

        expect(component.isLoading()).toBeFalse();
        expect(errorSpy).toHaveBeenCalled();
        expect(component.analysisResult()).toBeUndefined();
    });

    it('should display results when available', () => {
        component.analysisResult.set(mockResponse);
        fixture.detectChanges();

        const goalsSection = fixture.debugElement.query(By.css('.analysis-results'));
        expect(goalsSection).toBeTruthy();
        expect(component.analysisResult()).toBeDefined();
    });

    describe('AI Action Methods', () => {
        const mockActionResponse: ChecklistActionResponse = {
            updatedProblemStatement: 'Updated problem statement',
            applied: true,
            summary: 'Fixed quality issue',
        };

        it('should fix a single quality issue', () => {
            const actionSpy = jest.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            const emitSpy = jest.spyOn(component.problemStatementChange, 'emit');

            component.fixQualityIssue({ description: 'Unclear wording', suggestedFix: 'Reword it', category: 'CLARITY' }, 0);

            expect(actionSpy).toHaveBeenCalledWith(
                exercise.id,
                expect.objectContaining({
                    actionType: ChecklistActionRequest.ActionTypeEnum.FixQualityIssue,
                    problemStatementMarkdown: 'Problem statement',
                }),
            );
            expect(emitSpy).toHaveBeenCalledWith('Updated problem statement');
            expect(component.isApplyingAction()).toBeFalse();
            expect(component.lastActionSummary()).toBe('Fixed quality issue');
        });

        it('should fix all quality issues', () => {
            component.analysisResult.set({
                ...mockResponse,
                qualityIssues: [
                    { description: 'Issue 1', category: 'CLARITY', severity: 'WARNING' },
                    { description: 'Issue 2', category: 'COMPLETENESS', severity: 'ERROR' },
                ],
            });
            const actionSpy = jest.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            component.fixAllQualityIssues();

            expect(actionSpy).toHaveBeenCalledWith(
                exercise.id,
                expect.objectContaining({
                    actionType: ChecklistActionRequest.ActionTypeEnum.FixAllQualityIssues,
                }),
            );
        });

        it('should adapt difficulty', () => {
            component.analysisResult.set(mockResponse);
            const actionSpy = jest.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            component.adaptDifficulty('HARD');

            expect(actionSpy).toHaveBeenCalledWith(
                exercise.id,
                expect.objectContaining({
                    actionType: ChecklistActionRequest.ActionTypeEnum.AdaptDifficulty,
                    context: expect.objectContaining({ targetDifficulty: 'HARD' }),
                }),
            );
        });

        it('should emphasize a competency', () => {
            const actionSpy = jest.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            component.emphasizeCompetency('Loops', 'APPLY');

            expect(actionSpy).toHaveBeenCalledWith(
                exercise.id,
                expect.objectContaining({
                    actionType: ChecklistActionRequest.ActionTypeEnum.EmphasizeCompetency,
                    context: expect.objectContaining({ competencyTitle: 'Loops', taxonomyLevel: 'APPLY' }),
                }),
            );
        });

        it('should deemphasize a competency', () => {
            const actionSpy = jest.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            component.deemphasizeCompetency('Loops');

            expect(actionSpy).toHaveBeenCalledWith(
                exercise.id,
                expect.objectContaining({
                    actionType: ChecklistActionRequest.ActionTypeEnum.DeemphasizeCompetency,
                    context: expect.objectContaining({ competencyTitle: 'Loops' }),
                }),
            );
        });

        it('should handle action error', () => {
            jest.spyOn(apiService, 'applyChecklistAction').mockReturnValue(throwError(() => new Error('Failed')) as any);
            const errorSpy = jest.spyOn(alertService, 'error');

            component.fixQualityIssue({ description: 'Test', category: 'CLARITY' }, 0);

            expect(errorSpy).toHaveBeenCalled();
            expect(component.isApplyingAction()).toBeFalse();
            expect(component.actionLoadingKey()).toBeUndefined();
        });

        it('should not apply action when already applying', () => {
            component.isApplyingAction.set(true);
            const actionSpy = jest.spyOn(apiService, 'applyChecklistAction');

            component.fixQualityIssue({ description: 'Test', category: 'CLARITY' }, 0);

            expect(actionSpy).not.toHaveBeenCalled();
        });

        it('should show warning when action produces no changes', () => {
            const noChangeResponse: ChecklistActionResponse = {
                updatedProblemStatement: 'Problem statement',
                applied: false,
            };
            jest.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(noChangeResponse) as any);
            const warningSpy = jest.spyOn(alertService, 'warning');

            component.fixQualityIssue({ description: 'Test', category: 'CLARITY' }, 0);

            expect(warningSpy).toHaveBeenCalled();
        });
    });
});
