package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainRegion
import dev.neurofocus.neurfocus_dnd.brain.domain.EegBand
import dev.neurofocus.neurfocus_dnd.brain.domain.ElectrodeSite

/**
 * Pure, stateless geometry for the brain visualization.
 *
 * Coordinate convention: front of head = top of canvas (y = 0).
 * All positions are normalized 0..1 internally and scaled to pixels by the
 * supplied [Size] — no Compose state, so geometry can be unit tested.
 */
internal object BrainGeometry {

    /**
     * Top-down brain silhouette built from cubic Beziers.
     * Slightly wider at the back (parietal/occipital) than the front (frontal),
     * matching real cerebral proportions seen from above.
     */
    fun outlinePath(size: Size): Path = Path().apply {
        val w = size.width
        val h = size.height

        moveTo(w * 0.50f, h * 0.03f)
        cubicTo(w * 0.78f, h * 0.04f, w * 0.95f, h * 0.18f, w * 0.98f, h * 0.40f)
        cubicTo(w * 1.00f, h * 0.62f, w * 0.92f, h * 0.85f, w * 0.72f, h * 0.96f)
        cubicTo(w * 0.62f, h * 0.99f, w * 0.55f, h * 0.99f, w * 0.50f, h * 0.98f)
        cubicTo(w * 0.45f, h * 0.99f, w * 0.38f, h * 0.99f, w * 0.28f, h * 0.96f)
        cubicTo(w * 0.08f, h * 0.85f, w * 0.00f, h * 0.62f, w * 0.02f, h * 0.40f)
        cubicTo(w * 0.05f, h * 0.18f, w * 0.22f, h * 0.04f, w * 0.50f, h * 0.03f)
        close()
    }

    /**
     * Longitudinal fissure (midline groove) — a soft S-curve drawn on top of
     * region washes so the brain reads as two hemispheres.
     */
    fun centralFissurePath(size: Size): Path = Path().apply {
        val w = size.width
        val h = size.height

        moveTo(w * 0.50f, h * 0.07f)
        cubicTo(
            w * 0.515f, h * 0.30f,
            w * 0.485f, h * 0.65f,
            w * 0.50f, h * 0.94f,
        )
    }

    /**
     * Center point of each lobe — origin of its radial-gradient activity glow.
     * Positions are an anatomical approximation; tuned for visual balance.
     */
    fun regionCenter(region: BrainRegion, size: Size): Offset {
        val (nx, ny) = when (region) {
            BrainRegion.FrontalL  -> 0.30f to 0.18f
            BrainRegion.FrontalR  -> 0.70f to 0.18f
            BrainRegion.ParietalL -> 0.32f to 0.48f
            BrainRegion.ParietalR -> 0.68f to 0.48f
            BrainRegion.TemporalL -> 0.10f to 0.55f
            BrainRegion.TemporalR -> 0.90f to 0.55f
            BrainRegion.OccipitalL -> 0.32f to 0.82f
            BrainRegion.OccipitalR -> 0.68f to 0.82f
            BrainRegion.Whole -> 0.50f to 0.50f
        }
        return Offset(nx * size.width, ny * size.height)
    }

    /** Glow radius for the radial-gradient region wash. Uniform per region. */
    fun regionRadius(size: Size): Float = size.minDimension * REGION_RADIUS_RATIO

    /** Pixel center of an electrode site (10-20 system, top-down). */
    fun electrodeCenter(site: ElectrodeSite, size: Size): Offset {
        val (nx, ny) = when (site) {
            ElectrodeSite.Fp1 -> 0.36f to 0.10f
            ElectrodeSite.Fp2 -> 0.64f to 0.10f
            ElectrodeSite.Cz  -> 0.50f to 0.50f
            ElectrodeSite.O1  -> 0.36f to 0.92f
            ElectrodeSite.O2  -> 0.64f to 0.92f
        }
        return Offset(nx * size.width, ny * size.height)
    }

    /**
     * Map per-band powers (0..1) to per-region activations (0..1) using
     * a clinical heuristic for which scalp lobes dominate which bands.
     *
     *   Delta:  background hum across all regions
     *   Theta:  temporal lobes
     *   Alpha:  occipital lobes (eyes-closed alpha signature)
     *   Beta:   frontal lobes (active thinking)
     *   Gamma:  frontal + parietal (cognitive binding)
     *
     * Demo heuristic — not a clinical metric.
     */
    fun bandPowersToRegionActivations(
        bandPowers: Map<EegBand, Float>,
    ): Map<BrainRegion, Float> {
        val delta = bandPowers[EegBand.Delta] ?: 0f
        val theta = bandPowers[EegBand.Theta] ?: 0f
        val alpha = bandPowers[EegBand.Alpha] ?: 0f
        val beta = bandPowers[EegBand.Beta] ?: 0f
        val gamma = bandPowers[EegBand.Gamma] ?: 0f

        val ambient = delta * DELTA_AMBIENT_WEIGHT

        val frontal = (ambient + beta * 0.7f + gamma * 0.5f).coerceIn(0f, 1f)
        val parietal = (ambient + gamma * 0.6f).coerceIn(0f, 1f)
        val temporal = (ambient + theta * 0.8f).coerceIn(0f, 1f)
        val occipital = (ambient + alpha * 0.8f).coerceIn(0f, 1f)

        return mapOf(
            BrainRegion.FrontalL to frontal,
            BrainRegion.FrontalR to frontal,
            BrainRegion.ParietalL to parietal,
            BrainRegion.ParietalR to parietal,
            BrainRegion.TemporalL to temporal,
            BrainRegion.TemporalR to temporal,
            BrainRegion.OccipitalL to occipital,
            BrainRegion.OccipitalR to occipital,
            BrainRegion.Whole to ambient.coerceIn(0f, 1f),
        )
    }

    private const val REGION_RADIUS_RATIO = 0.30f
    private const val DELTA_AMBIENT_WEIGHT = 0.20f
}
