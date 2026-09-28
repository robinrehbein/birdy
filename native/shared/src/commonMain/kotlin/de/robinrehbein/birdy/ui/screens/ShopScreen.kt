package de.robinrehbein.birdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import de.robinrehbein.birdy.game.Menu
import de.robinrehbein.birdy.ui.MenuLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.birdy.game.ShopTab
import de.robinrehbein.birdy.game.ShopUi
import de.robinrehbein.birdy.game.ThumbnailUi
import de.robinrehbein.birdy.game.UiCommand
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.meta.SkinItem
import de.robinrehbein.birdy.meta.Strings
import de.robinrehbein.birdy.meta.TrailItem
import de.robinrehbein.birdy.ui.BirdyColors
import de.robinrehbein.birdy.ui.ButtonStyle
import de.robinrehbein.birdy.ui.GameButton
import de.robinrehbein.birdy.ui.GameIcon
import de.robinrehbein.birdy.ui.OutlinedText
import de.robinrehbein.birdy.ui.Panel
import de.robinrehbein.birdy.ui.RichText
import de.robinrehbein.birdy.ui.ShopTile

/** Shop screen (`#shop`, main-b.md §7): tabs, item grid, description, buy/select, dice, coin packs. */
@Composable
fun ShopScreen(
    ui: ShopUi,
    strings: Strings,
    onCommand: (UiCommand) -> Unit,
    modifier: Modifier = Modifier,
    thumbnails: Map<String, ThumbnailUi> = emptyMap(),
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // `#shop .panel { max-height: min(66vh, 580px) }`, `.skins { max-height: min(26vh, 190px) }`.
        val panelMax = minOf(maxHeight * 0.66f, 580.dp)
        val gridMax = minOf(maxHeight * 0.26f, 190.dp)
        MenuLayout(
            menu = Menu.Shop,
            onCommand = onCommand,
            title = { de.robinrehbein.birdy.ui.Heading(ui.name) },
        ) { frame ->
        Panel(frame.heightIn(max = panelMax).verticalScroll(rememberScrollState())) {
            ShopTabs(ui.tab, ui.diceEnabled, strings, onCommand)

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).heightIn(max = gridMax).fadeBottom(ui.tiles.size > 15),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(ui.tiles, key = { it.id }) { tile ->
                    val item = ui.tab.kind?.let { Catalog.find(it, tile.id) }
                    val flatColor = (item as? SkinItem)?.body
                    val trailColors = (item as? TrailItem)?.colors ?: emptyList()
                    val thumb = tile.thumbKey?.let { thumbnails[it] }
                    ShopTile(
                        tile = tile,
                        tab = ui.tab,
                        flatColor = flatColor,
                        trailColors = trailColors,
                        thumbnail = thumb,
                        item = item,
                        onClick = {
                            if (ui.tab == ShopTab.Upgrade) onCommand(UiCommand.ShopItem(Kind.Skin, tile.id))
                            else ui.tab.kind?.let { onCommand(UiCommand.ShopItem(it, tile.id)) }
                        },
                    )
                }
            }

            if (ui.desc.isNotEmpty()) {
                RichText(ui.desc, Modifier.fillMaxWidth().padding(bottom = 10.dp), size = 13.sp, center = true)
            }

            if (ui.surprise != null) {
                GameButton(
                    ui.surprise.label,
                    { onCommand(UiCommand.Surprise(ui.tab.kind ?: Kind.Skin)) },
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    ButtonStyle.Surprise,
                    enabled = ui.surprise.enabled,
                )
            }

            if (ui.coinPacks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (pack in ui.coinPacks) {
                        GameButton(
                            pack.label,
                            { onCommand(UiCommand.BuyReal(pack.productId)) },
                            Modifier.weight(1f),
                            ButtonStyle.CoinPack,
                            enabled = pack.enabled && !ui.billingBusy,
                        )
                    }
                }
            }

            if (ui.rewardAd != null) {
                GameButton(
                    ui.rewardAd.label,
                    { onCommand(UiCommand.RequestRewardedAd(de.robinrehbein.birdy.platform.RewardKind.Coins)) },
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ButtonStyle.Purple,
                    enabled = ui.rewardAd.enabled,
                )
            }
            if (ui.stylePass != null) {
                GameButton(
                    ui.stylePass.label,
                    { onCommand(UiCommand.RequestRewardedAd(de.robinrehbein.birdy.platform.RewardKind.Pass)) },
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ButtonStyle.Purple,
                    enabled = ui.stylePass.enabled,
                )
            }

            GameButton(
                ui.action.label,
                { onCommand(UiCommand.ShopAction) },
                Modifier.fillMaxWidth(),
                if (ui.actionIsBuy) ButtonStyle.Gold else ButtonStyle.Primary,
                enabled = ui.action.enabled,
            )

            if (ui.realBuy != null) {
                GameButton(
                    ui.realBuy.label,
                    { ui.realBuyProductId?.let { onCommand(UiCommand.BuyReal(it)) } },
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    ButtonStyle.RealMoney,
                    enabled = ui.realBuy.enabled && !ui.billingBusy,
                )
            }
            if (ui.adPrivacy) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                    de.robinrehbein.birdy.ui.TextLink(strings.t("adPrivacy"), { onCommand(UiCommand.AdPrivacy) })
                }
            }

            GameButton(strings.t("back"), { onCommand(UiCommand.OpenShop(false)) }, Modifier.fillMaxWidth().padding(top = 10.dp), ButtonStyle.Secondary)
        }
        }
    }
}

/**
 * `#shop-tabs`: a 5-column grid of 3D tab buttons (icon over a small Fredoka label), the active
 * one orange, the trailing dice tab purple (`#shop-dice`, 45 % when disabled).
 */
@Composable
private fun ShopTabs(active: ShopTab, diceEnabled: Boolean, strings: Strings, onCommand: (UiCommand) -> Unit) {
    val cells: List<@Composable (Modifier) -> Unit> = ShopTab.entries.map { tab ->
        @Composable { m: Modifier ->
            TabButton(tab.icon, strings.t("tab_${tab.id}"), if (tab == active) BirdyColors.Orange else BirdyColors.PanelInset, true, m) {
                onCommand(UiCommand.SelectShopTab(tab))
            }
        }
    } + listOf(
        @Composable { m: Modifier ->
            TabButton("dice", strings.t("tab_dice"), BirdyColors.Purple, diceEnabled, m) { onCommand(UiCommand.RandomizeOutfit) }
        },
    )
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (row in cells.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (cell in row) cell(Modifier.weight(1f))
                repeat(5 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TabButton(icon: String, label: String, bg: androidx.compose.ui.graphics.Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        propagateMinConstraints = true,
    ) {
        Box(Modifier.matchParentSize().offset(y = 3.dp).background(BirdyColors.Ink, shape))
        Column(
            Modifier
                .background(bg, shape)
                .border(4.dp, BirdyColors.Ink, shape)
                .padding(top = 6.dp, bottom = 5.dp, start = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            GameIcon(icon, size = 20.dp)
            OutlinedText(label, size = 10.sp, thickness = 0.dp, down = 1.dp, font = de.robinrehbein.birdy.ui.bodyFont())
        }
    }
}

/** `.skins.more`: `mask-image: linear-gradient(#000 78%, transparent)` while the grid scrolls. */
private fun Modifier.fadeBottom(on: Boolean): Modifier = if (!on) this else this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(0.78f to androidx.compose.ui.graphics.Color.Black, 1f to androidx.compose.ui.graphics.Color.Transparent),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
