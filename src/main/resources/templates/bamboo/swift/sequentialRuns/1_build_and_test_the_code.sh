cd /assignment

echo "move Tests to assignment"
mv ../Tests .

echo "use Package.swift file from test repo"
rm Package.swift
mv ../Package.swift .

echo "swift build"
swift build

echo "swift test"
swift test
