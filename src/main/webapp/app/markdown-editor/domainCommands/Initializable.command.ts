export interface InitializableCommand {
    initializeEditor: () => void;
}

/**
 * This is a custom type guard, as currently there is no way in TS to check for the implementation of an interface.
 * Returns true if the given object implements the interface.
 *
 * ATTENTION: When a new member is added to the interface, the type guard needs to be extended!
 *
 * @param object
 */
export function instanceOfInitializableCommand(object: any): object is InitializableCommand {
    return 'initializeEditor' in object;
}
