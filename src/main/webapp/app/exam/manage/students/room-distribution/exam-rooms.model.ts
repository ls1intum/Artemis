export interface ExamRoomUploadInformationDTO {
    uploadedFileName: string;
    numberOfUploadedRooms: number;
    numberOfUploadedSeats: number;
    uploadedRoomNames: string[];
}

export interface ExamRoomDTO {
    roomNumber: string;
    name: string;
    building: string;
    numberOfSeats: number;
    layoutStrategies?: ExamRoomLayoutStrategyDTO[];
}

export interface ExamRoomDTOExtended extends ExamRoomDTO {
    defaultCapacity: number;
    maxCapacity: number;
}

export interface ExamRoomLayoutStrategyDTO {
    name: string;
    type: string;
    capacity: number;
}

export interface NumberOfAvailable {
    examRooms: number;
    examSeats: number;
}

export interface ExamRoomOverviewDTO {
    newestUniqueExamRooms: ExamRoomDTO[];
}

export interface ExamRoomDeletionSummaryDTO {
    numberOfDeletedExamRooms: number;
}
