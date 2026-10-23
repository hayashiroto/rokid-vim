# rokid-vim

Rokid AI Glasses（緑単色 HUD）＋ Bluetooth キーボード TE96 で動かす疑似 Neovim。
テキスト編集の基本機能と .nvimrc 風の設定を備えた軽量エディタ。

## 構成（Gradle マルチモジュール）

| モジュール | 種別 | 内容 |
|---|---|---|
| `core` | 純 Kotlin（JVM） | エディタ本体。モード遷移・バッファ・カーソル・レジスタ・undo/redo・検索・ex コマンド・マッピング・東アジア幅・ビューポート計算・入力ゲート類。**Android 非依存**で、JUnit で網羅テストする。 |
| `android-ui` | Android ライブラリ | KeyEvent → 抽象キー `EditorKey` の正規化（CtrlTrack / KeyPressGate 相当・リピート制御）、等幅グリッド描画の `EditorHudView`（緑単色 5 段階の配色）。 |
| `sample-app` | Android アプリ | エディタ Activity（`:w` / `:e` はアプリ内部ストレージ）と、実機検証用の KeyEvent 診断画面。 |

コアを UI から切り離してあるため、次の発注で clawsses の glasses-app に
「エディタモード」として組み込む際は `core`（＋必要なら `android-ui`）を
そのまま利用し、HudActivity 側のキー割当とだけ分岐を作ればよい。

## 対応キー（初版）

- モード: normal / insert / visual / command
- 移動: `h j k l`（矢印キー可）、`w b e`、`0 $`、`gg G`、数字プレフィックス（`3j` `2w` `10G`）
- 編集: `i a o O`、`x dd yy p`、visual の `v` + `d/x/y`
- undo/redo: `u`、`Ctrl-r`
- 検索: `/pattern`（リテラル部分一致）、`n` / `N`
- ex: `:w [name]` `:q` `:q!` `:wq` `:e name` `:set …` `:colorscheme …` `:map / :nnoremap / :inoremap`
- サンプルアプリ限定: `:diag` で KeyEvent 診断画面

## .nvimrc 風設定

アプリ初回起動時に内部ストレージへ既定の `.nvimrc` を書き出す。各行はそのまま ex コマンドとして実行される（`"` 始まりはコメント）。

```vim
" 行番号
set number
" タブ幅・スクロール余白・列数
set tabstop=4
set scrolloff=2
set columns=40
" キーリピート（ms）
set repeatdelay=350
set repeatinterval=70
" 配色: rokid-green / rokid-soft / rokid-mono
colorscheme rokid-green
" マッピング
inoremap jk <Esc>
map Y yy
nnoremap J 3j
```

## 配色（緑単色 HUD 前提）

眼鏡は緑単色・黒が透過なので、`#40FF5E` を最大として **100 / 72 / 48 / 24 / 12 %** の
5 段階の階調だけで組む（`GreenPalette`）。カーソルは反転ブロック（insert 中は縦バー）、
visual 選択は中間輝度の背景＋黒文字（= 透過の抜き文字）。配色の割り当ては
`colorscheme` で切り替えられる（`rokid-green` / `rokid-soft` / `rokid-mono`）。

## 入力まわり（TE96 / Rokid 既知の問題への対応）

- KeyEvent は `dispatchKeyEvent` と `dispatchKeyShortcutEvent` の**両方**で受ける。
- Ctrl 付きキーがシステムに先取りされ keyDown が来ず keyUp だけ届くケースに備え、
  修飾キーの自前追跡（`ModifierTracker`）と、down を見ていない up からの合成を行う。
- 同じ押下が複数経路から届く二重 down は `(keyCode, downTime)` で除去（`KeyPressGate`）。
- キーリピートは `repeatdelay` / `repeatinterval` で間引き、押しっぱなしでも暴走しない。
- `BACK` は Esc の代替として扱う（眼鏡のタッチパッド戻る操作を想定）。
- 日本語入力（IME)は初版対象外。表示は全角 2 桁の東アジア幅で正しく扱う。

## ビルドとテスト

```bash
# core の単体テスト（117 件）
./gradlew :core:test

# サンプルアプリの APK
./gradlew :sample-app:assembleDebug
```

Android SDK の場所は `local.properties`（コミット禁止）か `ANDROID_HOME` で指定する。

## 実機確認待ち（診断画面で確認する）

資料間で食い違いがある・実機でしか確かめられない点。サンプルアプリの
`:diag`（または診断アイコン）で KeyEvent ダンプと InputDevice 一覧を確認する。

- 画面解像度: 公式 480×640 縦 / 別資料 480×400 / AIUI 基準 480×352 の食い違い
  （`set columns=…` とフォントサイズ自動調整でどの解像度でも成立するようにしてある）
- TE96 を眼鏡に直結したときの実際の keyCode / metaState / リピート挙動
- Ctrl chord の down 欠落がどのキーで起きるか（Esc、Backspace、Ctrl-r を重点確認）
- 眼鏡の周辺機器ペアリングが 1 台のみか（RGR06 リングと TE96 の同時利用可否）
- リフレッシュレートと描画負荷

## 今後（初版の範囲外）

- clawsses glasses-app への「エディタモード」組み込み
  （HudActivity の M/DEL/W/Enter/BACK 割当・キーリピート破棄をモード分岐で無効化）
- `P`、名前付きレジスタ、`dw` / `cw` などオペレータ＋モーション合成
- 正規表現検索、`?` 後方検索、ハイライト（hlsearch）
- タブ文字の保持（現状は挿入時にスペース展開。読み込んだ `\t` は表示のみタブストップ対応）
- `:set` の永続化と `rokid-vim.toml` 相当の設定ファイル
- スワップファイルとクラッシュ復旧（現状は onPause で `<name>.autosave` に自動保存のみ）
- 電話との同期（CXR-S の `Caps` メッセージ経由）
- タッチパッド / RGR06 リングでの補助操作（スクロール・タップ Insert 切替）
- JetBrains Mono の同梱（現状はシステム等幅フォント）
- 日本語入力（IME が眼鏡に無いため、電話側からの転送入力などを検討)
