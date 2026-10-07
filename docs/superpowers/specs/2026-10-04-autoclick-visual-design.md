# Autoclick Visual Design

User approved the deep teal direction on 2026-10-04.

## Scope
Unify the existing home screen, recording overlay, schedule panel and accessibility prompt. Preserve task, recording and scheduling behavior. Use a light theme with dark status-bar icons, no dynamic colors, and the Android system font for readable Chinese.

## Tokens
Primary #126B60; pressed #0D5148; canvas #F3F6F5; panel #FFFFFF; soft accent #E4F2EE; text #1C302C; secondary text #586C65; border #D6E1DC; error #A33D36. Spacing follows 4/8/12/16/24 dp. Buttons have a minimum 48 dp height and 14 dp corners; panels use 24 dp corners.

## Surfaces
Home separates the recording draft from the saved schedule, with a prominent recording action and understated task controls. The schedule panel uses the same colors, Chinese weekday labels, and paired save/cancel actions. The floating control uses a teal disc, a white crosshair and readable click count. Preserve the current hit target and coordinate calculation. Accessibility permission prompts use local resource overrides so other applications keep their existing dialog.

## Validation
Build and lint autoclick. Re-run existing lifecycle/gesture integration tests. Inspect actual screenshots of the empty home, saved task, permission prompt, floating control and schedule panel, including landscape and larger font settings. No functional expansion or additional permissions.
