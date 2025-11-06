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
    numberOfDeletedExamRooms: number;
}
