import { Component, OnInit } from '@angular/core';
import { BuildPlanService } from 'app/exercises/programming/manage/services/build-plan.service';

@Component({
    selector: 'jhi-build-plan-editor',
    templateUrl: '/build-plan-editor.component.html',
})
export class BuildPlanEditorComponent implements OnInit {
    constructor(private buildPlanService: BuildPlanService) {}

    ngOnInit(): void {
        throw new Error('Hi');
    }
}
