import { StudentDTO } from 'app/entities/student-dto.model';

export class TutorialGroupRegistrationImportDTO {
    public title: string;
    public student: StudentDTO;
    // properties set by the server when the import is done
    public importSuccessful?: boolean;
    public error?: string;
}
