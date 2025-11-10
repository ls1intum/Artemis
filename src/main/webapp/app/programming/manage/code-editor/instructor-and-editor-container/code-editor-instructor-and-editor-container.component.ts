import { Component, ViewChild, inject, signal } from '@angular/core';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faArrowLeft, faArrowRight, faCircleNotch, faPlus, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faCheckDouble } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { getRepoPath, humanizeCategory, severityToString } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-and-editor-container.component.html',
    styleUrl: 'code-editor-instructor-and-editor-container.scss',
    imports: [
        FaIconComponent,
        TranslateDirective,
        CodeEditorContainerComponent,
        IncludedInScoreBadgeComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        UpdatingResultComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseEditableInstructionComponent,
        ProgrammingExerciseInstructionComponent,
        NgbTooltip,
        ArtemisTranslatePipe,
    ],
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent {
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;
    @ViewChild(ProgrammingExerciseEditableInstructionComponent, { static: false }) editableInstructions: ProgrammingExerciseEditableInstructionComponent;

    //TODO: Remove
    mockIssues: ConsistencyIssue[] = [
        {
            severity: ConsistencyIssue.SeverityEnum.High,
            category: ConsistencyIssue.CategoryEnum.MethodReturnTypeMismatch,
            description: 'Description 1.',
            suggestedFix: 'Fix 1',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TemplateRepository,
                    filePath: 'template_repository/src/TESTI/BubbleSort.java',
                    startLine: 1,
                    endLine: 1,
                },
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'solution_repository/src/TESTI/BubbleSort.java',
                    startLine: 1,
                    endLine: 1,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Medium,
            category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
            description: 'Description 2',
            suggestedFix: 'Fix 2',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'solution_repository/src/TESTI/BubbleSort.java',
                    startLine: 1,
                    endLine: 2,
                },
                {
                    type: ArtifactLocation.TypeEnum.TemplateRepository,
                    filePath: 'template_repository/src/TESTI/BubbleSort.java',
                    startLine: 1,
                    endLine: 2,
                },
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'tests_repository/test/TESTI/MethodTest.java',
                    startLine: 1,
                    endLine: 2,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Low,
            category: ConsistencyIssue.CategoryEnum.VisibilityMismatch,
            description: 'Description 2',
            suggestedFix: 'Fix 2',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.ProblemStatement,
                    filePath: 'problem_statement.md',
                    startLine: 1,
                    endLine: 3,
                },
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'tests_repository/test/TESTI/MethodTest.java',
                    startLine: 1,
                    endLine: 3,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Low,
            category: ConsistencyIssue.CategoryEnum.VisibilityMismatch,
            description: 'Description 2',
            suggestedFix: 'Fix 2',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'tests_repository/test/TESTI/MethodTest.java',
                    startLine: 1,
                    endLine: 3,
                },
            ],
        },
    ];

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly consistencyIssues = signal<ConsistencyIssue[]>(this.mockIssues);

    private consistencyCheckService = inject(ConsistencyCheckService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private profileService = inject(ProfileService);

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;
    irisSettings?: IrisSettings;
    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);
    selectedIssue: ConsistencyIssue | undefined = undefined;
    locationIndex: number = 0;

    protected readonly RepositoryType = RepositoryType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faCheckDouble = faCheckDouble;

    /**
     * Checks whether a consistency check operation is currently running.
     *
     * @returns {boolean} `true` if either the rewrite or consistency check process is currently loading; otherwise `false`.
     */
    isCheckingConsistency(): boolean {
        return this.artemisIntelligenceService.isLoading();
    }

    /**
     * Runs a consistency check for the given programming exercise.
     *
     * First verifies that all required repositories are set up correctly.
     * If no setup issues are found, performs a full content consistency check.
     * Displays alerts for errors, warnings, or successful results.
     *
     * @param {ProgrammingExercise} exercise - The exercise to check.
     */
    checkConsistencies(exercise: ProgrammingExercise) {
        // Clear previous consistency issues
        this.consistencyIssues.set([]);

        if (!exercise.id) {
            this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
            return;
        }

        this.consistencyCheckService.checkConsistencyForProgrammingExercise(exercise.id!).subscribe({
            // This first consistency check ensures, that the exercise has all repositories set up
            // This does not yet check the actual content of the exercise
            next: (inconsistencies: ConsistencyCheckError[]) => {
                if (inconsistencies.length > 0) {
                    for (const inconsistency of inconsistencies) {
                        this.alertService.error(this.translateService.instant(`artemisApp.consistencyCheck.error.${inconsistency.type}`));
                    }
                    return;
                }

                // Now the content is checked
                this.artemisIntelligenceService.consistencyCheck(exercise.id!).subscribe({
                    next: (response: ConsistencyCheckResponse) => {
                        this.consistencyIssues.set(response.issues ?? []);

                        if (this.consistencyIssues().length === 0) {
                            this.alertService.success(this.translateService.instant('artemisApp.consistencyCheck.noInconsistencies'));
                        } else {
                            this.alertService.warning(this.translateService.instant('artemisApp.consistencyCheck.inconsistenciesFoundAlert'));
                        }
                    },
                    error: () => {
                        this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
                    },
                });
            },
            error: (err) => {
                this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
            },
        });
    }

    getIssueLabel(issue: ConsistencyIssue) {
        return severityToString(issue.severity) + ': ' + humanizeCategory(issue.category);
    }

    async onIssueNavigate(issue: ConsistencyIssue, deltaIndex: number, event: Event) {
        if (issue === this.selectedIssue) {
            // Stay in bounds of the array
            this.locationIndex = (this.locationIndex + this.selectedIssue.relatedLocations.length + deltaIndex) % this.selectedIssue.relatedLocations.length;
        } else {
            this.selectedIssue = issue;
            this.locationIndex = 0;
        }

        if (issue.relatedLocations[this.locationIndex].type === 'PROBLEM_STATEMENT') {
            this.codeEditorContainer.selectedFile = this.codeEditorContainer.problemStatementIdentifier;
            return;
        }

        if (issue.relatedLocations[this.locationIndex].type === 'TEMPLATE_REPOSITORY' && this.codeEditorContainer.selectedRepository !== 'TEMPLATE') {
            await this.selectTemplateParticipation();
        } else if (issue.relatedLocations[this.locationIndex].type === 'SOLUTION_REPOSITORY' && this.codeEditorContainer.selectedRepository !== 'SOLUTION') {
            await this.selectSolutionParticipation();
        } else if (issue.relatedLocations[this.locationIndex].type === 'TESTS_REPOSITORY' && this.codeEditorContainer.selectedRepository !== 'TESTS') {
            await this.selectTestRepository();
        }

        // We need to wait for the editor to be fully loaded,
        // else the file content does not show
        setTimeout(() => {
            this.codeEditorContainer.selectedFile = getRepoPath(issue.relatedLocations[this.locationIndex]);
        }, 0);
    }
}
