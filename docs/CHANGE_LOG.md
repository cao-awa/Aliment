[English Version](CHANGE_LOG.md) | [中文版本](CHANGE_LOG_zh.md)

# Changelog

All notable changes to the **Aliment** mod will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Changed
- **The CYP3A4 index now slides down instead of snapping.** Naringin was a *step function* on the enzyme, so the index only ever held one of five values and the whole inhibition arrived the instant a threshold was crossed. It is now a **curve**: the activity is exactly the calibrated value at the naringin it was calibrated for - **85** in a body that has eaten nothing, **60** at two slices, **45** at four, **25** at seven and **10** at eight and a half - and it interpolates between them, so one more slice always takes a little more off the liver rather than everything changing at once. Every calibrated value and every one of its naringin knots is unchanged; what is gone is the flat plateau between them. Each segment is a smoothstep, `3t^2 - 2t^3`, whose value and slope are both zero at either end, so the curve is flat *at* each knot and has no kink where two segments meet either. Past eight and a half slices it holds, which is still what makes that the deepest the inhibition goes.
  - The self test asserts the property rather than the table, because a table of five values cannot tell a curve from a staircase: it sweeps the whole naringin range in steps of a hundredth of a slice and requires the largest move the index makes to stay inside the steepest segment's own slope. The step function fails that outright, moving **25 points** in a single step. Alongside it, the curve is pinned to its six calibration points - including the flat run from eight and a half to the cap - and swept for any rise and any escape from the reported range.
- **Grapefruit consequently holds berberine down for longer.** The plateaus the index used to sit on are gone, so the liver runs lower through the middle of a dose: a single coptis herb still clears in about **9,400 ticks** on its own, but after nine slices of grapefruit it now takes about **20,100** rather than the 17,800 the step function gave - a little over twice as long instead of a little under. The shape of the interaction is unchanged, and so is every number it was calibrated from; only the descent between them is now a slope.
