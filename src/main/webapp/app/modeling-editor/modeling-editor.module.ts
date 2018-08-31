import { ModelingEditorComponent } from './modeling-editor.component';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { modelingEditorRoute } from './modeling-editor.route';
import { ArTEMiSResultModule, ResultComponent } from '../entities/result';
import { ModelingEditorService } from './modeling-editor.service';

const ENTITY_STATES = [
    ...modelingEditorRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        ArTEMiSResultModule
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
