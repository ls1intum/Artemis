import { Component, EventEmitter, Signal, WritableSignal, computed, effect, inject, signal } from '@angular/core';
import { ExamRoomAdminOverviewDTO, ExamRoomDTO, ExamRoomDeletionSummaryDTO, ExamRoomUploadInformationDTO } from 'app/core/admin/exam-rooms/exam-rooms.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortService } from 'app/shared/service/sort.service';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSort } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExamRoomsService } from 'app/core/admin/exam-rooms/exam-rooms.service';
import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { Subject } from 'rxjs';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';

// privately used interfaces, i.e., not sent from the server like this
interface ExamRoomDTOExtended extends ExamRoomDTO {
    maxCapacity: number;
    layoutStrategyNames: string;
}

@Component({
    selector: 'jhi-exam-rooms',
    templateUrl: './exam-rooms.component.html',
    imports: [TranslateDirective, SortDirective, SortByDirective, FaIconComponent, ArtemisTranslatePipe],
})
export class ExamRoomsComponent {
    // readonly
    private readonly baseTranslationPath = 'artemisApp.examRooms.adminOverview';

    // injected
    private examRoomsService: ExamRoomsService = inject(ExamRoomsService);
    private sortService: SortService = inject(SortService);
    private deleteDialogService: DeleteDialogService = inject(DeleteDialogService);
    private translateService: TranslateService = inject(TranslateService);
    private alertService: AlertService = inject(AlertService);

    // Writeable signals
    private selectedFile: WritableSignal<File | undefined> = signal(undefined);
    private actionStatus: WritableSignal<'uploading' | 'uploadSuccess' | 'deleting' | 'deletionSuccess' | undefined> = signal(undefined);
    private actionInformation: WritableSignal<ExamRoomUploadInformationDTO | ExamRoomDeletionSummaryDTO | undefined> = signal(undefined);
    overview: WritableSignal<ExamRoomAdminOverviewDTO | undefined> = signal(undefined);

    // Computed signals
    hasSelectedFile: Signal<boolean> = computed(() => !!this.selectedFile());
    selectedFileName: Signal<string | undefined> = computed(() => this.selectedFile()?.name);
    canUpload: Signal<boolean> = computed(() => this.hasSelectedFile() && !this.isUploading());
    isUploading: Signal<boolean> = computed(() => this.actionStatus() === 'uploading');
    hasUploadInformation: Signal<boolean> = computed(() => this.actionStatus() === 'uploadSuccess' && !!this.uploadInformation());
    isDeleting: Signal<boolean> = computed(() => this.actionStatus() === 'deleting');
    hasDeletionInformation: Signal<boolean> = computed(() => this.actionStatus() === 'deletionSuccess' && !!this.deletionInformation());
    uploadInformation: Signal<ExamRoomUploadInformationDTO | undefined> = computed(() => this.actionInformation() as ExamRoomUploadInformationDTO);
    deletionInformation: Signal<ExamRoomDeletionSummaryDTO | undefined> = computed(() => this.actionInformation() as ExamRoomDeletionSummaryDTO);
    hasOverview: Signal<boolean> = computed(() => !!this.overview());
    numberOfUniqueExamRooms: Signal<number> = computed(() => this.overview()?.newestUniqueExamRooms?.length ?? 0);
    numberOfUniqueExamSeats: Signal<number> = computed(
        () =>
            this.overview()
                ?.newestUniqueExamRooms?.map((examRoomDTO) => examRoomDTO.numberOfSeats)
                .reduce((acc, val) => acc + val, 0) ?? 0,
    );
    numberOfUniqueLayoutStrategies: Signal<number> = computed(
        () =>
            this.overview()
                ?.newestUniqueExamRooms?.map((examRoomDTO) => examRoomDTO.layoutStrategies?.length ?? 0)
                .reduce((acc, val) => acc + val, 0) ?? 0,
    );
    distinctLayoutStrategyNames: Signal<string> = computed(() =>
        [...new Set(this.overview()?.newestUniqueExamRooms?.flatMap((examRoomDTO) => examRoomDTO.layoutStrategies?.map((layoutStrategy) => layoutStrategy.name)) ?? [])]
            .slice()
            .sort()
            .join(', '),
    );
    hasExamRoomData: Signal<boolean> = computed(() => !!this.numberOfUniqueExamRooms());
    examRoomData: Signal<ExamRoomDTOExtended[] | undefined> = computed(() => {
        return this.overview()?.newestUniqueExamRooms?.map(
            (examRoomDTO) =>
                ({
                    ...examRoomDTO,
                    maxCapacity: this.getMaxCapacityOfExamRoom(examRoomDTO),
                    layoutStrategyNames: this.getLayoutStrategyNames(examRoomDTO),
                }) as ExamRoomDTOExtended,
        );
    });

    // Icons
    faSort = faSort;

    // Fields for working with SortDirective
    sortAttribute: 'roomNumber' | 'name' | 'building' | 'maxCapacity' = 'roomNumber';
    ascending: boolean = true;

    // Fields for working with DeletionDialogService
    private dialogErrorSource = new Subject<string>();
    private dialogError = this.dialogErrorSource.asObservable();

    // Basically ngInit / constructor
    initEffect = effect(() => {
        this.loadExamRoomOverview();
    });

    /**
     * Makes a REST request to fetch a new exam room overview and displays it
     */
    loadExamRoomOverview(): void {
        this.examRoomsService.getAdminOverview().subscribe({
            next: (examRoomAdminOverviewResponse: HttpResponse<ExamRoomAdminOverviewDTO>) => {
                this.overview.set(examRoomAdminOverviewResponse.body as ExamRoomAdminOverviewDTO);
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

        const file = files[0];
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
            },
            error: (errorResponse: HttpErrorResponse) => {
                this.showErrorNotification('uploadError', {}, errorResponse.message);
                this.actionStatus.set(undefined);
            },
            complete: () => {
                this.loadExamRoomOverview();
            },
        });
    }

    /**
     * REST request to delete ALL exam room related data.
     */
    clearExamRooms(): void {
        const deleteEmitter = new EventEmitter<{ [key: string]: boolean }>();

        deleteEmitter.subscribe(() => {
            this.actionStatus.set('deleting');
            this.examRoomsService.deleteAllExamRooms().subscribe({
                next: () => {
                    this.actionStatus.set('deletionSuccess');
                    this.actionInformation.set(undefined);
                },
                error: (errorResponse: HttpErrorResponse) => {
                    this.showErrorNotification('deletionError', {}, errorResponse.message);
                    this.actionStatus.set(undefined);
                },
                complete: () => {
                    this.dialogErrorSource.next(''); // this.showErrorNotification is easier to use
                    this.loadExamRoomOverview();
                },
            });
        });

        this.deleteDialogService.openDeleteDialog({
            deleteQuestion: `${this.baseTranslationPath}.deleteAllExamRoomsQuestion`,
            buttonType: ButtonType.ERROR,
            actionType: ActionType.Delete,
            delete: deleteEmitter,
            dialogError: this.dialogError,
            requireConfirmationOnlyForAdditionalChecks: false,
            translateValues: {},
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
            },
            error: (errorResponse: HttpErrorResponse) => {
                this.showErrorNotification('deletionError', {}, errorResponse.message);
                this.actionStatus.set(undefined);
            },
            complete: () => {
                this.loadExamRoomOverview();
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
        return examRoom.layoutStrategies?.map((layoutStrategy) => layoutStrategy.capacity ?? 0).reduce((max, curr) => Math.max(max, curr), 0) ?? 0;
    }

    private getLayoutStrategyNames(examRoom: ExamRoomDTO): string {
        return (
            examRoom.layoutStrategies
                ?.map((layoutStrategy) => layoutStrategy.name)
                .sort()
                .join(', ') ?? ''
        );
    }
}
