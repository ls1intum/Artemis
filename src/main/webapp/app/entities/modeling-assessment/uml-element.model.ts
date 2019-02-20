import {ModelElementType} from "app/entities/modeling-assessment/modeling-assessment.model";

export interface UMLElement {
    id: string;
    kind: ModelElementType;
}

export interface UMLClass extends UMLElement {
    name: string;
    methods: UMLMethod[];
    attributes: UMLAttribute[];
    type: string;
}

export interface UMLMethod extends UMLElement {
    name: string;
}

export interface UMLAttribute extends UMLElement {
    name: string;
}

export interface UMLRelation extends UMLElement {

}
