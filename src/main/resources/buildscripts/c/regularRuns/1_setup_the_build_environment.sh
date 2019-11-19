echo "--------------------info--------------------"
python3 --version
pip3 --version
# Generic debug infos:
# whoami
# tree

echo "--------------------tests--------------------"
ls -la tests
echo "--------------------tests--------------------"
echo "--------------------assignment--------------------"
ls -la assignment
echo "--------------------assignment--------------------"

cd tests
REQ_FILE=requirements.txt
if [ -f "$REQ_FILE" ]; then
    pip3 install --user -r requirements.txt
else
    echo "$REQ_FILE does not exist"
fi
exit 0