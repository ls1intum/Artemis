export interface RoomForDistributionDTO {
    id: number;
    number: string;
    alternativeNumber?: string;
    name: string;
    alternativeName?: string;
    building: string;
}

export interface ExamDistributionCapacityDTO {
    combinedDefaultCapacity: number;
    combinedMaximumCapacity: number;
}

export interface CapacityDisplayDTO {
    totalStudents: number;
    usableCapacity: number;
    percentage: number;
}
