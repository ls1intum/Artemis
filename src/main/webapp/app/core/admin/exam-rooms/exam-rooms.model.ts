export interface ExamRoomUploadInformationDTO {
    uploadedFileName: string;
    durationNanos: number;
    numberOfUploadedRooms: number;
    numberOfUploadedSeats: number;
    uploadedRoomNames: string[];
}

export interface ExamRoomDTO {
    roomNumber: string;
    name: string;
    building: string;
    numberOfSeats: number;
    layoutStrategies: ExamRoomLayoutStrategyDTO[];
}

export interface ExamRoomDTOExtended extends ExamRoomDTO {
    maxCapacity: number;
    layoutStrategyNames: string;
}

export interface ExamRoomLayoutStrategyDTO {
    name: string;
    type: string;
    capacity: number | undefined;
}

export interface NumberOfStored {
    examRooms: number;
    examSeats: number;
    layoutStrategies: number;
    uniqueExamRooms: number;
    uniqueExamSeats: number;
    uniqueLayoutStrategies: number;
}

export interface ExamRoomAdminOverviewDTO {
    numberOfStoredExamRooms: number;
    numberOfStoredExamSeats: number;
    numberOfStoredLayoutStrategies: number;
    newestUniqueExamRooms: ExamRoomDTO[];
}

export interface ExamRoomDeletionSummaryDTO {
    durationNanos: number;
    numberOfDeletedExamRooms: number;
}
