import { Component, OnInit, inject, signal } from '@angular/core';
import { faBan, faExclamationTriangle, faPlus, faQuestionCircle, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/foundation/util/global.utils';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { combineLatest } from 'rxjs';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { debounceTime, repeat, switchMap } from 'rxjs/operators';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export type LectureUnitDTOS = {
    unitName: string;
    releaseDate?: dayjs.Dayjs;
    startPage: number;
    endPage: number;
};

export type LectureUnitInformationDTO = {
    units: LectureUnitDTOS[];
    numberOfPages: number;
    removeSlidesCommaSeparatedKeyPhrases: string;
};

@Component({
    selector: 'jhi-attachment-video-units',
    templateUrl: './attachment-video-units.component.html',
    imports: [LectureUnitLayoutComponent, TranslateDirective, FormsModule, FormDateTimePickerComponent, FaIconComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class AttachmentVideoUnitsComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);

    lectureId: number;
    courseId: number;
    readonly isLoading = signal(false);
    isProcessingMode: boolean;
    readonly units = signal<LectureUnitDTOS[]>([]);
    readonly numberOfPages = signal<number>(undefined!);
    faSave = faSave;
    faBan = faBan;
    faTimes = faTimes;
    faPlus = faPlus;
    faExclamationTriangle = faExclamationTriangle;
    faQuestionCircle = faQuestionCircle;

    readonly invalidUnitTableMessage = signal<string | undefined>(undefined);
    //Comma-seperated keyphrases used to detect slides to be removed
    keyphrases: string;
    private search = new Subject<void>();
    readonly removedSlidesNumbers = signal<number[]>([]);

    file: File;
    filename: string;
    //time until the file gets uploaded again. Must be less or equal than minutesUntilDeletion in AttachmentVideoUnitResource.java
    readonly MINUTES_UNTIL_DELETION = 29;

    constructor() {
        this.file = this.router.currentNavigation()?.extras?.state?.file;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap]).subscribe(([params]) => {
            this.lectureId = Number(params.get('lectureId'));
            this.courseId = Number(params.get('courseId'));
        });
    }

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit(): void {
        this.keyphrases = '';
        this.removedSlidesNumbers.set([]);
        this.isLoading.set(true);
        this.isProcessingMode = true;

        if (!this.file) {
            this.alertService.error(this.translateService.instant(`artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.noFile`));
            this.isLoading.set(true);
            return;
        }

        //regularly re-upload the file when it gets deleted in the server
        setTimeout(
            () => {
                this.attachmentVideoUnitService
                    .uploadSlidesForProcessing(this.lectureId, this.file)
                    .pipe(repeat({ delay: 1000 * 60 * this.MINUTES_UNTIL_DELETION }))
                    .subscribe({
                        next: (res) => {
                            this.filename = res.body!;
                        },
                        error: (res: HttpErrorResponse) => {
                            onError(this.alertService, res);
                            this.isLoading.set(false);
                        },
                    });
            },
            1000 * 60 * this.MINUTES_UNTIL_DELETION,
        );

        this.attachmentVideoUnitService
            .uploadSlidesForProcessing(this.lectureId, this.file)
            .pipe(
                switchMap((res) => {
                    if (res instanceof HttpErrorResponse) {
                        throw new Error(res.message);
                    } else {
                        this.filename = res.body!;
                        return this.attachmentVideoUnitService.getSplitUnitsData(this.lectureId, this.filename);
                    }
                }),
            )
            .subscribe({
                next: (res) => {
                    this.units.set(res.body!.units || this.units());
                    this.numberOfPages.set(res.body!.numberOfPages);
                    this.isLoading.set(false);
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.isLoading.set(false);
                },
            });

        this.search
            .pipe(
                debounceTime(500),
                switchMap(() => {
                    return this.attachmentVideoUnitService.getSlidesToRemove(this.lectureId, this.filename, this.keyphrases);
                }),
            )
            .subscribe({
                next: (res) => {
                    if (res.body) {
                        this.removedSlidesNumbers.set(res.body.map((n) => n + 1));
                    }
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                },
            });
    }

    /**
     * Creates the attachment video units with the information given on this page
     */
    createAttachmentVideoUnits(): void {
        if (this.validUnitInformation()) {
            this.isLoading.set(true);
            const lectureUnitInformation: LectureUnitInformationDTO = {
                units: this.units(),
                numberOfPages: this.numberOfPages(),
                removeSlidesCommaSeparatedKeyPhrases: this.keyphrases,
            };

            this.attachmentVideoUnitService.createUnits(this.lectureId, this.filename, lectureUnitInformation).subscribe({
                next: () => {
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                    this.isLoading.set(false);
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                },
            });
        }
    }

    set searchTerm(searchTerm: string) {
        //only consider non-empty searches for slide removal
        if (searchTerm.trim() !== '') {
            this.keyphrases = searchTerm;
            this.search.next();
        } else {
            this.removedSlidesNumbers.set([]);
        }
    }

    get searchTerm(): string {
        return this.keyphrases;
    }

    /**
     * Go back to the lecture page
     */
    cancelSplit() {
        this.router.navigate(['course-management', this.courseId.toString(), 'lectures', this.lectureId.toString()]);
    }

    addRow() {
        const unitDynamic = {
            unitName: '',
            startPage: 0,
            endPage: 0,
        };
        this.units.update((units) => [...units, unitDynamic]);
        return true;
    }

    deleteRow(i: number) {
        if (this.units().length === 1) {
            return false;
        } else {
            this.units.update((units) => {
                const updated = [...units];
                updated.splice(i, 1);
                return updated;
            });
            return true;
        }
    }

    validUnitInformation(): boolean {
        const numberOfPages = this.numberOfPages();
        for (const unit of this.units()) {
            if (!unit.unitName) {
                this.invalidUnitTableMessage.set(this.translateService.instant(`artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.validation.empty.unitName`));
                return false;
            }

            if (unit.startPage === null) {
                this.invalidUnitTableMessage.set(this.translateService.instant(`artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.validation.empty.startPage`));
                return false;
            }

            if (unit.endPage === null) {
                this.invalidUnitTableMessage.set(this.translateService.instant(`artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.validation.empty.endPage`));
                return false;
            }

            if (unit.startPage < 1) {
                this.invalidUnitTableMessage.set(this.translateService.instant(`artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.validation.startPage`));
                return false;
            }

            if (unit.startPage > numberOfPages) {
                this.invalidUnitTableMessage.set(
                    this.translateService.instant(`artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.validation.startPageBigger`, {
                        max: numberOfPages ?? '',
                    }),
                );
                return false;
            }

            if (unit.endPage < 1) {
                this.invalidUnitTableMessage.set(this.translateService.instant(`artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.validation.endPageLower`));
                return false;
            }

            if (unit.endPage > numberOfPages) {
                this.invalidUnitTableMessage.set(
                    this.translateService.instant(`artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.validation.endPage`, {
                        max: numberOfPages ?? '',
                    }),
                );
                return false;
            }

            if (unit.startPage > unit.endPage) {
                this.invalidUnitTableMessage.set(
                    this.translateService.instant(`artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.validation.invalidPages`, {
                        unitName: unit.unitName ?? '',
                    }),
                );
                return false;
            }
        }

        this.invalidUnitTableMessage.set(undefined);
        return true;
    }
}
