import { Injectable, Signal, WritableSignal, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, catchError, map, throwError } from 'rxjs';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO, SeatsOfExamRoomDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';

@Injectable({ providedIn: 'root' })
export class StudentsRoomDistributionService {
    private http = inject(HttpClient);

    private readonly BASE_URL = 'api/exam';

    private availableRoomsInternal: WritableSignal<RoomForDistributionDTO[] | undefined> = signal(undefined);
    private capacityDataInternal: WritableSignal<ExamDistributionCapacityDTO | undefined> = signal(undefined);

    readonly availableRooms: Signal<RoomForDistributionDTO[]> = computed(() => this.availableRoomsInternal() ?? []);
    readonly capacityData: Signal<ExamDistributionCapacityDTO> = computed(
        () =>
            this.capacityDataInternal() ??
            ({
                combinedDefaultCapacity: 0,
                combinedMaximumCapacity: 0,
            } as ExamDistributionCapacityDTO),
    );

    /**
     * Sends a GET request to retrieve basic data about all exam rooms available for distribution and updates {@link availableRooms}.
     */
    loadRoomData(): void {
        this.http
            .get<RoomForDistributionDTO[]>(`${this.BASE_URL}/rooms/distribution-data`)
            .pipe(
                map((rooms: RoomForDistributionDTO[]) => {
                    this.availableRoomsInternal.set(rooms);
                }),
                catchError((error) => {
                    this.availableRoomsInternal.set(undefined);
                    return throwError(() => error);
                }),
            )
            .subscribe();
    }

    /**
     * Sends a POST request to retrieve information about the capacities of the chosen rooms and updates {@link capacityData}
     *
     * @param roomIds ids of the rooms
     * @param reserveFactor percentage of seats that should be left empty
     */
    updateCapacityData(roomIds: number[], reserveFactor: number): void {
        if (roomIds.length === 0) {
            this.capacityDataInternal.set(undefined);
            return;
        }

        const requestUrl = `${this.BASE_URL}/rooms/distribution-capacities`;
        const params = new HttpParams().appendAll({ examRoomIds: roomIds }).set('reserveFactor', reserveFactor);

        this.http
            .get<ExamDistributionCapacityDTO>(requestUrl, { params })
            .pipe(
                map((capacityData: ExamDistributionCapacityDTO) => {
                    this.capacityDataInternal.set(capacityData);
                }),
                catchError((error) => {
                    this.capacityDataInternal.set(undefined);
                    return throwError(() => error);
                }),
            )
            .subscribe();
    }

    /**
     * Sends a POST request that distributes the exam users of the selected exam across the rooms, which are provided by id
     *
     * @param courseId id of the course
     * @param examId id of the exam
     * @param roomIds ids of the rooms
     * @param reserveFactor percentage of seats that should be left empty
     * @param useOnlyDefaultLayouts defines if only default layouts should be used for distribution
     */
    distributeStudentsAcrossRooms(courseId: number, examId: number, roomIds: number[], reserveFactor: number, useOnlyDefaultLayouts: boolean): Observable<HttpResponse<void>> {
        const requestUrl = `${this.BASE_URL}/courses/${courseId}/exams/${examId}/distribute-registered-students`;
        const params = new HttpParams().set('reserveFactor', reserveFactor).set('useOnlyDefaultLayouts', useOnlyDefaultLayouts);

        return this.http.post<void>(requestUrl, roomIds, { params, observe: 'response' });
    }

    /**
     * Sends a GET request to retrieve all rooms that are already used in the given exam
     *
     * @param courseId id of the course
     * @param examId id of the exam
     */
    loadRoomsUsedInExam(courseId: number, examId: number): Observable<RoomForDistributionDTO[]> {
        const requestUrl = `${this.BASE_URL}/courses/${courseId}/exams/${examId}/rooms-used`;
        return this.http.get<RoomForDistributionDTO[]>(requestUrl).pipe(
            map((rooms: RoomForDistributionDTO[]) => rooms),
            catchError((error) => {
                return throwError(() => error);
            }),
        );
    }

    /**
     * Sends a GET request to retrieve all seats of a specific exam room
     *
     * @param examRoomNumber id of the exam room
     */
    loadSeatsOfExamRoom(examRoomNumber: string): Observable<SeatsOfExamRoomDTO> {
        const requestUrl = `${this.BASE_URL}/rooms/seats`;
        const params = new HttpParams().set('examRoomNumber', examRoomNumber);

        return this.http.get<SeatsOfExamRoomDTO>(requestUrl, { params }).pipe(
            map((seatsOfExamRoom: SeatsOfExamRoomDTO) => seatsOfExamRoom),
            catchError((error) => {
                return throwError(() => error);
            }),
        );
    }

    /**
     * Sends a POST request to reseat a specific student to a new room and seat
     *
     * @param courseId id of the course
     * @param examId id of the exam
     * @param examUserId id of the exam user/student
     * @param newRoom name (more specifically the room number) of the room
     * @param newSeat name of the new seat
     * @param persistedLocation whether the location is persisted in the database
     */
    reseatStudent(courseId: number, examId: number, examUserId: number, newRoom: string, newSeat: string, persistedLocation: boolean): Observable<void> {
        const requestUrl = `${this.BASE_URL}/courses/${courseId}/exams/${examId}/reseat-student`;
        const requestBody = {
            examUserId: examUserId,
            newRoom: newRoom,
            newSeat: newSeat,
            persistedLocation: persistedLocation,
        };

        return this.http.post<void>(requestUrl, requestBody).pipe(
            map(() => {}),
            catchError((error) => {
                return throwError(() => error);
            }),
        );
    }
}
