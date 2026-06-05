# Emotion Lottie Assets - Internal Test Only

This directory contains temporary Lottie JSON files for the SDK emotion demo.

Source:

- AnimatEmojis individual emoji pages
- Google Animated Noto Emoji Lottie JSON files listed by AnimatEmojis

Current mapping:

| SDK emotion | Emoji source | Codepoint |
|---|---|---|
| `neutral` | Slightly smiling face | `1f642` |
| `happy` | Grinning face | `1f600` |
| `thinking` | Thinking face | `1f914` |
| `sad` | Pensive face | `1f614` |
| `confused` | Confused face | `1f615` |
| `love` | Smiling face with hearts | `1f970` |
| `angry` | Angry face | `1f620` |
| `sleepy` | Sleepy face | `1f62a` |
| `delicious` | Face savoring food | `1f60b` |
| `surprised` | Astonished face | `1f632` |
| `cool` | Smiling face with sunglasses | `1f60e` |

Rules:

- These files are only for internal testing and demo validation.
- Do not ship these assets in a formal public release until authorization and attribution are reviewed.
- Before formal release, either complete AnimatEmojis / Google Animated Noto Emoji authorization and attribution, or replace this directory with Xiaowei-owned emotion assets.
- Keep file names aligned with the server emotion enum so the demo code can use a direct `emotion/{name}.json` mapping.
