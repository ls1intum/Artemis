import { v4 as uuidv4 } from 'uuid';

/**
 * This file contains all of the global utility functions not directly related to cypress.
 */

/**
 * Generates a unique identifier.
 * */
export function generateUUID() {
    return uuidv4().replace(/-/g, '');
}
