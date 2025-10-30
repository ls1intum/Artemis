import {
    Component,
    InputSignal,
    ModelSignal,
    OutputEmitterRef,
    Signal,
    ViewEncapsulation,
    WritableSignal,
    computed,
    effect,
    inject,
    input,
    model,
    output,
    signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { Observable, debounceTime, distinctUntilChanged, map } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faBan, faThLarge } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StudentsRoomDistributionService } from 'app/exam/manage/students/room-distribution/students-room-distribution.service';
import { CapacityDisplayDTO, ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';

@Component({
    selector: 'jhi-students-room-distribution-dialog',
    templateUrl: './students-room-distribution-dialog.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, TranslateDirective, FaIconComponent, NgbTypeaheadModule, ArtemisTranslatePipe, HelpIconComponent, DialogModule, ButtonModule],
})
export class StudentsRoomDistributionDialogComponent {
    readonly RESERVE_FACTOR_DEFAULT_PERCENTAGE: number = 10;

    // Icons
    protected readonly faBan = faBan;
    protected readonly faThLarge = faThLarge;

    private readonly studentsRoomDistributionService: StudentsRoomDistributionService = inject(StudentsRoomDistributionService);
    courseId: InputSignal<number> = input.required();
    exam: InputSignal<Exam> = input.required();

    dialogVisible: ModelSignal<boolean> = model(false);
    onSave: OutputEmitterRef<void> = output();

    // Configurable options
    private reservePercentage: WritableSignal<number> = signal(this.RESERVE_FACTOR_DEFAULT_PERCENTAGE);
    private reserveFactor: Signal<number> = computed(() => this.reservePercentage() / 100);
    allowNarrowLayouts: WritableSignal<boolean> = signal(false);

    private availableRooms: Signal<RoomForDistributionDTO[]> = this.studentsRoomDistributionService.availableRooms;
    private selectedRoomsCapacity: Signal<ExamDistributionCapacityDTO> = this.studentsRoomDistributionService.capacityData;
    selectedRooms: WritableSignal<RoomForDistributionDTO[]> = signal([]);
    hasSelectedRooms: Signal<boolean> = computed(() => this.selectedRooms().length > 0);
    seatInfo: Signal<CapacityDisplayDTO> = computed(() => this.computeSeatInfo());
    canSeatAllStudents: Signal<boolean> = computed(() => this.seatInfo().usableCapacity >= this.seatInfo().totalStudents);

    constructor() {
        effect(() => {
            const selectedRoomIds: number[] = this.selectedRooms().map((room) => room.id);
            this.studentsRoomDistributionService.updateCapacityData(selectedRoomIds, this.reserveFactor());
        });
    }

    private computeSeatInfo(): CapacityDisplayDTO {
        const totalStudents: number = this.exam().numberOfExamUsers ?? this.exam().examUsers?.length ?? 0;
        let usableCapacity: number = this.allowNarrowLayouts() ? this.selectedRoomsCapacity().combinedMaximumCapacity : this.selectedRoomsCapacity().combinedDefaultCapacity;
        if (usableCapacity > totalStudents) {
            usableCapacity = totalStudents;
        }
        const percentage: number = totalStudents > 0 ? Math.min(100, Math.round((usableCapacity / totalStudents) * 100)) : 0;

        return {
            totalStudents,
            usableCapacity,
            percentage,
        } as CapacityDisplayDTO;
    }

    openDialog(): void {
        this.selectedRooms.set([]);
        this.dialogVisible.set(true);

        this.studentsRoomDistributionService.loadRoomData();
    }

    closeDialog(): void {
        this.dialogVisible.set(false);
    }

    attemptDistributeAndCloseDialog(): void {
        const selectedRoomIds = this.selectedRooms().map((room) => room.id);
        this.studentsRoomDistributionService
            .distributeStudentsAcrossRooms(this.courseId(), this.exam().id!, selectedRoomIds, this.reserveFactor(), !this.allowNarrowLayouts())
            .subscribe({
                next: () => {
                    this.closeDialog();
                    this.onSave.emit();
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

    private findAllMatchingRoomsForTerm = (term: string): RoomForDistributionDTO[] => {
        const trimmed = term.trim();
        if (!trimmed) {
            return this.removeAllRoomsThatAreAlreadySelected(this.availableRooms());
        }

        const tokens = trimmed.toLowerCase().split(/\s+/);
        return this.removeAllRoomsThatAreAlreadySelected(
            this.availableRooms().filter((room) => {
                const roomFields = [room.name, room.alternativeName, room.roomNumber, room.alternativeRoomNumber, room.building].filter(Boolean).map((str) => str!.toLowerCase());

                // each token must match at least one field
                return tokens.every((token) => {
                    return roomFields.some((roomField) => this.isSubsequence(roomField, token));
                });
            }),
        );
    };

    private removeAllRoomsThatAreAlreadySelected(rooms: RoomForDistributionDTO[]): RoomForDistributionDTO[] {
        const selectedIds = new Set(this.selectedRooms().map((room) => room.id));
        return rooms.filter((room) => !selectedIds.has(room.id));
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
     * Formats the metadata of an exam room into a human-readable format for the dropdown search menu
     *
     * @param room The exam room
     */
    formatter(room: RoomForDistributionDTO): string {
        const namePart = room.alternativeName ? `${room.name} (${room.alternativeName})` : room.name;
        const numberPart = room.alternativeRoomNumber ? `${room.roomNumber} (${room.alternativeRoomNumber})` : room.roomNumber;

        return `${namePart} – ${numberPart} - [${room.building}]`;
    }

    emptyStringFormatter(_room: RoomForDistributionDTO): string {
        return '';
    }

    /**
     * Adds a room to the {@link selectedRooms} and removes it from the {@link availableRooms} if it isn't included and removed already
     *
     * @param event An event containing a selected room
     */
    pickSelectedRoom(event: { item: RoomForDistributionDTO }): void {
        const selectedRoom: RoomForDistributionDTO = event.item as RoomForDistributionDTO;

        if (this.selectedRooms().every((room) => room.id !== selectedRoom.id)) {
            this.selectedRooms.update((rooms) => [...rooms, selectedRoom]);
        }
    }

    /**
     * Removes a room from the {@link selectedRooms} and adds it back to the {@link availableRooms}
     *
     * @param room The room to remove
     */
    removeSelectedRoom(room: RoomForDistributionDTO): void {
        this.selectedRooms.update((selectedRooms) => selectedRooms.filter((selectedRoom) => room.id !== selectedRoom.id));
    }

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

    selectAllTextAndOpenDropdown(focusEvent: FocusEvent): void {
        const input = focusEvent.target as HTMLInputElement;
        setTimeout(() => input.select(), 0);

        const fakeInputEvent = new Event('input', { bubbles: true });
        input.dispatchEvent(fakeInputEvent);
    }

    toggleNarrowLayouts(): void {
        this.allowNarrowLayouts.update((oldValue) => !oldValue);
    }
}
