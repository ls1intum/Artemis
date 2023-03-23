import { StudentsUploadImagesButtonComponent } from 'app/exam/manage/students/upload-images/students-upload-images-button.component';
import { StudentsUploadImagesDialogComponent } from 'app/exam/manage/students/upload-images/students-upload-images-dialog.component';
import { NgModule } from '@angular/core';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@NgModule({
    imports: [ArtemisSharedComponentModule, ArtemisSharedCommonModule],
    declarations: [StudentsUploadImagesDialogComponent, StudentsUploadImagesButtonComponent],
    exports: [StudentsUploadImagesDialogComponent, StudentsUploadImagesButtonComponent],
})
export class StudentsUploadImagesModule {}
