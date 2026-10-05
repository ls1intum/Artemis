import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Component, InputSignal, ModelSignal, OnInit, OutputEmitterRef, Signal, WritableSignal, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { TumAetUiAutoCompleteComponent, TumAetUiButtonDirective, TumAetUiCheckboxComponent, TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentsRoomDistributionService } from 'app/exam/manage/services/students-room-distribution.service';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { faBan, faChair } from '@fortawesome/free-solid-svg-icons';
import { RoomForDistributionDTO, SeatsOfExamRoomDTO } from './students-room-distribution.model';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

/** A room as listed by the search field; the field shows `label` and hands the suggestion back on selection. */
interface RoomSuggestion {
    label: string;
    room: RoomForDistributionDTO;
}

@Component({
    selector: 'jhi-students-reseating-dialog',
    templateUrl: './students-reseating-dialog.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        FaIconComponent,
        ArtemisTranslatePipe,
        HelpIconComponent,
        TumAetUiAutoCompleteComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiDialogComponent,
    ],
})
export class StudentsReseatingDialogComponent implements OnInit {
    private readonly studentsRoomDistributionService = inject(StudentsRoomDistributionService);

    protected readonly faBan = faBan;
    protected readonly faChair = faChair;

    courseId: InputSignal<number> = input.required();
    exam: InputSignal<Exam> = input.required();
    examUser: WritableSignal<ExamUser | undefined> = signal(undefined);

    dialogVisible: ModelSignal<boolean> = model(false);
    onSave: OutputEmitterRef<void> = output();

    protected roomSuggestions: WritableSignal<RoomSuggestion[]> = signal([]);
    protected seatSuggestions: WritableSignal<string[]> = signal([]);
    private roomsUsedInExam: WritableSignal<RoomForDistributionDTO[]> = signal([]);
    selectedRoomNumber: WritableSignal<string> = signal('');
    private readonly selectedRoom: Signal<RoomForDistributionDTO | undefined> = computed(() => this.getRoomDTOFromSelectedRoomNumber());
    private readonly selectedRoomId: Signal<number | undefined> = computed(() => this.selectedRoom()?.id);
    readonly selectedRoomIsPersisted: Signal<boolean> = computed(() => this.selectedRoomId() !== undefined && this.selectedRoomId()! >= 0);
    private seatsOfSelectedRoom: WritableSignal<SeatsOfExamRoomDTO> = signal({ seats: [] });
    selectedSeat: WritableSignal<string> = signal('');
    readonly selectedSeatIsPersisted: Signal<boolean> = computed(() => this.selectedRoomIsPersisted() && this.seatsOfSelectedRoom().seats.includes(this.selectedSeat()));

    constructor() {
        effect(() => {
            if (!this.selectedRoomIsPersisted()) {
                this.seatsOfSelectedRoom.set({ seats: [] });
                return;
            }

            const roomId = this.selectedRoomId()!;
            this.studentsRoomDistributionService.loadSeatsOfExamRoom(roomId).subscribe({
                next: (data) => {
                    if (this.selectedRoomId() === roomId) {
                        // ignore late responses
                        this.seatsOfSelectedRoom.set(data);
                    }
                },
                // An error is expected whenever the room is not persisted
                error: () => this.seatsOfSelectedRoom.set({ seats: [] }),
            });
        });
    }

    ngOnInit(): void {
        this.updateRoomsUsedInExam();
    }

    updateRoomsUsedInExam(): void {
        this.studentsRoomDistributionService.loadRoomsUsedInExam(this.courseId(), this.exam().id).subscribe({
            next: (rooms: RoomForDistributionDTO[]) => {
                this.roomsUsedInExam.set(rooms);

                this.exam().examUsers?.forEach((examUser: ExamUser, index: number) => {
                    if (examUser.plannedRoom && this.roomsUsedInExam().find((room) => room.roomNumber === examUser.plannedRoom) === undefined) {
                        const unpersistedRoom: RoomForDistributionDTO = {
                            id: -index - 1, // a negative id indicates that the room is not persisted
                            roomNumber: examUser.plannedRoom,
                            name: 'N/A',
                            building: 'N/A',
                        };
                        this.roomsUsedInExam.update((currentRooms) => [...currentRooms, unpersistedRoom]);
                    }
                });
            },
            error: (_err) => {
                this.roomsUsedInExam.set([]);
            },
        });
    }

    openDialog(examUser: ExamUser): void {
        this.examUser.set(examUser);
        this.selectedRoomNumber.set(this.examUser()!.plannedRoom ?? '');
        this.selectedSeat.set('');
        this.dialogVisible.set(true);
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

    /** The search field reports typed text as a string and a picked suggestion as an object. */
    protected onRoomChange(value: string | RoomSuggestion | null | undefined): void {
        this.selectedRoomNumber.set(typeof value === 'object' && value !== null ? value.room.roomNumber : (value ?? ''));
    }

    protected onSeatChange(value: string | null | undefined): void {
        this.selectedSeat.set(value ?? '');
    }

    protected searchRooms(term: string): void {
        this.roomSuggestions.set(this.findAllMatchingRoomsForTerm(term).map((room) => ({ label: this.roomFormatter(room), room })));
    }

    protected searchSeats(term: string): void {
        this.seatSuggestions.set(this.findAllMatchingSeatsForTerm(term));
    }

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
            const normalizedSeatName = seatName.toLowerCase();
            return tokens.every((token) => {
                return this.isSubsequence(normalizedSeatName, token);
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
}
