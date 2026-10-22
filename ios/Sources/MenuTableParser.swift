import Foundation
import CoreGraphics

/// One line of OCR text with its bounding box in Vision's normalized space (origin at bottom-left).
struct RecognizedLine: Equatable {
    let text: String
    let box: CGRect
}

struct MealSection: Codable, Equatable, Identifiable {
    let name: String
    let items: [String]

    var id: String { name }
}

struct MenuDay: Codable, Equatable, Identifiable {
    let month: Int
    let day: Int
    let weekday: String
    let meals: [MealSection]

    var id: Int { month * 100 + day }
    var heading: String { "\(month)월 \(day)일 (\(weekday))" }
}

struct MenuTable: Codable, Equatable, Identifiable {
    let cafeteria: String
    let days: [MenuDay]

    var id: String { cafeteria }
}

/// Rebuilds a weekly menu table (one column per day, one row group per meal) from OCR lines and their positions.
enum MenuTableParser {
    private static let mealNames: Set<String> = ["조식", "중식", "석식"]
    private static let headerRegex = try? NSRegularExpression(
        pattern: #"(\d{1,2})\s*월\s*(\d{1,2})\s*일\s*\(\s*([월화수목금토일])\s*\)"#
    )

    private struct Header {
        let month: Int
        let day: Int
        let weekday: String
        let box: CGRect
    }

    static func parse(_ lines: [RecognizedLine]) -> MenuTable? {
        let headers = lines.compactMap(header(from:)).sorted { $0.box.midX < $1.box.midX }
        guard let firstHeader = headers.first else { return nil }

        let headerBottom = headers.map(\.box.minY).min() ?? 0
        let columnSpacing = columnSpacing(of: headers)

        // Meal labels sit in the left-hand column of the table.
        let mealLabels = lines
            .filter { mealNames.contains($0.text.trimmed) && $0.box.midX < firstHeader.box.minX }
            .sorted { $0.box.midY > $1.box.midY }

        // Everything at or below the origin-notice block is footer text. The block's first row can sit
        // above its "원산지" label, so take the highest line that looks like origin text.
        let originKeywords = ["원산지", "국내산", "외국산", "중국산", "수입산"]
        let originTops: [CGFloat] = lines
            .filter { line in originKeywords.contains { line.text.contains($0) } }
            .map { $0.box.midY }
        let footerCutoff: CGFloat = (originTops.max() ?? -0.02) + 0.02

        var cells = Array(
            repeating: Array(repeating: [RecognizedLine](), count: max(mealLabels.count, 1)),
            count: headers.count
        )
        for line in lines {
            guard line.box.midY < headerBottom, line.box.midY > footerCutoff,
                  !mealNames.contains(line.text.trimmed), !line.text.trimmed.isEmpty,
                  let column = nearestColumn(to: line, in: headers, maxDistance: columnSpacing * 0.6) else { continue }
            let meal = nearestMeal(to: line, in: mealLabels)
            cells[column][meal].append(line)
        }

        let mealTitles = mealLabels.isEmpty ? ["메뉴"] : mealLabels.map { $0.text.trimmed }
        let commonItems = (0..<mealTitles.count).map { extractCommonItem(from: &cells, meal: $0) }

        let days = headers.enumerated().map { column, header in
            let meals = mealTitles.indices.compactMap { meal -> MealSection? in
                var items = cells[column][meal].sorted { $0.box.midY > $1.box.midY }.map { $0.text.trimmed }
                if let common = commonItems[meal] { items.append(common) }
                return items.isEmpty ? nil : MealSection(name: mealTitles[meal], items: items)
            }
            return MenuDay(month: header.month, day: header.day, weekday: header.weekday, meals: meals)
        }

        let cafeteria = lines.first(where: { $0.text.contains("주간메뉴표") })
            .map { $0.text.replacingOccurrences(of: "주간메뉴표", with: "").trimmed } ?? ""
        return MenuTable(cafeteria: cafeteria, days: days)
    }

    private static func header(from line: RecognizedLine) -> Header? {
        let text = line.text
        guard let match = headerRegex?.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let month = Range(match.range(at: 1), in: text).flatMap({ Int(text[$0]) }),
              let day = Range(match.range(at: 2), in: text).flatMap({ Int(text[$0]) }),
              let weekday = Range(match.range(at: 3), in: text).map({ String(text[$0]) }) else { return nil }
        return Header(month: month, day: day, weekday: weekday, box: line.box)
    }

    private static func columnSpacing(of headers: [Header]) -> CGFloat {
        guard headers.count > 1 else { return (headers.first?.box.width ?? 0.1) * 2 }
        let gaps = zip(headers, headers.dropFirst()).map { $1.box.midX - $0.box.midX }
        return gaps.reduce(0, +) / CGFloat(gaps.count)
    }

    private static func nearestColumn(to line: RecognizedLine, in headers: [Header], maxDistance: CGFloat) -> Int? {
        let nearest = headers.indices.min { abs(headers[$0].box.midX - line.box.midX) < abs(headers[$1].box.midX - line.box.midX) }
        guard let nearest, abs(headers[nearest].box.midX - line.box.midX) <= maxDistance else { return nil }
        return nearest
    }

    private static func nearestMeal(to line: RecognizedLine, in labels: [RecognizedLine]) -> Int {
        labels.indices.min { abs(labels[$0].box.midY - line.box.midY) < abs(labels[$1].box.midY - line.box.midY) } ?? 0
    }

    /// Rows that span every day (e.g. "아산쌀밥/김치/샐러드") are centered, so they OCR into the middle column.
    /// Treat the lowest middle-column item as shared when it sits below every other column's last item.
    private static func extractCommonItem(from cells: inout [[[RecognizedLine]]], meal: Int) -> String? {
        guard cells.count % 2 == 1 else { return nil }
        let center = cells.count / 2
        let centerLines = cells[center][meal].sorted { $0.box.midY > $1.box.midY }
        guard let candidate = centerLines.last else { return nil }

        let otherBottoms = cells.indices.filter { $0 != center }
            .compactMap { cells[$0][meal].map(\.box.midY).min() }
        guard let otherBottom = otherBottoms.min(), candidate.box.midY < otherBottom - 0.01 else { return nil }

        cells[center][meal].removeAll { $0 == candidate }
        return candidate.text.trimmed
    }
}

extension String {
    fileprivate var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
