import SwiftUI

// MARK: - Root

enum AppTab: CaseIterable, Identifiable {
    case today, week, hours, settings

    var id: Self { self }

    var label: String {
        switch self {
        case .today: String(localized: "오늘")
        case .week: String(localized: "이번 주")
        case .hours: String(localized: "운영 시간")
        case .settings: String(localized: "설정")
        }
    }

    var icon: String {
        switch self {
        case .today: "tray"
        case .week: "calendar"
        case .hours: "clock"
        case .settings: "gearshape"
        }
    }
}

/// The app's tray mark, drawn from the app icon artwork; takes its colour from `foregroundStyle`.
struct TrayIcon: View {
    var size: CGFloat = 20

    var body: some View {
        // The artwork has padding inside its square, so the frame is larger than the visible tray.
        Image("Tray")
            .resizable()
            .scaledToFit()
            .frame(width: size * 1.4, height: size * 1.4)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// A tab's icon: the tray for Today, an SF Symbol for the rest.
struct TabIcon: View {
    let tab: AppTab
    let size: CGFloat

    var body: some View {
        if tab == .today {
            TrayIcon(size: size)
        } else {
            Image(systemName: tab.icon)
                .font(.system(size: size, weight: .medium))
        }
    }
}

/// How much room the window has: a phone, an iPad in portrait, or an iPad in landscape.
struct ScreenLayout {
    let width: CGFloat

    /// Landscape iPad: a sidebar replaces the bottom tab bar.
    var usesSidebar: Bool { width >= 1000 }
    /// Both cafeterias side by side instead of a cafeteria picker.
    var showsColumns: Bool { width >= 700 }
    var isWide: Bool { showsColumns }
    var horizontalPadding: CGFloat { isWide ? 40 : 16 }
}

struct MenuRootView: View {
    let viewModel: MenuViewModel

    @State private var tab: AppTab
    @State private var selectedCafeteria: String?
    @AppStorage("defaultCafeteria") private var defaultCafeteria = ""
    @AppStorage("menuTextSize") private var textSize: MenuTextSize = .normal

    init(viewModel: MenuViewModel, initialTab: AppTab = .today) {
        self.viewModel = viewModel
        _tab = State(initialValue: initialTab)
    }

    var body: some View {
        GeometryReader { proxy in
            let layout = ScreenLayout(width: proxy.size.width)
            Group {
                if layout.usesSidebar {
                    HStack(spacing: 0) {
                        Sidebar(selection: $tab)
                        page(layout: layout)
                    }
                } else {
                    VStack(spacing: 0) {
                        page(layout: layout)
                        BottomTabBar(selection: $tab)
                    }
                }
            }
            .background(Theme.background)
        }
        .foregroundStyle(Theme.text)
        .tint(Theme.accent)
        .environment(\.menuTextScale, textSize.scale)
        .environment(\.uiScale, Theme.uiScale)
        .environment(\.tr, Tr.current)
        .environment(viewModel)
    }

    @ViewBuilder
    private func page(layout: ScreenLayout) -> some View {
        switch tab {
        case .today:
            MenuScreen(viewModel: viewModel, layout: layout, cafeteria: cafeteriaBinding,
                       subtitle: { _ in AppDate.full(viewModel.today, english: Tr.current.isEnglish) },
                       title: String(localized: "오늘의 메뉴")) { menu, cafeteria in
                TodayPage(menu: menu, today: viewModel.today, cafeteria: cafeteria, layout: layout)
            }
        case .week:
            MenuScreen(viewModel: viewModel, layout: layout, cafeteria: cafeteriaBinding,
                       subtitle: { menu in menu.flatMap { AppDate.range(of: $0.tables.flatMap(\.days), english: Tr.current.isEnglish) } ?? "" },
                       title: String(localized: "이번 주 메뉴")) { menu, cafeteria in
                WeekPage(menu: menu, today: viewModel.today, cafeteria: cafeteria, layout: layout)
            }
        case .hours:
            HoursPage(layout: layout)
        case .settings:
            SettingsPage(viewModel: viewModel, layout: layout, defaultCafeteria: $defaultCafeteria, textSize: $textSize)
        }
    }

    /// The picked cafeteria, falling back to the default from Settings, then the first one in the post.
    private var cafeteriaBinding: Binding<String> {
        Binding(
            get: {
                let names = viewModel.menu?.tables.map(\.cafeteria) ?? []
                if let selectedCafeteria, names.contains(selectedCafeteria) { return selectedCafeteria }
                if names.contains(defaultCafeteria) { return defaultCafeteria }
                return names.first ?? ""
            },
            set: { selectedCafeteria = $0 }
        )
    }
}

// MARK: - Navigation

struct BottomTabBar: View {
    @Binding var selection: AppTab

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases) { tab in
                let isSelected = tab == selection
                Button { selection = tab } label: {
                    VStack(spacing: 4) {
                        TabIcon(tab: tab, size: 20)
                            .frame(height: 24)
                        Text(tab.label)
                            .appFont(13, isSelected ? .bold : .regular)
                    }
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .foregroundStyle(isSelected ? Theme.accent : Theme.secondaryText)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.horizontal, 8)
        .padding(.top, 6)
        .background(Theme.surface.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { Theme.border.frame(height: 1) }
    }
}

struct Sidebar: View {
    @Binding var selection: AppTab

    var body: some View {
        VStack(alignment: .leading, spacing: 28) {
            HStack(spacing: 12) {
                TrayIcon(size: 24)
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(Theme.accent, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .accessibilityHidden(true)
                Text("순대 학식")
                    .appFont(22, .bold)
            }
            .padding(.horizontal, 12)

            VStack(spacing: 6) {
                ForEach(AppTab.allCases) { tab in
                    let isSelected = tab == selection
                    Button { selection = tab } label: {
                        HStack(spacing: 14) {
                            TabIcon(tab: tab, size: 21)
                                .frame(width: 26)
                            Text(tab.label)
                                .appFont(19, .bold)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 16)
                        .frame(minHeight: 56)
                        .foregroundStyle(isSelected ? .white : Theme.tertiaryText)
                        .background(isSelected ? Theme.accent : .clear, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 24)
        .frame(width: 280)
        .background(Theme.surface.ignoresSafeArea())
        .overlay(alignment: .trailing) { Theme.border.frame(width: 1).ignoresSafeArea() }
    }
}

// MARK: - Screen chrome (header, loading, errors, refresh)

struct ScreenHeader<Accessory: View>: View {
    let subtitle: String
    let title: String
    let layout: ScreenLayout
    var isRefreshing = false
    /// Sits at the right end of the title row (e.g. an edit button).
    var trailing: AnyView?
    @ViewBuilder var accessory: Accessory

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(subtitle)
                        .appFont(layout.isWide ? 18 : 16, .bold)
                        .foregroundStyle(Theme.secondaryText)
                    Text(title)
                        .appFont(layout.isWide ? 40 : 32, .bold)
                        .accessibilityAddTraits(.isHeader)
                }
                Spacer()
                if isRefreshing {
                    ProgressView()
                        .padding(.bottom, 8)
                        .accessibilityLabel("새로고침 중")
                }
                trailing
            }
            accessory
        }
        .padding(.horizontal, layout.isWide ? 40 : 20)
        .padding(.top, layout.isWide ? 36 : 24)
        .padding(.bottom, 22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface.ignoresSafeArea(edges: .top))
        .overlay(alignment: .bottom) { Theme.border.frame(height: 1) }
    }
}

/// Shared frame for the Today and Week tabs: header, cafeteria picker, and the loading/error/empty states.
struct MenuScreen<Content: View>: View {
    let viewModel: MenuViewModel
    let layout: ScreenLayout
    @Binding var cafeteria: String
    let subtitle: (WeeklyMenu?) -> String
    let title: String
    @ViewBuilder let content: (WeeklyMenu, String) -> Content

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(subtitle: subtitle(viewModel.menu), title: title, layout: layout,
                         isRefreshing: viewModel.isLoading && viewModel.menu != nil) {
                if let menu = viewModel.menu, menu.tables.count > 1, !layout.showsColumns {
                    CafeteriaPicker(names: menu.tables.map(\.cafeteria), selection: $cafeteria)
                }
            }

            if let menu = viewModel.menu {
                content(menu, cafeteria)
            } else if viewModel.isLoading {
                LoadingState()
            } else if let message = viewModel.errorMessage {
                ContentUnavailableView {
                    Label("메뉴를 불러올 수 없어요", systemImage: "wifi.slash")
                } description: {
                    Text(message)
                } actions: {
                    Button("다시 시도") { Task { await viewModel.refresh() } }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                ContentUnavailableView {
                    Label { Text("메뉴가 없어요") } icon: { TrayIcon(size: 48) }
                }
            }
        }
    }
}

/// Scrolling body shared by the menu pages: stale-data banner on top, source footer at the bottom, pull to refresh.
struct MenuScroll<Content: View>: View {
    let menu: WeeklyMenu
    let layout: ScreenLayout
    @Environment(MenuViewModel.self) private var viewModel
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let message = viewModel.errorMessage {
                    StaleMenuBanner(message: message)
                }
                content
                SourceFooter(sources: menu.sources)
                    .padding(.top, 12)
            }
            .padding(.horizontal, layout.horizontalPadding)
            .padding(.top, 20)
            .padding(.bottom, 28)
        }
        .refreshable { await viewModel.refresh() }
    }
}

struct CafeteriaPicker: View {
    let names: [String]
    @Binding var selection: String
    @Namespace private var thumb
    @Environment(\.tr) private var tr

    var body: some View {
        HStack(spacing: 0) {
            ForEach(names, id: \.self) { name in
                let isSelected = name == selection
                Button {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) { selection = name }
                } label: {
                    let label = tr.place(name)
                    Text(label)
                        .appFont(label.count > 12 ? 14 : 17, .bold)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                        .foregroundStyle(isSelected ? .white : Theme.tertiaryText)
                        .padding(.horizontal, 4)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background {
                            if isSelected {
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(Theme.accent)
                                    .matchedGeometryEffect(id: "thumb", in: thumb)
                            }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(4)
        .background(Theme.track, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("식당 선택")
    }
}

struct LoadingState: View {
    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
                .controlSize(.large)
            Text("메뉴를 읽는 중…")
                .appFont(17, .bold)
            Text("처음 불러올 때는 시간이 조금 걸려요.")
                .appFont(15)
                .foregroundStyle(Theme.secondaryText)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct StaleMenuBanner: View {
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Label("새로고침하지 못해 저장된 메뉴를 보여줘요.", systemImage: "wifi.exclamationmark")
                .appFont(15, .bold)
            Text(message)
                .appFont(13)
                .foregroundStyle(Theme.secondaryText)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(.orange.opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

struct SourceFooter: View {
    let sources: [MenuSource]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(sources, id: \.url) { source in
                VStack(alignment: .leading, spacing: 4) {
                    Text(source.title)
                    Text("게시일 \(source.postedAt)")
                    Link("원본 게시물 열기", destination: source.url)
                        .foregroundStyle(Theme.text)
                        .underline()
                }
            }
        }
        .appFont(13)
        .foregroundStyle(Theme.secondaryText)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Today

struct TodayPage: View {
    let menu: WeeklyMenu
    let today: Date
    let cafeteria: String
    let layout: ScreenLayout

    var body: some View {
        let entries = menu.entries(on: today)

        if entries.isEmpty {
            MenuScroll(menu: menu, layout: layout) {
                ContentUnavailableView(
                    "오늘 메뉴가 없어요",
                    systemImage: "calendar.badge.exclamationmark",
                    description: Text("이 게시물에는 오늘 날짜가 없어요. 이번 주 탭을 확인해 주세요.")
                )
            }
        } else if layout.showsColumns {
            MenuScroll(menu: menu, layout: layout) {
                HStack(alignment: .top, spacing: 24) {
                    ForEach(menu.tables) { table in
                        TodayColumn(table: table, day: entries.first { $0.cafeteria == table.cafeteria }?.day,
                                    now: today, isWide: true)
                            .frame(maxWidth: .infinity, alignment: .top)
                    }
                }
            }
        } else if let table = menu.tables.first(where: { $0.cafeteria == cafeteria }) ?? menu.tables.first {
            ScrollViewReader { proxy in
                MenuScroll(menu: menu, layout: layout) {
                    TodayColumn(table: table, day: entries.first { $0.cafeteria == table.cafeteria }?.day,
                                now: today, isWide: false)
                }
                .onAppear { scrollToFocus(in: table, entries: entries, proxy: proxy, animated: false) }
                .onChange(of: cafeteria) { scrollToFocus(in: table, entries: entries, proxy: proxy, animated: true) }
            }
        }
    }

    /// Bring the meal being served (or the next one) into view, as the design does on open.
    private func scrollToFocus(in table: MenuTable, entries: [(cafeteria: String, day: MenuDay)],
                               proxy: ScrollViewProxy, animated: Bool) {
        guard let day = entries.first(where: { $0.cafeteria == table.cafeteria })?.day else { return }
        let meals = day.meals.map(\.name)
        guard let focus = MealSchedule.focusMeal(among: meals, at: today, cafeteria: table.cafeteria), focus != meals.first else { return }
        withAnimation(animated ? .easeInOut : nil) { proxy.scrollTo(focus, anchor: .top) }
    }
}

struct TodayColumn: View {
    let table: MenuTable
    let day: MenuDay?
    let now: Date
    let isWide: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CafeteriaHeading(table: table, isWide: isWide)

            if let day {
                let focus = MealSchedule.focusMeal(among: day.meals.map(\.name), at: now, cafeteria: table.cafeteria)
                ForEach(day.meals) { meal in
                    MealCard(meal: meal, status: MealSchedule.status(of: meal.name, at: now, cafeteria: table.cafeteria),
                             isFocused: meal.name == focus, isWide: isWide)
                        .id(meal.name)
                }
            } else {
                EmptyMealCard(title: String(localized: "오늘 메뉴가 없어요"),
                              detail: String(localized: "\(Tr.current.place(table.cafeteria)) 메뉴에 오늘 날짜가 없어요."))
            }
        }
    }
}

struct CafeteriaHeading: View {
    let table: MenuTable
    let isWide: Bool
    @Environment(\.tr) private var tr

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if !table.cafeteria.isEmpty {
                Text(tr.place(table.cafeteria))
                    .appFont(isWide ? 26 : 24, .bold)
                    .accessibilityAddTraits(.isHeader)
            }
            if !table.mealNames.isEmpty {
                Text(table.mealNames.map(tr.meal).joined(separator: " · "))
                    .appFont(isWide ? 16 : 15)
                    .foregroundStyle(Theme.secondaryText)
            }
        }
        .padding(.horizontal, 4)
        .padding(.bottom, 4)
    }
}

struct MealCard: View {
    let meal: MealSection
    let status: MealSchedule.Status?
    let isFocused: Bool
    let isWide: Bool
    @Environment(\.tr) private var screenTr
    @State private var showKorean = false

    /// This card's language: the screen's, or Korean after a tap.
    private var tr: Tr { showKorean ? .korean : screenTr }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                Text(meal.timeLabel(tr))
                    .appFont(16, .bold)
                    .foregroundStyle(isFocused ? Theme.accent : Theme.secondaryText)
                Spacer(minLength: 0)
                if let status {
                    StatusPill(status: status)
                }
            }

            if let main = meal.items.first {
                Text(tr.dish(main))
                    .appFont(24, .bold, menu: true)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if meal.items.count > 1 {
                FlowLayout(spacing: 8) {
                    ForEach(Array(meal.items.dropFirst().enumerated()), id: \.offset) { _, item in
                        Text(tr.dish(item))
                            .appFont(17, menu: true)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Theme.chip, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                    }
                }
            }
        }
        .padding(isWide ? 22 : 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(highlighted: isFocused, radius: isWide ? 18 : 16)
        .accessibilityElement(children: .combine)
        .languageFlip($showKorean, isEnabled: screenTr.isEnglish, radius: isWide ? 18 : 16)
        .onChange(of: meal.items) { showKorean = false }
    }
}

struct StatusPill: View {
    let status: MealSchedule.Status

    var body: some View {
        Text(status.displayLabel)
            .appFont(14, .bold)
            .foregroundStyle(status == .open ? .white : (status == .closed ? Theme.secondaryText : Theme.text))
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(status == .open ? Theme.accent : Theme.track, in: Capsule())
    }
}

struct EmptyMealCard: View {
    let title: String
    let detail: String

    var body: some View {
        VStack(spacing: 6) {
            Text(title)
                .appFont(20, .bold)
            Text(detail)
                .appFont(16)
                .foregroundStyle(Theme.secondaryText)
        }
        .multilineTextAlignment(.center)
        .padding(.vertical, 36)
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(Theme.dashedBorder, style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
        }
    }
}

// MARK: - Week

/// Pick a meal, then read it down the week, one row per day.
struct WeekPage: View {
    let menu: WeeklyMenu
    let today: Date
    let cafeteria: String
    let layout: ScreenLayout

    @State private var period: String?

    var body: some View {
        let tables = visibleTables
        let periods = Self.periods(in: tables)
        let active = period.flatMap { periods.contains($0) ? $0 : nil } ?? defaultPeriod(in: periods)

        VStack(spacing: 0) {
            if periods.count > 1 {
                PeriodBar(periods: periods, selection: Binding(get: { active }, set: { period = $0 }))
            }
            MenuScroll(menu: menu, layout: layout) {
                if layout.showsColumns {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(alignment: .top, spacing: 24) {
                            ForEach(tables) { table in
                                CafeteriaHeading(table: table, isWide: true)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        WeekGrid(tables: tables, period: active, today: today)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(tables) { table in
                            WeekRows(table: table, period: active, today: today, isWide: false)
                        }
                    }
                }
            }
        }
    }

    private var visibleTables: [MenuTable] {
        if layout.showsColumns { return menu.tables }
        return menu.tables.first(where: { $0.cafeteria == cafeteria }).map { [$0] } ?? Array(menu.tables.prefix(1))
    }

    /// Meals in serving order across the shown cafeterias.
    private static func periods(in tables: [MenuTable]) -> [String] {
        var seen = Set<String>()
        return tables.flatMap(\.mealNames).filter { seen.insert($0).inserted }
    }

    /// The meal being served or coming up next; lunch otherwise.
    private func defaultPeriod(in periods: [String]) -> String {
        MealSchedule.focusMeal(among: periods, at: today)
            ?? (periods.contains("중식") ? "중식" : periods.first ?? "")
    }
}

struct PeriodBar: View {
    let periods: [String]
    @Binding var selection: String

    var body: some View {
        HStack(spacing: 0) {
            ForEach(periods, id: \.self) { name in
                let isSelected = name == selection
                Button { selection = name } label: {
                    VStack(spacing: 0) {
                        Text(Tr.current.meal(name))
                            .appFont(17, .bold)
                        if let hours = MealSchedule.hoursLabel(for: name) {
                            Text(hours)
                                .appFont(13)
                        }
                    }
                    .foregroundStyle(isSelected ? Theme.text : Theme.secondaryText)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .overlay(alignment: .bottom) {
                        (isSelected ? Theme.accent : .clear).frame(height: 3)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.horizontal, 12)
        .background(Theme.surface)
        .overlay(alignment: .bottom) { Theme.border.frame(height: 1) }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("식사 선택")
    }
}

struct WeekRows: View {
    let table: MenuTable
    let period: String
    let today: Date
    let isWide: Bool

    var body: some View {
        let todayID = Self.dayID(of: today)
        let rows = table.days.compactMap { day in
            day.meals.first { $0.name == period }.map { (day: day, meal: $0) }
        }

        if rows.isEmpty {
            EmptyMealCard.notServing(table)
        } else {
            EqualHeightGrid(columns: 1) {
                ForEach(rows, id: \.day.id) { row in
                    WeekRow(day: row.day, meal: row.meal, isToday: row.day.id == todayID, isWide: isWide)
                }
            }
        }
    }

    static func dayID(of date: Date) -> Int? {
        let parts = Calendar.current.dateComponents([.month, .day], from: date)
        guard let month = parts.month, let day = parts.day else { return nil }
        return month * 100 + day
    }
}

/// iPad week: one row per day across the cafeteria columns, every row the same height.
struct WeekGrid: View {
    let tables: [MenuTable]
    let period: String
    let today: Date

    private enum Cell {
        case row(MenuDay, MealSection, isToday: Bool)
        case closed(MenuTable)
        case blank
    }

    var body: some View {
        EqualHeightGrid(columns: max(tables.count, 1)) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, cell in
                switch cell {
                case let .row(day, meal, isToday):
                    WeekRow(day: day, meal: meal, isToday: isToday, isWide: true)
                case let .closed(table):
                    EmptyMealCard.notServing(table)
                case .blank:
                    Color.clear
                }
            }
        }
    }

    /// Row-major cells: each day in the week, then each cafeteria's meal for that day.
    /// A cafeteria that doesn't serve this meal shows one notice in its first row.
    private var cells: [Cell] {
        let todayID = WeekRows.dayID(of: today)
        let meals = tables.map { table in
            Dictionary(table.days.compactMap { day in day.meals.first { $0.name == period }.map { (day.id, (day, $0)) } },
                       uniquingKeysWith: { first, _ in first })
        }
        let dayIDs = Set(meals.flatMap(\.keys)).sorted()
        return dayIDs.enumerated().flatMap { rowIndex, dayID in
            tables.indices.map { column -> Cell in
                if let (day, meal) = meals[column][dayID] { return .row(day, meal, isToday: dayID == todayID) }
                if meals[column].isEmpty && rowIndex == 0 { return .closed(tables[column]) }
                return .blank
            }
        }
    }
}

struct WeekRow: View {
    let day: MenuDay
    let meal: MealSection
    let isToday: Bool
    let isWide: Bool
    @Environment(\.tr) private var screenTr
    @State private var showKorean = false

    /// This card's language: the screen's, or Korean after a tap.
    private var tr: Tr { showKorean ? .korean : screenTr }

    var body: some View {
        HStack(spacing: isWide ? 16 : 14) {
            VStack(spacing: 0) {
                Text(tr.weekday(day.weekday))
                    .appFont(isWide ? 16 : 15, .bold)
                Text("\(day.day)")
                    .appFont(isWide ? 24 : 22, .bold)
            }
            .foregroundStyle(isToday ? Theme.accent : Theme.text)
            .frame(width: isWide ? 52 : 48)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(tr.dish(meal.items.first ?? ""))
                        .appFont(isWide ? 21 : 19, .bold, menu: true)
                        .fixedSize(horizontal: false, vertical: true)
                    if isToday {
                        Text("오늘")
                            .appFont(isWide ? 14 : 13, .bold)
                            .foregroundStyle(.white)
                            .padding(.horizontal, isWide ? 9 : 8)
                            .padding(.vertical, 2)
                            .background(Theme.accent, in: Capsule())
                    }
                }
                if meal.items.count > 1 {
                    Text(meal.items.dropFirst().map(tr.dish).joined(separator: " · "))
                        .appFont(15, menu: true)
                        .foregroundStyle(Theme.secondaryText)
                        .lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 14)
        .padding(.horizontal, isWide ? 18 : 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .card(highlighted: isToday)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(verbatim: accessibilityText))
        .languageFlip($showKorean, isEnabled: screenTr.isEnglish)
        .onChange(of: meal.items) { showKorean = false }
    }

    private var accessibilityText: String {
        let date = tr.isEnglish ? "\(tr.weekday(day.weekday)) \(day.month)/\(day.day)" : day.heading
        let today = isToday ? [String(localized: "오늘")] : []
        return ([date] + today + [meal.items.map(tr.dish).joined(separator: ", ")]).joined(separator: ", ")
    }
}

extension EmptyMealCard {
    /// "향설1관은 중식만 운영해요" for a cafeteria that doesn't serve the chosen meal.
    static func notServing(_ table: MenuTable) -> EmptyMealCard {
        let tr = Tr.current
        return EmptyMealCard(
            title: String(localized: "이 시간에는 운영하지 않아요"),
            detail: String(localized: "\(tr.place(table.cafeteria))은 \(table.mealNames.map(tr.meal).joined(separator: " · "))만 운영해요")
        )
    }
}

// MARK: - Model helpers

extension MenuTable {
    /// The meals this cafeteria serves, in the order they appear in the table.
    var mealNames: [String] {
        var seen = Set<String>()
        return days.flatMap { $0.meals.map(\.name) }.filter { seen.insert($0).inserted }
    }
}

extension MealSection {
    /// "중식 · 11:00–14:00" ("Lunch · 11:00–14:00"), or just the meal name when its hours aren't known.
    func timeLabel(_ tr: Tr) -> String {
        MealSchedule.hoursLabel(for: name).map { "\(tr.meal(name)) · \($0)" } ?? tr.meal(name)
    }
}
