import Foundation

class SampleText {
    var text: String

    init(text: String) {
        self.text = text
    }

    convenience init() {
        self.init(text: "")
    }

    func getText() -> String {
        return text
    }
}
