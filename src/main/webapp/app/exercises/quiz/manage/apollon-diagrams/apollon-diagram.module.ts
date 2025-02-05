import { NgModule } from '@angular/core';
import { ApollonDiagramCreateFormComponent } from './apollon-diagram-create-form.component';
import { ApollonDiagramImportDialogComponent } from './apollon-diagram-import-dialog.component';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';

@NgModule({
    imports: [ApollonDiagramCreateFormComponent, ApollonDiagramDetailComponent, ApollonDiagramListComponent, ApollonDiagramImportDialogComponent],
})
export class ArtemisApollonDiagramsModule {}
