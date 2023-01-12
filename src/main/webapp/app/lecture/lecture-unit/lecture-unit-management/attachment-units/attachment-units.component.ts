import { Component, OnInit } from '@angular/core';
import { faBan, faClock, faExclamationTriangle, faGlobe, faPlus, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { combineLatest } from 'rxjs';
import dayjs from 'dayjs/esm';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateService } from '@ngx-translate/core';

type AttachmentUnitsInfoResponseType = {
    unitName: string;
    releaseDate?: dayjs.Dayjs;
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
    courseId: number;
    isLoading: boolean;
    isProcessingMode: boolean;

    units: AttachmentUnitsInfoResponseType[] = [];
    numberOfPages: number;

    faSave = faSave;
    faBan = faBan;
    faGlobe = faGlobe;
    faClock = faClock;
    faTimes = faTimes;
    faPlus = faPlus;
    faExclamationTriangle = faExclamationTriangle;

    file: File;
    fileName: string;
    invalidUnitTableMessage?: string;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private attachmentUnitService: AttachmentUnitService,
        private alertService: AlertService,
        private translateService: TranslateService,
    ) {
        this.file = this.router.getCurrentNavigation()!.extras.state!.file;
        this.fileName = this.router.getCurrentNavigation()!.extras.state!.fileName;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap]).subscribe(([params]) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(params.get('courseId'));
        });
    }

    ngOnInit(): void {
        this.isLoading = true;
        this.isProcessingMode = true;

        const formData: FormData = new FormData();
        formData.append('file', this.file);

        this.attachmentUnitService.getSplitUnitsData(this.lectureId, formData).subscribe({
            next: (res: any) => {
                if (res) {
                    this.units = res.body.lectureUnitDTOS;
                    this.numberOfPages = res.body.numberOfPages;
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

        const formData: FormData = new FormData();
        formData.append('file', this.file);
        formData.append('lectureUnitSplitDTOs', objectToJsonBlob(this.units));

        this.attachmentUnitService.createUnits(this.lectureId, formData).subscribe({
            next: () => {
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

    cancelSplit() {
        this.router.navigate(['course-management', this.courseId.toString(), 'lectures', this.lectureId.toString()]);
    }

    addRow() {
        const unitDynamic = {
            unitName: '',
            startPage: 0,
            endPage: 0,
        };
        this.units.push(unitDynamic);
        return true;
    }

    deleteRow(i: number) {
        if (this.units.length === 1) {
            return false;
        } else {
            this.units.splice(i, 1);
            return true;
        }
    }

    validUnitInformation(): boolean {
        for (const unit of this.units) {
            if (!unit.unitName) {
                this.invalidUnitTableMessage = this.translateService.instant(`artemisApp.attachmentUnit.createAttachmentUnits.validation.empty.unitName`);
                return false;
            }

            if (!unit.startPage) {
                this.invalidUnitTableMessage = `${this.translateService.instant(`artemisApp.attachmentUnit.createAttachmentUnits.validation.empty.startPage`)}`;
                return false;
            }

            if (!unit.endPage) {
                this.invalidUnitTableMessage = `${this.translateService.instant(`artemisApp.attachmentUnit.createAttachmentUnits.validation.empty.endPage`)}`;
                return false;
            }

            if (unit.startPage < 1) {
                this.invalidUnitTableMessage = this.translateService.instant(`artemisApp.attachmentUnit.createAttachmentUnits.validation.startPage`);
                return false;
            }

            if (unit.startPage > this.numberOfPages) {
                this.invalidUnitTableMessage = `${this.translateService.instant(`artemisApp.attachmentUnit.createAttachmentUnits.validation.startPageBigger`)} ${
                    this.numberOfPages
                }`;
                return false;
            }

            if (unit.endPage < 1) {
                this.invalidUnitTableMessage = this.translateService.instant(`artemisApp.attachmentUnit.createAttachmentUnits.validation.endPageLower`);
                return false;
            }

            if (unit.endPage > this.numberOfPages) {
                this.invalidUnitTableMessage = `${this.translateService.instant(`artemisApp.attachmentUnit.createAttachmentUnits.validation.endPage`)} ${this.numberOfPages}`;
                return false;
            }
        }

        this.invalidUnitTableMessage = undefined;
        return true;
    }

    get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }
}
