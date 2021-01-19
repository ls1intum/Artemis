python3 -m compileall . -q || error=true
if [ ! $error ]
then
    pytest --junitxml=test-reports/results.xml
else
    exit 1
fi