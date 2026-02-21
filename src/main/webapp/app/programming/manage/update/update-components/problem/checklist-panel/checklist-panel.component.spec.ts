import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { HttpResponse } from '@angular/common/http';
import { Competency, CompetencyExerciseLink, CompetencyTaxonomy, CourseCompetency } from 'app/atlas/shared/entities/competency.model';

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
        difficultyAssessment: { suggested: 'EASY', reasoning: 'Reason', matchesDeclared: true, taskCount: 5, testCount: 10 },
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

        // Find analyze button
        const button = fixture.debugElement.query(By.css('button'));
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
            const issueToFix = { description: 'Unclear wording', suggestedFix: 'Reword it', category: 'CLARITY', severity: 'MEDIUM' };
            const otherIssue = { description: 'Missing info', category: 'COMPLETENESS', severity: 'HIGH' };
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
                    { description: 'Issue 1', category: 'CLARITY', severity: 'MEDIUM' },
                    { description: 'Issue 2', category: 'COMPLETENESS', severity: 'HIGH' },
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

            component.fixQualityIssue({ description: 'Test', category: 'CLARITY' }, 0);

            expect(errorSpy).toHaveBeenCalled();
            expect(component.isApplyingAction()).toBeFalsy();
            expect(component.actionLoadingKey()).toBeUndefined();
        });

        it('should not apply action when already applying', () => {
            component.isApplyingAction.set(true);
            const actionSpy = vi.spyOn(apiService, 'applyChecklistAction');

            component.fixQualityIssue({ description: 'Test', category: 'CLARITY' }, 0);

            expect(actionSpy).not.toHaveBeenCalled();
        });

        it('should show warning when action produces no changes', () => {
            const noChangeResponse: ChecklistActionResponse = {
                updatedProblemStatement: 'Problem statement',
                applied: false,
            };
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(noChangeResponse) as any);
            const warningSpy = vi.spyOn(alertService, 'warning');

            component.fixQualityIssue({ description: 'Test', category: 'CLARITY' }, 0);

            expect(warningSpy).toHaveBeenCalled();
        });
    });

    describe('Competency Linking Methods', () => {
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

        it('should link matching competencies and emit changes', () => {
            component.analysisResult.set(mockResponseWithCompetencies);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [mockCourseCompetency] })) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');
            const successSpy = vi.spyOn(alertService, 'success');

            component.linkMatchingCompetencies();

            expect(competencyService.getAllForCourse).toHaveBeenCalledWith(courseId);
            expect(emitSpy).toHaveBeenCalled();
            const emittedLinks = emitSpy.mock.calls[0][0] as CompetencyExerciseLink[];
            expect(emittedLinks).toHaveLength(1);
            expect(emittedLinks[0].competency?.id).toBe(1);
            expect(successSpy).toHaveBeenCalled();
            expect(component.isLinkingCompetencies()).toBeFalsy();
        });

        it('should show warning when no matching competencies found', () => {
            component.analysisResult.set(mockResponseWithCompetencies);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [] })) as any);
            const warningSpy = vi.spyOn(alertService, 'warning');

            component.linkMatchingCompetencies();

            expect(warningSpy).toHaveBeenCalled();
            expect(component.isLinkingCompetencies()).toBeFalsy();
        });

        it('should handle error when linking competencies', () => {
            component.analysisResult.set(mockResponseWithCompetencies);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(throwError(() => new Error('Failed')) as any);
            const errorSpy = vi.spyOn(alertService, 'error');

            component.linkMatchingCompetencies();

            expect(errorSpy).toHaveBeenCalled();
            expect(component.isLinkingCompetencies()).toBeFalsy();
        });

        it('should create and link new competencies', () => {
            component.analysisResult.set(mockResponseWithCompetencies);
            // Only 'Loops' exists, so 'Recursion' should be created
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [mockCourseCompetency] })) as any);

            const createdCompetency = Object.assign(new Competency(), { id: 99, title: 'Recursion', taxonomy: CompetencyTaxonomy.ANALYZE });
            vi.spyOn(competencyService, 'create').mockReturnValue(of(new HttpResponse({ body: createdCompetency })) as any);

            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');
            const successSpy = vi.spyOn(alertService, 'success');

            component.createAndLinkCompetencies();

            expect(competencyService.create).toHaveBeenCalledWith(expect.objectContaining({ title: 'Recursion' }), courseId);
            expect(emitSpy).toHaveBeenCalled();
            expect(successSpy).toHaveBeenCalled();
            expect(component.isCreatingCompetencies()).toBeFalsy();
        });

        it('should show warning when all competencies already exist', () => {
            const responseAllExist: ChecklistAnalysisResponse = {
                inferredCompetencies: [{ competencyTitle: 'Loops', taxonomyLevel: 'APPLY', rank: 1 }],
                difficultyAssessment: { suggested: 'EASY', reasoning: 'Reason', matchesDeclared: true },
                qualityIssues: [],
            };
            component.analysisResult.set(responseAllExist);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [mockCourseCompetency] })) as any);
            const warningSpy = vi.spyOn(alertService, 'warning');

            component.createAndLinkCompetencies();

            expect(warningSpy).toHaveBeenCalled();
            expect(component.isCreatingCompetencies()).toBeFalsy();
        });

        it('should handle error when creating competencies', () => {
            component.analysisResult.set(mockResponseWithCompetencies);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [] })) as any);
            vi.spyOn(competencyService, 'create').mockReturnValue(throwError(() => new Error('Failed')) as any);
            const errorSpy = vi.spyOn(alertService, 'error');

            component.createAndLinkCompetencies();

            expect(errorSpy).toHaveBeenCalled();
            expect(component.isCreatingCompetencies()).toBeFalsy();
        });

        it('should not link when already linking', () => {
            component.isLinkingCompetencies.set(true);
            const getAllSpy = vi.spyOn(competencyService, 'getAllForCourse');

            component.linkMatchingCompetencies();

            expect(getAllSpy).not.toHaveBeenCalled();
        });

        it('should not create when already creating', () => {
            component.isCreatingCompetencies.set(true);
            const getAllSpy = vi.spyOn(competencyService, 'getAllForCourse');

            component.createAndLinkCompetencies();

            expect(getAllSpy).not.toHaveBeenCalled();
        });

        it('should correctly identify linked and created competencies', () => {
            component.linkedCompetencyTitles.set(new Set(['Loops']));
            component.createdCompetencyTitles.set(new Set(['Recursion']));

            expect(component.isCompetencyLinked({ competencyTitle: 'Loops' })).toBeTruthy();
            expect(component.isCompetencyLinked({ competencyTitle: 'Recursion' })).toBeFalsy();
            expect(component.isCompetencyCreated({ competencyTitle: 'Recursion' })).toBeTruthy();
            expect(component.isCompetencyCreated({ competencyTitle: 'Loops' })).toBeFalsy();
        });

        it('should link competencies with fuzzy title matching', () => {
            // AI infers "Algorithm Design and Analysis" but course has "Algorithm Analysis"
            const fuzzyResponse: ChecklistAnalysisResponse = {
                inferredCompetencies: [
                    {
                        competencyTitle: 'Algorithm Design and Analysis',
                        taxonomyLevel: 'ANALYZE',
                        confidence: 0.9,
                        rank: 1,
                    },
                ],
                difficultyAssessment: { suggested: 'MEDIUM', reasoning: 'Reason', matchesDeclared: true },
                qualityIssues: [],
            };
            const fuzzyCompetency: CourseCompetency = Object.assign(new Competency(), {
                id: 10,
                title: 'Algorithm Analysis',
            });

            component.analysisResult.set(fuzzyResponse);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [fuzzyCompetency] })) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');

            component.linkMatchingCompetencies();

            // "Algorithm Analysis" is contained in "Algorithm Design and Analysis" → should match
            expect(emitSpy).toHaveBeenCalled();
            const emittedLinks = emitSpy.mock.calls[0][0] as CompetencyExerciseLink[];
            expect(emittedLinks).toHaveLength(1);
            expect(emittedLinks[0].competency?.id).toBe(10);
        });

        it('should not match competencies with very different titles', () => {
            const noMatchResponse: ChecklistAnalysisResponse = {
                inferredCompetencies: [
                    {
                        competencyTitle: 'Machine Learning Fundamentals',
                        taxonomyLevel: 'UNDERSTAND',
                        confidence: 0.8,
                        rank: 1,
                    },
                ],
                difficultyAssessment: { suggested: 'HARD', reasoning: 'Reason', matchesDeclared: true },
                qualityIssues: [],
            };
            const unrelatedCompetency: CourseCompetency = Object.assign(new Competency(), {
                id: 20,
                title: 'Database Design',
            });

            component.analysisResult.set(noMatchResponse);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [unrelatedCompetency] })) as any);
            const warningSpy = vi.spyOn(alertService, 'warning');

            component.linkMatchingCompetencies();

            expect(warningSpy).toHaveBeenCalled();
        });

        it('should match competencies despite morphological differences via stemming', () => {
            // AI infers "Sorting Algorithms" but course has "Sort Algorithm"
            const stemResponse: ChecklistAnalysisResponse = {
                inferredCompetencies: [
                    {
                        competencyTitle: 'Sorting Algorithms',
                        taxonomyLevel: 'APPLY',
                        confidence: 0.9,
                        rank: 1,
                    },
                ],
                difficultyAssessment: { suggested: 'MEDIUM', reasoning: 'Reason', matchesDeclared: true },
                qualityIssues: [],
            };
            const stemCompetency: CourseCompetency = Object.assign(new Competency(), {
                id: 30,
                title: 'Sort Algorithm',
            });

            component.analysisResult.set(stemResponse);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [stemCompetency] })) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');

            component.linkMatchingCompetencies();

            // "Sorting" → "sort", "Algorithms" → "algorithm" should match "Sort" → "sort", "Algorithm" → "algorithm"
            expect(emitSpy).toHaveBeenCalled();
            const emittedLinks = emitSpy.mock.calls[0][0] as CompetencyExerciseLink[];
            expect(emittedLinks).toHaveLength(1);
            expect(emittedLinks[0].competency?.id).toBe(30);
        });

        it('should match competencies when titles differ only by stop words', () => {
            // AI infers "Design of Data Structures" but course has "Data Structure Design"
            const stopWordResponse: ChecklistAnalysisResponse = {
                inferredCompetencies: [
                    {
                        competencyTitle: 'Design of Data Structures',
                        taxonomyLevel: 'APPLY',
                        confidence: 0.85,
                        rank: 1,
                    },
                ],
                difficultyAssessment: { suggested: 'MEDIUM', reasoning: 'Reason', matchesDeclared: true },
                qualityIssues: [],
            };
            const stopWordCompetency: CourseCompetency = Object.assign(new Competency(), {
                id: 40,
                title: 'Data Structure Design',
            });

            component.analysisResult.set(stopWordResponse);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [stopWordCompetency] })) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');

            component.linkMatchingCompetencies();

            // "of" is a stop word → ignored; "Structures" → "structur", "Structure" → "structur" → match
            expect(emitSpy).toHaveBeenCalled();
            const emittedLinks = emitSpy.mock.calls[0][0] as CompetencyExerciseLink[];
            expect(emittedLinks).toHaveLength(1);
            expect(emittedLinks[0].competency?.id).toBe(40);
        });

        it('should match competencies despite minor typos via Damerau-Levenshtein distance', () => {
            // AI infers "Objcet Oriented Design" (typo: transposition) but course has "Object Oriented Design"
            const typoResponse: ChecklistAnalysisResponse = {
                inferredCompetencies: [
                    {
                        competencyTitle: 'Objcet Oriented Design',
                        taxonomyLevel: 'APPLY',
                        confidence: 0.85,
                        rank: 1,
                    },
                ],
                difficultyAssessment: { suggested: 'MEDIUM', reasoning: 'Reason', matchesDeclared: true },
                qualityIssues: [],
            };
            const typoCompetency: CourseCompetency = Object.assign(new Competency(), {
                id: 50,
                title: 'Object Oriented Design',
            });

            component.analysisResult.set(typoResponse);
            vi.spyOn(competencyService, 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [typoCompetency] })) as any);
            const emitSpy = vi.spyOn(component.competencyLinksChange, 'emit');

            component.linkMatchingCompetencies();

            // "Objcet" vs "Object" is a single transposition → Damerau-Levenshtein distance = 1
            expect(emitSpy).toHaveBeenCalled();
            const emittedLinks = emitSpy.mock.calls[0][0] as CompetencyExerciseLink[];
            expect(emittedLinks).toHaveLength(1);
            expect(emittedLinks[0].competency?.id).toBe(50);
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
            component.analysisResult.set({ ...mockResponse, qualityIssues: [{ description: 'Issue', category: 'CLARITY', severity: 'LOW' }] });
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            component.fixQualityIssue({ description: 'Issue', category: 'CLARITY', severity: 'LOW' }, 0);

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

        it('should accumulate stale sections across multiple actions', () => {
            component.analysisResult.set({ ...mockResponse, qualityIssues: [{ description: 'Issue', category: 'CLARITY', severity: 'LOW' }] });
            vi.spyOn(apiService, 'applyChecklistAction').mockReturnValue(of(mockActionResponse) as any);

            // First: fix quality → competencies + difficulty stale
            component.fixQualityIssue({ description: 'Issue', category: 'CLARITY', severity: 'LOW' }, 0);

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
            qualityIssues: [{ description: 'New issue', category: 'COHERENCE', severity: 'HIGH' }],
            inferredCompetencies: [{ competencyTitle: 'New Comp', taxonomyLevel: 'UNDERSTAND', rank: 1 }],
            difficultyAssessment: { suggested: 'HARD', reasoning: 'Updated', matchesDeclared: false, taskCount: 3, testCount: 6 },
        };

        it('should update only the quality section when reanalyzing quality', () => {
            component.analysisResult.set(mockResponse);
            component.staleSections.set(new Set(['quality']));
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(fullResponse) as any);

            component.reanalyzeSection('quality');

            // Quality updated from fullResponse
            expect(component.analysisResult()?.qualityIssues).toEqual(fullResponse.qualityIssues);
            // Competencies and difficulty remain from mockResponse
            expect(component.analysisResult()?.inferredCompetencies).toEqual(mockResponse.inferredCompetencies);
            expect(component.analysisResult()?.difficultyAssessment).toEqual(mockResponse.difficultyAssessment);
            // Stale cleared for quality
            expect(component.isSectionStale('quality')).toBeFalsy();
            expect(component.sectionLoading()).toBeUndefined();
        });

        it('should update only the competencies section when reanalyzing competencies', () => {
            component.analysisResult.set(mockResponse);
            component.staleSections.set(new Set(['competencies']));
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(fullResponse) as any);

            component.reanalyzeSection('competencies');

            expect(component.analysisResult()?.inferredCompetencies).toEqual(fullResponse.inferredCompetencies);
            expect(component.analysisResult()?.qualityIssues).toEqual(mockResponse.qualityIssues);
            expect(component.isSectionStale('competencies')).toBeFalsy();
        });

        it('should update only the difficulty section when reanalyzing difficulty', () => {
            component.analysisResult.set(mockResponse);
            component.staleSections.set(new Set(['difficulty']));
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(fullResponse) as any);

            component.reanalyzeSection('difficulty');

            expect(component.analysisResult()?.difficultyAssessment).toEqual(fullResponse.difficultyAssessment);
            expect(component.analysisResult()?.qualityIssues).toEqual(mockResponse.qualityIssues);
            expect(component.isSectionStale('difficulty')).toBeFalsy();
        });

        it('should handle re-analyze error gracefully', () => {
            component.analysisResult.set(mockResponse);
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(throwError(() => new Error('Failed')) as any);
            const errorSpy = vi.spyOn(alertService, 'error');

            component.reanalyzeSection('quality');

            expect(errorSpy).toHaveBeenCalled();
            expect(component.sectionLoading()).toBeUndefined();
            // Original result preserved
            expect(component.analysisResult()).toEqual(mockResponse);
        });

        it('should not start re-analyze when another section is already loading', () => {
            component.sectionLoading.set('quality');
            const analyzeSpy = vi.spyOn(apiService, 'analyzeChecklist');

            component.reanalyzeSection('difficulty');

            expect(analyzeSpy).not.toHaveBeenCalled();
        });

        it('should set sectionLoading while re-analyzing', () => {
            component.analysisResult.set(mockResponse);
            vi.spyOn(apiService, 'analyzeChecklist').mockReturnValue(of(fullResponse) as any);

            expect(component.sectionLoading()).toBeUndefined();
            component.reanalyzeSection('quality');
            // After completion, loading is cleared
            expect(component.sectionLoading()).toBeUndefined();
        });
    });
});
