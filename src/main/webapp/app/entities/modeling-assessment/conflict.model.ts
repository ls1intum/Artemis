import {ModelingAssessment} from "app/entities/modeling-assessment/modeling-assessment.model";
import {Score} from "app/entities/modeling-assessment/score.model";
import {UMLElement} from "app/entities/modeling-assessment/UMLEntities";

export class Conflict {
    elementInConflict: UMLElement;
    conflictingAssessment: ModelingAssessment;
    scoresInConflict: Score[];
    initiator: any;//TODO set type of attributes
    id: string;
}
