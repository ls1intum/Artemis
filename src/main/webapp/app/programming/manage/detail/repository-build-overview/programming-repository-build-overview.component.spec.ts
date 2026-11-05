import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { RouterLink, provideRouter } from '@angular/router';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { TumAetUiButtonDirective, TumAetUiCardComponent } from '@tumaet/ui-angular';
import { ProgrammingRepositoryBuildOverviewComponent } from './programming-repository-build-overview.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { ProgrammingRepositoryBuildOverviewDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/instructor-repo-download/programming-exercise-instructor-repo-download.component';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/instructor/programming-exercise-instructor-trigger-build-button.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';
import { ProgrammingDiffReportDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { NoDataComponent } from 'app/shared-ui/components/no-data/no-data-component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

// Removing the repository/status composition or hardcoding checkout paths must break these tests.
describe('ProgrammingRepositoryBuildOverviewComponent', () => {
    let fixture: ComponentFixture<ProgrammingRepositoryBuildOverviewComponent>;
    let detail: ProgrammingRepositoryBuildOverviewDetail;
    const getDirectories = vi.fn();

    beforeEach(async () => {
        getDirectories.mockReset();
        getDirectories.mockReturnValue(
            of({
                submissionBuildPlanCheckoutDirectories: { exerciseCheckoutDirectory: '/assignment', testCheckoutDirectory: '/' },
                solutionBuildPlanCheckoutDirectories: { solutionCheckoutDirectory: '/assignment', testCheckoutDirectory: '/' },
            }),
        );
        await TestBed.configureTestingModule({
            imports: [ProgrammingRepositoryBuildOverviewComponent],
            providers: [provideRouter([]), { provide: ProgrammingExerciseService, useValue: { getCheckoutDirectoriesForProgrammingLanguage: getDirectories } }],
        })
            .overrideComponent(ProgrammingRepositoryBuildOverviewComponent, {
                set: {
                    imports: [
                        TumAetUiCardComponent,
                        TumAetUiButtonDirective,
                        FaIconComponent,
                        MockDirective(FeatureToggleDirective),
                        MockDirective(FeatureToggleLinkDirective),
                        RouterLink,
                        MockDirective(TranslateDirective),
                        MockPipe(ArtemisTranslatePipe, (key) => key ?? ''),
                        MockComponent(CodeButtonComponent),
                        MockComponent(ProgrammingExerciseInstructorRepoDownloadComponent),
                        MockComponent(UpdatingResultComponent),
                        MockComponent(ProgrammingExerciseInstructorTriggerBuildButtonComponent),
                        MockComponent(ProgrammingExerciseInstructorStatusComponent),
                        MockComponent(ProgrammingDiffReportDetailComponent),
                        MockComponent(NoDataComponent),
                    ],
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(ProgrammingRepositoryBuildOverviewComponent);
        const exercise: ProgrammingExercise = {
            ...new ProgrammingExercise(undefined, undefined),
            id: 12,
            isAtLeastEditor: true,
            programmingLanguage: ProgrammingLanguage.JAVA,
            templateParticipation: { id: 1, repositoryUri: 'https://git/template', buildPlanId: 'BASE', buildPlanUrl: 'https://ci/base' },
            solutionParticipation: { id: 2, repositoryUri: 'https://git/solution', buildPlanId: 'SOLUTION', buildPlanUrl: 'https://ci/solution' },
            testRepositoryUri: 'https://git/tests',
            auxiliaryRepositories: [{ id: 9, name: 'library', repositoryUri: 'https://git/library', checkoutDirectory: 'lib' }],
        };
        detail = {
            type: DetailType.ProgrammingRepositoryBuildOverview,
            data: {
                exercise,
                localCIEnabled: true,
                supportsAuxiliaryRepositories: true,
                templateStatus: {
                    exercise,
                    participation: exercise.templateParticipation,
                    loading: signal(false),
                    type: ProgrammingExerciseParticipationType.TEMPLATE,
                    submissionRouterLink: ['/course-management/3/programming-exercises/12/participations/1/submissions'],
                    onParticipationChange: vi.fn(),
                },
                solutionStatus: {
                    exercise,
                    participation: exercise.solutionParticipation,
                    loading: signal(false),
                    type: ProgrammingExerciseParticipationType.SOLUTION,
                    submissionRouterLink: ['/course-management/3/exams/4/exercise-groups/5/programming-exercises/12/participations/2'],
                    onParticipationChange: vi.fn(),
                },
                comparison: {
                    type: DetailType.ProgrammingDiffReport,
                    data: {
                        lineChangesLoading: signal(false),
                        repositoryDiffInformation: signal(undefined),
                        templateFileContentByPath: signal(new Map()),
                        solutionFileContentByPath: signal(new Map()),
                    },
                },
            },
        };
    });

    async function render() {
        fixture.componentRef.setInput('detail', detail);
        await fixture.whenStable();
    }

    it('pairs each repository with its live familiar result and renders shared repositories and comparison once', async () => {
        await render();
        const panels = fixture.debugElement.queryAll(By.css('[data-testid="template-build-panel"], [data-testid="solution-build-panel"]'));
        expect(panels).toHaveLength(2);
        const codes = fixture.debugElement.queryAll(By.directive(CodeButtonComponent)).map((element) => element.componentInstance as CodeButtonComponent);
        expect(codes.map((code) => code.repositoryViewLink())).toEqual([
            ['.', 'repository', RepositoryType.TEMPLATE],
            ['.', 'repository', RepositoryType.SOLUTION],
            ['.', 'repository', RepositoryType.TESTS],
            ['.', 'repository', RepositoryType.AUXILIARY, 9],
        ]);
        expect(codes.every((code) => !code.allowOnlineIde())).toBe(true);
        expect(fixture.debugElement.queryAll(By.directive(ProgrammingExerciseInstructorRepoDownloadComponent))).toHaveLength(4);
        expect(fixture.debugElement.queryAll(By.directive(ProgrammingExerciseInstructorTriggerBuildButtonComponent))).toHaveLength(2);
        expect(fixture.debugElement.queryAll(By.directive(ProgrammingExerciseInstructorStatusComponent))).toHaveLength(2);
        const results = fixture.debugElement.queryAll(By.directive(UpdatingResultComponent));
        expect(results.map((result) => result.componentInstance.participation().id)).toEqual([1, 2]);
        expect(results.every((result) => !result.componentInstance.short() && result.componentInstance.showUngradedResults())).toBe(true);
        results[0].triggerEventHandler('onParticipationChange', undefined);
        expect(detail.data.templateStatus.onParticipationChange).toHaveBeenCalledOnce();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.programmingExercise.repositoryBuildOverview.templateExpectedScore');
        expect(fixture.nativeElement.textContent).toContain('artemisApp.programmingExercise.repositoryBuildOverview.solutionExpectedScore');
        expect(fixture.debugElement.queryAll(By.directive(ProgrammingDiffReportDetailComponent))).toHaveLength(1);
        const sharedRepository = fixture.nativeElement.querySelector('[data-testid="auxiliary-repository"]') as HTMLElement;
        const comparison = fixture.nativeElement.querySelector('[data-testid="repository-comparison"]') as HTMLElement;
        expect(sharedRepository.compareDocumentPosition(comparison) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        expect(fixture.debugElement.queryAll(By.directive(RouterLink)).map((link) => (link.nativeElement as HTMLAnchorElement).getAttribute('href'))).toEqual([
            '/course-management/3/programming-exercises/12/participations/1/submissions?isTmpOrSolutionProgrParticipation=true',
            '/course-management/3/exams/4/exercise-groups/5/programming-exercises/12/participations/2?isTmpOrSolutionProgrParticipation=true',
        ]);
    });

    it.each(['/course-management/3/programming-exercises/12', '/course-management/3/exams/4/exercise-groups/5/programming-exercises/12'])(
        'preserves browser editing for both participations under %s',
        async (base) => {
            detail.data.editorBaseResource = base;
            await render();
            const codes = fixture.debugElement.queryAll(By.directive(CodeButtonComponent)).map((element) => element.componentInstance as CodeButtonComponent);
            expect(codes.map((code) => code.repositoryEditorLink())).toEqual([
                [base, 'code-editor', RepositoryType.TEMPLATE, 1],
                [base, 'code-editor', RepositoryType.SOLUTION, 2],
                undefined,
                undefined,
            ]);
            expect(codes[0].repositoryViewLink()).toEqual(['.', 'repository', RepositoryType.TEMPLATE]);
            expect(codes[1].repositoryViewLink()).toEqual(['.', 'repository', RepositoryType.SOLUTION]);
        },
    );

    it('does not offer editor access to viewers even when a base resource is available', async () => {
        detail.data.editorBaseResource = '/course-management/3/programming-exercises/12';
        detail.data.exercise.isAtLeastEditor = false;
        await render();
        expect(fixture.debugElement.queryAll(By.directive(CodeButtonComponent)).every((element) => !element.componentInstance.repositoryEditorLink())).toBe(true);
    });

    it('uses configured paths and optional solution checkout independently for each build', async () => {
        detail.data.exercise.buildConfig = {
            allowBranching: false,
            branchRegex: '.*',
            checkoutSolutionRepository: true,
            assignmentCheckoutPath: 'custom/source',
            testCheckoutPath: 'checks',
            solutionCheckoutPath: 'reference',
        };
        await render();
        const panels = fixture.debugElement.queryAll(By.css('[data-testid="template-build-panel"], [data-testid="solution-build-panel"]'));
        expect(panels[0].nativeElement.textContent).toContain('/custom/source');
        expect(panels[0].nativeElement.textContent).toContain('/reference');
        expect(panels[1].nativeElement.textContent).toContain('/custom/source');
        expect(panels[1].nativeElement.textContent).not.toContain('/reference');
        expect(panels.every((panel) => panel.nativeElement.textContent.includes('/checks'))).toBe(true);
        expect(getDirectories).toHaveBeenCalledWith(ProgrammingLanguage.JAVA, true);
    });

    it('keeps loading reactive, omits absent participation operations and respects editor access', async () => {
        detail.data.templateStatus.loading = signal(true);
        detail.data.solutionStatus.participation = undefined;
        detail.data.exercise.solutionParticipation = undefined;
        detail.data.exercise.isAtLeastEditor = false;
        await render();
        expect(fixture.debugElement.queryAll(By.directive(UpdatingResultComponent))).toHaveLength(0);
        expect(fixture.debugElement.queryAll(By.directive(ProgrammingExerciseInstructorTriggerBuildButtonComponent))).toHaveLength(0);
        expect(fixture.debugElement.queryAll(By.directive(RouterLink))).toHaveLength(0);
        expect(getDirectories).not.toHaveBeenCalled();
        (detail.data.templateStatus.loading as WritableSignal<boolean>).set(false);
        await fixture.whenStable();
        expect(fixture.debugElement.queryAll(By.directive(UpdatingResultComponent))).toHaveLength(1);
    });

    it('retains external build plan links without requesting local checkout configuration', async () => {
        detail.data.localCIEnabled = false;
        detail.data.supportsAuxiliaryRepositories = false;
        await render();
        expect(getDirectories).not.toHaveBeenCalled();
        expect(Array.from(fixture.nativeElement.querySelectorAll('a[href^="https://ci"]')).map((link) => (link as HTMLAnchorElement).href)).toEqual([
            'https://ci/base',
            'https://ci/solution',
        ]);
        expect(fixture.debugElement.queryAll(By.directive(CodeButtonComponent))).toHaveLength(3);
    });

    it('shows empty checkout information without inventing paths when the configuration is absent', async () => {
        getDirectories.mockReturnValue(of({}));
        detail.data.supportsAuxiliaryRepositories = false;
        await render();
        expect(fixture.debugElement.queryAll(By.directive(NoDataComponent))).toHaveLength(2);
        expect(fixture.nativeElement.textContent).not.toContain('/assignment');
    });

    it('shows unavailable checkout information on fetch errors without losing repository actions', async () => {
        getDirectories.mockReturnValue(throwError(() => new Error('unavailable')));
        await render();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.programmingExercise.repositoryBuildOverview.checkoutUnavailable');
        expect(fixture.debugElement.queryAll(By.directive(CodeButtonComponent))).toHaveLength(4);
    });
});
