import { Component, InputSignal, Signal, ViewEncapsulation, WritableSignal, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal, NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { Observable, catchError, combineLatest, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faBan, faThLarge } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StudentsRoomDistributionService } from 'app/exam/manage/students/room-distribution/students-room-distribution.service';
import { CapacityDisplayDTO, ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-students-room-distribution-dialog',
    templateUrl: './students-room-distribution-dialog.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, TranslateDirective, FaIconComponent, NgbTypeaheadModule, ArtemisTranslatePipe, HelpIconComponent],
})
export class StudentsRoomDistributionDialogComponent {
    private activeModal = inject(NgbActiveModal);
    private studentsRoomDistributionService: StudentsRoomDistributionService = inject(StudentsRoomDistributionService);

    courseId: InputSignal<number> = input.required();
    exam: InputSignal<Exam> = input.required();

    // Configurable options
    readonly reserveFactorDefaultPercentage: number = 10;
    private reservePercentage: WritableSignal<number> = signal(this.reserveFactorDefaultPercentage);
    private reserveFactor: Signal<number> = computed(() => this.reservePercentage() / 100);
    allowNarrowLayouts: WritableSignal<boolean> = signal(false);

    private availableRooms: Signal<RoomForDistributionDTO[] | undefined> = toSignal(
        this.studentsRoomDistributionService.getRoomData().pipe(
            map((result) => result.body as RoomForDistributionDTO[]),
            catchError(() => of(undefined)),
        ),
        { initialValue: undefined },
    );
    selectedRooms: WritableSignal<RoomForDistributionDTO[]> = signal([]);
    hasSelectedRooms: Signal<boolean> = computed(() => this.selectedRooms().length > 0);
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
    canSeatAllStudents: Signal<boolean> = computed(() => this.seatInfo().usableCapacity >= this.seatInfo().totalStudents);

    // Icons
    faBan = faBan;
    faThLarge = faThLarge;

    // constructor() {
    //     effect(() => {
    //         this.studentsRoomDistributionService.getRoomData().subscribe({
    //             next: (result: HttpResponse<RoomForDistributionDTO[]>) => {
    //                 this.availableRooms.set(result.body as RoomForDistributionDTO[]);
    //             },
    //             error: () => {
    //                 this.availableRooms.set(undefined);
    //             },
    //         });
    //     });
    // }

    /**
     * Dismisses the dialog
     */
    clear(): void {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Attempts a distribution and closes the dialog if successful
     */
    onFinish(): void {
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
        if (this.selectedRooms().every((room) => room.id !== event.item.id)) {
            this.selectedRooms.update((rooms) => [...rooms, event.item]);
        }
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

    /**
     * Handles a change of the reserve factor value.
     *
     * @param event an event with an input element
     */
    handleReserveFactorInput(event: Event): void {
        const input: HTMLInputElement = event.target as HTMLInputElement;

        input.value = input.value.replaceAll(/\D/g, '');
        const percentage: number = Number(input.value) || 0;
        if (!Number.isInteger(percentage) || percentage < 0 || percentage > 100) {
            this.resetReserveFactorText(event);
            return;
        }

        this.reservePercentage.set(percentage);
    }

    /**
     * Resets the reserve factor text to what is stored internally
     *
     * @param event an event with an input element
     */
    resetReserveFactorText(event: Event): void {
        const input: HTMLInputElement = event.target as HTMLInputElement;
        input.value = `${this.reservePercentage()}`;
    }

    /**
     * Selects all the text from a given focus event
     *
     * @param focusEvent a focus event
     */
    selectAllText(focusEvent: FocusEvent): void {
        const input = focusEvent.target as HTMLInputElement;
        setTimeout(() => input.select(), 0);
    }

    /**
     * Toggles whether narrow layouts are allowed
     */
    toggleNarrowLayouts(): void {
        this.allowNarrowLayouts.set(!this.allowNarrowLayouts());
    }
}
