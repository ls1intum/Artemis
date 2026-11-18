//
//  ListItem.swift
//  ${appName}
//

import SwiftUI

/*
 Task 3.2:
 Implement the the List Item View
*/

/// The view for the listed date items.
struct ListItem: View {
    var date: Date
    var body: some View {
        HStack {
            Image(systemName: "calendar").font(.largeTitle)
            VStack(alignment: .leading) {
                /// Using formatted dates for a clear presentation.
                Text("\(DateFormatter.onlyDate.string(from: date))")
                Text("\(DateFormatter.onlyTime.string(from: date))")
            }
        }
        .padding()
        /// Using a custom modifier.
        .listItemModifier()
    }
}

/// The preview.
struct ListItem_Previews: PreviewProvider {
    static var previews: some View {
        ListItem(date: Date())
    }
}
