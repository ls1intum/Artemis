import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { faBan, faExclamationTriangle, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/foundation/util/global.utils';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { combineLatest } from 'rxjs';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { debounceTime, repeat, switchMap } from 'rxjs/operators';
import { LectureUnitLayoutComponent } from '../lecture-unit-layout/lecture-unit-layout.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TumAetUiButtonDirective,
    TumAetUiCheckboxComponent,
    TumAetUiDatePickerComponent,
    TumAetUiFormFieldComponent,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
    TumAetUiTableDirective,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
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

/** The PDF item whose file is split, when the lecture editor opened this page for one of its items. */
export interface SplitSourceUnit {
    id: number;
    name?: string;
}

@Component({
    selector: 'jhi-attachment-video-units',
    templateUrl: './attachment-video-units.component.html',
    imports: [
        LectureUnitLayoutComponent,
        TranslateDirective,
        FormsModule,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiDatePickerComponent,
        TumAetUiFormFieldComponent,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiTableDirective,
        TumAetUiTooltipDirective,
        ArtemisTranslatePipe,
    ],
})
export class AttachmentVideoUnitsComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private readonly lectureUnitService = inject(LectureUnitService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);

    lectureId!: number; // set in constructor from route params
    courseId!: number; // set in constructor from route params
    readonly isLoading = signal(false);
    isProcessingMode = false;
    readonly units = signal<LectureUnitDTOS[]>([]);
    readonly numberOfPages = signal<number>(undefined!);
    /** How many sections Artemis found in the PDF, before the user changed the proposal. */
    readonly foundSections = signal(0);
    /** What Artemis found, which also says how many slides the ranges can use. */
    readonly summaryKey = computed(() => {
        const found = this.foundSections();
        return `artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.split.${found === 0 ? 'summaryNone' : found === 1 ? 'summarySingle' : 'summary'}`;
    });
    readonly createLabelKey = computed(() => {
        const count = this.units().length;
        return `artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.${count === 0 ? 'createEmpty' : count === 1 ? 'createSingle' : 'create'}`;
    });
    faBan = faBan;
    faTimes = faTimes;
    faPlus = faPlus;
    faExclamationTriangle = faExclamationTriangle;

    readonly invalidUnitTableMessage = signal<string | undefined>(undefined);
    //Comma-seperated keyphrases used to detect slides to be removed
    keyphrases = '';
    private search = new Subject<void>();
    readonly removedSlidesNumbers = signal<number[]>([]);

    /** What the page that opened this one passed: the file, and from the lecture editor the PDF item it comes from. */
    private readonly navigationState = this.router.currentNavigation()?.extras?.state;
    file: File = this.navigationState?.file;
    /** The PDF item the file comes from; the lecture editor passes it only to instructors, who may delete content. */
    readonly sourceUnit: SplitSourceUnit | undefined = this.navigationState?.sourceUnit;
    /** Whether the PDF item is deleted once its sections exist as items, so the lecture does not hold the slides twice. */
    readonly removeSourceUnit = signal(true);
    readonly isRemovingSourceUnit = computed(() => !!this.sourceUnit && this.removeSourceUnit());
    /** Set when the lecture editor opened this page, which is where the page leads back to. */
    private readonly returnToEditor = this.navigationState?.returnToEditor === true;
    filename!: string; // set asynchronously after the slides upload completes, before subsequent reads
    //time until the file gets uploaded again. Must be less or equal than minutesUntilDeletion in AttachmentVideoUnitResource.java
    readonly MINUTES_UNTIL_DELETION = 29;

    constructor() {
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
                    const proposedUnits = res.body!.units ?? [];
                    // The server sends the release dates as text, but the date picker works with dates.
                    for (const unit of proposedUnits) {
                        unit.releaseDate = unit.releaseDate ? dayjs(unit.releaseDate) : undefined;
                    }
                    this.units.set(res.body!.units ? proposedUnits : this.units());
                    this.foundSections.set(proposedUnits.length);
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
     * Creates the attachment video units with the information given on this page, then deletes the PDF item they come from if the user chose so.
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
                    if (this.isRemovingSourceUnit()) {
                        this.deleteSourceUnitAndLeave(this.sourceUnit!.id);
                    } else {
                        this.leaveAfterCreation();
                    }
                },
                error: (res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    this.isLoading.set(false);
                },
            });
        }
    }

    private deleteSourceUnitAndLeave(sourceUnitId: number): void {
        this.lectureUnitService.delete(sourceUnitId, this.lectureId).subscribe({
            next: () => this.leaveAfterCreation(),
            error: (res: HttpErrorResponse) => {
                // The new items exist, so the page is left anyway; the alert tells that the PDF item is still there.
                onError(this.alertService, res);
                this.leaveAfterCreation();
            },
        });
    }

    private leaveAfterCreation(): void {
        this.isLoading.set(false);
        if (this.returnToEditor) {
            void this.router.navigate(this.editorRoute());
        } else {
            void this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
        }
    }

    private editorRoute(): string[] {
        return ['course-management', this.courseId.toString(), 'lectures', this.lectureId.toString(), 'edit'];
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
     * Goes back to the lecture editor when it opened this page, else to the lecture page
     */
    cancelSplit() {
        void this.router.navigate(this.returnToEditor ? this.editorRoute() : ['course-management', this.courseId.toString(), 'lectures', this.lectureId.toString()]);
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
