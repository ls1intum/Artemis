import { Component, Signal, WritableSignal, computed, effect, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ExamRoomAdminOverviewDTO, ExamRoomDTO, ExamRoomDeletionSummaryDTO, ExamRoomUploadInformationDTO } from 'app/core/admin/exam-rooms/exam-rooms.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortService } from 'app/shared/service/sort.service';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSort } from '@fortawesome/free-solid-svg-icons';

// privately used interfaces, i.e., not sent from the server like this
export interface ExamRoomDTOExtended extends ExamRoomDTO {
    maxCapacity: number;
    layoutStrategyNames: string;
}

@Component({
    selector: 'jhi-exam-rooms',
    templateUrl: './exam-rooms.component.html',
    imports: [TranslateDirective, SortDirective, SortByDirective, FaIconComponent],
})
export class ExamRoomsComponent {
    private http: HttpClient = inject(HttpClient);
    private sortService: SortService = inject(SortService);

    // Writeable signals
    selectedFile: WritableSignal<File | undefined> = signal(undefined);
    actionStatus: WritableSignal<'uploading' | 'uploadSuccess' | 'uploadError' | 'deleting' | 'deletionSuccess' | 'deletionError' | undefined> = signal(undefined);
    actionInformation: WritableSignal<ExamRoomUploadInformationDTO | ExamRoomDeletionSummaryDTO | undefined> = signal(undefined);
    overview: WritableSignal<ExamRoomAdminOverviewDTO | undefined> = signal(undefined);

    // Computed signals
    canUpload: Signal<boolean> = computed(() => !!this.selectedFile());
    isUploading: Signal<boolean> = computed(() => this.actionStatus() === 'uploading');
    hasUploadInformation: Signal<boolean> = computed(() => this.actionStatus() === 'uploadSuccess' && !!this.uploadInformation());
    hasUploadFailed: Signal<boolean> = computed(() => this.actionStatus() === 'uploadError');
    isDeleting: Signal<boolean> = computed(() => this.actionStatus() === 'deleting');
    hasDeletionInformation: Signal<boolean> = computed(() => this.actionStatus() === 'deletionSuccess' && !!this.deletionInformation());
    hasDeletionFailed: Signal<boolean> = computed(() => this.actionStatus() === 'deletionError');
    uploadInformation: Signal<ExamRoomUploadInformationDTO | undefined> = computed(() => this.actionInformation() as ExamRoomUploadInformationDTO);
    deletionInformation: Signal<ExamRoomDeletionSummaryDTO | undefined> = computed(() => this.actionInformation() as ExamRoomDeletionSummaryDTO);
    hasOverview: Signal<boolean> = computed(() => !!this.overview());
    hasExamRoomData: Signal<boolean> = computed(() => !!this.overview()?.examRoomDTOS?.length);
    examRoomData: Signal<ExamRoomDTOExtended[] | undefined> = computed(() => {
        return this.overview()?.examRoomDTOS.map(
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

    // Attributes for working with SortDirective
    sort_attribute: 'roomNumber' | 'name' | 'building' | 'maxCapacity' = 'roomNumber';
    ascending: boolean = true;

    // Basically ngInit / constructor
    initEffect = effect(() => {
        this.loadExamRoomOverview();
    });

    /**
     * Makes a REST request to fetch a new exam room overview and displays it
     */
    loadExamRoomOverview(): void {
        this.http.get<ExamRoomAdminOverviewDTO>('/api/exam/admin/exam-rooms/admin-overview').subscribe({
            next: (response) => {
                this.overview.set(response);
            },
            error: () => {
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
        const input = event.target as HTMLInputElement;
        if (input.files && input.files.length > 0) {
            const file = input.files[0];
            if (file.name.endsWith('.zip')) {
                this.selectedFile.set(file);
            } else {
                this.selectedFile.set(undefined);
            }
        }
    }

    /**
     * Uploads the {@link selectedFile} if one is present.
     * Afterward, it refreshes the exam room overview to immediately see any changes.
     */
    upload(): void {
        const file = this.selectedFile();
        if (!file) return;

        const formData = new FormData();
        formData.append('file', file);

        this.actionStatus.set('uploading');

        this.http.post('/api/exam/admin/exam-rooms/upload', formData).subscribe({
            next: (uploadInformation) => {
                this.actionStatus.set('uploadSuccess');
                this.selectedFile.set(undefined);
                this.actionInformation.set(uploadInformation as ExamRoomUploadInformationDTO);
            },
            error: () => {
                this.actionStatus.set('uploadError');
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
        if (!confirm('Are you sure you want to delete ALL exam rooms? This action cannot be undone.')) {
            return;
        }

        this.actionStatus.set('deleting');

        this.http.delete<void>('/api/exam/admin/exam-rooms').subscribe({
            next: () => {
                this.actionStatus.set('deletionSuccess');
                alert('All exam rooms deleted.');
            },
            error: (err) => {
                this.actionStatus.set('deletionError');
                alert('Failed to clear exam rooms: ' + err.message);
            },
            complete: () => {
                this.actionInformation.set(undefined); // since this purges everything, we don't need a summary
                this.loadExamRoomOverview();
            },
        });
    }

    /**
     * REST request to delete all outdated and unused exams.
     * An exam room is outdated if there exists a newer entry of the same (number, name) combination.
     * An exam room is unused if it isn't connected to any exam.
     */
    deleteOutdatedAndUnusedExamRooms(): void {
        this.actionStatus.set('deleting');

        this.http.delete<ExamRoomDeletionSummaryDTO>('/api/exam/admin/exam-rooms/outdated-and-unused').subscribe({
            next: (summary) => {
                this.actionInformation.set(summary as ExamRoomDeletionSummaryDTO);
                this.actionStatus.set('deletionSuccess');
            },
            error: () => {
                this.actionStatus.set('deletionError');
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
        this.sortService.sortByProperty(this.examRoomData()!, this.sort_attribute, this.ascending);
    }

    private getMaxCapacityOfExamRoom(examRoom: ExamRoomDTO): number {
        return examRoom!.layoutStrategies?.map((layoutStrategy) => layoutStrategy.capacity ?? 0).reduce((max, curr) => Math.max(max, curr), 0) ?? 0;
    }

    private getLayoutStrategyNames(examRoom: ExamRoomDTO): string {
        return (
            examRoom!.layoutStrategies
                ?.map((layoutStrategy) => layoutStrategy.name)
                .sort()
                .join(', ') ?? ''
        );
    }
}
