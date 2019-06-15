import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { AccountService } from '../core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ModelingExercise, ModelingExercisePopupService, ModelingExerciseService } from 'app/entities/modeling-exercise';
import { ModelingEditorComponent } from 'app/modeling-editor';
import { UMLModel } from '@ls1intum/apollon';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

@Component({
    selector: 'jhi-example-modeling-solution',
    templateUrl: './example-modeling-solution.component.html',
    styleUrls: ['./example-modeling-solution.component.scss'],
})
export class ExampleModelingSolutionComponent implements OnInit {
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;

    exercise: ModelingExercise;
    exerciseId: number;
    exampleSolution: UMLModel;
    isAtLeastInstructor = false;
    formattedProblemStatement: string;

    constructor(
        private exerciseService: ModelingExerciseService,
        private jhiAlertService: JhiAlertService,
        private accountService: AccountService,
        private modelingExercisePopupService: ModelingExercisePopupService,
        private route: ActivatedRoute,
        private router: Router,
        private artemisMarkdown: ArtemisMarkdown,
    ) {}

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));

        // Make sure the modeling exercise popup gets closed
        this.modelingExercisePopupService.close();

        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<ModelingExercise>) => {
            this.exercise = exerciseResponse.body;
            if (this.exercise.sampleSolutionModel) {
                this.exampleSolution = JSON.parse(this.exercise.sampleSolutionModel);
            }
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course);
            this.formattedProblemStatement = this.artemisMarkdown.htmlForMarkdown(this.exercise.problemStatement);
        });
    }

    saveExampleSolution(): void {
        if (!this.exercise || !this.modelingEditor.getCurrentModel()) {
            return;
        }
        this.exampleSolution = this.modelingEditor.getCurrentModel();
        this.exercise.sampleSolutionModel = JSON.stringify(this.exampleSolution);
        this.exerciseService.update(this.exercise).subscribe(
            (exerciseResponse: HttpResponse<ModelingExercise>) => {
                this.exercise = exerciseResponse.body;
                if (this.exercise.sampleSolutionModel) {
                    this.exampleSolution = JSON.parse(this.exercise.sampleSolutionModel);
                }
                this.jhiAlertService.success('artemisApp.modelingEditor.saveSuccessful');
            },
            (error: HttpErrorResponse) => {
                console.error(error);
                this.jhiAlertService.error(error.message);
            },
        );
    }

    async back() {
        const courseId = this.exercise.course.id;
        await this.router.navigate([`/course/${courseId}/`]);
        this.router.navigate(['/', { outlets: { popup: 'modeling-exercise/' + this.exerciseId + '/edit' } }]);
    }
}
