import { Component, OnInit, inject, model } from '@angular/core';
import { GradingSystemService } from 'app/assessment/manage/grading-system/grading-system.service';
import { GradeStep, GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { faChevronLeft } from '@fortawesome/free-solid-svg-icons';
import { GradeEditMode } from 'app/assessment/manage/grading-system/base-grading-system/base-grading-system.component';
import { BonusService } from 'app/assessment/manage/grading-system/bonus/bonus.service';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { ScoreType } from 'app/shared/constants/score-type.constants';
import { ActivatedRoute } from '@angular/router';
import { loadGradingKeyUrlParams } from 'app/assessment/manage/grading-system/grading-key-overview/grading-key-helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ScoresStorageService } from 'app/core/course/manage/course-scores/scores-storage.service';

@Component({
    selector: 'jhi-grade-key-table',
    templateUrl: './grading-key-table.component.html',
    styleUrls: ['../grading-key-overview/grading-key-overview.scss'],
    imports: [TranslateDirective, ArtemisTranslatePipe, GradeStepBoundsPipe, SafeHtmlPipe, HelpIconComponent],
})
export class GradingKeyTableComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private gradingSystemService = inject(GradingSystemService);
    private bonusService = inject(BonusService);
    private scoresStorageService = inject(ScoresStorageService);

    readonly faChevronLeft = faChevronLeft;

    readonly GradeEditMode = GradeEditMode;

    studentGradeOrBonusPointsOrGradeBonus = model<string>();
    forBonus = model<boolean>();

    plagiarismGrade: string;
    noParticipationGrade: string;

    isExam = false;

    courseId?: number;
    examId?: number;

    title?: string;
    gradeSteps: GradeStep[] = [];
    isBonus = false;

    hasPointsSet = false;

    ngOnInit(): void {
        const { courseId, examId, forBonus, isExam, studentGradeOrBonusPointsOrGradeBonus } = loadGradingKeyUrlParams(this.route);
        this.courseId = courseId;
        this.examId = examId;
        this.forBonus.set(this.forBonus() || forBonus);
        this.isExam = isExam;
        this.studentGradeOrBonusPointsOrGradeBonus.set(this.studentGradeOrBonusPointsOrGradeBonus() || studentGradeOrBonusPointsOrGradeBonus);

        this.findGradeSteps(this.courseId, this.examId).subscribe((gradeSteps) => {
            if (gradeSteps) {
                this.title = gradeSteps.title;
                this.isBonus = gradeSteps.gradeType === GradeType.BONUS;
                this.gradeSteps = this.gradingSystemService.sortGradeSteps(gradeSteps.gradeSteps);
                this.plagiarismGrade = gradeSteps.plagiarismGrade;
                this.noParticipationGrade = gradeSteps.noParticipationGrade;
                if (gradeSteps.maxPoints !== undefined) {
                    if (!this.isExam) {
                        let maxPoints = 0;
                        const totalScoresForCourse = this.scoresStorageService.getStoredTotalScores(this.courseId!);
                        if (totalScoresForCourse) {
                            maxPoints = totalScoresForCourse[ScoreType.REACHABLE_POINTS];
                        }
                        this.gradingSystemService.setGradePoints(this.gradeSteps, maxPoints);
                    } else {
                        // for exams the max points filed should equal the total max points (otherwise exams can't be started)
                        this.gradingSystemService.setGradePoints(this.gradeSteps, gradeSteps.maxPoints!);
                    }
                }
            }
        });

        this.hasPointsSet = this.gradingSystemService.hasPointsSet(this.gradeSteps);
    }

    private findGradeSteps(courseId: number, examId?: number): Observable<GradeStepsDTO | undefined> {
        if (!this.forBonus()) {
            return this.gradingSystemService.findGradeSteps(courseId, examId);
        } else {
            // examId must be present if forBonus is true.
            return this.bonusService.findBonusForExam(courseId, examId!, true).pipe(
                map((bonusResponse) => {
                    const source = bonusResponse.body?.sourceGradingScale;
                    if (!source) {
                        return undefined;
                    }
                    return {
                        title: this.gradingSystemService.getGradingScaleTitle(source)!,
                        gradeType: source.gradeType,
                        gradeSteps: source.gradeSteps,
                        maxPoints: this.gradingSystemService.getGradingScaleMaxPoints(source),
                        plagiarismGrade: source.plagiarismGrade || GradingScale.DEFAULT_PLAGIARISM_GRADE,
                        noParticipationGrade: source.noParticipationGrade || GradingScale.DEFAULT_NO_PARTICIPATION_GRADE,
                        presentationsNumber: source.presentationsNumber,
                        presentationsWeight: source.presentationsWeight,
                    };
                }),
            );
        }
    }
}
