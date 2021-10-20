//
//  MainView.swift
//  ${appName}
//

import SwiftUI

/*
 Task 3.1:
 Implement the Main View
*/

struct MainView: View {
    let context = Context()
    var sortAlgorithm: SortAlgorithm = SortAlgorithm.BubbleSort
    
    var body: some View {
        Text("MainView")
    }
}

/// The preview.
struct MainView_Previews: PreviewProvider {
    static var previews: some View {
        MainView()
    }
}
