/* angular */
import { Component, OnInit } from '@angular/core';

/* 3rd party*/

/* application */
import { FileUploadAssessmentsService } from 'app/entities/file-upload-assessment/file-upload-assessment.service';
import { WindowRef } from 'app/core';

@Component({
    providers: [FileUploadAssessmentsService, WindowRef],
    templateUrl: './file-upload-assessment.component.html',
})
export class FileUploadAssessmentComponent implements OnInit {
    ngOnInit(): void {}
}
