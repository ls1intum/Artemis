import { StudentDTO } from 'app/entities/student-dto.model';

export class ExamUserDTO extends StudentDTO {
    room?: string;
    seat?: string;
}
