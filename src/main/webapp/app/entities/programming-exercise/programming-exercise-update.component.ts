import { ActivatedRoute } from '@angular/router';
import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Observable } from 'rxjs';

import { Course, CourseService } from 'app/entities/course';
import { ExerciseCategory, ExerciseService } from 'app/entities/exercise';
import { FileService } from 'app/shared/http/file.service';

import { Feedback } from '../feedback';
import { ProgrammingExercise, ProgrammingLanguage } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';
import { RepositoryFileService } from '../repository';
import { ResultService } from '../result';

@Component({
    selector: 'jhi-programming-exercise-update',
    templateUrl: './programming-exercise-update.component.html',
})
export class ProgrammingExerciseUpdateComponent implements OnInit {
    readonly JAVA = ProgrammingLanguage.JAVA;
    readonly PYTHON = ProgrammingLanguage.PYTHON;

    programmingExercise: ProgrammingExercise;
    isSaving: boolean;

    maxScorePattern = '^[1-9]{1}[0-9]{0,4}$'; // make sure max score is a positive natural integer and not too large
    packageNamePattern = '^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+[0-9a-z_]$'; // package name must have at least 1 dot and must not start with a number
    shortNamePattern = '^[a-zA-Z][a-zA-Z0-9]*'; // must start with a letter and cannot contain special characters
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    courses: Course[];
    latestResult: Array<any>;
    resultDetails: Feedback[];

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private courseService: CourseService,
        private jhiAlertService: JhiAlertService,
        private exerciseService: ExerciseService,
        private activatedRoute: ActivatedRoute,
        private repositoryFileService: RepositoryFileService,
        private fileService: FileService,
        private resultService: ResultService,
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            this.programmingExercise = programmingExercise;
            // If the exercise is being created, insert the instruction template into the problem statement.
            if (this.programmingExercise.id === undefined) {
                this.fileService.getTemplateFile('programming-exercise-instructions').subscribe(file => (this.programmingExercise.problemStatement = file));
                // Historical fallback: Older exercises have an instruction file in the git repo
            } else {
                if (this.programmingExercise.problemStatement === undefined) {
                    this.repositoryFileService.get(this.programmingExercise.templateParticipation.id, 'README.md').subscribe(
                        fileObj => {
                            this.programmingExercise.problemStatement = fileObj.fileContent;
                        },
                        err => {
                            // TODO: handle the case that there is no README.md file
                            console.log('Error while getting README.md file!', err);
                        },
                    );
                }
            }
        });
        this.activatedRoute.params.subscribe(params => {
            if (params['courseId']) {
                const courseId = params['courseId'];
                this.courseService.find(courseId).subscribe(res => {
                    const course = res.body;
                    this.programmingExercise.course = course;
                    this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.programmingExercise);
                    this.courseService.findAllCategoriesOfCourse(this.programmingExercise.course.id).subscribe(
                        (categoryRes: HttpResponse<string[]>) => {
                            this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body);
                        },
                        (categoryRes: HttpErrorResponse) => this.onError(categoryRes),
                    );
                    this.resultService
                        .findResultsForParticipation(this.programmingExercise.course.id, this.programmingExercise.id, this.programmingExercise.templateParticipation.id, {
                            showAllResults: true,
                        })
                        .subscribe((latestResult: any) => {
                            this.latestResult = latestResult.body;
                            if (this.latestResult.length) {
                                this.resultService.getFeedbackDetailsForResult(this.latestResult[0].id).subscribe(resultDetails => {
                                    this.resultDetails = resultDetails.body;
                                });
                            }
                        });
                });
            }
        });
        this.courseService.query().subscribe(
            (res: HttpResponse<Course[]>) => {
                this.courses = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    previousState() {
        window.history.back();
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.programmingExercise.categories = categories.map(el => JSON.stringify(el));
    }

    save() {
        this.isSaving = true;
        if (this.programmingExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.programmingExerciseService.update(this.programmingExercise));
        } else {
            this.subscribeToSaveResponse(this.programmingExerciseService.automaticSetup(this.programmingExercise));
        }
    }

    private renderInstructions(markdown: string) {
        // return this.markdownService.renderInstructions(markdown, undefined, undefined);
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe((res: HttpResponse<ProgrammingExercise>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError(res));
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-arTeMiSApp-alert');
        // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
        const jhiAlert = this.jhiAlertService.error(errorMessage);
        jhiAlert.msg = errorMessage;
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    trackCourseById(index: number, item: Course) {
        return item.id;
    }
}
