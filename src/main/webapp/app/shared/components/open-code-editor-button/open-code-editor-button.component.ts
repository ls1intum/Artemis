import { Component, Input, OnInit } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

@Component({
    selector: 'jhi-open-code-editor-button',
    templateUrl: './open-code-editor-button.component.html',
})
export class OpenCodeEditorButtonComponent implements OnInit {
    @Input()
    loading = false;
    @Input()
    smallButtons: boolean;
    @Input()
    participations: ProgrammingExerciseStudentParticipation[];
    @Input()
    courseAndExerciseNavigationURLSegment: any[];

    FeatureToggle = FeatureToggle;
    codeEditorHeadlines: string[] = [];
    courseAndExerciseNavigationURL: string;

    // Icons
    faFolderOpen = faFolderOpen;

    ngOnInit() {
        this.codeEditorHeadlines = this.participations.map((participation) =>
            participation.testRun ? 'artemisApp.exerciseActions.openPracticeCodeEditor' : 'artemisApp.exerciseGradedActions.openCodeEditor',
        );
        this.courseAndExerciseNavigationURL = this.courseAndExerciseNavigationURLSegment.reduce((acc, segment) => acc + '/' + segment);
    }
}
