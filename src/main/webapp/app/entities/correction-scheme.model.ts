import { UMLDiagramType } from 'app/entities/modeling-exercise.model';

export const CORRECTION_SCHEME_CORRECT_MARKING = 'correctMarking';
export const CORRECTION_SCHEME_CORRECT_ARC = 'correctArc';
export const CORRECTION_SCHEME_CORRECT_INITIAL_MARKING = 'correctInitialMarking';
export const CORRECTION_SCHEME_ADDITIONAL_MARKING = 'additionalMarking';
export const CORRECTION_SCHEME_ADDITIONAL_ARC = 'additionalArc';
export const CORRECTION_SCHEME_ADDITIONAL_INITIAL_MARKING = 'additionalInitialMarking';

export const CORRECTION_SCHEME = {};

CORRECTION_SCHEME[UMLDiagramType.ReachabilityGraph] = {};

CORRECTION_SCHEME[UMLDiagramType.ReachabilityGraph][CORRECTION_SCHEME_CORRECT_MARKING] = 1;
CORRECTION_SCHEME[UMLDiagramType.ReachabilityGraph][CORRECTION_SCHEME_CORRECT_ARC] = 1;
CORRECTION_SCHEME[UMLDiagramType.ReachabilityGraph][CORRECTION_SCHEME_CORRECT_INITIAL_MARKING] = 1;
CORRECTION_SCHEME[UMLDiagramType.ReachabilityGraph][CORRECTION_SCHEME_ADDITIONAL_MARKING] = -1;
CORRECTION_SCHEME[UMLDiagramType.ReachabilityGraph][CORRECTION_SCHEME_ADDITIONAL_ARC] = -1;
CORRECTION_SCHEME[UMLDiagramType.ReachabilityGraph][CORRECTION_SCHEME_ADDITIONAL_INITIAL_MARKING] = -1;
