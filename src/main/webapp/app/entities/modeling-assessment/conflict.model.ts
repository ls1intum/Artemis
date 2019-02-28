import { Score } from 'app/entities/modeling-assessment/score.model';
import { UMLElement } from 'app/entities/modeling-assessment/uml-element.model';
import { User } from 'app/core';
import { Feedback } from 'app/entities/feedback';

export class Conflict {
    elementInConflict: UMLElement;
    conflictingAssessment: Feedback;
    scoresInConflict: Score[];
    initiator: User;
    id: string;
}
