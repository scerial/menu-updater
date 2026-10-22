import SwiftUI

@main
struct WeeklyMenuApp: App {
    @State private var viewModel = MenuViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            MenuRootView(viewModel: viewModel)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { viewModel.didBecomeActive() }
        }
    }
}

@MainActor
@Observable
final class MenuViewModel {
    var menu: WeeklyMenu?
    var isLoading = false
    var errorMessage: String?
    /// The date the views treat as "today"; updated whenever the app comes to the foreground.
    var today = Date()

    private let repository = MenuRepository()
    private var lastRefreshed: Date?
    /// How long a successful load stays fresh before returning to the app fetches again.
    private let refreshInterval: TimeInterval = 30 * 60

    init() {
        menu = repository.loadCachedMenu()
        Task { await refresh() }
    }

    #if DEBUG
    /// For Xcode previews: shows fixed data and never touches the network.
    init(previewMenu: WeeklyMenu?, today: Date, errorMessage: String? = nil, isLoading: Bool = false) {
        menu = previewMenu
        self.today = today
        self.errorMessage = errorMessage
        self.isLoading = isLoading
    }
    #endif

    /// Called when the app returns to the foreground: updates today's date and reloads if the menu is stale.
    func didBecomeActive(now: Date = Date()) {
        let dayChanged = !Calendar.current.isDate(today, inSameDayAs: now)
        today = now
        let isStale = lastRefreshed.map { now.timeIntervalSince($0) > refreshInterval } ?? true
        if dayChanged || isStale {
            Task { await refresh() }
        }
    }

    func refresh() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let result = try await repository.fetchLatestMenu()
            menu = result
            lastRefreshed = Date()
            repository.saveCachedMenu(result)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
