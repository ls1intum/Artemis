import { Component, OnInit } from '@angular/core';
import { faBan, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { combineLatest, delay, interval, take } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { AlertService } from 'app/core/util/alert.service';

type UnitResponseType = {
    attachmentName: string;
    description?: string;
    file: string;
    releaseDate?: dayjs.Dayjs;
    version?: number;
    startPage: number;
    endPage: number;
};

@Component({
    selector: 'jhi-attachment-units',
    templateUrl: './attachment-units.component.html',
    styleUrls: [],
})
export class AttachmentUnitsComponent implements OnInit {
    attachmentUnitToCreate: AttachmentUnit = new AttachmentUnit();
    attachmentToCreate: Attachment = new Attachment();

    lectureId: number;
    isLoading: boolean;
    isProcessingMode: boolean;

    units: UnitResponseType[];

    faSave = faSave;
    faBan = faBan;

    file: File;
    fileName: string;

    // formData = new FormData();
    // this.formData.append('file', file);

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private attachmentUnitService: AttachmentUnitService,
        private httpClient: HttpClient,
        private alertService: AlertService,
    ) {
        console.log(this.router.getCurrentNavigation());
        this.file = this.router.getCurrentNavigation()!.extras.state!.file;
        this.fileName = this.router.getCurrentNavigation()!.extras.state!.fileName;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap]).subscribe(([params]) => {
            this.lectureId = Number(params.get('lectureId'));
        });
        this.attachmentUnitToCreate = new AttachmentUnit();
        this.attachmentToCreate = new Attachment();
    }

    ngOnInit(): void {
        this.isLoading = true;
        const formData: FormData = new FormData();
        // @ts-ignore
        formData.append('file', this.file);

        console.log('test11');
        this.units = [];
        this.isProcessingMode = true;
        console.log('test22');

        this.attachmentUnitService.splitFileIntoUnits(formData).subscribe({
            next: (res: any) => {
                console.log(res);
                this.units = res.body;
                if (this.units.length > 0) {
                    this.isLoading = false;
                }
            },
            error: (res: HttpErrorResponse) => {
                if (res.error.params === 'file' && res?.error?.title) {
                    this.alertService.error(res.error.title);
                } else {
                    onError(this.alertService, res);
                }
                this.isLoading = false;
            },
        });
    }

    createAttachmentUnits(): void {
        this.isLoading = true;
        // const { description, name, releaseDate } = attachmentUnitFormData.formProperties;
        // const { file, fileName } = attachmentUnitFormData.fileProperties;

        this.units.map((unit, index) => {
            // === Setting attachment ===

            console.log(unit);
            const { attachmentName: name, description, releaseDate, file: unitFile } = unit;

            const fileBlob = this.base64toBlob(unitFile);
            const newUnitFile = new File([fileBlob], name, { type: 'application/pdf' });

            if (name) {
                this.attachmentToCreate.name = name;
            }
            if (releaseDate) {
                this.attachmentToCreate.releaseDate = releaseDate;
            }
            this.attachmentToCreate.attachmentType = AttachmentType.FILE;
            this.attachmentToCreate.version = 1;
            this.attachmentToCreate.uploadDate = dayjs();

            // === Setting attachmentUnit ===
            if (description) {
                this.attachmentUnitToCreate.description = description;
            }

            const formData = new FormData();

            formData.append('file', newUnitFile, name + '.pdf');
            formData.append('attachment', objectToJsonBlob(this.attachmentToCreate));
            formData.append('attachmentUnit', objectToJsonBlob(this.attachmentUnitToCreate));
            this.attachmentUnitService
                .create(formData, this.lectureId)
                .pipe(delay(3000))
                .subscribe({
                    next: () => {},
                    error: (res: HttpErrorResponse) => {
                        if (res.error.params === 'file' && res?.error?.title) {
                            this.alertService.error(res.error.title);
                        } else {
                            onError(this.alertService, res);
                        }
                        this.isLoading = false;
                    },
                })
                .add();
        });
        this.isLoading = false;
    }

    previousState() {
        this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
    }

    private base64toBlob(dataURI: string) {
        const byteString = window.atob(dataURI);
        const arrayBuffer = new ArrayBuffer(byteString.length);
        const int8Array = new Uint8Array(arrayBuffer);
        for (let i = 0; i < byteString.length; i++) {
            int8Array[i] = byteString.charCodeAt(i);
        }
        const blob = new Blob([int8Array], { type: 'image/png' });
        return blob;
    }
}
