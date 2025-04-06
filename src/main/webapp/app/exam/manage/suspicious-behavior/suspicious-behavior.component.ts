import { Component, OnInit, inject } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SuspiciousExamSessions, SuspiciousSessionsAnalysisOptions } from 'app/exam/shared/entities/exam-session.model';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';
import { ActivatedRoute, Router } from '@angular/router';
import { PlagiarismCasesService } from 'app/plagiarism/shared/plagiarism-cases.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { PlagiarismResultsService } from 'app/plagiarism/shared/plagiarism-results.service';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PlagiarismCasesOverviewComponent } from 'app/exam/manage/suspicious-behavior/plagiarism-cases-overview/plagiarism-cases-overview.component';
import { FormsModule } from '@angular/forms';
import { ButtonComponent } from 'app/shared/components/button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';

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

    exercises: Exercise[] = [];
    plagiarismCasesPerExercise: Map<Exercise, number> = new Map<Exercise, number>();
    plagiarismResultsPerExercise: Map<Exercise, number> = new Map<Exercise, number>();
    anyPlagiarismCases = false;
    suspiciousSessions: SuspiciousExamSessions[] = [];
    examId: number;
    courseId: number;
    checkboxCriterionDifferentStudentExamsSameIPAddressChecked = false;
    checkboxCriterionDifferentStudentExamsSameBrowserFingerprintChecked = false;
    checkboxCriterionSameStudentExamDifferentIPAddressesChecked = false;
    checkboxCriterionSameStudentExamDifferentBrowserFingerprintsChecked = false;
    checkboxCriterionIPOutsideOfASpecificRangeChecked = false;
    ipSubnet?: string;
    analyzing = false;
    analyzed = false;
    /** Regex to either match an IPv4 or IPv6 subnet
     * Borrowed from https://www.regextester.com/93988 and https://www.regextester.com/93987
     * */
    readonly ipSubnetRegexPattern: RegExp = new RegExp(
        '(^([0-9]{1,3}.){3}[0-9]{1,3}(/([0-9]|[1-2][0-9]|3[0-2]))?$)|(^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:)))(%.+)?s*(\\/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8]))?$)',
    );

    readonly documentationType: DocumentationType = 'SuspiciousBehavior';

    ngOnInit(): void {
        this.examId = Number(this.activatedRoute.snapshot.paramMap.get('examId'));
        this.courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.examService.getExercisesWithPotentialPlagiarismForExam(this.courseId, this.examId).subscribe((res) => {
            this.exercises = res;
            this.retrievePlagiarismCases();
        });
    }

    private retrievePlagiarismCases = () => {
        this.exercises.forEach((exercise) => {
            this.plagiarismCasesService.getNumberOfPlagiarismCasesForExercise(exercise).subscribe((res) => {
                this.plagiarismCasesPerExercise.computeIfAbsent(exercise, () => res);
                if (res > 0) this.anyPlagiarismCases = true;
            });
            this.plagiarismResultsService.getNumberOfPlagiarismResultsForExercise(exercise.id!).subscribe((res) => {
                this.plagiarismResultsPerExercise.computeIfAbsent(exercise, () => res);
            });
        });
    };

    get analyzeButtonEnabled(): boolean {
        return (
            (this.checkboxCriterionDifferentStudentExamsSameIPAddressChecked ||
                this.checkboxCriterionDifferentStudentExamsSameBrowserFingerprintChecked ||
                this.checkboxCriterionSameStudentExamDifferentIPAddressesChecked ||
                this.checkboxCriterionSameStudentExamDifferentBrowserFingerprintsChecked ||
                this.checkboxCriterionIPOutsideOfASpecificRangeChecked) &&
            (!this.checkboxCriterionIPOutsideOfASpecificRangeChecked || (this.checkboxCriterionIPOutsideOfASpecificRangeChecked && this.ipSubnetRegexPattern.test(this.ipSubnet!)))
        );
    }

    goToSuspiciousSessions() {
        this.router.navigate(['/course-management', this.courseId, 'exams', this.examId, 'suspicious-behavior', 'suspicious-sessions'], {
            state: { suspiciousSessions: this.suspiciousSessions, ipSubnet: this.ipSubnet },
        });
    }

    analyzeSessions() {
        const options = new SuspiciousSessionsAnalysisOptions(
            this.checkboxCriterionDifferentStudentExamsSameIPAddressChecked,
            this.checkboxCriterionDifferentStudentExamsSameBrowserFingerprintChecked,
            this.checkboxCriterionSameStudentExamDifferentIPAddressesChecked,
            this.checkboxCriterionSameStudentExamDifferentBrowserFingerprintsChecked,
            this.checkboxCriterionIPOutsideOfASpecificRangeChecked,
            this.ipSubnet,
        );
        this.analyzing = true;
        this.suspiciousSessionsService.getSuspiciousSessions(this.courseId, this.examId, options).subscribe({
            next: (suspiciousSessions) => {
                this.suspiciousSessions = suspiciousSessions;
                this.analyzing = false;
                this.analyzed = true;
            },
            error: () => {
                this.analyzing = false;
                this.analyzed = true;
            },
        });
    }
}
