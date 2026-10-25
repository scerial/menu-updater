import SwiftUI

// MARK: - Data

/// `shared/translations/en.json`, shared with Android: English for dishes, cafeteria and venue
/// names, meal names and weekdays. Korean stays the key everywhere; this is display-only.
struct TranslationData: Decodable {
    /// Canonical dish name → English.
    var dishes: [String: String] = [:]
    /// Common OCR misread → canonical dish name (iOS has no corrector, so it relies on these).
    var aliases: [String: String] = [:]
    /// Parts for the word-by-word guess. A value starting with `^` moves to the front in English
    /// (무침 → "^Seasoned"); the rest keep the Korean order (돈육김치볶음 → "Pork Kimchi Stir-fry").
    var glossary: [String: String] = [:]
    var places: [String: String] = [:]
    var meals: [String: String] = [:]
    var weekdays: [String: String] = [:]

    init(dishes: [String: String] = [:], aliases: [String: String] = [:], glossary: [String: String] = [:],
         places: [String: String] = [:], meals: [String: String] = [:], weekdays: [String: String] = [:]) {
        self.dishes = dishes
        self.aliases = aliases
        self.glossary = glossary
        self.places = places
        self.meals = meals
        self.weekdays = weekdays
    }

    private enum CodingKeys: String, CodingKey { case dishes, aliases, glossary, places, meals, weekdays }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        dishes = try c.decodeIfPresent([String: String].self, forKey: .dishes) ?? [:]
        aliases = try c.decodeIfPresent([String: String].self, forKey: .aliases) ?? [:]
        glossary = try c.decodeIfPresent([String: String].self, forKey: .glossary) ?? [:]
        places = try c.decodeIfPresent([String: String].self, forKey: .places) ?? [:]
        meals = try c.decodeIfPresent([String: String].self, forKey: .meals) ?? [:]
        weekdays = try c.decodeIfPresent([String: String].self, forKey: .weekdays) ?? [:]
    }
}

// MARK: - Translator

/// Turns Korean menu text into English for display. A port of the Android `Translator`; every
/// function returns its input unchanged when it has no translation.
final class Translator {
    enum Source { case dictionary, guess, none }

    private let data: TranslationData
    private let glossaryKeys: Set<String>
    private let maxPart: Int
    private let dishKeys: Set<String>
    private let maxDish: Int

    private static let separators: Set<Character> = ["/", "*", "&", ",", "+", "~"]
    /// Separators spaced for English; `*` (the menu's "with") reads as `+`.
    private static let spaced: [String: String] = ["/": " / ", "*": " + ", "&": " & ", ",": ", ", "+": " + ", "~": " ~ "]

    init(data: TranslationData) {
        self.data = data
        glossaryKeys = Set(data.glossary.keys)
        maxPart = data.glossary.keys.map(\.count).max() ?? 0
        dishKeys = Set(data.dishes.keys).union(data.aliases.keys)
        maxDish = dishKeys.map(\.count).max() ?? 0
    }

    /// The bundled `en.json`, or nil when it is missing or unreadable.
    static func load(from bundle: Bundle = .main) -> Translator? {
        guard let url = bundle.url(forResource: "en", withExtension: "json"),
              let json = try? Data(contentsOf: url),
              let data = try? JSONDecoder().decode(TranslationData.self, from: json) else { return nil }
        return Translator(data: data)
    }

    /// A whole menu item, translated segment by segment ("토스트&딸기잼" → "Toast & Strawberry Jam").
    func dish(_ item: String) -> String {
        tokens(item)
            .map { isSeparator($0) ? (Self.spaced[$0] ?? $0) : segment($0).text.trimmingCharacters(in: .whitespaces) }
            .joined()
            .replacingOccurrences(of: " {2,}", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
    }

    /// How each dish in [item] was translated; for tests and coverage checks.
    func sources(_ item: String) -> [Source] {
        tokens(item).filter { !isSeparator($0) && !$0.trimmingCharacters(in: .whitespaces).isEmpty }.map { segment($0).source }
    }

    func meal(_ name: String) -> String { data.meals[name.trimmingCharacters(in: .whitespaces)] ?? name }
    func weekday(_ name: String) -> String { data.weekdays[name.trimmingCharacters(in: .whitespaces)] ?? name }
    func place(_ name: String) -> String { data.places[name.trimmingCharacters(in: .whitespaces)] ?? name }

    // MARK: Segments

    private func tokens(_ item: String) -> [String] {
        var out: [String] = []
        var current = ""
        for ch in item {
            if Self.separators.contains(ch) {
                if !current.isEmpty { out.append(current) }
                current = ""
                out.append(String(ch))
            } else {
                current.append(ch)
            }
        }
        if !current.isEmpty { out.append(current) }
        return out
    }

    private func isSeparator(_ token: String) -> Bool {
        token.count == 1 && Self.separators.contains(token.first!)
    }

    private func segment(_ raw: String) -> (text: String, source: Source) {
        let core = raw.trimmingCharacters(in: .whitespaces)
        guard !core.isEmpty else { return (raw, .none) }
        for candidate in [core, core.replacingOccurrences(of: " ", with: "")] {
            if let english = dishName(candidate) { return (english, .dictionary) }
        }
        if let guess = guess(core) { return (guess, .guess) }
        return (core, .none)
    }

    private func dishName(_ word: String) -> String? { data.dishes[data.aliases[word] ?? word] }

    /// Every space-separated word must resolve, or there is no guess. Words that are whole dishes
    /// (or several dishes run together, 감자튀김케찹) join with "&"; others are built from glossary parts.
    private func guess(_ core: String) -> String? {
        var phrases: [(text: String, wholeDish: Bool)] = []
        for word in core.split(whereSeparator: \.isWhitespace).map(String.init) {
            guard let phrase = resolve(word) else { return nil }
            phrases.append(phrase)
        }
        guard !phrases.isEmpty else { return nil }
        let separator = phrases.count > 1 && phrases.allSatisfy(\.wholeDish) ? " & " : " "
        return phrases.map(\.text).joined(separator: separator)
    }

    private func resolve(_ word: String) -> (text: String, wholeDish: Bool)? {
        if let english = dishName(word) { return (english, true) }
        if let parts = split(word, keys: dishKeys, maxLength: maxDish, minLength: 2), parts.count > 1 {
            return (parts.compactMap { dishName($0) }.joined(separator: " & "), true)
        }
        guard let parts = split(word, keys: glossaryKeys, maxLength: maxPart, minLength: 1) else { return nil }
        var front: [String] = []
        var rest: [String] = []
        for part in parts {
            let english = data.glossary[part] ?? part
            if english.hasPrefix("^") { front.append(String(english.dropFirst())) } else { rest.append(english) }
        }
        return ((front + rest).joined(separator: " "), false)
    }

    /// The fewest `keys` that exactly cover `word`, each at least `minLength` characters, or nil.
    private func split(_ word: String, keys: Set<String>, maxLength: Int, minLength: Int) -> [String]? {
        guard maxLength > 0 else { return nil }
        let chars = Array(word)
        let n = chars.count
        guard n > 0, minLength <= maxLength else { return nil }
        var best = [Int](repeating: Int.max, count: n + 1)
        var from = [Int](repeating: -1, count: n + 1)
        best[0] = 0
        for end in 1...n {
            let longest = min(maxLength, end)
            guard minLength <= longest else { continue }
            for length in minLength...longest {
                let start = end - length
                guard best[start] != Int.max, keys.contains(String(chars[start..<end])) else { continue }
                if best[start] + 1 < best[end] {
                    best[end] = best[start] + 1
                    from[end] = start
                }
            }
        }
        guard best[n] != Int.max else { return nil }
        var parts: [String] = []
        var end = n
        while end > 0 {
            let start = from[end]
            parts.insert(String(chars[start..<end]), at: 0)
            end = start
        }
        return parts
    }
}

// MARK: - Display language

/// Display-time translation. Korean stays the data everywhere (meal names, cafeterias, settings);
/// a `Tr` only changes what is drawn. `Tr.korean` draws everything as stored.
struct Tr {
    let translator: Translator?

    var isEnglish: Bool { translator != nil }

    func dish(_ text: String) -> String { translator?.dish(text) ?? text }
    func meal(_ name: String) -> String { translator?.meal(name) ?? name }
    func place(_ name: String) -> String { translator?.place(name) ?? name }
    func weekday(_ name: String) -> String { translator?.weekday(name) ?? name }

    /// A venue in the hours list: a place name, or a meal name (향설생활관1's 조식/중식/석식).
    func venue(_ name: String) -> String {
        guard let translator else { return name }
        let place = translator.place(name)
        return place != name ? place : translator.meal(name)
    }

    static let korean = Tr(translator: nil)

    /// English when the app runs in English: the phone's language, or the app's own language in
    /// Settings. iOS relaunches the app when that changes, so this is read once.
    static let current: Tr = {
        let english = Bundle.main.preferredLocalizations.first?.hasPrefix("en") == true
        return english ? Tr(translator: Translator.load()) : .korean
    }()
}

private struct TrKey: EnvironmentKey {
    static let defaultValue = Tr.korean
}

extension EnvironmentValues {
    var tr: Tr {
        get { self[TrKey.self] }
        set { self[TrKey.self] = newValue }
    }
}

/// In English, tapping a menu card flips it to the original Korean and back.
struct LanguageFlip: ViewModifier {
    @Binding var showKorean: Bool
    let isEnabled: Bool
    let radius: CGFloat

    func body(content: Content) -> some View {
        if isEnabled {
            content
                .contentShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
                .onTapGesture { withAnimation(.easeInOut(duration: 0.15)) { showKorean.toggle() } }
                .accessibilityAddTraits(.isButton)
                .accessibilityHint(Text("탭하면 언어를 바꿔요"))
        } else {
            content
        }
    }
}

extension View {
    func languageFlip(_ showKorean: Binding<Bool>, isEnabled: Bool, radius: CGFloat = 16) -> some View {
        modifier(LanguageFlip(showKorean: showKorean, isEnabled: isEnabled, radius: radius))
    }
}

// MARK: - Dates

/// Menu dates in the screen's language: "10월 21일 수요일" / "Wednesday, October 21".
enum AppDate {
    static func full(_ date: Date, english: Bool) -> String {
        date.formatted(.dateTime.month(.wide).day().weekday(.wide)
            .locale(Locale(identifier: english ? "en_US" : "ko_KR")))
    }

    /// The days a post covers: "10월 19일 – 10월 23일" / "Oct 19 – 23".
    static func range(of days: [MenuDay], english: Bool) -> String? {
        let sorted = days.sorted { $0.id < $1.id }
        guard let first = sorted.first, let last = sorted.last else { return nil }
        return span(first.month, first.day, last.month, last.day, english: english)
    }

    /// Two yyyymmdd values: "8월 31일 – 12월 11일" / "Aug 31 – Dec 11".
    static func range(from: Int, until: Int, english: Bool) -> String {
        span(from / 100 % 100, from % 100, until / 100 % 100, until % 100, english: english)
    }

    private static func span(_ m1: Int, _ d1: Int, _ m2: Int, _ d2: Int, english: Bool) -> String {
        let same = m1 == m2 && d1 == d2
        guard english else { return same ? "\(m1)월 \(d1)일" : "\(m1)월 \(d1)일 – \(m2)월 \(d2)일" }
        let start = "\(month(m1)) \(d1)"
        if same { return start }
        return m1 == m2 ? "\(start) – \(d2)" : "\(start) – \(month(m2)) \(d2)"
    }

    private static let englishMonths: [String] = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US")
        return formatter.shortMonthSymbols
    }()

    private static func month(_ m: Int) -> String {
        englishMonths.indices.contains(m - 1) ? englishMonths[m - 1] : "\(m)"
    }
}

// MARK: - Labels for domain values (localized via Localizable.xcstrings)

extension MealSchedule.Status {
    var displayLabel: String {
        switch self {
        case .upcoming: String(localized: "운영 예정")
        case .open: String(localized: "운영 중")
        case .closed: String(localized: "운영 종료")
        }
    }
}

extension VenueStatus {
    var displayLabel: String {
        switch kind {
        case .notOperating: String(localized: "미운영")
        case .dayOff: String(localized: "오늘 휴무")
        case .opensAt: String(localized: "\(opensAt ?? "") 오픈")
        case .ended: String(localized: "운영 종료")
        case .closingSoon: String(localized: "\(minutesLeft ?? 0)분 후 마감")
        case .open: String(localized: "운영 중")
        }
    }
}

extension MenuTextSize {
    var displayLabel: String {
        switch self {
        case .small: String(localized: "작게")
        case .normal: String(localized: "보통")
        case .large: String(localized: "크게")
        }
    }
}
