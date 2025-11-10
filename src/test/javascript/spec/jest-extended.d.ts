// This file ensures that when importing from '@jest/globals',
// all jest-extended matchers are available with proper type definitions

// Import jest-extended to trigger its type augmentation of the jest namespace
import 'jest-extended';

declare module '@jest/globals' {
    interface Matchers<R = void, T = unknown> extends jest.Matchers<R, T> {}

    // Extend Expect interface with jest-extended expectations
    interface Expect extends jest.Expect {}

    // Extend InverseAsymmetricMatchers for .not matchers
    interface InverseAsymmetricMatchers extends jest.InverseAsymmetricMatchers {}
}
