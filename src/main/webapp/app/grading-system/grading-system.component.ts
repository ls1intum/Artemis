import { Component, OnInit } from '@angular/core';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { GradeStep } from 'app/entities/grade-step.model';
import { ActivatedRoute } from '@angular/router';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-grading-system',
    templateUrl: './grading-system.component.html',
    styleUrls: ['./grading-system.component.scss'],
})
export class GradingSystemComponent implements OnInit {
    ButtonSize = ButtonSize;
    gradingScale: GradingScale;
    lowerBoundInclusivity = true;
    existingGradingScale = false;
    firstPassingGrade: string;
    courseId?: number;
    examId?: number;
    isExam = false;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(private gradingSystemService: GradingSystemService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            if (params['examId']) {
                this.examId = Number(params['examId']);
                this.isExam = true;
            }
        });
        if (this.isExam) {
            this.gradingSystemService.findGradingScaleForExam(this.courseId!, this.examId!).subscribe((gradingSystemResponse) => {
                if (gradingSystemResponse.body) {
                    this.handleFindResponse(gradingSystemResponse.body);
                }
            });
        } else {
            this.gradingSystemService.findGradingScaleForCourse(this.courseId!).subscribe((gradingSystemResponse) => {
                if (gradingSystemResponse.body) {
                    this.handleFindResponse(gradingSystemResponse.body);
                }
            });
        }
    }

    private handleFindResponse(gradingScale?: GradingScale) {
        if (gradingScale) {
            gradingScale.gradeSteps = this.sortGradeSteps(gradingScale.gradeSteps);
            this.gradingScale = gradingScale;
            this.existingGradingScale = true;
            this.setBoundInclusivity();
            this.determineFirstPassingGrade();
        }
    }

    save(): void {
        this.gradingScale.gradeSteps = this.sortGradeSteps(this.gradingScale.gradeSteps);
        this.gradingScale.gradeSteps = this.setInclusivity(this.gradingScale.gradeSteps);
        this.gradingScale.gradeSteps = this.setPassingGrades(this.gradingScale.gradeSteps);
        this.gradingScale.gradeSteps.forEach((gradeStep) => {
            gradeStep.id = undefined;
        });
        if (this.existingGradingScale) {
            if (this.isExam) {
                this.gradingSystemService.updateGradingScaleForExam(this.courseId!, this.examId!, this.gradingScale).subscribe((gradingSystemResponse) => {
                    this.handleSaveResponse(gradingSystemResponse.body!);
                });
            } else {
                this.gradingSystemService.updateGradingScaleForCourse(this.courseId!, this.gradingScale).subscribe((gradingSystemResponse) => {
                    this.handleSaveResponse(gradingSystemResponse.body!);
                });
            }
        } else {
            if (this.isExam) {
                this.gradingSystemService.createGradingScaleForExam(this.courseId!, this.examId!, this.gradingScale).subscribe((gradingSystemResponse) => {
                    this.handleSaveResponse(gradingSystemResponse.body!);
                });
            } else {
                this.gradingSystemService.createGradingScaleForCourse(this.courseId!, this.gradingScale).subscribe((gradingSystemResponse) => {
                    this.handleSaveResponse(gradingSystemResponse.body!);
                });
            }
        }
    }

    private handleSaveResponse(newGradingScale?: GradingScale): void {
        if (newGradingScale) {
            newGradingScale.gradeSteps = this.sortGradeSteps(newGradingScale.gradeSteps);
            this.gradingScale = newGradingScale;
            this.existingGradingScale = true;
        }
    }

    delete(): void {
        if (!this.existingGradingScale) {
            return;
        }
        if (this.isExam) {
            this.gradingSystemService.deleteGradingScaleForExam(this.courseId!, this.examId!).subscribe(() => {
                this.existingGradingScale = false;
                this.dialogErrorSource.next('');
            });
        } else {
            this.gradingSystemService.deleteGradingScaleForCourse(this.courseId!).subscribe(() => {
                this.existingGradingScale = false;
                this.dialogErrorSource.next('');
            });
        }
    }

    sortGradeSteps(gradeSteps: GradeStep[]): GradeStep[] {
        return gradeSteps.sort((gradeStep1, gradeStep2) => {
            return gradeStep1.lowerBoundPercentage - gradeStep2.lowerBoundPercentage;
        });
    }

    setBoundInclusivity(): void {
        this.lowerBoundInclusivity = this.gradingScale.gradeSteps.every((gradeStep) => {
            return gradeStep.lowerBoundInclusive || gradeStep.lowerBoundPercentage === 0;
        });
    }

    setInclusivity(gradeSteps: GradeStep[]): GradeStep[] {
        gradeSteps.forEach((gradeStep) => {
            if (this.lowerBoundInclusivity) {
                gradeStep.lowerBoundInclusive = true;
                gradeStep.upperBoundInclusive = gradeStep.upperBoundPercentage === 100;
            } else {
                gradeStep.upperBoundInclusive = true;
                gradeStep.lowerBoundInclusive = gradeStep.lowerBoundPercentage === 0;
            }
        });
        return gradeSteps;
    }

    determineFirstPassingGrade(): void {
        this.firstPassingGrade =
            this.gradingScale.gradeSteps.find((gradeStep) => {
                return gradeStep.isPassingGrade;
            })?.gradeName ?? this.gradingScale.gradeSteps[this.gradingScale.gradeSteps.length - 1].gradeName;
    }

    setPassingGrades(gradeSteps: GradeStep[]): GradeStep[] {
        let passingGrade = false;
        gradeSteps.forEach((gradeStep) => {
            if (gradeStep.gradeName === this.firstPassingGrade) {
                passingGrade = true;
            }
            gradeStep.isPassingGrade = passingGrade;
        });
        return gradeSteps;
    }

    isGradeType(): boolean {
        return this.gradingScale.gradeType === GradeType.GRADE;
    }

    createGradeStep(): void {
        const gradeStep: GradeStep = {
            gradeName: '',
            lowerBoundPercentage: 100,
            upperBoundPercentage: 100,
            isPassingGrade: true,
            lowerBoundInclusive: this.lowerBoundInclusivity,
            upperBoundInclusive: !this.lowerBoundInclusivity,
        };
        if (!this.gradingScale) {
            this.gradingScale = new GradingScale();
        }
        if (!this.gradingScale.gradeSteps) {
            this.gradingScale.gradeSteps = [];
        }
        this.gradingScale.gradeSteps.push(gradeStep);
    }

    deleteGradeStep(index: number): void {
        this.gradingScale.gradeSteps.splice(index, 1);
    }

    generateDefaultGradingScale(): void {
        this.gradingScale = this.getDefaultGradingScale();
        this.gradingScale.gradeType = GradeType.GRADE;
        this.firstPassingGrade = this.gradingScale.gradeSteps[3].gradeName;
        this.lowerBoundInclusivity = true;
    }

    getDefaultGradingScale(): GradingScale {
        const gradeStep1: GradeStep = {
            gradeName: '5.0',
            lowerBoundPercentage: 0,
            upperBoundPercentage: 40,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: false,
        };
        const gradeStep2: GradeStep = {
            gradeName: '4.7',
            lowerBoundPercentage: 40,
            upperBoundPercentage: 45,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: false,
        };
        const gradeStep3: GradeStep = {
            gradeName: '4.3',
            lowerBoundPercentage: 45,
            upperBoundPercentage: 50,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: false,
        };
        const gradeStep4: GradeStep = {
            gradeName: '4.0',
            lowerBoundPercentage: 50,
            upperBoundPercentage: 55,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep5: GradeStep = {
            gradeName: '3.7',
            lowerBoundPercentage: 55,
            upperBoundPercentage: 60,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep6: GradeStep = {
            gradeName: '3.3',
            lowerBoundPercentage: 60,
            upperBoundPercentage: 65,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep7: GradeStep = {
            gradeName: '3.0',
            lowerBoundPercentage: 65,
            upperBoundPercentage: 70,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep8: GradeStep = {
            gradeName: '2.7',
            lowerBoundPercentage: 70,
            upperBoundPercentage: 75,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep9: GradeStep = {
            gradeName: '2.3',
            lowerBoundPercentage: 75,
            upperBoundPercentage: 80,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep10: GradeStep = {
            gradeName: '2.0',
            lowerBoundPercentage: 80,
            upperBoundPercentage: 85,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep11: GradeStep = {
            gradeName: '1.7',
            lowerBoundPercentage: 85,
            upperBoundPercentage: 90,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep12: GradeStep = {
            gradeName: '1.3',
            lowerBoundPercentage: 90,
            upperBoundPercentage: 95,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep13: GradeStep = {
            gradeName: '1.0',
            lowerBoundPercentage: 95,
            upperBoundPercentage: 100,
            lowerBoundInclusive: true,
            upperBoundInclusive: true,
            isPassingGrade: true,
        };
        return {
            gradeSteps: [
                gradeStep1,
                gradeStep2,
                gradeStep3,
                gradeStep4,
                gradeStep5,
                gradeStep6,
                gradeStep7,
                gradeStep8,
                gradeStep9,
                gradeStep10,
                gradeStep11,
                gradeStep12,
                gradeStep13,
            ],
            gradeType: GradeType.GRADE,
        };
    }
}
