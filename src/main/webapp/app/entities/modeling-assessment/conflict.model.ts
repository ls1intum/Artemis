import {Score} from "app/entities/modeling-assessment/score.model";
import {UMLElement} from "app/entities/modeling-assessment/uml-element.model";
import {ModelingAssessment} from "app/entities/modeling-assessment/modeling-assessment.model";
import {User} from "app/core";

export class Conflict {
    elementInConflict: UMLElement;
    conflictingAssessment: ModelingAssessment;
    scoresInConflict: Score[];
    initiator:User;
    id: string;
}
