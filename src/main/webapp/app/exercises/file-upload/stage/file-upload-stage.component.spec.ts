import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FileUploadStageComponent } from './file-upload-stage.component';

describe('StageComponent', () => {
    let component: FileUploadStageComponent;
    let fixture: ComponentFixture<FileUploadStageComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [FileUploadStageComponent],
        });
        fixture = TestBed.createComponent(FileUploadStageComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
