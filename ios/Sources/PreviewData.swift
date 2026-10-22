#if DEBUG
import SwiftUI

/// Sample menus for Xcode previews. Dishes follow the real 10월 4주차 posts.
enum PreviewData {
    /// Wednesday 2026-10-21, 12:00: lunch is being served.
    static let lunchTime = date(21, 12, 0)
    static let eveningTime = date(21, 19, 0)
    static let lateTime = date(21, 23, 30)

    static func date(_ day: Int, _ hour: Int, _ minute: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour, minute: minute))!
    }

    private static let weekdays = [("월", 19), ("화", 20), ("수", 21), ("목", 22), ("금", 23)]

    private static func table(_ name: String, meals: [String: [[String]]]) -> MenuTable {
        let order = ["조식", "중식", "석식"]
        let days = weekdays.enumerated().map { index, entry in
            MenuDay(month: 10, day: entry.1, weekday: entry.0, meals: order.compactMap { meal in
                meals[meal].map { MealSection(name: meal, items: $0[index]) }
            })
        }
        return MenuTable(cafeteria: name, days: days)
    }

    static let hyangseol1 = table("향설 1관", meals: [
        "조식": [
            ["콩나물해장국", "참맛스테이크", "도토리묵무침", "부추나물", "도시락김"],
            ["순대야채볶음", "군만두", "숙주무침", "청경채무침", "고추무침/된장국"],
            ["돈육김치찌개&당면사리", "부추산적*케찹", "호박볶음", "우엉조림", "도시락김/초코머핀", "아산쌀밥/김치/샐러드"],
            ["오징어볶음", "메밀전병", "파래무침", "미나리무침", "도시락김/콩나물국"],
            ["참치야채볶음밥", "새우감자고로케", "비름나물무침", "명엽채볶음", "무말랭이무침"]
        ],
        "중식": [
            ["삼겹볶음밥", "야채고로케*케찹", "비엔나어묵떡볶이", "얼갈이무침", "단무지"],
            ["장각삼계탕", "비빔만두", "열무된장무침", "콩조림", "무말랭이무침"],
            ["새우계란볶음밥", "함박스테이크*소스", "어묵볶음", "후르츠샐러드", "오이양파무침", "우동국"],
            ["보쌈", "막국수", "상추쌈", "콩나물무침", "어묵국"],
            ["돈육김치볶음", "찐두부", "소세지야채볶음", "청포묵무침", "파래무침", "콩나물국"]
        ],
        "석식": [
            ["제육덮밥", "미니돈까스", "계란국", "배추김치", "단무지"],
            ["치킨마요덮밥", "유부장국", "단무지무침", "배추김치"],
            ["부대찌개&당면사리", "만두찜", "우엉조림", "호박볶음", "도시락김", "김치/샐러드"],
            ["닭갈비덮밥", "어묵국", "깍두기", "샐러드"],
            ["오므라이스", "크림스프", "피클", "배추김치"]
        ]
    ])

    static let faculty = table("교직원식당", meals: [
        "중식": [
            ["찜닭", "생선까스*소스", "청포묵*양념", "상추야채겉절이", "미역국", "배추김치", "샐러드, 토스트&딸기잼"],
            ["매콤돈육조림", "메추리알조림", "물파래무생채", "된장국", "청경채무침", "배추김치", "샐러드, 토스트&딸기잼"],
            ["콩나물밥*양념장", "&계란후라이", "꽁치캔김치찜", "사골우거지탕", "김구이", "배추김치", "샐러드, 토스트&딸기잼"],
            ["간장돈육불고기", "쑥갓두부무침", "마늘쫑어묵볶음", "콩나물국", "야채쌈*쌈장", "배추김치", "샐러드, 토스트&딸기잼"],
            ["코다리콩나물찜", "모둠튀김", "계절나물", "유부장국", "냉우동샐러드", "배추김치", "샐러드, 토스트&딸기잼"]
        ]
    ])

    static let medical = table("순천향의대식당", meals: [
        "중식": [
            ["함박스테이크", "우동장국", "토마토스파게티", "브로컬리숙회", "야채샐러드/흑임자", "김치"],
            ["사태김치찜", "무우채국", "크림감자뇨끼수제비", "미역줄기볶음", "후르츠고구마범벅", "얼갈이겉절이"],
            ["깍두기볶음밥", "배추국", "생선까스/감귤타르타르", "새송이애호박볶음", "야채샐러드/딸기", "김구이", "김치"],
            ["안동찜닭", "보리밥", "고추장찌개", "불어묵양파케찹볶음", "가지연근튀김", "콩나물무침", "김치"],
            ["마파두부덮밥", "흑미밥", "유부장국", "만두떡강정", "야채샐러드/블루베리", "요플레", "김치"]
        ]
    ])

    static let menu = WeeklyMenu(
        title: "26년 10월 4주차 순천향대학교 식단", postedAt: "2026-10-16",
        sourceURL: URL(string: "https://www.agoodplacefnb.com/today")!,
        tables: [hyangseol1, faculty, medical],
        additionalSources: [MenuSource(
            title: "26년 10월 4주차 순천향대학교 의과대학 식단", postedAt: "2026-10-16",
            url: URL(string: "https://www.agoodplacefnb.com/today/?bmode=view&idx=175120850&t=board")!
        )]
    )

    /// A menu that doesn't include today's date.
    static let staleMenu = WeeklyMenu(
        title: "26년 10월 2주차 순천향대학교 식단", postedAt: "2026-10-06",
        sourceURL: URL(string: "https://www.agoodplacefnb.com/today")!,
        tables: [hyangseol1]
    )

    @MainActor
    static func viewModel(menu: WeeklyMenu? = PreviewData.menu, now: Date = lunchTime,
                          error: String? = nil, loading: Bool = false) -> MenuViewModel {
        MenuViewModel(previewMenu: menu, today: now, errorMessage: error, isLoading: loading)
    }
}

// MARK: - iPhone (402 × 874)

#Preview("Today · 점심 시간") {
    MenuRootView(viewModel: PreviewData.viewModel())
}

#Preview("Today · 저녁 시간") {
    MenuRootView(viewModel: PreviewData.viewModel(now: PreviewData.eveningTime))
}

#Preview("Today · 밤 (전부 종료)") {
    MenuRootView(viewModel: PreviewData.viewModel(now: PreviewData.lateTime))
}

#Preview("Today · 오늘 날짜 없음") {
    MenuRootView(viewModel: PreviewData.viewModel(menu: PreviewData.staleMenu))
}

#Preview("Today · 새로고침 실패") {
    MenuRootView(viewModel: PreviewData.viewModel(error: "The Internet connection appears to be offline."))
}

#Preview("Today · 불러오는 중") {
    MenuRootView(viewModel: PreviewData.viewModel(menu: nil, loading: true))
}

#Preview("이번 주") {
    MenuRootView(viewModel: PreviewData.viewModel(), initialTab: .week)
}

#Preview("운영 시간 · 점심") {
    MenuRootView(viewModel: PreviewData.viewModel(), initialTab: .hours)
}

#Preview("운영 시간 · 저녁 (곧 마감)") {
    MenuRootView(viewModel: PreviewData.viewModel(now: PreviewData.eveningTime), initialTab: .hours)
}

#Preview("설정") {
    MenuRootView(viewModel: PreviewData.viewModel(), initialTab: .settings)
}

#Preview("Today · 다크") {
    MenuRootView(viewModel: PreviewData.viewModel())
        .preferredColorScheme(.dark)
}

// MARK: - iPad

#Preview("iPad 가로 · Today", traits: .fixedLayout(width: 1194, height: 834)) {
    MenuRootView(viewModel: PreviewData.viewModel())
}

#Preview("iPad 가로 · 이번 주", traits: .fixedLayout(width: 1194, height: 834)) {
    MenuRootView(viewModel: PreviewData.viewModel(), initialTab: .week)
}

#Preview("iPad 가로 · 운영 시간", traits: .fixedLayout(width: 1194, height: 834)) {
    MenuRootView(viewModel: PreviewData.viewModel(), initialTab: .hours)
}

#Preview("iPad 세로 · Today", traits: .fixedLayout(width: 834, height: 1194)) {
    MenuRootView(viewModel: PreviewData.viewModel())
}

#Preview("iPad 세로 · 이번 주", traits: .fixedLayout(width: 834, height: 1194)) {
    MenuRootView(viewModel: PreviewData.viewModel(), initialTab: .week)
}
#endif
