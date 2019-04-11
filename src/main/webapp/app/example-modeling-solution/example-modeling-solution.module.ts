import { NgModule } from '@angular/core';

import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { JhiAlertService } from 'ng-jhipster';
import { ArTEMiSModelingEditorModule } from 'app/modeling-editor/modeling-editor.module';
import { ExampleModelingSolutionComponent } from 'app/example-modeling-solution/example-modeling-solution.component';
import { exampleModelingSolutionRoute } from 'app/example-modeling-solution/example-modeling-solution.route';

const ENTITY_STATES = [...exampleModelingSolutionRoute];

@NgModule({
    // TODO CZ: do we need all the inputs? do we need the course service?
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSModelingEditorModule],
    declarations: [ExampleModelingSolutionComponent],
    providers: [JhiAlertService],
})
export class ArTEMiSExampleModelingSolutionModule {}
