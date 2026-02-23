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
import { DifficultyAssessment } from 'app/openapi/model/difficultyAssessment';
import { By } from '@angular/platform-browser';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { HttpResponse } from '@angular/common/http';
import {
    Competency,
    CompetencyExerciseLink,
    CompetencyTaxonomy,
    CourseCompetency,
    HIGH_COMPETENCY_LINK_WEIGHT,
    LOW_COMPETENCY_LINK_WEIGHT,
    MEDIUM_COMPETENCY_LINK_WEIGHT,
} from 'app/atlas/shared/entities/competency.model';

describe('ChecklistPanelComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ChecklistPanelComponent;
    let fixture: ComponentFixture<ChecklistPanelComponent>;
    let apiService: HyperionProblemStatementApiService;
    let alertService: AlertService;
    let competencyService: CompetencyService;

    const courseId = 42;
    const exercise = new ProgrammingExercise(undefined, undefined);
    exercise.id = 123;
    exercise.problemStatement = 'Problem statement';
    exercise.difficulty = DifficultyLevel.EASY; // Changed 'EASY' to DifficultyLevel.EASY for consistency with mockExercise

    const mockResponse: ChecklistAnalysisResponse = {
        inferredCompetencies: [
            {
                competencyTitle: 'Loops',
                taxonomyLevel: 'APPLY',
                confidence: 0.9,
                whyThisMatches: 'Explanation',
                relatedTaskNames: ['Implement loop'],
            },
        ],
        difficultyAssessment: { suggested: DifficultyAssessment.SuggestedEnum.Easy, reasoning: 'Reason', matchesDeclared: true, taskCount: 5, testCount: 10 },
        qualityIssues: [],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, ChecklistPanelComponent],
            providers: [MockProvider(HyperionProblemStatementApiService), MockProvider(AlertService), MockProvider(TranslateService), MockProvider(CompetencyService)],
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
        competencyService = TestBed.inject(CompetencyService);

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
            component.analysisResult.set({ ...mockResponse, qualityIssues: [issueToFix, otherIssue] });

            const actionSpy = vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);
            const emitSpy = vi.spyOn(component.problemStatementChange, 'emit');

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
            component.analysisResult.set({
                ...mockResponse,
                qualityIssues: [
                    { description: 'Issue 1', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Medium },
                    { description: 'Issue 2', category: QualityIssue.CategoryEnum.Completeness, severity: QualityIssue.SeverityEnum.High },
                ],
            });
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

        it('should adapt difficulty and update assessment optimistically', () => {
            component.analysisResult.set(mockResponse);
            const actionSpy = vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);
            const difficultyEmitSpy = vi.spyOn(component.difficultyChange, 'emit');

            component.adaptDifficulty('HARD');

            expect(actionSpy).toHaveBeenCalledWith(
                courseId,
                expect.objectContaining({
                    actionType: ChecklistActionRequest.ActionTypeEnum.AdaptDifficulty,
                    context: expect.objectContaining({ targetDifficulty: 'HARD' }),
                }),
            );
            expect(component.analysisResult()?.difficultyAssessment?.suggested).toBe('HARD');
            expect(component.analysisResult()?.difficultyAssessment?.delta).toBe('MATCH');
            expect(difficultyEmitSpy).toHaveBeenCalledWith('HARD');
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

    describe('Apply Competencies (unified link + create)', () => {
        const mockCourseCompetency: CourseCompetency = Object.assign(new Competency(), {
            id: 1,
            title: 'Loops',
            taxonomy: CompetencyTaxonomy.APPLY,
        });

        const mockResponseWithCompetencies: ChecklistAnalysisResponse = {
            inferredCompetencies: [
                {
                    competencyTitle: 'Loops',
                    taxonomyLevel: 'APPLY',
                    confidence: 0.9,
                    whyThisMatches: 'Uses loop constructs',
                    rank: 1,
                    matchedCourseCompetencyId: 1,
                    isLikelyPrimary: true,
                },
                {
                    competencyTitle: 'Recursion',
                    taxonomyLevel: 'ANALYZE',
                    confidence: 0.7,
                    whyThisMatches: 'Recursive patterns',
                    rank: 2,
                },
            ],
            difficultyAssessment: { suggested: 'EASY', reasoning: 'Reason', matchesDeclared: true, taskCount: 5, testCount: 10 },
            qualityIssues: [],
        };

        it('should link matched competencies and create unmatched ones in a single call', async () => {
            component.analysisResult.set(mockResponseWithCompetencies);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [mockCourseCompetency] })) as any);

            const createdCompetency = Object.assign(new Competency(), { id: 99, title: 'Recursion', taxonomy: CompetencyTaxonomy.ANALYZE });
            vi.spyOn(competencyService, 'create').mockReturnValue(of(new HttpResponse({ body: createdCompetency })) as any);

            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');
            const successSpy = vi.spyOn(alertService, 'success');

            component.applyCompetencies();

            // Flush microtasks from forkJoin
            await new Promise<void>((resolve) => setTimeout(resolve));

            expect(competencyService.getAllForCourse).toHaveBeenCalledWith(courseId);
            // 'Recursion' should be created (not matched by AI)
            expect(competencyService.create).toHaveBeenCalledWith(expect.objectContaining({ title: 'Recursion' }), courseId);
            expect(emitSpy).toHaveBeenCalled();
            const emittedLinks = emitSpy.mock.calls[0][0] as CompetencyExerciseLink[];
            // Both 'Loops' (linked) and 'Recursion' (created) should be in the emitted links
            expect(emittedLinks).toHaveLength(2);
            expect(emittedLinks.map((l) => l.competency?.title).sort()).toEqual(['Loops', 'Recursion']);
            // Loops is primary (rank 1, confidence 0.9) → HIGH weight; Recursion is non-primary, confidence 0.7 → MEDIUM weight
            const loopsLink = emittedLinks.find((l) => l.competency?.title === 'Loops');
            const recursionLink = emittedLinks.find((l) => l.competency?.title === 'Recursion');
            expect(loopsLink?.weight).toBe(HIGH_COMPETENCY_LINK_WEIGHT);
            expect(recursionLink?.weight).toBe(MEDIUM_COMPETENCY_LINK_WEIGHT);
            expect(successSpy).toHaveBeenCalled();
            expect(component.isSyncingCompetencies()).toBeFalsy();
        });

        it('should create all competencies when none match existing ones', async () => {
            const responseNoMatches: ChecklistAnalysisResponse = {
                inferredCompetencies: [
                    {
                        competencyTitle: 'Recursion',
                        taxonomyLevel: 'ANALYZE',
                        confidence: 0.7,
                        whyThisMatches: 'Recursive patterns',
                        rank: 1,
                    },
                ],
                difficultyAssessment: { suggested: 'EASY', reasoning: 'Reason', matchesDeclared: true },
                qualityIssues: [],
            };
            component.analysisResult.set(responseNoMatches);
            // No course competency matches 'Recursion'
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [mockCourseCompetency] })) as any);
            const createdCompetency = Object.assign(new Competency(), { id: 99, title: 'Recursion', taxonomy: CompetencyTaxonomy.ANALYZE });
            vi.spyOn(competencyService, 'create').mockReturnValue(of(new HttpResponse({ body: createdCompetency })) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');

            component.applyCompetencies();

            await new Promise<void>((resolve) => setTimeout(resolve));

            expect(competencyService.create).toHaveBeenCalledWith(expect.objectContaining({ title: 'Recursion' }), courseId);
            expect(emitSpy).toHaveBeenCalled();
            expect(component.isSyncingCompetencies()).toBeFalsy();
        });

        it('should assign LOW relevance weight to low-confidence non-primary competencies', async () => {
            const lowConfidenceResponse: ChecklistAnalysisResponse = {
                inferredCompetencies: [
                    {
                        competencyTitle: 'Loops',
                        taxonomyLevel: 'APPLY',
                        confidence: 0.5,
                        whyThisMatches: 'Minor usage',
                        rank: 1,
                        matchedCourseCompetencyId: 1,
                        isLikelyPrimary: false,
                    },
                ],
                difficultyAssessment: { suggested: 'EASY', reasoning: 'Reason', matchesDeclared: true },
                qualityIssues: [],
            };
            component.analysisResult.set(lowConfidenceResponse);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [mockCourseCompetency] })) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');

            component.applyCompetencies();

            await new Promise<void>((resolve) => setTimeout(resolve));

            expect(emitSpy).toHaveBeenCalled();
            const emittedLinks = emitSpy.mock.calls[0][0] as CompetencyExerciseLink[];
            const loopsLink = emittedLinks.find((l) => l.competency?.title === 'Loops');
            expect(loopsLink?.weight).toBe(LOW_COMPETENCY_LINK_WEIGHT);
        });

        it('should show warning when all competencies are already linked', () => {
            const existingLink = new CompetencyExerciseLink(mockCourseCompetency, component.exercise(), 1);
            fixture.componentRef.setInput('exercise', Object.assign(new ProgrammingExercise(undefined, undefined), { id: 1, competencyLinks: [existingLink] }));

            const allMatchedResponse: ChecklistAnalysisResponse = {
                inferredCompetencies: [
                    {
                        competencyTitle: 'Loops',
                        taxonomyLevel: 'APPLY',
                        confidence: 0.9,
                        rank: 1,
                        matchedCourseCompetencyId: 1,
                    },
                ],
                difficultyAssessment: { suggested: 'EASY', reasoning: 'Reason', matchesDeclared: true },
                qualityIssues: [],
            };
            component.analysisResult.set(allMatchedResponse);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [mockCourseCompetency] })) as any);
            const warningSpy = vi.spyOn(alertService, 'warning');

            component.applyCompetencies();

            expect(warningSpy).toHaveBeenCalled();
            expect(component.isSyncingCompetencies()).toBeFalsy();
        });

        it('should handle error when loading course competencies fails', () => {
            component.analysisResult.set(mockResponseWithCompetencies);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => new Error('Failed')) as any);
            const errorSpy = vi.spyOn(alertService, 'error');

            component.applyCompetencies();

            expect(errorSpy).toHaveBeenCalled();
            expect(component.isSyncingCompetencies()).toBeFalsy();
        });

        it('should not run when already syncing', () => {
            component.isSyncingCompetencies.set(true);
            const getAllSpy = vi.spyOn(competencyService, 'getAllForCourse');

            component.applyCompetencies();

            expect(getAllSpy).not.toHaveBeenCalled();
        });

        it('should correctly identify linked competencies', () => {
            component.linkedCompetencyTitles.set(new Set(['loops']));
            component.createdCompetencyTitles.set(new Set(['recursion']));

            expect(component.isCompetencyLinked({ competencyTitle: 'Loops' })).toBeTruthy();
            expect(component.isCompetencyLinked({ competencyTitle: 'Recursion' })).toBeFalsy();
        });
    });

    describe('Local Task/Test Counting', () => {
        it('should count tasks and tests from problem statement with [task] markers', () => {
            fixture.componentRef.setInput(
                'problemStatement',
                '1. [task][Implement BubbleSort](testBubbleSort,testBubbleSortReverse) \n 2. [task][Implement MergeSort](testMergeSort)',
            );
            fixture.detectChanges();

            const counts = component.localTaskTestCounts();
            expect(counts.tasks).toBe(2);
            expect(counts.tests).toBe(3);
        });

        it('should return zero counts for problem statements without tasks', () => {
            fixture.componentRef.setInput('problemStatement', 'A simple problem statement with no tasks.');
            fixture.detectChanges();

            const counts = component.localTaskTestCounts();
            expect(counts.tasks).toBe(0);
            expect(counts.tests).toBe(0);
        });

        it('should count unique tests across tasks', () => {
            fixture.componentRef.setInput('problemStatement', '[task][Task A](test1,test2) \n [task][Task B](test2,test3) \n [task][Task C](test1)');
            fixture.detectChanges();

            const counts = component.localTaskTestCounts();
            expect(counts.tasks).toBe(3);
            expect(counts.tests).toBe(3); // test1, test2, test3 are unique
        });

        it('should update counts when problem statement changes via applyAction', () => {
            fixture.componentRef.setInput('problemStatement', '[task][Task A](test1)');
            fixture.detectChanges();

            expect(component.localTaskTestCounts().tasks).toBe(1);
            expect(component.localTaskTestCounts().tests).toBe(1);

            // Simulate applyAction success which updates latestProblemStatement
            const updatedPS = '[task][Task A](test1) \n [task][Task B](test2,test3)';
            const mockActionResponse = { updatedProblemStatement: updatedPS, applied: true };
            component.analysisResult.set(mockResponse);
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);

            component.adaptDifficulty('HARD');

            // Counts should reflect the updated problem statement
            expect(component.localTaskTestCounts().tasks).toBe(2);
            expect(component.localTaskTestCounts().tests).toBe(3);
        });
    });

    describe('Stale Section Tracking', () => {
        const mockActionResponse: ChecklistActionResponse = {
            updatedProblemStatement: 'Updated problem statement',
            applied: true,
            summary: 'Applied',
        };

        it('should mark competencies and difficulty as stale after fixing a quality issue', () => {
            component.analysisResult.set({
                ...mockResponse,
                qualityIssues: [{ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }],
            });
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            component.fixQualityIssue({ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }, 0);

            expect(component.isSectionStale('competencies')).toBeTruthy();
            expect(component.isSectionStale('difficulty')).toBeTruthy();
            expect(component.isSectionStale('quality')).toBeFalsy();
        });

        it('should mark quality and competencies as stale after adapting difficulty', () => {
            component.analysisResult.set(mockResponse);
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            component.adaptDifficulty('HARD');

            expect(component.isSectionStale('quality')).toBeTruthy();
            expect(component.isSectionStale('competencies')).toBeTruthy();
            expect(component.isSectionStale('difficulty')).toBeFalsy();
        });

        it('should not call analyzeChecklist after a successful action', () => {
            component.analysisResult.set(mockResponse);
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);
            const analyzeSpy = vi.spyOn(apiService, 'analyzeChecklist');

            component.adaptDifficulty('HARD');

            expect(analyzeSpy).not.toHaveBeenCalled();
        });

        it('should clear all stale sections when full analyze is called', () => {
            component.staleSections.set(new Set(['quality', 'competencies', 'difficulty']));
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);

            component.analyze();

            expect(component.staleSections().size).toBe(0);
        });

        it('should clear linked and created competency titles when full analyze completes', () => {
            component.linkedCompetencyTitles.set(new Set(['loops']));
            component.createdCompetencyTitles.set(new Set(['recursion']));
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(mockResponse) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');

            component.analyze();

            expect(component.linkedCompetencyTitles().size).toBe(0);
            expect(component.createdCompetencyTitles().size).toBe(0);
            expect(emitSpy).toHaveBeenCalledWith([]);
        });

        it('should accumulate stale sections across multiple actions', () => {
            component.analysisResult.set({
                ...mockResponse,
                qualityIssues: [{ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }],
            });
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            // First: fix quality → competencies + difficulty stale
            component.fixQualityIssue({ description: 'Issue', category: QualityIssue.CategoryEnum.Clarity, severity: QualityIssue.SeverityEnum.Low }, 0);

            expect(component.isSectionStale('competencies')).toBeTruthy();
            expect(component.isSectionStale('difficulty')).toBeTruthy();

            // Reset applying state for next action
            component.isApplyingAction.set(false);
            component.analysisResult.set(mockResponse);

            // Second: adapt difficulty → quality + competencies stale (quality was not stale, now it is)
            component.adaptDifficulty('HARD');

            expect(component.isSectionStale('quality')).toBeTruthy();
            expect(component.isSectionStale('competencies')).toBeTruthy();
            expect(component.isSectionStale('difficulty')).toBeTruthy();
        });
    });

    describe('Per-Section Re-analyze', () => {
        const fullResponse: ChecklistAnalysisResponse = {
            qualityIssues: [{ description: 'New issue', category: QualityIssue.CategoryEnum.Coherence, severity: QualityIssue.SeverityEnum.High }],
            inferredCompetencies: [{ competencyTitle: 'New Comp', taxonomyLevel: 'UNDERSTAND', rank: 1 }],
            difficultyAssessment: { suggested: DifficultyAssessment.SuggestedEnum.Hard, reasoning: 'Updated', matchesDeclared: false, taskCount: 3, testCount: 6 },
        };

        it('should update only the quality section when reanalyzing quality', () => {
            component.analysisResult.set(mockResponse);
            component.staleSections.set(new Set(['quality']));
            vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(of(fullResponse) as any);

            component.reanalyzeSection('quality');

            // Quality updated from fullResponse
            expect(component.analysisResult()?.qualityIssues).toEqual(fullResponse.qualityIssues);
            // Competencies and difficulty remain from mockResponse
            expect(component.analysisResult()?.inferredCompetencies).toEqual(mockResponse.inferredCompetencies);
            expect(component.analysisResult()?.difficultyAssessment).toEqual(mockResponse.difficultyAssessment);
            // Stale cleared for quality
            expect(component.isSectionStale('quality')).toBeFalsy();
            expect(component.sectionLoading().size).toBe(0);
        });

        it('should update only the competencies section when reanalyzing competencies', () => {
            component.analysisResult.set(mockResponse);
            component.staleSections.set(new Set(['competencies']));
            vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(of(fullResponse) as any);

            component.reanalyzeSection('competencies');

            expect(component.analysisResult()?.inferredCompetencies).toEqual(fullResponse.inferredCompetencies);
            expect(component.analysisResult()?.qualityIssues).toEqual(mockResponse.qualityIssues);
            expect(component.isSectionStale('competencies')).toBeFalsy();
        });

        it('should clear linked and created competency titles when competencies section is reanalyzed', () => {
            component.analysisResult.set(mockResponse);
            component.linkedCompetencyTitles.set(new Set(['loops']));
            component.createdCompetencyTitles.set(new Set(['recursion']));
            vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(of(fullResponse) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');

            component.reanalyzeSection('competencies');

            expect(component.linkedCompetencyTitles().size).toBe(0);
            expect(component.createdCompetencyTitles().size).toBe(0);
            expect(emitSpy).toHaveBeenCalledWith([]);
        });

        it('should NOT emit competencyLinksChange when non-competencies section is reanalyzed', () => {
            component.analysisResult.set(mockResponse);
            component.linkedCompetencyTitles.set(new Set(['loops']));
            vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(of(fullResponse) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');

            component.reanalyzeSection('quality');

            expect(emitSpy).not.toHaveBeenCalled();
        });

        it('should update only the difficulty section when reanalyzing difficulty', () => {
            component.analysisResult.set(mockResponse);
            component.staleSections.set(new Set(['difficulty']));
            vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(of(fullResponse) as any);

            component.reanalyzeSection('difficulty');

            expect(component.analysisResult()?.difficultyAssessment).toEqual(fullResponse.difficultyAssessment);
            expect(component.analysisResult()?.qualityIssues).toEqual(mockResponse.qualityIssues);
            expect(component.isSectionStale('difficulty')).toBeFalsy();
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

        it('should allow parallel re-analyze of different sections', () => {
            component.analysisResult.set(mockResponse);
            component.sectionLoading.set(new Set<ChecklistSectionType>(['quality']));
            const analyzeSpy = vi.spyOn(apiService, 'analyzeChecklistSection').mockReturnValue(of(fullResponse) as any);

            component.reanalyzeSection('difficulty');

            expect(analyzeSpy).toHaveBeenCalledWith(courseId, 'DIFFICULTY', expect.objectContaining({ problemStatementMarkdown: 'Problem statement' }));
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
});
