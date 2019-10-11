# Actual build process:
cd tests

# Structural tests
cp structural/Tests.py ./
cp -r structural/tests ./tests
python3 Tests.py structural
rm Tests.py
rm -rf ./tests

exit 0