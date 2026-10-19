import { Subscription } from 'rxjs';
import { Component, InputSignal, ModelSignal, OnInit, OutputEmitterRef, Signal, WritableSignal, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faBan, faThLarge } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { StudentsRoomDistributionService } from 'app/exam/manage/services/students-room-distribution.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { CapacityDisplayDTO, ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import {
    TumAetUiAutoCompleteComponent,
    type TumAetUiAutoCompleteOptionEvent,
    TumAetUiButtonDirective,
    TumAetUiDialogComponent,
    TumAetUiInputNumberComponent,
    TumAetUiMessageComponent,
    TumAetUiTableDirective,
    TumAetUiToggleSwitchComponent,
} from '@tumaet/ui-angular';
import { RouterLink } from '@angular/router';

/** A room as listed by the search field; the field shows `label` and hands the suggestion back on selection. */
export interface RoomSuggestion {
    label: string;
    room: RoomForDistributionDTO;
}

@Component({
    selector: 'jhi-students-room-distribution-dialog',
    templateUrl: './students-room-distribution-dialog.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        FaIconComponent,
        ArtemisTranslatePipe,
        HelpIconComponent,
        RouterLink,
        TumAetUiAutoCompleteComponent,
        TumAetUiButtonDirective,
        TumAetUiDialogComponent,
        TumAetUiInputNumberComponent,
        TumAetUiMessageComponent,
        TumAetUiTableDirective,
        TumAetUiToggleSwitchComponent,
    ],
})
export class StudentsRoomDistributionDialogComponent implements OnInit {
    private readonly studentsRoomDistributionService: StudentsRoomDistributionService = inject(StudentsRoomDistributionService);
    private readonly examManagementService = inject(ExamManagementService);

    readonly RESERVE_FACTOR_DEFAULT_PERCENTAGE: number = 10;

    // Icons
    protected readonly faBan = faBan;
    protected readonly faThLarge = faThLarge;

    courseId: InputSignal<number> = input.required();
    exam: InputSignal<Exam> = input.required();

    dialogVisible: ModelSignal<boolean> = model(false);
    onSave: OutputEmitterRef<void> = output();

    // Configurable options
    readonly reservePercentage: WritableSignal<number> = signal(this.RESERVE_FACTOR_DEFAULT_PERCENTAGE);
    private reserveFactor: Signal<number> = computed(() => this.reservePercentage() / 100);
    allowNarrowLayouts: WritableSignal<boolean> = signal(false);

    private availableRooms: Signal<RoomForDistributionDTO[]> = this.studentsRoomDistributionService.availableRooms;
    private selectedRoomsCapacity: Signal<ExamDistributionCapacityDTO> = this.studentsRoomDistributionService.capacityData;
    selectedRooms: WritableSignal<RoomForDistributionDTO[]> = signal([]);
    /**
     * The number of students registered for the exam. The exam of the route does not carry it, so it is loaded whenever the dialog opens.
     * It is undefined while the request is pending and if it failed, so that no capacity check passes against an unknown number.
     */
    readonly registeredStudents: WritableSignal<number | undefined> = signal(undefined);
    readonly registeredStudentsKnown: Signal<boolean> = computed(() => this.registeredStudents() !== undefined);
    private registeredStudentsSubscription: Subscription | undefined;
    /** Rooms offered by the search field, each with the text it is listed under. */
    roomSuggestions: WritableSignal<RoomSuggestion[]> = signal([]);
    /** Value of the search field. Reset after a pick so the field is empty again for the next room. */
    roomSearchValue: WritableSignal<unknown> = signal(undefined);
    hasSelectedRooms: Signal<boolean> = computed(() => this.selectedRooms().length > 0);
    seatInfo: Signal<CapacityDisplayDTO> = computed(() => this.computeSeatInfo());
    canSeatAllStudents: Signal<boolean> = computed(() => this.registeredStudentsKnown() && this.seatInfo().usableCapacity >= this.seatInfo().totalStudents);

    constructor() {
        effect(() => {
            const selectedRoomIds: number[] = this.selectedRooms().map((room) => room.id);
            this.studentsRoomDistributionService.updateCapacityData(selectedRoomIds, this.reserveFactor());
        });
    }

    ngOnInit(): void {
        this.studentsRoomDistributionService.loadRoomData();
    }

    private computeSeatInfo(): CapacityDisplayDTO {
        const totalStudents: number = this.registeredStudents() ?? 0;
        let usableCapacity: number = this.allowNarrowLayouts() ? this.selectedRoomsCapacity().combinedMaximumCapacity : this.selectedRoomsCapacity().combinedDefaultCapacity;
        if (usableCapacity > totalStudents) {
            usableCapacity = totalStudents;
        }
        const percentage: number = totalStudents > 0 ? Math.min(100, Math.floor((usableCapacity / totalStudents) * 100)) : 0;

        return {
            totalStudents,
            usableCapacity,
            percentage,
        };
    }

    openDialog(): void {
        this.dialogVisible.set(true);

        // A page of one student is enough: without a search term or filter, the total is the number of registered students.
        // An opening that follows quickly on another one replaces the pending request, so an older response cannot overwrite the newer count.
        this.registeredStudentsSubscription?.unsubscribe();
        this.registeredStudents.set(undefined);
        this.registeredStudentsSubscription = this.examManagementService
            .findExamStudentsPaged(this.courseId(), this.exam().id!, { page: 0, pageSize: 1, sortingOrder: SortingOrder.ASCENDING, sortedColumn: 'login', searchTerm: '' })
            .subscribe({
                next: (result) => this.registeredStudents.set(result.totalElements),
                error: () => this.registeredStudents.set(undefined),
            });

        this.studentsRoomDistributionService.loadRoomsUsedInExam(this.courseId(), this.exam().id).subscribe({
            next: (usedRooms: RoomForDistributionDTO[]) => {
                this.selectedRooms.set(usedRooms.toSorted((room1, room2) => room1.name.toLowerCase().localeCompare(room2.name.toLowerCase())));
            },
            error: (_error) => {
                this.selectedRooms.set([]);
            },
        });
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
     * Offers all exam rooms that might fit the search text, leaving out those that are already selected.
     *
     * @param term The text typed into the search field
     */
    searchRooms(term: string): void {
        this.roomSuggestions.set(this.findAllMatchingRoomsForTerm(term).map((room) => ({ label: this.formatter(room), room })));
    }

    private findAllMatchingRoomsForTerm = (term: string): RoomForDistributionDTO[] => {
        const trimmed = term.trim();
        if (!trimmed) {
            return this.removeAllRoomsThatAreAlreadySelected(this.availableRooms());
        }

        const ANY_WHITESPACE: RegExp = /\s+/;
        const tokens: string[] = trimmed.toLowerCase().split(ANY_WHITESPACE);

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
     * Returns true if the subsequence is part of the string.
     * A string is a subsequence of another, if it matches the other string, while allowing for omitted characters.
     *
     * Essentially this functions performs the equivalent of inserting '.*' before and after each character of the
     * subsequence, then using that modified subsequence as a regex expression to match against the token.
     *
     * @param token A string without any whitespace
     * @param subsequence A string that we want to check if it is a subsequence of the token
     */
    public isSubsequence(token: string, subsequence: string): boolean {
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

    onRoomSuggestionSelected(event: TumAetUiAutoCompleteOptionEvent): void {
        this.pickSelectedRoom((event.value as RoomSuggestion).room);
    }

    pickSelectedRoom(selectedRoom: RoomForDistributionDTO): void {
        if (this.selectedRooms().every((room) => room.id !== selectedRoom.id)) {
            this.selectedRooms.update((rooms) => [...rooms, selectedRoom]);
        }
        this.roomSearchValue.set(undefined);
    }

    removeSelectedRoom(room: RoomForDistributionDTO): void {
        this.selectedRooms.update((selectedRooms) => selectedRooms.filter((selectedRoom) => room.id !== selectedRoom.id));
    }

    setReservePercentage(percentage: number | null): void {
        this.reservePercentage.set(Math.min(100, Math.max(0, Math.round(percentage ?? 0))));
    }
}
