import SwiftUI

struct ContentView: View {
    var body: some View {
        let sampleText = SampleText(text: "Hello, Artemis!")
        Text(sampleText.getText())
            .padding()
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
