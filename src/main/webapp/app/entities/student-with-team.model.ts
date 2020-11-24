import { StudentDTO } from './student-dto.model';

export class StudentWithTeam extends StudentDTO {
    public Name: string;
    public Surname: string;
    public 'Registration Number': string;
    public 'Team Name': string;
    public login: string;
}
