//
//  ContentView.swift
//  ${appName}
//
//  Created by Daniel Kainz on 06.08.21.
//

import SwiftUI

struct ContentView: View {
    var body: some View {
        /*
         Task 2:
         Greet the world!
        */
        Text(Greeting.greet())
            .padding()
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
