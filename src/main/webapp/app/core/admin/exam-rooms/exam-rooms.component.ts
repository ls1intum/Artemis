import { Component, Signal, WritableSignal, computed, effect, inject, signal } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { ExamRoomAdminOverviewDTO, ExamRoomDTO, ExamRoomDeletionSummaryDTO, ExamRoomUploadInformationDTO } from 'app/core/admin/exam-rooms/exam-rooms.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortService } from 'app/shared/service/sort.service';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faEye, faFilter, faPlus, faSort, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { NgbHighlight } from '@ng-bootstrap/ng-bootstrap';

// privately used interfaces, i.e., not sent from the server like this
export interface ExamRoomDTOExtended extends ExamRoomDTO {
    maxCapacity: number;
    layoutStrategyNames: string[];
}

@Component({
    selector: 'app-exam-room-repository',
    templateUrl: './exam-rooms.component.html',
    imports: [TranslateDirective, SortDirective, SortByDirective, FaIconComponent, NgbHighlight],
})
export class ExamRoomsComponent {
    private http: HttpClient = inject(HttpClient);
    private sortService: SortService = inject(SortService);

    // Writeable signals
    selectedFile: WritableSignal<File | undefined> = signal(undefined);
    uploading: WritableSignal<boolean> = signal(false);
    uploadResult: WritableSignal<'success' | 'error' | undefined> = signal(undefined);
    uploadInformation: WritableSignal<ExamRoomUploadInformationDTO | undefined> = signal(undefined);
    overview: WritableSignal<ExamRoomAdminOverviewDTO | undefined> = signal(undefined);
    deletionSummary: WritableSignal<ExamRoomDeletionSummaryDTO | undefined> = signal(undefined);

    // Computed signals
    canUpload: Signal<boolean> = computed(() => !!this.selectedFile());
    isUploading: Signal<boolean> = computed(() => this.uploading());
    hasUploadSucceeded: Signal<boolean> = computed(() => this.uploadResult() === 'success');
    hasUploadFailed: Signal<boolean> = computed(() => this.uploadResult() === 'error');
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
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;
    faFilter = faFilter;

    sort_attribute: string = 'roomNumber';
    ascending: boolean = true;

    // Basically ngInit / constructor
    initEffect = effect(() => {
        this.loadExamRoomOverview();
    });

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

    onFileSelected(event: Event): void {
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

    upload(): void {
        const file = this.selectedFile();
        if (!file) return;

        const formData = new FormData();
        formData.append('file', file);

        this.uploading.set(true);
        this.uploadResult.set(undefined);

        this.http
            .post('/api/exam/admin/exam-rooms/upload', formData, {
                reportProgress: true,
                observe: 'events',
            })
            .subscribe({
                next: (event: HttpEvent<any>) => {
                    if (event.type === HttpEventType.Response) {
                        this.uploadResult.set('success');
                        this.selectedFile.set(undefined);
                        this.uploadInformation.set(event.body);
                    }
                },
                error: () => {
                    this.uploading.set(false);
                    this.uploadResult.set('error');
                },
                complete: () => {
                    this.uploading.set(false);
                    this.deletionSummary.set(undefined);
                    this.loadExamRoomOverview();
                },
            });
    }

    clearExamRooms(): void {
        if (!confirm('Are you sure you want to delete ALL exam rooms? This action cannot be undone.')) {
            return;
        }
        this.http.delete<void>('/api/exam/admin/exam-rooms').subscribe({
            next: () => {
                alert('All exam rooms deleted.');
            },
            error: (err) => {
                alert('Failed to clear exam rooms: ' + err.message);
            },
            complete: () => {
                this.turnOffUploadResult();
                this.deletionSummary.set(undefined);
                this.loadExamRoomOverview();
            },
        });
    }

    deleteOutdatedAndUnusedExamRooms(): void {
        this.http.delete<ExamRoomDeletionSummaryDTO>('api/exam/admin/exam-rooms/outdated-and-unused').subscribe({
            next: (summary) => {
                this.deletionSummary.set(summary);
            },
            complete: () => {
                this.uploadInformation.set(undefined);
                this.loadExamRoomOverview();
            },
        });
    }

    sortRows(): void {
        if (!this.hasExamRoomData()) return;
        this.sortService.sortByProperty(this.examRoomData()!, this.sort_attribute, this.ascending);
    }

    private turnOffUploadResult(): void {
        this.uploadResult.set(undefined);
        this.uploadInformation.set(undefined);
    }

    private getMaxCapacityOfExamRoom(examRoom: ExamRoomDTO): number {
        return examRoom!.layoutStrategies?.map((layoutStrategy) => layoutStrategy.capacity ?? 0).reduce((max, curr) => Math.max(max, curr), 0) ?? 0;
    }

    private getLayoutStrategyNames(examRoom: ExamRoomDTO): string[] {
        return examRoom!.layoutStrategies?.map((layoutStrategy) => layoutStrategy.name).sort() ?? [];
    }
}
