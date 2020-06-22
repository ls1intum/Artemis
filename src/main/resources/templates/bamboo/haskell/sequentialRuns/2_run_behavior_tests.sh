stack config set system-ghc --global true
# Build the libraries, delete the solution and tests (so that students cannot access it), and run the test executable.
# Do not forget to set the right compilation flags (Prod and Solution).
# Finally, return 0: as a convention, a failed haskell tasty test suite returns 1, but this stops the JUnit Parser from running.
stack build --allow-different-user --flag test:Prod --flag test:Solution && rm -rf solution && rm -rf test && (stack exec test --allow-different-user || exit 0)
