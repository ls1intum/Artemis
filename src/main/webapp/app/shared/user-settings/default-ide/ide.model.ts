import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

export interface Ide {
    name: string;
    deepLink: string;
}

export interface IdeMappingDTO {
    programmingLanguage: ProgrammingLanguage;
    ide: Ide;
}
