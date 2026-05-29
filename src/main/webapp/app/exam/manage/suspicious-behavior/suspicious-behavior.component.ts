import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SuspiciousExamSessions, SuspiciousSessionsAnalysisOptions } from 'app/exam/shared/entities/exam-session.model';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';
import { ActivatedRoute, Router } from '@angular/router';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { PlagiarismResultsService } from 'app/plagiarism/shared/services/plagiarism-results.service';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { PlagiarismCasesOverviewComponent } from 'app/exam/manage/suspicious-behavior/plagiarism-cases-overview/plagiarism-cases-overview.component';
import { FormsModule } from '@angular/forms';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-suspicious-behavior',
    templateUrl: './suspicious-behavior.component.html',
    imports: [FormsModule, TranslateDirective, ArtemisTranslatePipe, PlagiarismCasesOverviewComponent, ButtonComponent, HelpIconComponent, DocumentationButtonComponent],
})
export class SuspiciousBehaviorComponent implements OnInit {
    private suspiciousSessionsService = inject(SuspiciousSessionsService);
    private activatedRoute = inject(ActivatedRoute);
    private plagiarismCasesService = inject(PlagiarismCasesService);
    private examService = inject(ExamManagementService);
    private plagiarismResultsService = inject(PlagiarismResultsService);
    private router = inject(Router);

    exercises = signal<Exercise[]>([]);
    plagiarismCasesPerExercise = signal<Map<Exercise, number>>(new Map<Exercise, number>());
    plagiarismResultsPerExercise = signal<Map<Exercise, number>>(new Map<Exercise, number>());
    anyPlagiarismCases = signal(false);
    suspiciousSessions = signal<SuspiciousExamSessions[]>([]);
    examId = signal<number | undefined>(undefined);
    courseId = signal<number | undefined>(undefined);
    checkboxCriterionDifferentStudentExamsSameIPAddressChecked = signal(false);
    checkboxCriterionDifferentStudentExamsSameBrowserFingerprintChecked = signal(false);
    checkboxCriterionSameStudentExamDifferentIPAddressesChecked = signal(false);
    checkboxCriterionSameStudentExamDifferentBrowserFingerprintsChecked = signal(false);
    checkboxCriterionIPOutsideOfASpecificRangeChecked = signal(false);
    ipSubnet = signal<string | undefined>(undefined);
    analyzing = signal(false);
    analyzed = signal(false);
    /** Regex to either match an IPv4 or IPv6 subnet
     * Borrowed from https://www.regextester.com/93988 and https://www.regextester.com/93987
     * */
    readonly ipSubnetRegexPattern: RegExp = new RegExp(
        '(^([0-9]{1,3}.){3}[0-9]{1,3}(/([0-9]|[1-2][0-9]|3[0-2]))?$)|(^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:)))(%.+)?s*(\\/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8]))?$)',
    );

    readonly documentationType: DocumentationType = 'SuspiciousBehavior';

    /**
     * Computes whether at least one criterion is selected and, if the IP-range
     * criterion is on, that the entered subnet matches the regex.
     */
    readonly analyzeButtonEnabled = computed(() => {
        const ipOutsideRange = this.checkboxCriterionIPOutsideOfASpecificRangeChecked();
        const anySelected =
            this.checkboxCriterionDifferentStudentExamsSameIPAddressChecked() ||
            this.checkboxCriterionDifferentStudentExamsSameBrowserFingerprintChecked() ||
            this.checkboxCriterionSameStudentExamDifferentIPAddressesChecked() ||
            this.checkboxCriterionSameStudentExamDifferentBrowserFingerprintsChecked() ||
            ipOutsideRange;
        return anySelected && (!ipOutsideRange || this.ipSubnetRegexPattern.test(this.ipSubnet()!));
    });

    ngOnInit(): void {
        this.examId.set(Number(this.activatedRoute.snapshot.paramMap.get('examId')));
        this.courseId.set(Number(this.activatedRoute.snapshot.paramMap.get('courseId')));
        this.examService.getExercisesWithPotentialPlagiarismForExam(this.courseId()!, this.examId()!).subscribe((res) => {
            this.exercises.set(res);
            this.retrievePlagiarismCases();
        });
    }

    private retrievePlagiarismCases = () => {
        this.exercises().forEach((exercise) => {
            this.plagiarismCasesService.getNumberOfPlagiarismCasesForExercise(exercise).subscribe((res) => {
                this.plagiarismCasesPerExercise().computeIfAbsent(exercise, () => res);
                if (res > 0) this.anyPlagiarismCases.set(true);
            });
            this.plagiarismResultsService.getNumberOfPlagiarismResultsForExercise(exercise.id!).subscribe((res) => {
                this.plagiarismResultsPerExercise().computeIfAbsent(exercise, () => res);
            });
        });
    };

    goToSuspiciousSessions() {
        this.router.navigate(['/course-management', this.courseId(), 'exams', this.examId(), 'suspicious-behavior', 'suspicious-sessions'], {
            state: { suspiciousSessions: this.suspiciousSessions(), ipSubnet: this.ipSubnet() },
        });
    }

    analyzeSessions() {
        const options = new SuspiciousSessionsAnalysisOptions(
            this.checkboxCriterionDifferentStudentExamsSameIPAddressChecked(),
            this.checkboxCriterionDifferentStudentExamsSameBrowserFingerprintChecked(),
            this.checkboxCriterionSameStudentExamDifferentIPAddressesChecked(),
            this.checkboxCriterionSameStudentExamDifferentBrowserFingerprintsChecked(),
            this.checkboxCriterionIPOutsideOfASpecificRangeChecked(),
            this.ipSubnet(),
        );
        this.analyzing.set(true);
        this.suspiciousSessionsService.getSuspiciousSessions(this.courseId()!, this.examId()!, options).subscribe({
            next: (suspiciousSessions) => {
                this.suspiciousSessions.set(suspiciousSessions);
                this.analyzing.set(false);
                this.analyzed.set(true);
            },
            error: () => {
                this.analyzing.set(false);
                this.analyzed.set(true);
            },
        });
    }
}
