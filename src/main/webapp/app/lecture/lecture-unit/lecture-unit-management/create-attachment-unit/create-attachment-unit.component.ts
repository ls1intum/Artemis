import { Component, OnInit, ViewChild } from '@angular/core';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import dayjs from 'dayjs/esm';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { AttachmentUnitFormComponent, AttachmentUnitFormData } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-unit-form/attachment-unit-form.component';
import { combineLatest } from 'rxjs';
import { objectToJsonBlob } from 'app/utils/blob-util';

@Component({
    selector: 'jhi-create-attachment-unit',
    templateUrl: './create-attachment-unit.component.html',
    styles: [],
})
export class CreateAttachmentUnitComponent implements OnInit {
    @ViewChild('attachmentUnitForm')
    attachmentUnitForm: AttachmentUnitFormComponent;
    attachmentUnitToCreate: AttachmentUnit = new AttachmentUnit();
    attachmentToCreate: Attachment = new Attachment();

    isLoading: boolean;
    lectureId: number;
    courseId: number;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private attachmentUnitService: AttachmentUnitService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap]).subscribe(([params, parentParams]) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(parentParams.get('courseId'));
        });
        this.attachmentUnitToCreate = new AttachmentUnit();
        this.attachmentToCreate = new Attachment();
    }

    createAttachmentUnit(attachmentUnitFormData: AttachmentUnitFormData): void {
        if (!attachmentUnitFormData?.formProperties?.name || !attachmentUnitFormData?.fileProperties?.file || !attachmentUnitFormData?.fileProperties?.fileName) {
            return;
        }
        const { description, name, releaseDate, competencies } = attachmentUnitFormData.formProperties;
        const { file, fileName } = attachmentUnitFormData.fileProperties;

        // === Setting attachment ===
        this.attachmentToCreate.name = name;
        this.attachmentToCreate.releaseDate = releaseDate;
        this.attachmentToCreate.attachmentType = AttachmentType.FILE;
        this.attachmentToCreate.version = 1;
        this.attachmentToCreate.uploadDate = dayjs();

        // === Setting attachmentUnit ===
        this.attachmentUnitToCreate.description = description;
        this.attachmentUnitToCreate.competencies = competencies || [];

        this.isLoading = true;

        const formData = new FormData();
        formData.append('file', file, fileName);
        formData.append('attachment', objectToJsonBlob(this.attachmentToCreate));
        formData.append('attachmentUnit', objectToJsonBlob(this.attachmentUnitToCreate));

        this.attachmentUnitService
            .create(formData, this.lectureId)
            .subscribe({
                next: () => this.router.navigate(['../../'], { relativeTo: this.activatedRoute }),
                error: (res: HttpErrorResponse) => {
                    if (res.error.params === 'file' && res?.error?.title) {
                        this.alertService.error(res.error.title);
                    } else {
                        onError(this.alertService, res);
                    }
                },
            })
            .add(() => (this.isLoading = false));
    }
}
