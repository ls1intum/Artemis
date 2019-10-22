cd tests || exit 1

cp behavioral/Tests.py ./
cp -r behavioral/tests ./tests
python3 Tests.py behavior