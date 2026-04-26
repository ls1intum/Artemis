import { StudentDTO } from 'app/core/shared/entities/student-dto.model';

export class ExamUserDTO extends StudentDTO {
    room?: string;
    seat?: string;
    didCheckImage?: boolean;
    didCheckName?: boolean;
    didCheckRegistrationNumber?: boolean;
    didCheckLogin?: boolean;
    signingImagePath?: string;
}
