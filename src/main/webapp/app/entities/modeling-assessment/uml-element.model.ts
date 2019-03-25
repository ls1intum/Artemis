import { ElementType } from '@ls1intum/apollon';

// TODO we should get rid of this and rather use Apollon types

export interface UMLElement {
    id: string;
    kind: ElementType;
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
