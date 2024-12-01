import { Component, OnInit, ViewChild } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { SuspiciousExamSessions, SuspiciousSessionsAnalysisOptions } from 'app/entities/exam/exam-session.model';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';
import { ActivatedRoute, Router } from '@angular/router';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { PlagiarismResultsService } from 'app/course/plagiarism-cases/shared/plagiarism-results.service';
import { NgForm } from '@angular/forms';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-suspicious-behavior',
    templateUrl: './suspicious-behavior.component.html',
})
export class SuspiciousBehaviorComponent implements OnInit {
    @ViewChild('analysis', { static: false }) analysisForm: NgForm;

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

    constructor(
        private suspiciousSessionsService: SuspiciousSessionsService,
        private activatedRoute: ActivatedRoute,
        private plagiarismCasesService: PlagiarismCasesService,
        private examService: ExamManagementService,
        private plagiarismResultsService: PlagiarismResultsService,
        private router: Router,
    ) {}

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
