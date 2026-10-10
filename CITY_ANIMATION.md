# City expedition marker animation

The fix applies to the ordinary city map, including both loot expeditions and
reconnaissance. STORY 1.3 and its road routing remain unchanged.

## Diagnosis

`GameView.tick` executes once per real second. It advances the existing simulation
by 1, 2 or 4 whole game minutes. `Expedition.routeProgress()` divides the integer
elapsed minutes by the phase duration; it does not provide fractional minutes.
The percentage in the status label is rounded, but that rounded value is **not**
the marker's input.

The previous renderer applied `position += (logicalPosition - position) * 0.2`
on every draw. Consequently it rapidly approached a new sample, then almost
stopped until the next one-second simulation tick. Its response depended on FPS,
and each jump was four times larger at ×4. It also reset position on every phase
change. The existing route already used segment lengths correctly; changing the
road graph was unnecessary.

## Presentation and simulation

`ExpeditionMarkerAnimation` uses the injected monotonic `System.nanoTime` clock.
For each changed logical sample it finishes any previous tween at the current
frame time, then interpolates from the displayed position to the new confirmed
target over one real second:

```
t = clamp((frameNanos - startedNanos) / 1_000_000_000, 0, 1)
position = from + (target - from) * t
```

This duration matches the existing sampling cadence; it is not a second game
timer. It never advances expedition time, changes phase, or issues rewards. It
never predicts beyond the authoritative sample, remains on the route, and reaches
the target exactly in finite time. Under regular ticks, visual lag is bounded by
one tick: up to 1/2/4 game minutes at ×1/×2/×4. At ×4 the path is traversed faster
using the same real-time interpolation, rather than a larger per-frame jump.

Arrival no longer resets the tween abruptly. Return travel reverses normalized
arc-length progress and begins at the destination. Completed expeditions are
removed immediately by the existing active-expedition filter, including their
route and marker; completion and rewards remain authoritative simulation actions.

Pausing, a blocking game event, or game-over freezes the displayed point. Resume
discards the elapsed paused time. Leaving the city tab, losing window visibility
or focus clears the transient tween. Reopening/restoring anchors the marker to the
current saved logical progress without replaying an unseen journey.

## Route geometry and performance

`CityRouteGeometry` caches cumulative segment lengths once per existing route and
layout. It binary-searches the distance along that polyline into a reusable output
buffer. Renderer and touch hit testing use the same cached geometry and displayed
progress. No XY blending is applied across corners, so the marker does not cut
across buildings. The existing rounded road segments are preserved. The marker
is a circular letter icon and has no orientation to smooth.

The road planner, road coordinates, obstacles, location coordinates, durations,
loot, story flags, faction relationships, and `save_v02` keys are untouched. Visual
state is intentionally not saved. Android Paths remain cached and the new sampler
creates no per-frame point arrays. Frame invalidation is requested only while a
visible, focused city marker has remaining interpolation; the normal one-second
game tick supplies subsequent logical samples. The online module is unchanged.

## Verification

Run `python3 tools/city_animation_regression.py` with the repository's configured
JDK. It exercises actual Java classes and Canvas/controller code using Android
doubles and a controlled monotonic clock. Schedules cover 15/24/30/60/120 FPS,
×1/×2/×4, speed switches, outbound/return limits, bounded lag, exact endpoints,
unequal/zero/short segments, no corner cutting, repeated draws, pause/resume,
tab/focus/background/process restore, cache identity, shared touch coordinates,
route removal, and unchanged persisted fields/rewards.

The existing expedition, loot-return and exploration-polish tests now advance an injected
frame clock before expecting visual movement; repeated draws alone cannot advance
the animation. All existing story, online, economy, defense, grounding and route
profiles must also pass. Route regression covers all 25 targets at three portrait
heights and loads a genuine older active expedition save.

These tests do not measure Android frame rates. Physical-device verification is
still required for the visual result, particularly turning at ×4, pausing mid-turn,
and returning from the radio network or app background.

Verified for this change: all 29 functional regression profiles passed, including
329,061 new animation assertions, 6,265,026 existing road-routing assertions,
STORY 1.0–1.3 and ONLINE 0.1–0.5. The older loot-return test was updated to supply
elapsed frame time and passed on rerun (844 assertions). `assembleDebug lintDebug`
completed successfully with 0 lint errors and the existing 24 warnings. APK
identity remained `com.lastdom.game`; CI supplies the next versionCode through
its unchanged workflow and permanent signing configuration.
