import { Service, Signal, WritableSignal, computed, inject, signal } from '@angular/core';
import { EMPTY, Observable, catchError, map, of } from 'rxjs';
import { ExamRoomDistributionApi } from 'app/openapi/api/exam-room-distribution-api';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';

@Service()
export class StudentsRoomDistributionService {
    private examRoomDistributionApi = inject(ExamRoomDistributionApi);

    private availableRoomsInternal: WritableSignal<RoomForDistributionDTO[] | undefined> = signal(undefined);
    private capacityDataInternal: WritableSignal<ExamDistributionCapacityDTO | undefined> = signal(undefined);

    readonly availableRooms: Signal<RoomForDistributionDTO[]> = computed(() => this.availableRoomsInternal() ?? []);
    readonly capacityData: Signal<ExamDistributionCapacityDTO> = computed(
        () =>
            this.capacityDataInternal() ?? {
                combinedDefaultCapacity: 0,
                combinedMaximumCapacity: 0,
            },
    );

    /**
     * Sends a GET request to retrieve basic data about all exam rooms available for distribution and updates {@link availableRooms}.
     *
     * @param courseId id of the course
     * @param examId id of the exam
     */
    loadRoomData(courseId: number, examId: number): void {
        this.examRoomDistributionApi
            .getRoomDataForDistribution(courseId, examId)
            .pipe(
                map((rooms: RoomForDistributionDTO[]) => {
                    this.availableRoomsInternal.set(rooms);
                }),
                catchError(() => {
                    this.availableRoomsInternal.set(undefined);
                    return EMPTY;
                }),
            )
            .subscribe();
    }

    /**
     * Sends a POST request to retrieve information about the capacities of the chosen rooms and updates {@link capacityData}
     *
     * @param courseId id of the course
     * @param examId id of the exam
     * @param roomIds ids of the rooms
     * @param reserveFactor percentage of seats that should be left empty
     */
    updateCapacityData(courseId: number, examId: number, roomIds: number[], reserveFactor: number): void {
        if (roomIds.length === 0) {
            this.capacityDataInternal.set(undefined);
            return;
        }

        this.examRoomDistributionApi
            .getDistributionCapacities(courseId, examId, reserveFactor, roomIds)
            .pipe(
                map((capacityData) => {
                    this.capacityDataInternal.set({
                        combinedDefaultCapacity: capacityData.combinedDefaultCapacity,
                        combinedMaximumCapacity: capacityData.combinedMaximumCapacity,
                    });
                }),
                catchError(() => {
                    this.capacityDataInternal.set(undefined);
                    return EMPTY;
                }),
            )
            .subscribe();
    }

    /**
     * Sends a GET request to retrieve all rooms that are already used in the given exam
     *
     * @param courseId id of the course
     * @param examId id of the exam
     */
    loadRoomsUsedInExam(courseId?: number, examId?: number): Observable<RoomForDistributionDTO[]> {
        if (!courseId || !examId) {
            return of([]);
        }

        return this.examRoomDistributionApi.getRoomsUsedInExam(courseId, examId);
    }
}
