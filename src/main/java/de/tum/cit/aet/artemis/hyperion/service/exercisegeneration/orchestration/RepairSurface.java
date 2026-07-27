package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

/**
 * The coherent artifact surface one semantic repair round is allowed to touch. Package-private so the scheduling contract can be tested directly; the traces that motivated it
 * are whole-generation properties no other seam exposes.
 */
enum RepairSurface {
    CONTRACT, ORACLE, SCAFFOLD
}
