import { Injectable, Signal, WritableSignal, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, catchError, map, of, throwError } from 'rxjs';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';

@Injectable({ providedIn: 'root' })
export class StudentsRoomDistributionService {
    private http = inject(HttpClient);

    private readonly BASE_URL = 'api/exam';

    private availableRoomsInternal: WritableSignal<RoomForDistributionDTO[] | undefined> = signal(undefined);
    private capacityDataInternal: WritableSignal<ExamDistributionCapacityDTO | undefined> = signal(undefined);

    // Signals that can be used by other components
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
    loadRoomData(): Observable<void> {
        return this.http.get<RoomForDistributionDTO[]>(`${this.BASE_URL}/rooms/distribution-data`).pipe(
            map((rooms: RoomForDistributionDTO[]) => {
                this.availableRoomsInternal.set(rooms);
            }),
            catchError((error) => {
                this.availableRoomsInternal.set(undefined);
                return throwError(() => error);
            }),
        );
    }

    /**
     * Sends a POST request to retrieve information about the capacities of the chosen rooms and updates {@link capacityData}
     *
     * @param roomIds ids of the rooms
     * @param reserveFactor percentage of seats that should be left empty
     */
    updateCapacityData(roomIds: number[], reserveFactor: number): Observable<void> {
        if (roomIds.length === 0) {
            this.capacityDataInternal.set(undefined);
            return of();
        }

        const requestUrl = `${this.BASE_URL}/rooms/distribution-capacities`;
        const params = new HttpParams().appendAll({ examRoomIds: roomIds }).set('reserveFactor', reserveFactor);

        return this.http.get<ExamDistributionCapacityDTO>(requestUrl, { params }).pipe(
            map((capacityData: ExamDistributionCapacityDTO) => {
                this.capacityDataInternal.set(capacityData);
            }),
            catchError((error) => {
                this.capacityDataInternal.set(undefined);
                return throwError(() => error);
            }),
        );
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
}
