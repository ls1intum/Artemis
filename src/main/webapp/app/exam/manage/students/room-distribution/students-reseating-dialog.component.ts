import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
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
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentsRoomDistributionService } from 'app/exam/manage/services/students-room-distribution.service';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { faBan, faChair } from '@fortawesome/free-solid-svg-icons';
import { RoomForDistributionDTO, SeatsOfExamRoomDTO } from './students-room-distribution.model';
import { Observable, debounceTime, distinctUntilChanged, map } from 'rxjs';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

@Component({
    selector: 'jhi-students-reseating-dialog',
    standalone: true,
    templateUrl: './students-reseating-dialog.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, TranslateDirective, FaIconComponent, NgbTypeaheadModule, ArtemisTranslatePipe, DialogModule, ButtonModule, HelpIconComponent],
})
export class StudentsReseatingDialogComponent {
    // Icons
    protected readonly faBan = faBan;
    protected readonly faChair = faChair;

    private readonly studentsRoomDistributionService = inject(StudentsRoomDistributionService);
    courseId: InputSignal<number> = input.required();
    exam: InputSignal<Exam> = input.required();
    examUser: WritableSignal<ExamUser | undefined> = signal(undefined);

    dialogVisible: ModelSignal<boolean> = model(false);
    onSave: OutputEmitterRef<void> = output();

    private roomsUsedInExam: WritableSignal<RoomForDistributionDTO[]> = signal([]);
    selectedRoomNumber: WritableSignal<string> = signal('');
    private readonly selectedRoom: Signal<RoomForDistributionDTO | undefined> = computed(() => this.getRoomDTOFromSelectedRoomNumber());
    private readonly selectedRoomId: Signal<number | undefined> = computed(() => this.selectedRoom()?.id);
    readonly selectedRoomIsPersisted: Signal<boolean> = computed(() => this.selectedRoomId() !== undefined);
    private seatsOfSelectedRoom: WritableSignal<SeatsOfExamRoomDTO> = signal({ seats: [] });
    selectedSeat: WritableSignal<string> = signal('');
    readonly selectedSeatIsPersisted: Signal<boolean> = computed(() => this.selectedRoomIsPersisted() && this.seatsOfSelectedRoom().seats.includes(this.selectedSeat()));

    constructor() {
        effect(() => {
            if (!this.selectedRoomIsPersisted()) {
                this.seatsOfSelectedRoom.set({ seats: [] });
                return;
            }

            this.studentsRoomDistributionService.loadSeatsOfExamRoom(this.selectedRoomId()!).subscribe({
                next: (data) => {
                    this.seatsOfSelectedRoom.set(data);
                },
                // An error is expected whenever the room is not persisted
                error: () => this.seatsOfSelectedRoom.set({ seats: [] }),
            });
        });
    }

    openDialog(examUser: ExamUser): void {
        this.examUser.set(examUser);
        this.selectedRoomNumber.set(this.examUser()!.plannedRoom ?? '');
        this.selectedSeat.set('');
        this.dialogVisible.set(true);

        this.studentsRoomDistributionService.loadRoomsUsedInExam(this.courseId(), this.exam().id!).subscribe({
            next: (rooms: RoomForDistributionDTO[]) => {
                this.roomsUsedInExam.set(rooms);
            },
            error: (_err) => {
                this.roomsUsedInExam.set([]);
            },
        });
    }

    closeDialog(): void {
        this.dialogVisible.set(false);
    }

    protected attemptReseatAndCloseDialogOnSuccess(): void {
        const actualSelectedRoomNumber: string = this.selectedRoom()?.roomNumber ?? this.selectedRoomNumber();
        this.studentsRoomDistributionService
            .reseatStudent(this.courseId(), this.exam().id!, this.examUser()!.id!, actualSelectedRoomNumber, this.selectedSeat() || undefined)
            .subscribe({
                next: () => {
                    this.closeDialog();
                    this.onSave.emit();
                },
            });
    }

    protected pickSelectedRoom(event: { item: RoomForDistributionDTO }): void {
        const selectedRoom: RoomForDistributionDTO = event.item as RoomForDistributionDTO;

        this.selectedRoomNumber.set(selectedRoom.roomNumber);
    }

    protected pickSelectedSeat(event: { item: string }): void {
        const selectedSeat: string = event.item as string;

        this.selectedSeat.set(selectedSeat);
    }

    /**
     * Filters out all exam rooms that might fit the search text.
     * This is not defined in the regular function way, because only this way does it keep the {@code this} reference,
     * as this function is passed by reference to another component.
     *
     * @param text$ An input text
     */
    protected roomSearch = (text$: Observable<string>): Observable<RoomForDistributionDTO[]> => {
        return text$.pipe(debounceTime(200), distinctUntilChanged(), map(this.findAllMatchingRoomsForTerm));
    };

    /**
     * Finds all exam seats that might fit the search text.
     *
     * @param text$ An input text
     */
    protected examSeatSearch = (text$: Observable<string>): Observable<string[]> => {
        return text$.pipe(debounceTime(200), distinctUntilChanged(), map(this.findAllMatchingSeatsForTerm));
    };

    private findAllMatchingRoomsForTerm = (term: string): RoomForDistributionDTO[] => {
        const potentialRooms: RoomForDistributionDTO[] = this.roomsUsedInExam();

        const trimmed = term.trim();
        if (!trimmed) {
            return potentialRooms;
        }

        const ANY_WHITESPACE: RegExp = /\s+/;
        const tokens: string[] = trimmed.toLowerCase().split(ANY_WHITESPACE);

        return potentialRooms.filter((room) => {
            const roomFields = [room.name, room.alternativeName, room.roomNumber, room.alternativeRoomNumber, room.building].filter(Boolean).map((str) => str!.toLowerCase());

            // each token must match at least one field
            return tokens.every((token) => {
                return roomFields.some((roomField) => this.isSubsequence(roomField, token));
            });
        });
    };

    private findAllMatchingSeatsForTerm = (term: string): string[] => {
        const potentialSeats: string[] = this.seatsOfSelectedRoom().seats;

        const trimmed = term.trim();
        if (!trimmed) {
            return potentialSeats;
        }

        const ANY_WHITESPACE: RegExp = /\s+/;
        const tokens: string[] = trimmed.toLowerCase().split(ANY_WHITESPACE);

        return potentialSeats.filter((seatName) => {
            return tokens.every((token) => {
                return this.isSubsequence(seatName, token);
            });
        });
    };

    /**
     * Formats the metadata of an exam room into a human-readable format for the dropdown search menu
     *
     * @param room The exam room
     */
    protected roomFormatter(room: RoomForDistributionDTO): string {
        const namePart = room.alternativeName ? `${room.name} (${room.alternativeName})` : room.name;
        const numberPart = room.alternativeRoomNumber ? `${room.roomNumber} (${room.alternativeRoomNumber})` : room.roomNumber;

        return `${namePart} – ${numberPart} - [${room.building}]`;
    }

    protected roomRoomNumberFormatter(room: RoomForDistributionDTO): string {
        return room.roomNumber;
    }

    protected examSeatFormatter(seatName: string): string {
        return seatName;
    }

    protected selectAllTextAndOpenDropdown(focusEvent: FocusEvent): void {
        const input = focusEvent.target as HTMLInputElement;
        setTimeout(() => input.select(), 0);

        const fakeInputEvent = new Event('input', { bubbles: true });
        input.dispatchEvent(fakeInputEvent);
    }

    /**
     * Returns true if the subsequence is part of the string.
     * A string is a subsequence of another, if it matches the other string, while allowing for omitted characters.
     *
     * Essentially this functions performs the equivalent of inserting '.*' before and after each character of the
     * subsequence, then using that modified subsequence as a regex expression to match against the token.
     *
     * @param token A string without any whitespace
     * @param subsequence A string that we want to check if it is a subsequence of the token
     */
    private isSubsequence(token: string, subsequence: string): boolean {
        if (token.length < subsequence.length) {
            return false;
        }

        let strIndex: number = 0;
        let subsequenceIndex: number = 0;
        while (strIndex < token.length && subsequenceIndex < subsequence.length) {
            if (token[strIndex] === subsequence[subsequenceIndex]) {
                subsequenceIndex++;
            }
            strIndex++;
        }

        return subsequenceIndex === subsequence.length;
    }

    protected getSelectedStudentName(): string {
        const name: string = (this.examUser()?.user?.firstName ?? '') + ' ' + (this.examUser()?.user?.lastName ?? '');
        return name.trim();
    }

    private getRoomDTOFromSelectedRoomNumber(): RoomForDistributionDTO | undefined {
        return this.roomsUsedInExam().find((room) => room.roomNumber === this.selectedRoomNumber());
    }

    protected setUnassignedRoomNumber($event: Event) {
        const input: HTMLInputElement = $event.target as HTMLInputElement;
        this.selectedRoomNumber.set(input.value);
    }

    protected setUnassignedSeat($event: Event) {
        const input: HTMLInputElement = $event.target as HTMLInputElement;
        this.selectedSeat.set(input.value);
    }
}
