import { ChangeDetectionStrategy, Component, OnInit, Signal, WritableSignal, computed, inject, signal } from '@angular/core';
import {
    ExamRoomAdminOverviewDTO,
    ExamRoomDTO,
    ExamRoomDTOExtended,
    ExamRoomDeletionSummaryDTO,
    ExamRoomUploadInformationDTO,
    NumberOfStored,
} from 'app/core/admin/exam-rooms/exam-rooms.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortService } from 'app/shared/service/sort.service';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExamRoomsService } from 'app/core/admin/exam-rooms/exam-rooms.service';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';

/**
 * Admin component for managing exam rooms.
 * Allows uploading room data via ZIP files and viewing/deleting exam room configurations.
 */
@Component({
    selector: 'jhi-exam-rooms',
    templateUrl: './exam-rooms.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, SortDirective, SortByDirective, FaIconComponent, ArtemisTranslatePipe],
})
export class ExamRoomsComponent implements OnInit {
    private readonly baseTranslationPath = 'artemisApp.examRooms.adminOverview';

    protected readonly faSort = faSort;

    private readonly examRoomsService = inject(ExamRoomsService);
    private readonly sortService = inject(SortService);
    private readonly translateService = inject(TranslateService);
    private readonly alertService = inject(AlertService);

    private selectedFile: WritableSignal<File | undefined> = signal(undefined);
    private actionStatus: WritableSignal<'uploading' | 'uploadSuccess' | 'deleting' | 'deletionSuccess' | undefined> = signal(undefined);
    private actionInformation: WritableSignal<ExamRoomUploadInformationDTO | ExamRoomDeletionSummaryDTO | undefined> = signal(undefined);
    private overview: WritableSignal<ExamRoomAdminOverviewDTO | undefined> = signal(undefined);

    hasSelectedFile: Signal<boolean> = computed(() => !!this.selectedFile());
    selectedFileName: Signal<string | undefined> = computed(() => this.selectedFile()?.name?.trim());
    canUpload: Signal<boolean> = computed(() => this.hasSelectedFile() && !this.isUploading());
    isUploading: Signal<boolean> = computed(() => this.actionStatus() === 'uploading');
    hasUploadInformation: Signal<boolean> = computed(() => this.actionStatus() === 'uploadSuccess' && !!this.uploadInformation());
    isDeleting: Signal<boolean> = computed(() => this.actionStatus() === 'deleting');
    hasDeletionInformation: Signal<boolean> = computed(() => this.actionStatus() === 'deletionSuccess' && !!this.deletionInformation());
    uploadInformation: Signal<ExamRoomUploadInformationDTO | undefined> = computed(() => this.actionInformation() as ExamRoomUploadInformationDTO);
    deletionInformation: Signal<ExamRoomDeletionSummaryDTO | undefined> = computed(() => this.actionInformation() as ExamRoomDeletionSummaryDTO);
    /**
     * Indicates if we have the {@link numberOf} and {@link distinctLayoutStrategyNames} fields
     */
    hasOverview: Signal<boolean> = computed(() => !!this.overview());
    private numberOfUniqueExamRooms: Signal<number> = computed(() => this.overview()?.newestUniqueExamRooms?.length ?? 0);
    private numberOfUniqueExamSeats: Signal<number> = computed(
        () =>
            this.overview()
                ?.newestUniqueExamRooms?.map((examRoomDTO) => examRoomDTO.numberOfSeats)
                .reduce((acc, val) => acc + val, 0) ?? 0,
    );
    private numberOfLayoutStrategiesOfUniqueRooms: Signal<number> = computed(
        () =>
            this.overview()
                ?.newestUniqueExamRooms?.map((examRoomDTO) => examRoomDTO.layoutStrategies?.length ?? 0)
                .reduce((acc, val) => acc + val, 0) ?? 0,
    );
    numberOf: Signal<NumberOfStored | undefined> = computed(() => {
        if (!this.hasOverview()) {
            return undefined;
        }

        return {
            examRooms: this.overview()!.numberOfStoredExamRooms,
            examSeats: this.overview()!.numberOfStoredExamSeats,
            layoutStrategies: this.overview()!.numberOfStoredLayoutStrategies,
            uniqueExamRooms: this.numberOfUniqueExamRooms(),
            uniqueExamSeats: this.numberOfUniqueExamSeats(),
            uniqueLayoutStrategies: this.numberOfLayoutStrategiesOfUniqueRooms(),
        } as NumberOfStored;
    });
    distinctLayoutStrategyNames: Signal<string> = computed(() =>
        [...new Set(this.overview()?.newestUniqueExamRooms?.flatMap((examRoomDTO) => examRoomDTO.layoutStrategies?.map((layoutStrategy) => layoutStrategy.name) ?? []) ?? [])]
            .slice()
            .sort()
            .join(', '),
    );
    hasExamRoomData: Signal<boolean> = computed(() => !!this.numberOfUniqueExamRooms());
    examRoomData: Signal<ExamRoomDTOExtended[] | undefined> = computed(() => this.calculateExamRoomData());

    // Fields for working with SortDirective
    sortAttribute: 'roomNumber' | 'name' | 'building' | 'defaultCapacity' | 'maxCapacity' = 'name';
    ascending: boolean = true;

    ngOnInit() {
        this.loadExamRoomOverview();
    }

    /**
     * Makes a REST request to fetch a new exam room overview and displays it
     */
    loadExamRoomOverview(): void {
        this.examRoomsService.getAdminOverview().subscribe({
            next: (examRoomAdminOverviewResponse: HttpResponse<ExamRoomAdminOverviewDTO>) => {
                this.overview.set(examRoomAdminOverviewResponse.body as ExamRoomAdminOverviewDTO);
                this.sortRows();
            },
            error: (errorResponse: HttpErrorResponse) => {
                this.showErrorNotification('examRoomOverview.loadError', {}, errorResponse.message);
                this.overview.set(undefined);
            },
        });
    }

    /**
     * Event handler for a file selection that accepts only zip files
     *
     * @param event A file selection event
     */
    onFileSelectedAcceptZip(event: Event): void {
        const { files } = event.target as HTMLInputElement;
        if (!files || files.length <= 0) {
            this.showErrorNotification('invalidFile');
            this.selectedFile.set(undefined);
            return;
        }

        const file: File = files[0];
        if (!file.name.toLowerCase().endsWith('.zip')) {
            this.showErrorNotification('noZipFile');
            this.selectedFile.set(undefined);
            return;
        }

        if (file.size > MAX_FILE_SIZE) {
            this.showErrorNotification('fileSizeTooBig', { MAX_FILE_SIZE: MAX_FILE_SIZE / 1024 ** 2 });
            this.selectedFile.set(undefined);
            return;
        }

        this.selectedFile.set(file);

        // fix for Chrome and Safari to allow a re-selection of the same file after upload
        (event.target as HTMLInputElement).value = '';
    }

    /**
     * Uploads the {@link selectedFile} if one is present.
     * Afterward, it refreshes the exam room overview to immediately see any changes.
     */
    upload(): void {
        const file = this.selectedFile();
        if (!file) return;

        this.actionStatus.set('uploading');

        this.examRoomsService.uploadRoomDataZipFile(file).subscribe({
            next: (uploadInformationResponse: HttpResponse<ExamRoomUploadInformationDTO>) => {
                this.actionStatus.set('uploadSuccess');
                this.selectedFile.set(undefined);
                this.actionInformation.set(uploadInformationResponse.body as ExamRoomUploadInformationDTO);
                this.loadExamRoomOverview();
            },
            error: (errorResponse: HttpErrorResponse) => {
                this.showErrorNotification('uploadError', {}, errorResponse.message);
                this.actionStatus.set(undefined);
            },
        });
    }

    /**
     * REST request to delete all outdated and unused exam rooms.
     * An exam room is outdated if there exists a newer entry of the same (number, name) combination.
     * An exam room is unused if it isn't connected to any exam.
     */
    deleteOutdatedAndUnusedExamRooms(): void {
        this.actionStatus.set('deleting');

        this.examRoomsService.deleteOutdatedAndUnusedExamRooms().subscribe({
            next: (examRoomDeletionSummaryResponse: HttpResponse<ExamRoomDeletionSummaryDTO>) => {
                this.actionInformation.set(examRoomDeletionSummaryResponse.body as ExamRoomDeletionSummaryDTO);
                this.actionStatus.set('deletionSuccess');
                this.loadExamRoomOverview();
            },
            error: (errorResponse: HttpErrorResponse) => {
                this.showErrorNotification('deletionError', {}, errorResponse.message);
                this.actionStatus.set(undefined);
            },
        });
    }

    /**
     * Redirection to {@link SortService} for sorting the table of exam rooms
     */
    sortRows(): void {
        if (!this.hasExamRoomData()) return;
        this.sortService.sortByProperty(this.examRoomData()!, this.sortAttribute, this.ascending);
    }

    private showErrorNotification(translationKey: string, interpolationValues?: any, trailingText?: string, translatePath: string = this.baseTranslationPath): void {
        const errorMessage = this.translateService.instant(`${translatePath}.${translationKey}`, interpolationValues);
        this.alertService.error(trailingText ? `${errorMessage}: "${trailingText}"` : errorMessage);
    }

    private getMaxCapacityOfExamRoom(examRoom: ExamRoomDTO): number {
        return examRoom.layoutStrategies?.map((layoutStrategy) => layoutStrategy.capacity).reduce((max, curr) => Math.max(max, curr), 0) ?? 0;
    }

    private getDefaultCapacityOfExamRoom(examRoom: ExamRoomDTO): number {
        return (
            examRoom.layoutStrategies
                ?.filter((layoutStrategy) => layoutStrategy.name.toLowerCase() === 'default')
                .map((layoutStrategy) => layoutStrategy.capacity)
                .at(0) ?? 0
        );
    }

    private calculateExamRoomData() {
        return this.overview()?.newestUniqueExamRooms?.map(
            (examRoomDTO) =>
                ({
                    ...examRoomDTO,
                    defaultCapacity: this.getDefaultCapacityOfExamRoom(examRoomDTO),
                    maxCapacity: this.getMaxCapacityOfExamRoom(examRoomDTO),
                }) as ExamRoomDTOExtended,
        );
    }
}
