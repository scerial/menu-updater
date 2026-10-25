import SwiftUI

struct SettingsPage: View {
    let viewModel: MenuViewModel
    let layout: ScreenLayout
    @Binding var defaultCafeteria: String
    @Binding var textSize: MenuTextSize
    @Environment(\.tr) private var tr

    var body: some View {
        let cafeterias = viewModel.menu?.tables.map(\.cafeteria).filter { !$0.isEmpty } ?? []

        VStack(spacing: 0) {
            ScreenHeader(subtitle: String(localized: "기본 식당 · 화면 · 정보"), title: String(localized: "설정"),
                         layout: layout) { EmptyView() }

            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    SettingsSection(title: String(localized: "기본 식당"),
                                    footer: cafeterias.isEmpty ? String(localized: "메뉴를 불러오면 식당을 고를 수 있어요.")
                                                               : String(localized: "앱을 열면 이 식당의 메뉴를 먼저 보여줘요.")) {
                        ForEach(Array(cafeterias.enumerated()), id: \.element) { index, name in
                            if index > 0 { SettingsDivider() }
                            let isSelected = name == defaultCafeteria || (index == 0 && !cafeterias.contains(defaultCafeteria))
                            Button { defaultCafeteria = name } label: {
                                HStack {
                                    Text(tr.place(name))
                                    Spacer()
                                    if isSelected {
                                        Image(systemName: "checkmark")
                                            .font(.system(size: 17, weight: .bold))
                                            .foregroundStyle(Theme.accent)
                                    }
                                }
                                .settingsRow()
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(isSelected ? .isSelected : [])
                        }
                    }

                    SettingsSection(title: String(localized: "화면")) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("글자 크기")
                                .appFont(17)
                            TextSizePicker(selection: $textSize)
                            VStack(alignment: .leading, spacing: 2) {
                                // A sample dish, drawn like a menu card so the size tiers can be compared.
                                Text(verbatim: tr.dish("제육볶음"))
                                    .appFont(19, .bold, menu: true)
                                Text(verbatim: ["쌀밥", "된장국", "잡채"].map(tr.dish).joined(separator: " · "))
                                    .appFont(15, menu: true)
                                    .foregroundStyle(Theme.secondaryText)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 14)
                            .background(Theme.chip, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .accessibilityLabel("미리보기")
                        }
                        .padding(16)
                    }

                    SettingsSection(title: String(localized: "정보")) {
                        if let url = viewModel.menu?.sourceURL {
                            Link(destination: url) {
                                HStack {
                                    Text("원본 게시물 보기")
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(Theme.secondaryText)
                                }
                                .settingsRow()
                            }
                            .buttonStyle(.plain)
                            SettingsDivider()
                        }
                        HStack {
                            Text("앱 버전")
                            Spacer()
                            Text(Self.appVersion)
                                .foregroundStyle(Theme.secondaryText)
                        }
                        .settingsRow()
                    }
                }
                .padding(.horizontal, layout.horizontalPadding)
                .padding(.top, 20)
                .padding(.bottom, 32)
                .frame(maxWidth: 720, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: layout.isWide ? .leading : .center)
            }
        }
    }

    private static var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
    }
}

struct SettingsSection<Content: View>: View {
    let title: String
    var footer: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .appFont(15, .bold)
                .foregroundStyle(Theme.secondaryText)
                .padding(.horizontal, 4)
                .accessibilityAddTraits(.isHeader)
            VStack(spacing: 0) { content }
                .card()
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            if let footer {
                Text(footer)
                    .appFont(15)
                    .foregroundStyle(Theme.secondaryText)
                    .padding(.horizontal, 4)
            }
        }
    }
}

struct SettingsDivider: View {
    var body: some View {
        Theme.divider.frame(height: 1).padding(.leading, 16)
    }
}

struct TextSizePicker: View {
    @Binding var selection: MenuTextSize
    @Namespace private var thumb

    var body: some View {
        HStack(spacing: 4) {
            ForEach(MenuTextSize.allCases) { size in
                let isSelected = size == selection
                Button {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { selection = size }
                } label: {
                    Text(size.displayLabel)
                        .appFont(16, .bold)
                        .foregroundStyle(isSelected ? Theme.text : Theme.tertiaryText)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background {
                            if isSelected {
                                RoundedRectangle(cornerRadius: 9, style: .continuous)
                                    .fill(Theme.surface)
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
        .background(Theme.track, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("글자 크기")
    }
}

private extension View {
    func settingsRow() -> some View {
        self
            .appFont(17)
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
            .contentShape(Rectangle())
    }
}
