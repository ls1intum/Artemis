import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

@Component({
    selector: 'jhi-plagiarism-inspector',
    styleUrls: ['./plagiarism-inspector.component.scss'],
    templateUrl: './plagiarism-inspector.component.html',
})
export class PlagiarismInspectorComponent implements OnDestroy, OnInit {
    modelingExercise: ModelingExercise;
    splitControlSubject: Subject<string> = new Subject<string>();

    private subscription: Subscription;

    constructor(private route: ActivatedRoute, private modelingExerciseService: ModelingExerciseService) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.modelingExerciseService.find(params['exerciseId']).subscribe((response: HttpResponse<ModelingExercise>) => {
                this.modelingExercise = response.body!;
            });
        });
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    checkPlagiarism() {
        console.log('Check plagiarism');
    }

    handleSplit(pane: string) {
        this.splitControlSubject.next(pane);
    }
}
