import { Component, OnInit, Signal, WritableSignal, computed, inject, model, signal } from '@angular/core';
import { ExamRoomOverviewNumberOfAvailable, ExamRoomWithCapacity } from 'app/exam/manage/students/room-distribution/exam-rooms.model';
import type { ExamRoom } from 'app/openapi/model/exam-room';
import type { ExamRoomDeletionSummary } from 'app/openapi/model/exam-room-deletion-summary';
import type { ExamRoomOverview } from 'app/openapi/model/exam-room-overview';
import type { ExamRoomUploadInformation } from 'app/openapi/model/exam-room-upload-information';
import { AdminExamRoomManagementApi } from 'app/openapi/api/admin-exam-room-management-api';
import { ExamRoomManagementApi } from 'app/openapi/api/exam-room-management-api';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SortService } from 'app/foundation/service/sort.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import {
    TumAetUiButtonDirective,
    TumAetUiCardComponent,
    TumAetUiEmptyStateComponent,
    TumAetUiTableDirective,
    TumAetUiTableLayoutComponent,
    TumAetUiTableSortEvent,
    TumAetUiTableSortableColumnComponent,
} from '@tumaet/ui-angular';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { ActivatedRoute } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';

/**
 * Component for managing exam rooms.
 * Allows uploading room data via ZIP files and viewing/deleting exam room configurations.
 */
@Component({
    selector: 'jhi-exam-rooms',
    templateUrl: './exam-rooms.component.html',
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        HelpIconComponent,
        TumAetUiButtonDirective,
        TumAetUiCardComponent,
        TumAetUiEmptyStateComponent,
        TumAetUiTableDirective,
        TumAetUiTableLayoutComponent,
        TumAetUiTableSortableColumnComponent,
    ],
})
export class ExamRoomsComponent implements OnInit {
    private readonly examRoomManagementApi = inject(ExamRoomManagementApi);
    private readonly adminExamRoomManagementApi = inject(AdminExamRoomManagementApi);
    private readonly sortService = inject(SortService);
    private readonly translateService = inject(TranslateService);
    private readonly alertService = inject(AlertService);
    private readonly route = inject(ActivatedRoute);
    private readonly accountService = inject(AccountService);

    private readonly baseTranslationPath = 'artemisApp.examRooms.management';

    private selectedFile: WritableSignal<File | undefined> = signal(undefined);
    private actionStatus: WritableSignal<'uploading' | 'uploadSuccess' | 'deleting' | 'deletionSuccess' | undefined> = signal(undefined);
    private actionInformation: WritableSignal<ExamRoomUploadInformation | ExamRoomDeletionSummary | undefined> = signal(undefined);
    private overview: WritableSignal<ExamRoomOverview | undefined> = signal(undefined);

    /** Only administrators upload room data and clean up the rooms; instructors view the overview in the context of an exam. */
    readonly isAdmin: boolean = this.accountService.isAdmin();

    readonly hasSelectedFile: Signal<boolean> = computed(() => !!this.selectedFile());
    readonly selectedFileName: Signal<string | undefined> = computed(() => this.selectedFile()?.name?.trim());
    readonly canUpload: Signal<boolean> = computed(() => this.hasSelectedFile() && !this.isUploading());
    readonly isUploading: Signal<boolean> = computed(() => this.actionStatus() === 'uploading');
    readonly hasUploadInformation: Signal<boolean> = computed(() => this.actionStatus() === 'uploadSuccess' && !!this.uploadInformation());
    readonly isDeleting: Signal<boolean> = computed(() => this.actionStatus() === 'deleting');
    readonly hasDeletionInformation: Signal<boolean> = computed(() => this.actionStatus() === 'deletionSuccess' && !!this.deletionInformation());
    readonly uploadInformation: Signal<ExamRoomUploadInformation | undefined> = computed(() => this.actionInformation() as ExamRoomUploadInformation);
    readonly deletionInformation: Signal<ExamRoomDeletionSummary | undefined> = computed(() => this.actionInformation() as ExamRoomDeletionSummary);
    readonly hasOverview: Signal<boolean> = computed(() => !!this.overview());
    private readonly numberOfUniqueExamRooms: Signal<number> = computed(() => this.overview()?.newestUniqueExamRooms?.length ?? 0);
    private readonly numberOfUniqueExamSeats: Signal<number> = computed(
        () =>
            this.overview()
                ?.newestUniqueExamRooms?.map((examRoomDTO) => examRoomDTO.numberOfSeats)
                .reduce((acc, val) => acc + val, 0) ?? 0,
    );
    readonly numberOfAvailable: Signal<ExamRoomOverviewNumberOfAvailable | undefined> = computed(() => {
        if (!this.hasOverview()) {
            return undefined;
        }

        return {
            examRooms: this.numberOfUniqueExamRooms(),
            examSeats: this.numberOfUniqueExamSeats(),
        };
    });
    readonly hasExamRoomData: Signal<boolean> = computed(() => !!this.numberOfUniqueExamRooms());
    readonly examRoomData: Signal<ExamRoomWithCapacity[] | undefined> = computed(() => this.calculateExamRoomData());

    // Sort state of the exam room table
    readonly sortAttribute = model<string>('name');
    readonly ascending = model(true);

    ngOnInit() {
        this.loadExamRoomOverview();
    }

    /**
     * Makes a REST request to fetch a new exam room overview and displays it
     */
    loadExamRoomOverview(): void {
        const { courseId, examId } = this.route.snapshot.queryParams;
        const overview$ =
            courseId && examId ? this.examRoomManagementApi.getExamRoomOverview(Number(courseId), Number(examId)) : this.adminExamRoomManagementApi.getExamRoomOverview();
        overview$.subscribe({
            next: (examRoomOverview: ExamRoomOverview) => {
                this.overview.set(examRoomOverview);
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

        this.adminExamRoomManagementApi.uploadRoomZip(file).subscribe({
            next: (uploadInformation: ExamRoomUploadInformation) => {
                this.actionStatus.set('uploadSuccess');
                this.selectedFile.set(undefined);
                this.actionInformation.set(uploadInformation);
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

        this.adminExamRoomManagementApi.deleteAllOutdatedAndUnusedExamRooms().subscribe({
            next: (deletionSummary: ExamRoomDeletionSummary) => {
                this.actionInformation.set(deletionSummary);
                this.actionStatus.set('deletionSuccess');
                this.loadExamRoomOverview();
            },
            error: (errorResponse: HttpErrorResponse) => {
                this.showErrorNotification('deletionError', {}, errorResponse.message);
                this.actionStatus.set(undefined);
            },
        });
    }

    protected onSortChange(event: TumAetUiTableSortEvent): void {
        this.sortAttribute.set(event.field);
        this.ascending.set(event.order === 1);
        this.sortRows();
    }

    /**
     * Redirection to {@link SortService} for sorting the table of exam rooms
     */
    sortRows(): void {
        if (!this.hasExamRoomData()) return;
        this.sortService.sortByProperty(this.examRoomData()!, this.sortAttribute(), this.ascending());
    }

    private showErrorNotification(
        translationKey: string,
        interpolationValues?: Record<string, unknown>,
        trailingText?: string,
        translatePath: string = this.baseTranslationPath,
    ): void {
        const errorMessage = this.translateService.instant(`${translatePath}.${translationKey}`, interpolationValues);
        this.alertService.error(trailingText ? `${errorMessage}: "${trailingText}"` : errorMessage);
    }

    private getMaxCapacityOfExamRoom(examRoom: ExamRoom): number {
        return examRoom.layoutStrategies?.map((layoutStrategy) => layoutStrategy.capacity).reduce((max, curr) => Math.max(max, curr), 0) ?? 0;
    }

    private getDefaultCapacityOfExamRoom(examRoom: ExamRoom): number {
        return (
            examRoom.layoutStrategies
                ?.filter((layoutStrategy) => layoutStrategy.name.toLowerCase() === 'default')
                .map((layoutStrategy) => layoutStrategy.capacity)
                .at(0) ?? 0
        );
    }

    private calculateExamRoomData() {
        return this.overview()?.newestUniqueExamRooms?.map((examRoomDTO) =>
            cloneWith(examRoomDTO, { defaultCapacity: this.getDefaultCapacityOfExamRoom(examRoomDTO), maxCapacity: this.getMaxCapacityOfExamRoom(examRoomDTO) }),
        );
    }
}
