//
//  MainView.swift
//  ${appName}
//
//  Created by Daniel Kainz on 12.10.21.
//

import SwiftUI

/*
 Task 3.1:
 Implement the Main View
*/

struct MainView: View {
    let context = Context()
    var sortAlgorithm: String = ""
    
    var body: some View {
        Text("MainView")
    }
}

/// The preview..
struct MainView_Previews: PreviewProvider {
    static var previews: some View {
        MainView()
    }
}
