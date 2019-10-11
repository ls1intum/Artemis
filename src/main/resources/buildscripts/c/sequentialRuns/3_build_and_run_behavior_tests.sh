# Actual build process:
cd tests

# Behavior tests
cp behavioral/Tests.py ./
cp -r behavioral/tests ./tests
python3 Tests.py behavior

exit 0