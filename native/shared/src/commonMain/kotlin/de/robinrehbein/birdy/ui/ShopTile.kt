package de.robinrehbein.birdy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.game.ShopTab
import de.robinrehbein.birdy.game.ShopTileUi
import de.robinrehbein.birdy.game.ThumbnailUi
import de.robinrehbein.birdy.meta.CatalogItem
import de.robinrehbein.birdy.meta.PipeItem
import de.robinrehbein.birdy.meta.WorldItem

/** Trail tiles: sky blue behind the colour dots (`tileBg`). */
private val TrailSky = Color(0xFF6FB8E6)
private val FlatDot = Color(0xFFFFF6D5)
private val ThumbBg = Color(0xFFBFE6F5)
private val PipOn = Color(0xFF7BE07B)

/** main.js `tileBg` trail dots: centre (x %, y %) of each dot, radius 11 % of the swatch. */
private val TRAIL_DOTS = listOf(25 to 30, 55 to 22, 75 to 50, 40 to 60, 62 to 78, 22 to 70)

/**
 * `tileBg()` (main-b.md §7.2): the flat/gradient background of a shop tile's dot behind its
 * icon/thumbnail. The trail dots themselves are drawn by [TrailDots]; [item] gives world and
 * pipe tiles their own colours.
 */
fun tileBrush(kind: ShopTab, flatColor: Int?, trailColors: List<Int>, item: CatalogItem? = null): Brush = when (kind) {
    ShopTab.Skin -> solid(flatColor?.toComposeColor() ?: BirdyColors.PanelInset)
    ShopTab.Trail -> solid(if (trailColors.isEmpty()) BirdyColors.PanelInset else TrailSky)
    ShopTab.World -> {
        val p = (item as? WorldItem)?.palette
        val top = p?.top?.toComposeColor() ?: Color(0xFF2A9BD0)
        val horizon = p?.horizon?.toComposeColor() ?: Color(0xFFA6E4EA)
        val grass = p?.grass?.toComposeColor() ?: Color(0xFF73BF2E)
        Brush.verticalGradient(0f to top, 0.55f to horizon, 0.56f to grass, 1f to grass)
    }
    ShopTab.Pipe -> {
        val c = (item as? PipeItem)?.colors
        val pipe = c?.pipe?.toComposeColor() ?: Color(0xFF73BF2E)
        val light = c?.light?.toComposeColor() ?: Color(0xFFB2EA6C)
        val dark = c?.dark?.toComposeColor() ?: Color(0xFF4F8A1F)
        Brush.horizontalGradient(
            0f to pipe, 0.2f to pipe, 0.2f to light, 0.36f to light, 0.36f to pipe,
            0.66f to pipe, 0.66f to dark, 0.8f to dark, 0.8f to pipe, 1f to pipe,
        )
    }
    else -> solid(FlatDot)
}

private fun solid(c: Color): Brush = Brush.linearGradient(listOf(c, c))

/** One shop grid tile (`.skin` button, reused for every kind and for upgrades, main-b.md §7). */
@Composable
fun ShopTile(
    tile: ShopTileUi,
    tab: ShopTab,
    flatColor: Int?,
    trailColors: List<Int>,
    thumbnail: ThumbnailUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    item: CatalogItem? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    // `.skin.sel { background: #fcb800; transform: translateY(-2px) }`.
    val bg = if (tile.selected) BirdyColors.Gold else BirdyColors.PanelInset
    Box(modifier.aspectRatio(1f).offset(y = if (tile.selected) (-2).dp else 0.dp).clickable(onClick = onClick)) {
        // `box-shadow: 0 3px 0 #543847`.
        Box(Modifier.matchParentSize().offset(y = 3.dp).background(BirdyColors.Ink, shape))
        BoxWithConstraints(Modifier.matchParentSize().background(bg, shape).border(3.dp, BirdyColors.Ink, shape)) {
        val tileWidth = maxWidth
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            val thumb = tile.thumbKey != null
            // `.skin .dot { width: 70% }`, `.dot.thumb { width: 86%; border-radius: 28% }`; the dot
            // shrinks (flex) so a price or pips below it still fit.
            val dotShape: Shape = if (thumb) RoundedCornerShape(28) else CircleShape
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = tileWidth * (if (thumb) 0.86f else 0.7f))
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .alpha(if (tile.locked) 0.75f else 1f)
                    .clip(dotShape)
                    .background(if (thumb) solid(ThumbBg) else tileBrush(tab, flatColor, trailColors, item))
                    .border(2.dp, BirdyColors.Ink, dotShape),
                contentAlignment = Alignment.Center,
            ) {
                if (tab == ShopTab.Trail && trailColors.isNotEmpty()) TrailDots(trailColors)
                if (thumbnail != null) ThumbnailImage(thumbnail, Modifier.fillMaxSize())
                when {
                    tile.closeIcon -> GameIcon("close", modifier = Modifier.fillMaxSize(0.7f))
                    tile.icon != null -> GameIcon(tile.icon, modifier = Modifier.fillMaxSize(0.7f))
                }
            }
            if (tile.locked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon("lock", size = 12.dp, style = IconStyle.Sticker)
                    OutlinedText(tile.price.toString(), Modifier.padding(start = 2.dp), size = 13.sp, thickness = 1.dp, down = 1.dp)
                }
            }
            if (tile.maxLevel > 0) UpgradePips(tile.level, tile.maxLevel)
        }
        }
        if (tile.equipped) {
            GameIcon("check", size = 17.dp, modifier = Modifier.offset(3.dp, 3.dp))
        }
        if (tile.rare) {
            Box(Modifier.align(Alignment.TopEnd)) {
                OutlinedText("★", size = 12.sp, color = BirdyColors.RareTag, thickness = 1.dp)
            }
        }
    }
}

/** `.skin.upgrade .pips`: 8px dots 3px apart; bought levels green with a 1px ink ring. */
@Composable
private fun UpgradePips(level: Int, max: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(max) { i ->
            val pip = Modifier.size(8.dp)
            if (i < level) {
                Box(pip.background(BirdyColors.Ink, CircleShape).padding(1.dp).background(PipOn, CircleShape))
            } else {
                Box(pip.background(BirdyColors.Ink.copy(alpha = 0.3f), CircleShape))
            }
        }
    }
}

/** Six dots in the trail's colours (radius 11 % of the swatch). */
@Composable
private fun TrailDots(colors: List<Int>) {
    Canvas(Modifier.fillMaxSize()) {
        val r = size.width * 0.11f
        TRAIL_DOTS.forEachIndexed { i, (x, y) ->
            drawCircle(colors[i % colors.size].toComposeColor(), r, Offset(size.width * x / 100f, size.height * y / 100f))
        }
    }
}

/**
 * Cached 3D thumbnail (main-b.md §7.1). Built from [ThumbnailUi.argb] with only common-Compose
 * APIs (per-pixel `Canvas.drawRect`, cached by [remember]).
 */
@Composable
fun ThumbnailImage(thumb: ThumbnailUi, modifier: Modifier = Modifier) {
    val bitmap = remember(thumb) { argbToImageBitmap(thumb.size, thumb.argb) }
    androidx.compose.foundation.Image(BitmapPainter(bitmap), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}

private fun argbToImageBitmap(size: Int, argb: IntArray): ImageBitmap {
    val bmp = ImageBitmap(size, size)
    val canvas = androidx.compose.ui.graphics.Canvas(bmp)
    val paint = androidx.compose.ui.graphics.Paint()
    for (y in 0 until size) {
        for (x in 0 until size) {
            paint.color = Color(argb[y * size + x])
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
        }
    }
    return bmp
}
