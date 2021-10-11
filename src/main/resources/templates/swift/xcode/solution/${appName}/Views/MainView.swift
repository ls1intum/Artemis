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
    
    @State var sortAlgorithm: String = "Merge Sort"
    private let sortAlgorithms = ["Merge Sort", "Bubble Sort"]
    
    @State private var displayDates = false
    @State private var sorted = false
    
    /// Button color depending on a disabled or not disabled Button.
    private var buttonColor: Color {
        return !displayDates ? .gray : .green
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 15) {
                /// The user can choose a sorting algorithm via a wheel picker.
                Group {
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Please choose a sorting algorithm!")
                            .font(.callout)
                            .bold()
                        Picker("Sorting Algorithm", selection: $sortAlgorithm) {
                            ForEach(sortAlgorithms, id: \.self) {
                                Text($0)
                            }
                        }.pickerStyle(WheelPickerStyle())
                    }.padding()
                }
                /// The user can create and display a list pf random unnsorted dates via a simple button tap.
                Button(action: self.createRandomDates) {
                    HStack {
                        Image(systemName: "calendar.badge.plus").font(.largeTitle).foregroundColor(.white)
                        Text("Create Random Dates").bold().foregroundColor(.white).padding()
                    }
                }
                .background(RoundedRectangle(cornerRadius: 25).fill(Color.blue).frame(width: abs(UIScreen.main.bounds.size.width - 45.0), height: .infinity, alignment: .center))
                /// The user can sort and display the list of unsorted Dates via a simple button tap.
                Button(action: self.sort) {
                    HStack {
                        Image(systemName: "arrow.up.arrow.down.circle").font(.largeTitle).foregroundColor(.white)
                        Text("Sort").bold().foregroundColor(.white).padding()
                    }
                }
                .disabled(!displayDates)
                .background(RoundedRectangle(cornerRadius: 25).fill(buttonColor).frame(width: UIScreen.main.bounds.size.width - 45.0, height: .infinity, alignment: .center))
                Spacer()
                Divider()
                /// The list of (un-) sorted dates.
                if displayDates {
                    Group {
                        Text(sorted ? "Sorted Dates" : "Unsorted Dates").bold().font(.title2)
                        ScrollView {
                            ForEach(self.context.getDates(), id: \.self) { date in
                                ListItem(date: date)
                            }
                        }
                    }
                }
            }
            /// The title of the screen..
            .navigationTitle("Sorting")
            /// The reset button.
            .navigationBarItems(trailing: Button(action: reset) {
                Image(systemName: "arrow.counterclockwise.circle").font(.largeTitle)
            })
        }
    }
    
    /**
     Configures the Policy with the given User Input.

     - Parameter none
    */
    private func sort() {
        Policy(context).configure(sortAlgorithm: sortAlgorithm)
        self.context.sort()
        self.sorted = true
    }
    
    /**
     Creates random dates in the Context and displays a list of unnsorted Dates.

     - Parameter none
    */
    private func createRandomDates() {
        self.context.setRandomDates()
        self.sorted = false
        self.displayDates = true
    }
    
    /**
     Resets the app to startup state.

     - Parameter none
    */
    private func reset() {
        self.context.setDates([])
        self.displayDates = false
        self.sorted = false
        self.sortAlgorithm = "Merge Sort"
    }
}

/// The preview..
struct MainView_Previews: PreviewProvider {
    static var previews: some View {
        MainView()
    }
}
