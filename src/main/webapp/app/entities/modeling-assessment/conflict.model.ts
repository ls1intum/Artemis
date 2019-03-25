import { Score } from 'app/entities/modeling-assessment/score.model';
import { User } from 'app/core';
import { Feedback } from 'app/entities/feedback';
import { UMLElement } from 'app/entities/modeling-assessment/uml-element.model';

export class Conflict {
    // TODO can we simply store the element id and retrieve this element from the apollon model?
    elementInConflict: UMLElement;
    conflictingFeedback: Feedback;
    scoresInConflict: Score[];
    initiator: User;
    id: string;
}
