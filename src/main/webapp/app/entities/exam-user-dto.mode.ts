import { StudentDTO } from 'app/entities/student-dto.model';

export class ExamUserDTO extends StudentDTO {
    actualRoom?: string;
    actualSeat?: string;
    plannedRoom?: string;
    plannedSeat?: string;
}
