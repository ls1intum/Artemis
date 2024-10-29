import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';

export interface Ide {
    name: string;
    deepLink: string;
}

export function ideEquals(a: Ide | undefined, b: Ide | undefined): boolean {
    if (!a || !b) {
        return false;
    }
    return a.deepLink == b.deepLink;
}

export interface IdeMappingDTO {
    programmingLanguage: ProgrammingLanguage;
    ide: Ide;
}
