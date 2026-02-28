import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChecklistPanelComponent, ChecklistSectionType } from './checklist-panel.component';
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
import { QualityIssue } from 'app/openapi/model/qualityIssue';
import { By } from '@angular/platform-browser';

describe('ChecklistPanelComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ChecklistPanelComponent;
    let fixture: ComponentFixture<ChecklistPanelComponent>;
    let apiService: HyperionProblemStatementApiService;
    let alertService: AlertService;

    const courseId = 42;

    const mockResponse: ChecklistAnalysisResponse = {
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

        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 123;
        exercise.problemStatement = 'Problem statement';

        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('problemStatement', 'Problem statement');
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call analyzeChecklist on button click', () => {
        const analyzeSpy = vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);

        // Find analyze button by data-testid
        const button = fixture.debugElement.query(By.css('[data-testid="analyze-button"]'));
        expect(button).toBeTruthy();
        button.nativeElement.click();

        expect(analyzeSpy).toHaveBeenCalledWith(courseId, expect.objectContaining({ problemStatementMarkdown: 'Problem statement' }));

        // Wait for observable
        fixture.detectChanges();

        expect(component.isLoading()).toBeFalsy();
        expect(component.analysisResult()).toEqual(mockResponse);
        expect(component.analysisResult()).toBeDefined();
    });

    it('should handle analysis error', () => {
        vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(throwError(() => new Error('Error')) as any);
        const errorSpy = vi.spyOn(alertService, 'error');

        component.analyze();

        expect(component.isLoading()).toBeFalsy();
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

        it('should fix a single quality issue and remove it optimistically', () => {
            const issueToFix: QualityIssue = {
                description: 'Unclear wording',
                suggestedFix: 'Reword it',
                category: QualityIssue.CategoryEnum.Clarity,
                severity: QualityIssue.SeverityEnum.Medium,
            };
            const otherIssue: QualityIssue = { description: 'Missing info', category: QualityIssue.CategoryEnum.Completeness, severity: QualityIssue.SeverityEnum.High };
            component.analysisResult.set(Object.assign({}, mockResponse, { qualityIssues: [issueToFix, otherIssue] }));

            const actionSpy = vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);
            const emitSpy = vi.spyOn(component.problemStatementDiffRequest, 'emit');

            component.fixQualityIssue(issueToFix, 0);

            expect(actionSpy).toHaveBeenCalledWith(
                courseId,
                expect.objectContaining({
                    actionType: ChecklistActionRequest.ActionTypeEnum.FixQualityIssue,
                    problemStatementMarkdown: 'Problem statement',
                }),
            );
            expect(emitSpy).toHaveBeenCalledWith('Updated problem statement');
            expect(component.isApplyingAction()).toBeFalsy();
            expect(component.analysisResult()?.qualityIssues).toHaveLength(1);
            expect(component.analysisResult()?.qualityIssues?.[0]).toEqual(otherIssue);
        });

        it('should fix all quality issues and clear them optimistically', () => {
            component.analysisResult.set(
                Object.assign({}, mockResponse, {
                    qualityIssues: [
                        { description: 'Issue 1', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Medium },
                        { description: 'Issue 2', category: QualityIssue.CategoryEnum.Completeness, severity: QualityIssue.SeverityEnum.High },
                    ],
                }),
            );
            const actionSpy = vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);

            component.fixAllQualityIssues();

            expect(actionSpy).toHaveBeenCalledWith(
                courseId,
                expect.objectContaining({
                    actionType: ChecklistActionRequest.ActionTypeEnum.FixAllQualityIssues,
                }),
            );
            expect(component.analysisResult()?.qualityIssues).toEqual([]);
        });

        it('should handle action error', () => {
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(throwError(() => new Error('Failed')) as any);
            const errorSpy = vi.spyOn(alertService, 'error');

            component.fixQualityIssue({ description: 'Test', category: QualityIssue.CategoryEnum.Clarity }, 0);

            expect(errorSpy).toHaveBeenCalled();
            expect(component.isApplyingAction()).toBeFalsy();
            expect(component.actionLoadingKey()).toBeUndefined();
        });

        it('should not apply action when already applying', () => {
            component.isApplyingAction.set(true);
            const actionSpy = vi.spyOn(apiService, 'applyChecklistAction');

            component.fixQualityIssue({ description: 'Test', category: QualityIssue.CategoryEnum.Clarity }, 0);

            expect(actionSpy).not.toHaveBeenCalled();
        });

        it('should show warning when action produces no changes', () => {
            const noChangeResponse: ChecklistActionResponse = {
                updatedProblemStatement: 'Problem statement',
                applied: false,
            };
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(noChangeResponse) as any);
            const warningSpy = vi.spyOn(alertService, 'warning');

            component.fixQualityIssue({ description: 'Test', category: QualityIssue.CategoryEnum.Clarity }, 0);

            expect(warningSpy).toHaveBeenCalled();
        });
    });

    describe('Stale Section Tracking', () => {
        const mockActionResponse: ChecklistActionResponse = {
            updatedProblemStatement: 'Updated problem statement',
            applied: true,
            summary: 'Applied',
        };

        it('should mark quality section stale after fixing a quality issue (no other sections in quality PR)', () => {
            component.analysisResult.set(
                Object.assign({}, mockResponse, {
                    qualityIssues: [{ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }],
                }),
            );
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            component.fixQualityIssue({ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }, 0);

            // In quality-only PR, no other sections exist to mark stale
            expect(component.isSectionStale('quality')).toBeFalsy();
        });

        it('should not call analyzeChecklist after a successful action', () => {
            component.analysisResult.set(
                Object.assign({}, mockResponse, {
                    qualityIssues: [{ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }],
                }),
            );
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            const analyzeSpy = vi.spyOn(apiService, 'analyzeChecklist');

            component.fixQualityIssue({ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }, 0);

            expect(analyzeSpy).not.toHaveBeenCalled();
        });

        it('should clear all stale sections when full analyze is called', () => {
            component.staleSections.set(new Set<ChecklistSectionType>(['quality']));
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);

            component.analyze();

            expect(component.staleSections().size).toBe(0);
        });

        it('should not accumulate stale sections from quality fixes (no other sections)', () => {
            component.analysisResult.set(
                Object.assign({}, mockResponse, {
                    qualityIssues: [{ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }],
                }),
            );
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            // Fix quality → no sections should be marked stale (quality-only PR)
            component.fixQualityIssue({ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }, 0);

            expect(component.staleSections().size).toBe(0);
        });
    });

    describe('Per-Section Re-analyze', () => {
        const fullResponse: ChecklistAnalysisResponse = {
            qualityIssues: [{ description: 'New issue', category: QualityIssue.CategoryEnum.Coherence, severity: QualityIssue.SeverityEnum.High }],
        };

        it('should update only the quality section when reanalyzing quality', () => {
            component.analysisResult.set(mockResponse);
            component.staleSections.set(new Set<ChecklistSectionType>(['quality']));
            vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(of(fullResponse) as any);

            component.reanalyzeSection('quality');

            // Quality updated from fullResponse
            expect(component.analysisResult()?.qualityIssues).toEqual(fullResponse.qualityIssues);
            // Stale cleared for quality
            expect(component.isSectionStale('quality')).toBeFalsy();
            expect(component.sectionLoading().size).toBe(0);
        });

        it('should handle re-analyze error gracefully', () => {
            component.analysisResult.set(mockResponse);
            vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(throwError(() => new Error('Failed')) as any);
            const errorSpy = vi.spyOn(alertService, 'error');

            component.reanalyzeSection('quality');

            expect(errorSpy).toHaveBeenCalled();
            expect(component.sectionLoading().size).toBe(0);
            // Original result preserved
            expect(component.analysisResult()).toEqual(mockResponse);
        });

        it('should not start re-analyze when the same section is already loading', () => {
            component.sectionLoading.set(new Set<ChecklistSectionType>(['quality']));
            const analyzeSpy = vi.spyOn(apiService, 'analyzeChecklistSection');

            component.reanalyzeSection('quality');

            expect(analyzeSpy).not.toHaveBeenCalled();
        });

        it('should set sectionLoading while re-analyzing', () => {
            component.analysisResult.set(mockResponse);
            vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(of(fullResponse) as any);

            expect(component.sectionLoading().size).toBe(0);
            component.reanalyzeSection('quality');
            // After completion, loading is cleared
            expect(component.sectionLoading().size).toBe(0);
        });

        it('should call analyzeChecklistSection with correct section parameter', () => {
            component.analysisResult.set(mockResponse);
            const sectionSpy = vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(of(fullResponse) as any);

            component.reanalyzeSection('quality');

            expect(sectionSpy).toHaveBeenCalledWith(courseId, 'QUALITY', expect.objectContaining({ problemStatementMarkdown: 'Problem statement' }));
        });
    });

    describe('Discard & Multi-Select', () => {
        const issue1: QualityIssue = { description: 'Issue 1', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low };
        const issue2: QualityIssue = { description: 'Issue 2', category: QualityIssue.CategoryEnum.Completeness, severity: QualityIssue.SeverityEnum.Medium };
        const issue3: QualityIssue = { description: 'Issue 3', category: QualityIssue.CategoryEnum.Coherence, severity: QualityIssue.SeverityEnum.High };

        beforeEach(() => {
            component.analysisResult.set(Object.assign({}, mockResponse, { qualityIssues: [issue1, issue2, issue3] }));
        });

        it('should discard a single quality issue and show success alert', () => {
            const successSpy = vi.spyOn(alertService, 'success');

            component.discardQualityIssue(1);

            expect(component.analysisResult()?.qualityIssues).toHaveLength(2);
            expect(component.analysisResult()?.qualityIssues?.[0]).toEqual(issue1);
            expect(component.analysisResult()?.qualityIssues?.[1]).toEqual(issue3);
            expect(successSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.instructorChecklist.quality.discarded');
        });

        it('should reindex selected indices after discarding an issue', () => {
            component.selectedIssueIndices.set(new Set([0, 2]));

            component.discardQualityIssue(1);

            // Index 0 stays, index 2 becomes 1 (shifted down)
            expect(component.selectedIssueIndices()).toEqual(new Set([0, 1]));
        });

        it('should remove discarded index from selection', () => {
            component.selectedIssueIndices.set(new Set([0, 1, 2]));

            component.discardQualityIssue(1);

            // Index 1 removed, index 2 shifted to 1
            expect(component.selectedIssueIndices()).toEqual(new Set([0, 1]));
        });

        it('should toggle issue selection on and off', () => {
            expect(component.isIssueSelected(0)).toBeFalsy();

            component.toggleIssueSelection(0);
            expect(component.isIssueSelected(0)).toBeTruthy();

            component.toggleIssueSelection(0);
            expect(component.isIssueSelected(0)).toBeFalsy();
        });

        it('should select all issues', () => {
            component.selectAllIssues();

            expect(component.selectedIssueIndices()).toEqual(new Set([0, 1, 2]));
            expect(component.allIssuesSelected()).toBeTruthy();
        });

        it('should deselect all issues', () => {
            component.selectAllIssues();
            component.deselectAllIssues();

            expect(component.selectedIssueIndices().size).toBe(0);
            expect(component.allIssuesSelected()).toBeFalsy();
        });

        it('should return false for allIssuesSelected when no issues exist', () => {
            component.analysisResult.set(Object.assign({}, mockResponse, { qualityIssues: [] }));
            expect(component.allIssuesSelected()).toBeFalsy();
        });

        it('should discard all selected issues and clear selection', () => {
            const successSpy = vi.spyOn(alertService, 'success');
            component.selectedIssueIndices.set(new Set([0, 2]));

            component.discardSelectedIssues();

            expect(component.analysisResult()?.qualityIssues).toHaveLength(1);
            expect(component.analysisResult()?.qualityIssues?.[0]).toEqual(issue2);
            expect(component.selectedIssueIndices().size).toBe(0);
            expect(successSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.instructorChecklist.quality.discardedMultiple');
        });

        it('should not discard when no issues are selected', () => {
            const successSpy = vi.spyOn(alertService, 'success');

            component.discardSelectedIssues();

            expect(component.analysisResult()?.qualityIssues).toHaveLength(3);
            expect(successSpy).not.toHaveBeenCalled();
        });

        it('should fix selected issues via AI and remove them optimistically', () => {
            const mockActionResponse: ChecklistActionResponse = {
                updatedProblemStatement: 'Updated',
                applied: true,
                summary: 'Fixed selected',
            };
            const actionSpy = vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            const emitSpy = vi.spyOn(component.problemStatementDiffRequest, 'emit');
            component.selectedIssueIndices.set(new Set([0, 2]));

            component.fixSelectedIssues();

            expect(actionSpy).toHaveBeenCalledWith(
                courseId,
                expect.objectContaining({
                    actionType: ChecklistActionRequest.ActionTypeEnum.FixAllQualityIssues,
                }),
            );
            expect(emitSpy).toHaveBeenCalledWith('Updated');
            // Only issue2 (index 1) should remain
            expect(component.analysisResult()?.qualityIssues).toHaveLength(1);
            expect(component.analysisResult()?.qualityIssues?.[0]).toEqual(issue2);
            expect(component.selectedIssueIndices().size).toBe(0);
        });

        it('should not fix selected when selection is empty', () => {
            const actionSpy = vi.spyOn(apiService, 'applyChecklistAction');

            component.fixSelectedIssues();

            expect(actionSpy).not.toHaveBeenCalled();
        });

        it('should clear selected indices when fixAllQualityIssues is called', () => {
            const mockActionResponse: ChecklistActionResponse = { updatedProblemStatement: 'Updated', applied: true };
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            component.selectedIssueIndices.set(new Set([0, 1]));

            component.fixAllQualityIssues();

            expect(component.selectedIssueIndices().size).toBe(0);
        });
    });

    describe('Problem Statement Change Invalidation', () => {
        it('should mark all sections stale when problem statement changes externally', () => {
            component.analysisResult.set(mockResponse);
            fixture.detectChanges();

            fixture.componentRef.setInput('problemStatement', 'Changed problem statement');
            fixture.detectChanges();

            expect(component.isSectionStale('quality')).toBeTruthy();
        });

        it('should not mark sections stale when no analysis result exists', () => {
            // analysisResult is undefined by default
            expect(component.analysisResult()).toBeUndefined();

            fixture.componentRef.setInput('problemStatement', 'Changed problem statement');
            fixture.detectChanges();

            expect(component.staleSections().size).toBe(0);
        });

        it('should not mark sections stale when problem statement stays the same', () => {
            component.analysisResult.set(mockResponse);
            fixture.detectChanges();

            fixture.componentRef.setInput('problemStatement', 'Problem statement');
            fixture.detectChanges();

            expect(component.staleSections().size).toBe(0);
        });
    });
});
