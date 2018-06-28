import { ModelingEditorComponent } from './modeling-editor.component';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { modelingEditorRoute } from './modeling-editor.route';
import { ResultComponent } from '../courses/results/result.component';
import { ArTEMiSCoursesModule } from '../courses/courses.module';
import { ModelingEditorService } from './modeling-editor.service';

const ENTITY_STATES = [
    ...modelingEditorRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        ArTEMiSCoursesModule
    ],
    declarations: [
        ModelingEditorComponent
    ],
    entryComponents: [
        ModelingEditorComponent,
        ResultComponent
    ],
    providers: [
        ModelingEditorService
    ]
})
export class ArTEMiSModelingEditorModule {}
