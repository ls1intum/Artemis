import { BaseEntity } from 'app/shared';
import { User } from 'app/core';
import { Course } from 'app/entities/course';
import { Exercise } from 'app/entities/exercise';

export class Team implements BaseEntity {
    public id: number;
    public name: string;
    public image: string;
    public exercise: Exercise;
    public students: User[];

    constructor() {}
}
