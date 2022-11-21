import { Component, OnInit } from '@angular/core';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { GradeStep } from 'app/entities/grade-step.model';
import { ActivatedRoute } from '@angular/router';
import { EntityResponseType, GradingSystemService } from 'app/grading-system/grading-system.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { Observable, Subject, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { catchError, finalize } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExportToCsv } from 'export-to-csv';
import { faExclamationTriangle, faPlus, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';

const csvColumnsGrade = Object.freeze({
    gradeName: 'gradeName',
    lowerBoundPercentage: 'lowerBoundPercentage',
    upperBoundPercentage: 'upperBoundPercentage',
    isPassingGrade: 'isPassingGrade',
});

const csvColumnsBonus = Object.freeze({
    bonusPoints: 'bonusPoints',
    lowerBoundPercentage: 'lowerBoundPercentage',
    upperBoundPercentage: 'upperBoundPercentage',
});

// needed to map from csv object to grade step
export type CsvGradeStep = object;

export enum GradeEditMode {
    POINTS,
    PERCENTAGE,
}

@Component({ template: '' })
export abstract class BaseGradingSystemComponent implements OnInit {
    GradeType = GradeType;
    ButtonSize = ButtonSize;
    GradingScale = GradingScale;

    gradingScale = new GradingScale();
    lowerBoundInclusivity = true;
    existingGradingScale = false;
    firstPassingGrade?: string;
    courseId?: number;
    examId?: number;
    isExam = false;
    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    isLoading = false;
    invalidGradeStepsMessage?: string;

    course?: Course;
    exam?: Exam;
    maxPoints?: number;

    // Icons
    faSave = faSave;
    faPlus = faPlus;
    faTimes = faTimes;
    faExclamationTriangle = faExclamationTriangle;

    constructor(
        protected gradingSystemService: GradingSystemService,
        private route: ActivatedRoute,
        private translateService: TranslateService,
        private courseService: CourseManagementService,
        private examService: ExamManagementService,
    ) {}

    ngOnInit(): void {
        this.route.parent?.params.subscribe((params) => {
            this.isLoading = true;
            this.courseId = Number(params['courseId']);
            if (params['examId']) {
                this.examId = Number(params['examId']);
                this.isExam = true;
            }
            if (this.isExam) {
                this.handleFindObservable(this.gradingSystemService.findGradingScaleForExam(this.courseId!, this.examId!));
            } else {
                this.handleFindObservable(this.gradingSystemService.findGradingScaleForCourse(this.courseId!));
            }
        });
    }

    private handleFindObservable(findObservable: Observable<EntityResponseType>) {
        findObservable
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe((gradingSystemResponse) => {
                if (gradingSystemResponse.body) {
                    this.handleFindResponse(gradingSystemResponse.body);
                }
                if (this.isExam) {
                    this.examService.find(this.courseId!, this.examId!).subscribe((examResponse) => {
                        this.exam = examResponse.body!;
                        this.maxPoints = this.exam?.maxPoints;
                        this.onChangeMaxPoints(this.exam?.maxPoints);
                    });
                } else {
                    this.courseService.find(this.courseId!).subscribe((courseResponse) => {
                        this.course = courseResponse.body!;
                        this.maxPoints = this.course?.maxPoints;
                        this.onChangeMaxPoints(this.course?.maxPoints);
                    });
                }
            });
    }

    /**
     * If the grading scale exists, sorts its grade steps,
     * and sets the inclusivity and first passing grade properties
     *
     * @param gradingScale the grading scale retrieved from the get request
     * @private
     */
    handleFindResponse(gradingScale?: GradingScale): void {
        if (gradingScale) {
            gradingScale.gradeSteps = this.gradingSystemService.sortGradeSteps(gradingScale.gradeSteps);
            this.gradingScale = gradingScale;
            this.existingGradingScale = true;
            this.setBoundInclusivity();
            this.determineFirstPassingGrade();
        }
    }

    /**
     * Sorts the grade steps by lower bound percentage, sets their inclusivity
     * and passing grade properties and saves the grading scale via the service
     */
    save(): void {
        this.isLoading = true;
        this.gradingScale.gradeSteps = this.gradingSystemService.sortGradeSteps(this.gradingScale.gradeSteps);
        this.setInclusivity();
        this.gradingScale.gradeSteps = this.setPassingGrades(this.gradingScale.gradeSteps);
        // new grade steps shouldn't have ids set
        this.gradingScale.gradeSteps.forEach((gradeStep) => {
            gradeStep.id = undefined;
        });
        if (this.isExam) {
            this.gradingScale.exam = this.exam;
            this.gradingScale.exam!.maxPoints = this.maxPoints;
        } else {
            this.gradingScale.course = this.course;
            this.gradingScale.course!.maxPoints = this.maxPoints;
        }
        if (this.existingGradingScale) {
            if (this.isExam) {
                this.handleSaveObservable(this.gradingSystemService.updateGradingScaleForExam(this.courseId!, this.examId!, this.gradingScale));
            } else {
                this.handleSaveObservable(this.gradingSystemService.updateGradingScaleForCourse(this.courseId!, this.gradingScale));
            }
        } else {
            if (this.isExam) {
                this.handleSaveObservable(this.gradingSystemService.createGradingScaleForExam(this.courseId!, this.examId!, this.gradingScale));
            } else {
                this.handleSaveObservable(this.gradingSystemService.createGradingScaleForCourse(this.courseId!, this.gradingScale));
            }
        }
    }

    /**
     * Checks if the currently entered grade steps are valid based on multiple criteria:
     * - there must be at least one grade step
     * - if max points are defined, they should be at least 0
     * - all fields must be filled out
     * - the percentage values must be at least 0
     * - if max points are defined, all points values must be at least 0
     * - all grade names must be unique
     * - the first passing must be set if the scale is of GRADE type
     * - the bonus points are at least 0 if the scale is of BONUS type
     * - the bonus points must be strictly ascending in values
     * - the max and min % of adjacent grade steps overlap
     * - the first grade step begins at 0%
     */
    validGradeSteps(): boolean {
        if (!this.gradingScale || this.gradingScale.gradeSteps.length === 0) {
            this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.empty');
            return false;
        }
        // check if max points are at least 0, if they are defined
        if (this.maxPoints != undefined && this.maxPoints! < 0) {
            this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.negativeMaxPoints');
            return false;
        }
        // check if any of the fields are empty
        for (const gradeStep of this.gradingScale.gradeSteps) {
            if (gradeStep.gradeName === '' || gradeStep.gradeName === null || gradeStep.lowerBoundPercentage === null || gradeStep.upperBoundPercentage === null) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.emptyFields');
                return false;
            }
            if (this.maxPointsValid() && (gradeStep.lowerBoundPoints == undefined || gradeStep.upperBoundPoints == undefined)) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.emptyFields');
                return false;
            }
        }
        // check if any of the fields have invalid percentages
        for (const gradeStep of this.gradingScale.gradeSteps) {
            if (gradeStep.lowerBoundPercentage < 0 || gradeStep.lowerBoundPercentage > gradeStep.upperBoundPercentage) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidMinMaxPercentages');
                return false;
            }
        }
        // check if any of the fields have invalid points
        if (this.maxPointsValid()) {
            for (const gradeStep of this.gradingScale.gradeSteps) {
                if (gradeStep.lowerBoundPoints! < 0 || gradeStep.lowerBoundPoints! > gradeStep.upperBoundPoints!) {
                    this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidMinMaxPoints');
                    return false;
                }
            }
        } else {
            // ensures that all updated have taken place before the grading key can be saved, not really an error, therefore no message is necessary
            for (const gradeStep of this.gradingScale.gradeSteps) {
                if (gradeStep.lowerBoundPoints != undefined || gradeStep.upperBoundPoints != undefined) {
                    return false;
                }
            }
        }
        if (this.gradingScale.gradeType === GradeType.GRADE) {
            // check if all grade names are unique if the grading scale is of type GRADE
            if (!this.gradingScale.gradeSteps.map((gradeStep) => gradeStep.gradeName).every((gradeName, index, gradeNames) => gradeNames.indexOf(gradeName) === index)) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.nonUniqueGradeNames');
                return false;
            }
            // check if the first passing grade is set if the grading scale is of type GRADE
            if (this.firstPassingGrade === undefined || this.firstPassingGrade === '') {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.unsetFirstPassingGrade');
                return false;
            }
        }
        // copy the grade steps in a separate array, so they don't get dynamically updated when sorting
        let sortedGradeSteps: GradeStep[] = [];
        this.gradingScale.gradeSteps.forEach((gradeStep) => sortedGradeSteps.push(Object.assign({}, gradeStep)));
        sortedGradeSteps = this.gradingSystemService.sortGradeSteps(sortedGradeSteps);
        if (this.gradingScale.gradeType === GradeType.BONUS) {
            // check if when the grade type is BONUS the bonus points are at least 0
            for (const gradeStep of sortedGradeSteps) {
                if (isNaN(Number(gradeStep.gradeName)) || Number(gradeStep.gradeName) < 0) {
                    this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidBonusPoints');
                    return false;
                }
            }
            // check if when the grade type is BONUS the bonus points have strictly ascending values
            if (
                !sortedGradeSteps
                    .map((gradeStep) => Number(gradeStep.gradeName))
                    .every((bonusPoints, index, bonusPointsArray) => index === 0 || bonusPoints > bonusPointsArray[index - 1])
            ) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.nonStrictlyIncreasingBonusPoints');
                return false;
            }
        }

        // check if grade steps have valid adjacency
        for (let i = 0; i < sortedGradeSteps.length - 1; i++) {
            if (sortedGradeSteps[i].upperBoundPercentage !== sortedGradeSteps[i + 1].lowerBoundPercentage) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidAdjacency');
                return false;
            }
        }
        // check if first and last grade step are valid
        if (sortedGradeSteps[0].lowerBoundPercentage !== 0 || sortedGradeSteps.last()!.upperBoundPercentage < 100) {
            this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidFirstAndLastStep');
            return false;
        }
        this.invalidGradeStepsMessage = undefined;
        return true;
    }

    private handleSaveObservable(saveObservable: Observable<EntityResponseType>) {
        saveObservable
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                catchError(() => of(new HttpResponse<GradingScale>({ status: 400 }))),
            )
            .subscribe((gradingSystemResponse) => {
                this.handleSaveResponse(gradingSystemResponse.body!);
            });
    }

    /**
     * Sorts the grading scale's grade steps after it has been saved
     * and sets the existingGradingScale property
     *
     * @param newGradingScale the grading scale that was just saved
     * @private
     */
    private handleSaveResponse(newGradingScale?: GradingScale): void {
        if (newGradingScale) {
            newGradingScale.gradeSteps = this.gradingSystemService.sortGradeSteps(newGradingScale.gradeSteps);
            this.existingGradingScale = true;
        }
    }

    /**
     * Determines if the max points for the course/exam are valid
     */
    maxPointsValid(): boolean {
        return this.maxPoints != undefined && this.maxPoints! > 0;
    }

    /**
     * Sets the percentage value of a grade step for one of its bounds
     *
     * @param gradeStep the grade step
     * @param lowerBound the bound
     */
    setPercentage(gradeStep: GradeStep, lowerBound: boolean) {
        if (lowerBound) {
            gradeStep.lowerBoundPercentage = (gradeStep.lowerBoundPoints! / this.maxPoints!) * 100;
        } else {
            gradeStep.upperBoundPercentage = (gradeStep.upperBoundPoints! / this.maxPoints!) * 100;
        }
    }

    /**
     * Sets the absolute points value of a grade step for one of its bounds.
     * Sets the value only if the course/exam has max points set
     *
     * @param gradeStep the grade step
     * @param lowerBound the bound
     */
    setPoints(gradeStep: GradeStep, lowerBound: boolean): void {
        if (!this.maxPoints) {
            return;
        } else {
            if (lowerBound) {
                gradeStep.lowerBoundPoints = (this.maxPoints! * gradeStep.lowerBoundPercentage) / 100;
            } else {
                gradeStep.upperBoundPoints = (this.maxPoints! * gradeStep.upperBoundPercentage) / 100;
            }
        }
    }

    /**
     * Recalculates both point bounds of all grade steps in the grading scale based on the new max points value
     *
     * @param maxPoints
     */
    onChangeMaxPoints(maxPoints?: number): void {
        // if max points aren't defined, the grade step point bounds should also be undefined
        if (maxPoints == undefined || maxPoints <= 0) {
            for (const gradeStep of this.gradingScale.gradeSteps) {
                gradeStep.lowerBoundPoints = undefined;
                gradeStep.upperBoundPoints = undefined;
            }
        } else {
            for (const gradeStep of this.gradingScale.gradeSteps) {
                this.setPoints(gradeStep, true);
                this.setPoints(gradeStep, false);
            }
        }
    }

    /**
     * Deletes a grading scale for the given course/exam via the service
     */
    delete(): void {
        if (!this.existingGradingScale) {
            return;
        }
        this.isLoading = true;
        if (this.isExam) {
            this.handleDeleteObservable(this.gradingSystemService.deleteGradingScaleForExam(this.courseId!, this.examId!));
        } else {
            this.handleDeleteObservable(this.gradingSystemService.deleteGradingScaleForCourse(this.courseId!));
        }
        this.gradingScale = new GradingScale();
    }

    handleDeleteObservable(deleteObservable: Observable<EntityResponseType>) {
        deleteObservable
            .pipe(
                catchError(() => of(new HttpResponse<GradingScale>({ status: 400 }))),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(() => {
                this.existingGradingScale = false;
                this.dialogErrorSource.next('');
            });
    }

    /**
     * Sets the lowerBoundInclusivity property based on grade steps based on the grade steps
     * Called on initialization
     */
    setBoundInclusivity(): void {
        this.lowerBoundInclusivity = this.gradingScale.gradeSteps.every((gradeStep) => {
            return gradeStep.lowerBoundInclusive || gradeStep.lowerBoundPercentage === 0;
        });
    }

    /**
     * Sets the inclusivity for all grade steps based on the lowerBoundInclusivity property
     * Called before a post/put request
     *
     * @param gradeSteps the grade steps which will be adjusted
     * @abstract
     */
    abstract setInclusivity(): void;

    /**
     * Sets the firstPassingGrade property based on the grade steps
     * Called on initialization
     */
    determineFirstPassingGrade(): void {
        this.firstPassingGrade = this.gradingScale.gradeSteps.find((gradeStep) => {
            return gradeStep.isPassingGrade;
        })?.gradeName;
    }

    /**
     * Sets the isPassingGrade property for all grade steps based on the lowerBoundInclusive property
     * Called before a post/put request
     *
     * @param gradeSteps the grade steps which will be adjusted
     */
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

    deleteGradeNames(): void {
        this.gradingScale.gradeSteps.forEach((gradeStep) => {
            gradeStep.gradeName = '';
        });
    }

    gradeStepsWithNonemptyNames(): GradeStep[] {
        if (this.gradingScale.gradeSteps) {
            return this.gradingScale.gradeSteps.filter((gradeStep) => {
                return gradeStep.gradeName !== '';
            });
        } else {
            return [];
        }
    }

    /**
     * Create a new grade step add the end of the current grade step set
     */
    createGradeStep(): void {
        if (!this.gradingScale) {
            this.gradingScale = new GradingScale();
        }
        if (!this.gradingScale.gradeSteps) {
            this.gradingScale.gradeSteps = [];
        }
        const gradeStepsArrayLength = this.gradingScale.gradeSteps.length;
        const lowerBound = gradeStepsArrayLength === 0 ? 0 : this.gradingScale.gradeSteps.last()!.upperBoundPercentage;
        const gradeStep: GradeStep = {
            gradeName: '',
            lowerBoundPercentage: lowerBound,
            upperBoundPercentage: 100,
            isPassingGrade: true,
            lowerBoundInclusive: this.lowerBoundInclusivity,
            upperBoundInclusive: true,
        };
        this.setPoints(gradeStep, true);
        this.setPoints(gradeStep, false);
        this.gradingScale.gradeSteps.push(gradeStep);
    }

    /**
     * Delete grade step at given index
     *
     * @param index the index of the grade step
     */
    deleteGradeStep(index: number): void {
        this.gradingScale.gradeSteps.splice(index, 1);
    }

    /**
     * Generates a default grading scale to be used as template
     */
    generateDefaultGradingScale(): void {
        this.gradingScale = this.getDefaultGradingScale();
        this.firstPassingGrade = this.gradingScale.gradeSteps[3].gradeName;
        this.lowerBoundInclusivity = true;
    }

    /**
     * Returns the mock grading scale from the university course PSE
     */
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
        const gradeSteps = [
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
        ];
        for (const gradeStep of gradeSteps) {
            this.setPoints(gradeStep, true);
            this.setPoints(gradeStep, false);
        }
        return {
            gradeSteps,
            gradeType: GradeType.GRADE,
        };
    }

    /**
     * Triggered when csv file is uploaded
     * @param event file read event
     */
    async onCSVFileSelect(event: any) {
        if (event.target.files.length > 0) {
            await this.readGradingStepsFromCSVFile(event, event.target.files[0]);
            this.lowerBoundInclusivity = true;
            this.setInclusivity();
            this.maxPoints = 100;
            this.onChangeMaxPoints(this.maxPoints);
            this.determineFirstPassingGrade();
            this.gradingScale.gradeSteps.sort((a, b) => a.lowerBoundPercentage - b.lowerBoundPercentage);
        }
    }

    /**
     * Import grade steps from csv file
     * @param event the read event
     * @param csvFile the csv file
     * @private
     */
    async readGradingStepsFromCSVFile(event: any, csvFile: File) {
        let csvGradeSteps: CsvGradeStep[] = [];
        try {
            csvGradeSteps = await this.parseCSVFile(csvFile);
        } catch (error) {
            return [];
        }

        if (csvGradeSteps.length === 0 || csvGradeSteps.length > 100) {
            this.gradingScale.gradeSteps = [];
            return;
        }

        // parse and set the grade type
        const gradeType = csvGradeSteps[0]['bonusPoints'] === undefined ? GradeType.GRADE : GradeType.BONUS;
        if (gradeType === GradeType.BONUS) {
            this.gradingScale.gradeType = GradeType.BONUS;
        } else {
            this.gradingScale.gradeType = GradeType.GRADE;
        }

        this.gradingScale.gradeSteps = this.mapCsvGradeStepsToGradeSteps(csvGradeSteps, gradeType);
    }

    /**
     * Map the imported csv objects to GradeStep object
     * In case that the grade type of the imported file is bonus, attribute gradeName will be set with the bonusPoints attribute
     * @param csvGradeSteps Imported grade steps as csv objects
     * @param gradeType Implicit Grade Type of imported csv file
     */
    mapCsvGradeStepsToGradeSteps(csvGradeSteps: CsvGradeStep[], gradeType: GradeType): GradeStep[] {
        return csvGradeSteps.map(
            (csvGradeStep) =>
                ({
                    gradeName: gradeType === GradeType.GRADE ? String(csvGradeStep[csvColumnsGrade.gradeName] ?? '') : String(csvGradeStep[csvColumnsBonus.bonusPoints] ?? ''),
                    lowerBoundPercentage: csvGradeStep[csvColumnsGrade.lowerBoundPercentage] ? Number(csvGradeStep[csvColumnsGrade.lowerBoundPercentage]) : undefined,
                    upperBoundPercentage: csvGradeStep[csvColumnsGrade.upperBoundPercentage] ? Number(csvGradeStep[csvColumnsGrade.upperBoundPercentage]) : undefined,
                    ...(gradeType === GradeType.GRADE && { isPassingGrade: csvGradeStep[csvColumnsGrade.isPassingGrade] === 'TRUE' }),
                } as GradeStep),
        );
    }

    /**
     * Parse CSV file to a list of CsvGradeStep objects
     * This method uses the 'papaparse' dependency, but Angular can not lazy load it in an abstract class.
     * Therefore, the implementation is moved to the concrete classes (code duplication for the benefit of reducing main bundle size).
     * @param csvFile the read csv file
     * @abstract
     */
    abstract parseCSVFile(csvFile: File): Promise<CsvGradeStep[]>;

    /**
     * Download the current grade steps to a csv file to the client
     */
    exportGradingStepsToCsv(): void {
        const headers = this.gradingScale.gradeType === GradeType.GRADE ? Object.keys(csvColumnsGrade) : Object.keys(csvColumnsBonus);

        const rows = this.gradingScale.gradeSteps.map((gradeStep) => this.convertToCsvRow(gradeStep));

        this.exportAsCSV(rows, headers);
    }

    /**
     * Convert a grade step to a csv row. Undefined values are mapped to empty strings
     * @param gradeStep
     */
    convertToCsvRow(gradeStep: GradeStep): any {
        return {
            ...(this.gradingScale.gradeType === GradeType.GRADE && { gradeName: gradeStep.gradeName ?? '' }),
            ...(this.gradingScale.gradeType === GradeType.BONUS && { bonusPoints: gradeStep.gradeName ?? '' }),
            lowerBoundPercentage: gradeStep.lowerBoundPercentage ?? '',
            upperBoundPercentage: gradeStep.upperBoundPercentage ?? '',
            ...(this.gradingScale.gradeType === GradeType.GRADE && { isPassingGrade: gradeStep.isPassingGrade }),
        };
    }

    /**
     * Start the user download for the csv file
     * @param rows rows representing each a grade step in csv structure
     * @param headers names of the csv columns
     */
    exportAsCSV(rows: any[], headers: string[]): void {
        const options = {
            fieldSeparator: ',',
            quoteStrings: '',
            decimalSeparator: 'locale',
            showLabels: true,
            filename: 'grading_key' + (this.gradingScale.course?.shortName ? '_' + this.gradingScale.course?.shortName : ''),
            useTextFile: false,
            useBom: true,
            headers,
        };

        const csvExporter = new ExportToCsv(options);
        csvExporter.generateCsv(rows);
    }
}
