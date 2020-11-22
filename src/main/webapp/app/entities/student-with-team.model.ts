import { StudentDTO } from './student-dto.model';

export class StudentWithTeam extends StudentDTO {
    public firstName: string;
    public lastName: string;
    public registrationNumber: string;
    public team: string;
    public login: string;
}
