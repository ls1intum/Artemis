import { Component, OnInit, QueryList, ViewChildren } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { cloneDeep } from 'lodash';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercises/shared/exercise/exercise-utils';
import { UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from '../../modeling/shared/modeling-editor.component';
import { TransformationModelingExercise } from 'app/entities/transformation-modeling-exercise.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { TransformationModelingExerciseService } from 'app/exercises/transformation/manage/transformation-modeling-exercise.service';
import {
    CORRECTION_SCHEME,
    CORRECTION_SCHEME_CORRECT_ARC,
    CORRECTION_SCHEME_CORRECT_INITIAL_MARKING,
    CORRECTION_SCHEME_CORRECT_MARKING,
} from 'app/entities/correction-scheme.model';

@Component({
    selector: 'jhi-transformation-modeling-exercise-update',
    templateUrl: './transformation-modeling-exercise-update.component.html',
    styleUrls: ['./transformation-modeling-exercise-update.scss'],
})
export class TransformationModelingExerciseUpdateComponent implements OnInit {
    readonly MODELING_EDITOR_PROBLEM_STATEMENT = 0;
    readonly MODELING_EDITOR_EXAMPLE_SOLUTION = 1;

    @ViewChildren(ModelingEditorComponent)
    modelingEditors?: QueryList<ModelingEditorComponent>;

    readonly IncludedInOverallScore = IncludedInOverallScore;

    EditorMode = EditorMode;
    AssessmentType = AssessmentType;
    UMLDiagramType = UMLDiagramType;

    transformationModelingExercise: TransformationModelingExercise;
    backupExercise: TransformationModelingExercise;
    problemModel: UMLModel;
    exampleSolution: UMLModel;
    isSaving: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;

    domainCommandsProblemStatement = [new KatexCommand()];
    domainCommandsSampleSolution = [new KatexCommand()];
    examCourseId?: number;
    correctionScheme: object;
    solutionCouldNotBeGenerated = false;

    constructor(
        private jhiAlertService: JhiAlertService,
        private modelingExerciseService: ModelingExerciseService,
        private transformationModelingExerciseService: TransformationModelingExerciseService,
        private modalService: NgbModal,
        private popupService: ExerciseUpdateWarningService,
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private exerciseGroupService: ExerciseGroupService,
        private eventManager: JhiEventManager,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    get editType(): EditType {
        return this.transformationModelingExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    /**
     * Initializes all relevant data for creating or editing transformation modeling exercises
     */
    ngOnInit(): void {
        // This is used to scroll page to the top of the page, because the routing keeps the position for the
        // new page from previous page.

        window.scroll(0, 0);

        // Get the transformationModelingExercise
        this.activatedRoute.data.subscribe(({ transformationModelingExercise }) => {
            this.correctionScheme = {};
            this.transformationModelingExercise = transformationModelingExercise;

            this.transformationModelingExercise.problemDiagramType = UMLDiagramType.PetriNet;
            this.transformationModelingExercise.diagramType = UMLDiagramType.ReachabilityGraph;

            for (const key of this.getCorrectionSchemeKeys()) {
                this.correctionScheme[key] = this.getCorrectionSchemeDefaultForKey(key);
            }

            if (this.transformationModelingExercise.problemModel != undefined) {
                this.problemModel = JSON.parse(this.transformationModelingExercise.problemModel);
            }

            if (this.transformationModelingExercise.sampleSolutionModel != undefined) {
                this.exampleSolution = JSON.parse(this.transformationModelingExercise.sampleSolutionModel);
            }

            this.backupExercise = cloneDeep(this.transformationModelingExercise);
            this.examCourseId = this.transformationModelingExercise.course?.id;
        });

        this.isSaving = false;
        this.notificationText = undefined;
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]): void {
        this.transformationModelingExercise.categories = categories;
    }

    /**
     * Validates if the date is correct
     */
    validateDate(): void {
        this.exerciseService.validateDate(this.transformationModelingExercise);
    }

    save() {
        this.transformationModelingExercise.problemModel = JSON.stringify(this.getProblemStatementModel());
        this.transformationModelingExercise.sampleSolutionModel = JSON.stringify(this.getExampleSolutionModel());
        this.transformationModelingExercise.correctionScheme = JSON.stringify(this.correctionScheme);

        this.isSaving = true;

        new SaveExerciseCommand(this.modalService, this.popupService, this.modelingExerciseService, this.backupExercise, this.editType)
            .save(this.transformationModelingExercise, this.notificationText)
            .subscribe(
                () => this.onSaveSuccess(),
                (error: HttpErrorResponse) => this.onSaveError(error),
                () => {
                    this.isSaving = false;
                },
            );
    }

    /**
     * Return to the previous page or a default if no previous page exists
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.transformationModelingExercise);
    }

    private onSaveSuccess(): void {
        this.eventManager.broadcast({ name: 'modelingExerciseListModification', content: 'OK' });
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse): void {
        onError(this.jhiAlertService, error);
        this.isSaving = false;
    }

    getProblemStatementModel(): UMLModel {
        return this.modelingEditors!.get(this.MODELING_EDITOR_PROBLEM_STATEMENT)!.getCurrentModel();
    }

    getExampleSolutionModel(): UMLModel {
        return this.modelingEditors!.get(this.MODELING_EDITOR_EXAMPLE_SOLUTION)!.getCurrentModel();
    }

    setExampleSolutionModel(model: UMLModel): void {
        this.modelingEditors!.get(this.MODELING_EDITOR_EXAMPLE_SOLUTION)!.umlModel = model;
    }

    getCorrectionSchemeKeys(): Array<string> {
        return Object.keys(CORRECTION_SCHEME[this.transformationModelingExercise.diagramType!]);
    }

    getCorrectionSchemeDefaultForKey(key: string): number {
        return CORRECTION_SCHEME[this.transformationModelingExercise.diagramType!][key];
    }

    fetchSolution(): void {
        this.transformationModelingExerciseService.fetchSolution(this.getProblemStatementModel()).subscribe(
            (model: UMLModel) => {
                this.solutionCouldNotBeGenerated = false;
                this.setExampleSolutionModel(model);
                this.exampleSolution = model;
                this.transformationModelingExercise.maxPoints =
                    model.elements.length * this.correctionScheme[CORRECTION_SCHEME_CORRECT_MARKING] +
                    model.relationships.length * this.correctionScheme[CORRECTION_SCHEME_CORRECT_ARC] +
                    this.correctionScheme[CORRECTION_SCHEME_CORRECT_INITIAL_MARKING];
            },
            () => (this.solutionCouldNotBeGenerated = true),
        );
    }
}
