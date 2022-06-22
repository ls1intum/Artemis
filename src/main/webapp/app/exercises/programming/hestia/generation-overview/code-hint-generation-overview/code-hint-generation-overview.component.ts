import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CoverageReport } from 'app/entities/hestia/coverage-report.model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { ProgrammingExerciseFullGitDiffReport } from 'app/entities/hestia/programming-exercise-full-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { faCode, faEye, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { SolutionEntryDetailsModalComponent } from 'app/exercises/programming/hestia/generation-overview/code-hint-generation-overview/solution-entry-details-modal/solution-entry-details-modal.component';
import { ProgrammingExerciseTestCaseType } from 'app/entities/programming-exercise-test-case.model';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/shared/code-hint.service';
import { HintType } from 'app/entities/hestia/exercise-hint.model';

@Component({
    selector: 'jhi-code-hint-generation-overview',
    templateUrl: './code-hint-generation-overview.component.html',
    styleUrls: ['./code-hint-generation-overview.component.scss'],
})
export class CodeHintGenerationOverviewComponent implements OnInit {
    exercise?: ProgrammingExercise;

    currentStepIndex = 0;
    // TODO: assign
    stepStatus = [false, false, false, false];
    stepLoading = [true, true, true, true];

    gitDiffReport?: ProgrammingExerciseFullGitDiffReport;
    coverageReport?: CoverageReport;
    fileContentByPath = new Map<string, string>();
    solutionEntries?: Map<ProgrammingExerciseSolutionEntry, boolean>;
    selectedAllEntries: boolean;
    codeHints?: CodeHint[];

    faCode = faCode;
    faTimes = faTimes;
    faWrench = faWrench;
    faEye = faEye;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private exerciseService: ProgrammingExerciseService,
        private codeHintService: CodeHintService,
        private alertService: AlertService,
        private modalService: NgbModal,
    ) {}

    ngOnInit() {
        this.route.data.subscribe(({ exercise }) => {
            this.exercise = exercise;

            // TODO: load all data smashed to one endpoint

            this.exerciseService.getFullDiffReport(exercise.id).subscribe({
                next: (report) => {
                    this.gitDiffReport = report;
                    this.stepStatus[0] = true;
                    this.stepLoading[0] = false;
                },
                error: () => {
                    this.stepStatus[0] = false;
                    this.stepLoading[0] = false;
                },
            });
            this.exerciseService.getSolutionRepositoryTestFilesWithContent(exercise.id).subscribe({
                next: (response: Map<string, string>) => {
                    this.exerciseService.getLatestFullTestwiseCoverageReport(exercise.id).subscribe({
                        next: (coverageReport) => {
                            this.coverageReport = coverageReport;
                            this.fileContentByPath = response;
                            this.stepStatus[1] = true;
                            this.stepLoading[1] = false;
                        },
                        error: () => {
                            this.stepStatus[1] = false;
                            this.stepLoading[1] = false;
                        },
                    });
                },
            });
            this.exerciseService.getSolutionEntriesForExercise(exercise.id).subscribe({
                next: (response: ProgrammingExerciseSolutionEntry[]) => {
                    const selectedAllSolutionEntries = new Map<ProgrammingExerciseSolutionEntry, boolean>();
                    response.forEach((entry) => selectedAllSolutionEntries.set(entry, true));
                    this.solutionEntries = selectedAllSolutionEntries;
                    this.stepLoading[2] = false;
                    this.selectedAllEntries = true;
                    this.stepStatus[2] = true;
                },
                error: (err) => {
                    console.log(err);
                    // this.stepLoading[2] = false;
                },
            });
            this.exerciseService.getCodeHintsForExercise(exercise.id).subscribe({
                next: (res) => {
                    this.codeHints = res.filter((entry) => entry.type === HintType.CODE);
                    this.stepLoading[3] = false;
                    this.stepStatus[3] = res.length > 0;
                    if (res.length > 0) {
                        this.currentStepIndex = 3;
                    }
                },
                error: () => {
                    this.stepLoading[3] = false;
                },
            });
        });
    }

    isNextStepAvailable(): boolean {
        return this.stepStatus[this.currentStepIndex];
    }

    onNextStep() {
        this.currentStepIndex++;
    }

    onPreviousStep() {
        this.currentStepIndex--;
    }

    updateBulkEntrySelection() {
        if (!this.selectedAllEntries) {
            this.solutionEntries?.forEach((value, key) => this.solutionEntries?.set(key, true));
            this.selectedAllEntries = true;
        } else {
            this.solutionEntries?.forEach((value, key) => this.solutionEntries?.set(key, false));
            this.selectedAllEntries = false;
        }
    }

    selectSingleEntry(entry: ProgrammingExerciseSolutionEntry, isSelected: boolean) {
        this.solutionEntries?.set(entry, isSelected);
        this.selectedAllEntries = Array.from(this.solutionEntries!.values()).every((selected) => selected);
        console.log(this.solutionEntries);
    }

    onStepChange(index: number) {
        this.currentStepIndex = index;
    }

    onGenerateStructuralSolutionEntries() {
        this.exerciseService.createStructuralSolutionEntries(this.exercise!.id!).subscribe({
            next: (updatedStructuralEntries) => {
                const updatedSolutionEntries = new Map<ProgrammingExerciseSolutionEntry, boolean>();
                this.solutionEntries?.forEach((selected, entry) => {
                    if (entry.testCase?.type === ProgrammingExerciseTestCaseType.BEHAVIORAL) {
                        updatedSolutionEntries.set(entry, selected);
                    }
                });
                updatedStructuralEntries.forEach((entry) => updatedSolutionEntries.set(entry, true));
                this.solutionEntries = updatedSolutionEntries;
                this.selectedAllEntries = true;
            },
            error: () => {},
        });
    }

    onGenerateBehavioralSolutionEntries() {
        this.exerciseService.createBehavioralSolutionEntries(this.exercise!.id!).subscribe({
            next: (updatedBehavioralEntries) => {
                const updatedSolutionEntries = new Map<ProgrammingExerciseSolutionEntry, boolean>();
                this.solutionEntries?.forEach((selected, entry) => {
                    if (entry.testCase?.type && entry.testCase?.type !== ProgrammingExerciseTestCaseType.BEHAVIORAL) {
                        updatedSolutionEntries.set(entry, selected);
                    }
                });
                updatedBehavioralEntries.forEach((entry) => updatedSolutionEntries.set(entry, true));
                this.solutionEntries = updatedSolutionEntries;
                this.selectedAllEntries = true;
            },
            error: () => {},
        });
    }

    onSolutionEntryView(solutionEntry: ProgrammingExerciseSolutionEntry, isEditable: boolean) {
        const modalRef: NgbModalRef = this.modalService.open(SolutionEntryDetailsModalComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.solutionEntry = solutionEntry;
        modalRef.componentInstance.isEditable = isEditable;
    }

    getSelectedEntries(): ProgrammingExerciseSolutionEntry[] {
        const result: ProgrammingExerciseSolutionEntry[] = [];
        this.solutionEntries?.forEach((selected, entry) => {
            if (selected) {
                result.push(entry);
            }
        });
        console.log(result);
        return result;
    }

    onGenerateCodeHints() {
        this.codeHintService.generateCodeHintsForExercise(this.exercise!.id!, true).subscribe({
            next: () => {
                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'artemisApp.programmingExercise.generateCodeHintsSuccess',
                });
            },
            error: () => {},
        });
    }
}
