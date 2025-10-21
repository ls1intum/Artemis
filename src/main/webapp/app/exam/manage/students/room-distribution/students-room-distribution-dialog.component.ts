import { Component, Signal, ViewEncapsulation, WritableSignal, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal, NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { Observable, combineLatest, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faArrowRight, faBan, faCheck, faCircleNotch, faSpinner, faUpload } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StudentsRoomDistributionService } from 'app/exam/manage/students/room-distribution/students-room-distribution.service';
import { HttpResponse } from '@angular/common/http';
import { CapacityDisplayDTO, ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';

@Component({
    selector: 'jhi-students-room-distribution-dialog',
    templateUrl: './students-room-distribution-dialog.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, TranslateDirective, FaIconComponent, NgbTypeaheadModule, ArtemisTranslatePipe],
})
export class StudentsRoomDistributionDialogComponent {
    private activeModal = inject(NgbActiveModal);
    private studentsRoomDistributionService: StudentsRoomDistributionService = inject(StudentsRoomDistributionService);

    courseId = input.required<number>();
    exam = input.required<Exam>();

    availableRooms: WritableSignal<RoomForDistributionDTO[] | undefined> = signal(undefined);
    selectedRooms: WritableSignal<RoomForDistributionDTO[]> = signal([]);
    hasSelectedRooms: Signal<boolean> = computed(() => this.selectedRooms().length > 0);

    // Configurable options
    readonly reserveFactorDefaultPercentage: number = 10;
    private reserveFactor: WritableSignal<number> = signal(this.reserveFactorDefaultPercentage / 100);
    allowNarrowLayouts: WritableSignal<boolean> = signal(false);

    private selectedRoomsCapacity: Signal<ExamDistributionCapacityDTO> = toSignal(
        combineLatest([toObservable(this.selectedRooms), toObservable(this.reserveFactor)]).pipe(
            switchMap(([rooms, reserveFactor]) => {
                const ids = rooms.map((room) => room.id);
                return this.studentsRoomDistributionService.getCapacityData(ids, reserveFactor).pipe(map((res) => res.body as ExamDistributionCapacityDTO));
            }),
        ),
        { initialValue: { combinedDefaultCapacity: 0, combinedMaximumCapacity: 0 } as ExamDistributionCapacityDTO },
    );
    seatInfo: Signal<CapacityDisplayDTO> = computed(() => {
        const totalStudents = this.exam().numberOfExamUsers ?? this.exam().examUsers?.length ?? 0;
        let usableCapacity = this.allowNarrowLayouts() ? this.selectedRoomsCapacity().combinedMaximumCapacity : this.selectedRoomsCapacity().combinedDefaultCapacity;
        if (usableCapacity > totalStudents) {
            usableCapacity = totalStudents;
        }
        const percentage = totalStudents > 0 ? Math.min(100, Math.round((usableCapacity / totalStudents) * 100)) : 0;

        return {
            totalStudents,
            usableCapacity,
            percentage,
        } as CapacityDisplayDTO;
    });

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;
    faCircleNotch = faCircleNotch;
    faUpload = faUpload;
    faArrowRight = faArrowRight;

    initEffect = effect(() => {
        this.studentsRoomDistributionService.getRoomData().subscribe({
            next: (result: HttpResponse<RoomForDistributionDTO[]>) => {
                this.availableRooms.set(result.body as RoomForDistributionDTO[]);
            },
            error: () => {
                this.availableRooms.set(undefined);
            },
        });
    });

    clear() {
        this.activeModal.dismiss('cancel');
    }

    onFinish() {
        const selectedRoomIds = this.selectedRooms().map((room) => room.id);
        this.studentsRoomDistributionService
            .distributeStudentsAcrossRooms(this.courseId(), this.exam().id!, selectedRoomIds, this.reserveFactor(), !this.allowNarrowLayouts())
            .subscribe({
                next: () => {
                    this.activeModal.close();
                },
            });
    }

    /**
     * Filters out all exam rooms that might fit the search text.
     * This is not defined in the regular function way, because only this way does it keep the {@code this} reference,
     * as this function is passed by reference to another component.
     *
     * @param text$ An input text
     */
    search = (text$: Observable<string>): Observable<RoomForDistributionDTO[]> => {
        return text$.pipe(debounceTime(200), distinctUntilChanged(), map(this.findAllMatchingRoomsForTerm));
    };

    findAllMatchingRoomsForTerm = (term: string): RoomForDistributionDTO[] => {
        const trimmed = term.trim();
        if (!trimmed) {
            return this.availableRooms() ?? [];
        }

        const tokens = trimmed.toLowerCase().split(/\s+/);
        return (
            this.availableRooms()?.filter((room) => {
                const roomFields = [room.name, room.alternativeName, room.number, room.alternativeNumber, room.building].filter(Boolean).map((str) => str!.toLowerCase());

                // each token must match at least one field
                return tokens.every((token) => {
                    return roomFields.some((roomField) => this.isSubsequence(roomField, token));
                });
            }) ?? []
        );
    };

    /**
     * Formats the metadata of an exam room into a human-readable format for the dropdown search menu
     *
     * @param room The exam room
     */
    formatter(room: RoomForDistributionDTO) {
        const namePart = room.alternativeName ? `${room.name} (${room.alternativeName})` : room.name;

        const numberPart = room.alternativeNumber ? `${room.number} (${room.alternativeNumber})` : room.number;

        return `${namePart} – ${numberPart} - [${room.building}]`;
    }

    /**
     * Adds a room to the {@link selectedRooms} if it isn't included already
     *
     * @param event An event containing a selected room
     */
    onRoomSelected(event: { item: RoomForDistributionDTO }) {
        this.selectedRooms.update((rooms) => (rooms.includes(event.item) ? rooms : [...rooms, event.item]));
    }

    /**
     * Removes a room from the {@link selectedRooms}
     *
     * @param room The room to remove
     */
    removeSelectedRoom(room: RoomForDistributionDTO) {
        this.selectedRooms.update((selectedRooms) => selectedRooms.filter((selectedRoom) => room.id !== selectedRoom.id));
    }

    /**
     * Returns true iff the subsequence is a subsequence of the string.
     * A string is a subsequence of another, if it matches the string, while allowing for omitted characters.
     *
     * @param str A string
     * @param subsequence A string that we want to check if it is a subsequence of {@code str}
     */
    private isSubsequence(str: string, subsequence: string): boolean {
        if (str.length < subsequence.length) {
            return false;
        }

        let strIndex: number = 0;
        let subsequenceIndex: number = 0;
        while (strIndex < str.length && subsequenceIndex < subsequence.length) {
            if (str[strIndex] === subsequence[subsequenceIndex]) {
                subsequenceIndex++;
            }
            strIndex++;
        }

        return subsequenceIndex === subsequence.length;
    }

    handleReserveFactorEvent(event: Event) {
        const input = event.target as HTMLInputElement;

        const percentage = parseInt(input.value);
        if (isNaN(percentage) || percentage < 0 || percentage > 100) {
            return;
        }

        this.reserveFactor.set(percentage / 100);
    }

    resetReserveFactorText(event: Event) {
        const input = event.target as HTMLInputElement;
        input.value = `${this.reserveFactor() * 100}`;
    }

    toggleNarrowLayouts(): void {
        this.allowNarrowLayouts.set(!this.allowNarrowLayouts());
    }
}
