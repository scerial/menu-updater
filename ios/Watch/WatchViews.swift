import SwiftUI

// Look follows the "워치 오늘의 메뉴" design: black ground, coral venue name, one card per meal with
// a status dot, hours, the main dish in bold and up to four sides.
private let accent = Color(red: 0xE2 / 255, green: 0x7D / 255, blue: 0x68 / 255)
private let openGreen = Color(red: 0x5B / 255, green: 0xD0 / 255, blue: 0x7A / 255)
private let soonWhite = Color(red: 0xE8 / 255, green: 0xEA / 255, blue: 0xEE / 255)
private let doneGray = Color(red: 0x8A / 255, green: 0x90 / 255, blue: 0x9B / 255)
private let mutedGray = Color(red: 0x9A / 255, green: 0xA0 / 255, blue: 0xAB / 255)
private let sideGray = Color(red: 0xD5 / 255, green: 0xD8 / 255, blue: 0xDE / 255)
private let faintGray = Color(red: 0x6B / 255, green: 0x71 / 255, blue: 0x7C / 255)
private let dotGray = Color(red: 0x4A / 255, green: 0x4F / 255, blue: 0x59 / 255)

struct WatchRootView: View {
    var store: WatchStore
    @AppStorage("watch-cafeteria") private var savedCafeteria = ""
    @State private var selectedDay: Int?
    @State private var showsPicker = false

    private var table: MenuTable? {
        guard let tables = store.menu?.tables, !tables.isEmpty else { return nil }
        return tables.first { $0.cafeteria == savedCafeteria }
            ?? tables.first { $0.cafeteria == store.phoneDefaultCafeteria }
            ?? tables.first
    }

    var body: some View {
        NavigationStack {
            Group {
                if let table {
                    DayPager(table: table, selectedDay: $selectedDay, store: store)
                        .id(table.cafeteria)
                } else {
                    EmptyState(store: store)
                }
            }
            .toolbar {
                // The ☰ button and the restaurant name share the top-left of the header.
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showsPicker = true
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "line.3.horizontal")
                            Text(table?.cafeteria ?? "순대 학식")
                                .font(.system(size: 17, weight: .bold))
                                .lineLimit(1)
                        }
                        .foregroundStyle(accent)
                    }
                    .buttonStyle(.plain)
                    .disabled(store.menu == nil)
                    .accessibilityLabel("식당 선택")
                }
            }
            .sheet(isPresented: $showsPicker) {
                CafeteriaPicker(names: store.menu?.tables.map(\.cafeteria) ?? [], selection: table?.cafeteria ?? "") {
                    savedCafeteria = $0
                    showsPicker = false
                }
            }
        }
        .background(Color.black)
    }
}

/// One page per day. Swipe left/right to change day.
private struct DayPager: View {
    let table: MenuTable
    @Binding var selectedDay: Int?
    var store: WatchStore

    private var currentDay: Int {
        if let selectedDay, table.days.contains(where: { $0.id == selectedDay }) { return selectedDay }
        return Self.initialDay(in: table)
    }

    /// Today's column, else the next day on the menu, else the last.
    static func initialDay(in table: MenuTable, now: Date = Date()) -> Int {
        let parts = Calendar.current.dateComponents([.month, .day], from: now)
        let today = (parts.month ?? 0) * 100 + (parts.day ?? 0)
        return table.days.first { $0.id == today }?.id
            ?? table.days.first { $0.id > today }?.id
            ?? table.days.last?.id ?? 0
    }

    var body: some View {
        TabView(selection: Binding(get: { currentDay }, set: { selectedDay = $0 })) {
            ForEach(table.days) { day in
                DayPage(day: day, cafeteria: table.cafeteria, store: store).tag(day.id)
            }
        }
        .tabViewStyle(.page)
        .indexViewStyle(.page(backgroundDisplayMode: .never))
    }
}

/// One day: a full-height card per meal. The Digital Crown pages from meal to meal.
private struct DayPage: View {
    let day: MenuDay
    let cafeteria: String
    var store: WatchStore
    @State private var mealID: String?

    private var isToday: Bool {
        let parts = Calendar.current.dateComponents([.month, .day], from: Date())
        return parts.month == day.month && parts.day == day.day
    }

    private var focusMeal: String? {
        isToday
            ? MealSchedule.focusMeal(among: day.meals.map(\.name), at: Date(), cafeteria: cafeteria)
            : nil
    }

    var body: some View {
        ScrollView(.vertical) {
            LazyVStack(spacing: 0) {
                if day.meals.isEmpty {
                    Text("메뉴 없음").foregroundStyle(mutedGray)
                }
                ForEach(Array(day.meals.enumerated()), id: \.element.id) { index, meal in
                    MealCard(
                        day: day, meal: meal, cafeteria: cafeteria, isToday: isToday,
                        next: index + 1 < day.meals.count ? day.meals[index + 1].name : nil
                    )
                    .containerRelativeFrame(.vertical)
                    .id(meal.id)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $mealID)
        .scrollIndicators(.hidden)
        .overlay(alignment: .trailing) {
            if day.meals.count > 1 {
                VStack(spacing: 4) {
                    ForEach(day.meals) { meal in
                        Capsule()
                            .fill(meal.id == (mealID ?? day.meals.first?.id) ? accent : dotGray)
                            .frame(width: 4, height: meal.id == (mealID ?? day.meals.first?.id) ? 14 : 4)
                    }
                }
                .padding(.trailing, 2)
                .allowsHitTesting(false)
            }
        }
        .onAppear { mealID = focusMeal ?? day.meals.first?.id }
    }
}

private struct MealCard: View {
    let day: MenuDay
    let meal: MealSection
    let cafeteria: String
    let isToday: Bool
    let next: String?

    private var status: MealSchedule.Status? {
        isToday ? MealSchedule.status(of: meal.name, at: Date(), cafeteria: cafeteria) : nil
    }

    private var statusColor: Color {
        switch status {
        case .open: openGreen
        case .upcoming: soonWhite
        default: doneGray
        }
    }

    var body: some View {
        let sides = Array(meal.items.dropFirst())
        let shown = Array(sides.prefix(4))
        VStack(alignment: .leading, spacing: 0) {
            Text(day.heading)
                .font(.system(size: 12))
                .foregroundStyle(faintGray)
            HStack(spacing: 8) {
                Text(meal.name).font(.system(size: 22, weight: .bold))
                if let status {
                    HStack(spacing: 4) {
                        Circle().fill(statusColor).frame(width: 6, height: 6)
                        Text(status.label)
                    }
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(statusColor)
                }
            }
            if let hours = MealSchedule.hoursLabel(for: meal.name) {
                Text(hours)
                    .font(.system(size: 13).monospacedDigit())
                    .foregroundStyle(mutedGray)
            }
            if let main = meal.items.first {
                Text(main)
                    .font(.system(size: 18, weight: .bold))
                    .padding(.top, 6)
            }
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(shown.enumerated()), id: \.offset) { _, side in
                    Text(side)
                        .font(.system(size: 13))
                        .foregroundStyle(sideGray)
                        .lineLimit(1)
                }
            }
            .padding(.top, 4)
            if sides.count > shown.count {
                Text("외 \(sides.count - shown.count)개")
                    .font(.system(size: 12))
                    .foregroundStyle(faintGray)
            }
            Spacer(minLength: 0)
            if let next {
                HStack(spacing: 4) {
                    Image(systemName: "chevron.down").font(.system(size: 9, weight: .bold))
                    Text(next).font(.system(size: 11))
                }
                .foregroundStyle(faintGray)
            }
        }
        .padding(.leading, 4)
        .padding(.trailing, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct CafeteriaPicker: View {
    let names: [String]
    let selection: String
    let onSelect: (String) -> Void

    var body: some View {
        List(names, id: \.self) { name in
            Button {
                onSelect(name)
            } label: {
                HStack {
                    Text(name)
                    Spacer()
                    if name == selection { Image(systemName: "checkmark").foregroundStyle(accent) }
                }
            }
        }
    }
}

private struct EmptyState: View {
    var store: WatchStore

    var body: some View {
        VStack(spacing: 8) {
            if store.isRequesting {
                ProgressView()
            }
            Text(store.statusMessage ?? "iPhone 앱에서 메뉴를 가져오는 중입니다")
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(mutedGray)
            Button("다시 시도") { store.requestMenu(refresh: true) }
        }
        .padding()
    }
}
