import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FileUploadStageComponent } from './file-upload-stage.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
export { FileUploadStageComponent } from './file-upload-stage.component';

@NgModule({
    declarations: [FileUploadStageComponent],
    imports: [CommonModule, ArtemisSharedCommonModule],
    exports: [FileUploadStageComponent],
})
export class FileUploadStageModule {}
