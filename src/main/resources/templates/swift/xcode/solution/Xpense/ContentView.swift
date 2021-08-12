//
//  ContentView.swift
//  Xpense
//
//  Created by Daniel Kainz on 06.08.21.
//

import SwiftUI

struct ContentView: View {
    var body: some View {
        Text(Greeting.greet())
            .padding()
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
