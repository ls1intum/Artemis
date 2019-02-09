import {ModelingAssessment} from "app/entities/modeling-assessment/modeling-assessment.model";
import {UMLElement} from "app/entities/modeling-assessment/uml-element.model";
import {Score} from "app/entities/modeling-assessment/score.model";

export class Conflict {
    elementInConflict: UMLElement;
    conflictingAssessment: ModelingAssessment;
    scoresInConflict: Score[];
    initiator: any;//TODO set type of attributes
    id: string;
}
