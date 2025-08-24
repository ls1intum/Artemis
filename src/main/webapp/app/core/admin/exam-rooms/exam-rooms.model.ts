export interface ExamRoomUploadInformationDTO {
    uploadedFileName: string;
    uploadDuration: string;
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

export interface ExamRoomLayoutStrategyDTO {
    name: string;
    type: string;
    capacity: number | undefined;
}

export interface ExamRoomAdminOverviewDTO {
    numberOfStoredExamRooms: number;
    numberOfStoredExamSeats: number;
    numberOfStoredLayoutStrategies: number;
    newestUniqueExamRooms: ExamRoomDTO[];
}

export interface ExamRoomDeletionSummaryDTO {
    deleteDuration: string;
    numberOfDeletedExamRooms: number;
}
