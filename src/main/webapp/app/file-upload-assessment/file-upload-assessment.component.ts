/* angular */
import { Component, OnInit, OnDestroy } from '@angular/core';

/* 3rd party*/

/* application */
import { FileUploadAssessmentsService } from 'app/entities/file-upload-assessment/file-upload-assessment.service';
import { WindowRef } from 'app/core';

@Component({
    providers: [FileUploadAssessmentsService, WindowRef],
    templateUrl: './text-assessment.component.html',
    styleUrls: ['./text-assessment.component.scss'],
})
export class FileUploadAssessmentComponent implements OnInit {
    ngOnInit(): void {}
}
