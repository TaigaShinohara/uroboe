#!/bin/bash
# 実機なし検証の標準（2026-11-23）の実行入口。
#   ./verify-ui.sh          … 照合モード（ゴールデン比較込みで全テストを実行）
#   ./verify-ui.sh record   … 記録モード（画面を意図して変えた時にゴールデンを再記録）
# 詳細は docs/UI_VERIFICATION.md。ビルドは build.sh 経由＝直列化ラッパー経由。
set -e
cd "$(dirname "$0")"
if [ "${1:-}" = "record" ]; then
  exec ./build.sh recordRoborazziDebug --tests "app.uroboe.ScreenshotTest"
else
  exec ./build.sh verifyRoborazziDebug
fi
