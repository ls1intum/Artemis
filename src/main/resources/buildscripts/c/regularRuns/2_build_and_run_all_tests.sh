# Actual build process:
cd tests

# Structural tests
cp structural/Tests.py ./
cp -r structural/tests ./tests
python3 Tests.py s
rm Tests.py
rm -rf ./tests

# Behavior tests
cp behavioral/Tests.py ./
cp -r behavioral/tests ./tests
python3 Tests.py b

exit 0