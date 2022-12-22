import { Component, OnInit } from '@angular/core';
import { faBan, faClock, faGlobe, faSave } from '@fortawesome/free-solid-svg-icons';
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
    unitName: string;
    description?: string;
    file: File;
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
    lectureId: number;
    isLoading: boolean;
    isProcessingMode: boolean;

    units: UnitResponseType[];

    faSave = faSave;
    faBan = faBan;
    faGlobe = faGlobe;
    faClock = faClock;

    file: File;
    fileName: string;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private attachmentUnitService: AttachmentUnitService,
        private httpClient: HttpClient,
        private alertService: AlertService,
    ) {
        this.file = this.router.getCurrentNavigation()!.extras.state!.file;
        this.fileName = this.router.getCurrentNavigation()!.extras.state!.fileName;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap]).subscribe(([params]) => {
            this.lectureId = Number(params.get('lectureId'));
        });
    }

    ngOnInit(): void {
        this.isLoading = true;
        this.isProcessingMode = true;

        const formData: FormData = new FormData();
        formData.append('file', this.file);

        this.attachmentUnitService.getSplitUnitsData(this.lectureId, formData).subscribe({
            next: (res: any) => {
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

        console.log(this.file, this.fileName);
    }

    createAttachmentUnits(): void {
        this.isLoading = true;

        const formData: FormData = new FormData();
        formData.append('file', this.file);
        formData.append('lectureUnitSplitDTOs', objectToJsonBlob(this.units));

        this.attachmentUnitService.createUnits(this.lectureId, formData).subscribe({
            next: (res: any) => {
                console.log(res);
                this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                this.isLoading = false;
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

    previousState() {
        this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
    }

    onDatesValuesChanged(unit: UnitResponseType) {
        // this.lecture.endDate = this.lecture.startDate;
        console.log(unit.releaseDate, unit.unitName);
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

    get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }
}
