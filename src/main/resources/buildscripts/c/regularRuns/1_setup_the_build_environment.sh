# Genric debug infos:
# echo "------------"
# whoami
# tree
# echo "------------"

echo "--------------------tests--------------------"
ls -la tests
echo "--------------------tests--------------------"
echo "--------------------assignment--------------------"
ls -la assignment
echo "--------------------assignment--------------------"

cd tests
python3 --version
pip3 install --user -r requirements.txt
exit 0