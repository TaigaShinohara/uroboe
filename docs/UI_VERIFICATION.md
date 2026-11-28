# UI検証（実機なし検証の標準・2026-11-23 導入）

エミュレータが使えない環境のため、交付前に JVM 上で画面を検証する。
正本ルールは AGENTS.md「Pre-delivery UI verification standard — no emulator」。

## 構成

- **ScreenshotTest**（`app/src/test/java/app/uroboe/ScreenshotTest.kt`）
  Robolectric + Roborazzi で主要画面を描画し、ゴールデン画像を記録／照合する。
  あわせてセマンティクス構造チェック（主要ノードが表示され、幅・高さが 0 でないこと）で
  レイアウト崩壊（Migakeru 型の真っ白）を捕まえる。
  カバー画面：ホーム（メモあり／空）、オンボーディング入口、書く画面、詳細画面（節目到達メモ）。
  **拡大フォントテスト**（`editScreen_largeFont_longCaption`、v0.7.1で追加。UX審査 U-5 対応）：
  360dp＋フォントスケール1.3倍で詳細画面を描画し、最長のキャプション
  （「次は約60日後に浮上」）と主ラベルが欠けずに読めることをゴールデン目視で確認する。
  初回記録では完全な丸型ボタン（999.dp）の四隅が複数行テキストに食い込む問題が見つかり、
  ボタンの角丸を 28.dp に抑える修正とセットで解消した。
- **UiInteractionTest**（同ディレクトリ）
  画面遷移と操作の配線：カードタップ→詳細、「その他」→書き出し到達、
  書く画面の自動保存（作成→更新→全消しで破棄）、オンボーディング前進→完了。
  **アプリ本体テスト**（`app_home_rendersContent`、v0.6.1で追加）：個別画面ではなく
  `UroboeApp` そのものを組み立て、起動直後のホームに内容が出ることを確認する。
  v0.6.0 で画面振り分け（AnimatedContent）にホームの受け皿が無く起動しても
  真っ白になる不具合を、個別画面テストだけでは捕まえられなかった教訓による。
  **長押しメニューテスト**（`home_longPressCard_opensMenuAndMovesToSomeday`、v0.6.2で追加）：
  カードの長押し（セマンティクス OnLongClick）でボトムシートが開き、
  「いつかやればいいへ」で保留コールバックが届くことを確認する。
  現行の総数は全44件（単体14+14、画面9、操作7）。MemoStore の単体には v0.7.2 で時刻リベース／復元の本文保護／通知スタック／同じメモの復元は直近のみ／隔離保持／遅延保存の回帰テストを追加した。v0.7.3 では再会の節目判定（ロジック5件）・閉じる記録の永続化と曲線リスタート（ストア2件）・再会カードの画面と操作（各1件）を追加した。v0.8.0 でモーフィング指標の画面テスト、v0.8.1 でキーボード表示中に操作ボタンが隠れる状態の画面テストを追加した。
- 既存の単体テスト（MemoLogicTest / MemoStoreTest）も同じ実行に含まれる。

## 実行

```bash
./verify-ui.sh          # 照合（交付前チェックはこれ）
./verify-ui.sh record   # ゴールデン再記録（画面を意図して変えた時だけ）
```

- 実行は `build.sh` 経由＝ビルド直列化ラッパー（同時2本まで）経由。
- ゴールデンは `app/src/test/screenshots/` に置き、コードと同じコミットに入れる。
- 再記録したら PNG を必ず目視確認する（崩壊した画面を正解として記録しないため）。

## 導入時の記録（2026-11-23）と落とし穴

- 結果：全23件合格（単体9+5、画面5、操作4）。ゴールデン5枚を目視確認済み。
  照合ラン（verifyRoborazziDebug・全件）でも一致を確認。
- Roborazzi 1.79 の注意点（実測）：
  - 素の `testDebugUnitTest` では capture は記録も照合もされない（無言で素通り）。
    必ずプラグインの `recordRoborazziDebug` / `verifyRoborazziDebug` タスクで実行する。
  - `captureRoboImage` は View の拡張関数 `view.captureRoboImage(file = ...)` が正しい形。
  - Compose 拡張（onRoot().captureRoboImage）はこの環境ではファイルを書かなかった。
- Robolectric + Compose の注意点（実測）：
  - `createAndroidComposeRule<ComponentActivity>()` は、デバッグ用マニフェスト
    （`app/src/debug/AndroidManifest.xml`）に ComponentActivity の宣言がないと
    "Unable to resolve activity" で全件失敗する。ui-test-manifest 依存だけでは単体テスト側に効かない。
  - `captureToImage()` は一時停止ルーパーの再描画待ちでタイムアウトする。使わない。
  - 画素レベルの真っ白チェックは描画経路差で空振りが続いたため、bounds による構造チェックに切り替えた。
  - ネイティブライブラリの展開先は `java.io.tmpdir` をプロジェクト内（`.local/tmp`）に寄せた
    （VM の /tmp は 512MB tmpfs で、他プロジェクトの展開残りで埋まると展開に失敗する）。
    設定は `app/build.gradle.kts` の testOptions に集約。
  - テストデータは `TestFixtures` で時刻固定（実行日に依存しない）。
- 導入当日は全プロジェクト同時ビルドで OOM・ランタイム再起動が多発した。
  ビルド直列化ルール導入後はロック経由で安定した。
