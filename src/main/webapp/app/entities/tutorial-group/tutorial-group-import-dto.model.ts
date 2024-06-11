import { StudentDTO } from 'app/entities/student-dto.model';

export class TutorialGroupRegistrationImportDTO {
    title: string;
    student: StudentDTO;
    campus?: string;
    capacity?: number;
    language?: string;
    additionalInformation?: string;
    isOnline?: string;

    // properties set by the server when the import is done
    public importSuccessful?: boolean;
    public error?: string;
}
