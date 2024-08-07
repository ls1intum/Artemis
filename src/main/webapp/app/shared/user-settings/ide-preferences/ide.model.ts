import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

export interface Ide {
    name: string;
    deepLink: string;
}

export function ideEqual(a: Ide | undefined, b: Ide | undefined): boolean {
    if (!a || !b) {
        return false;
    }
    return a.name == b.name && a.deepLink == b.deepLink;
}

export interface IdeMappingDTO {
    programmingLanguage: ProgrammingLanguage;
    ide: Ide;
}
