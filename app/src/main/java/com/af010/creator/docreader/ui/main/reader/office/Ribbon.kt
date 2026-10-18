package com.af010.creator.docreader.ui.main.reader.office

import androidx.annotation.ColorInt
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemRibbonBinding
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.editor.docsdk.DocumentEditor
import com.editor.docsdk.DocumentType
import com.editor.docsdk.EditAction
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.visibleOrGone

/** Things the ribbon offers that are screens of the app, not commands of the SDK. */
enum class RibbonExtra { Format, Table, Camera, Slideshow }

/** How a ribbon button looks: a square icon, a text with a chevron, or an icon over its name. */
enum class RibbonLook { Icon, Chip, Tile }

/**
 * A button of the ribbon: an SDK command ([action]) or one of the app's own ([extra]). [icon]
 * and [label] default to the SDK's for the command.
 */
data class RibbonItem(
    val look: RibbonLook,
    val action: EditAction? = null,
    val extra: RibbonExtra? = null,
    @DrawableRes val icon: Int = action?.icon ?: 0,
    @StringRes val label: Int = action?.label ?: 0,
    val danger: Boolean = false
)

data class RibbonTab(@StringRes val title: Int, val items: List<RibbonItem>)

/**
 * Runs an SDK command; false when it is not available or the SDK failed on it (some commands of
 * SDK 1.0.0 throw), so a tap on the ribbon never takes the screen down.
 */
fun DocumentEditor.runSafely(action: EditAction): Boolean =
    runCatching { run(action) }.getOrDefault(false)

fun DocumentEditor.runSafely(action: EditAction, value: Any?): Boolean =
    runCatching { run(action, value) }.getOrDefault(false)

/** The colour of a format's ribbon and selection. */
val DocumentType.accentColor: Int
    @ColorRes get() = when (this) {
        DocumentType.EXCEL -> R.color.color_excel
        DocumentType.POWERPOINT -> R.color.color_slides
        else -> R.color.color_word
    }

/** The ribbon of each format; only the commands the open document has are kept. */
object Ribbons {
    fun of(editor: DocumentEditor): List<RibbonTab> {
        val tabs = when (editor.documentType) {
            DocumentType.EXCEL -> excel()
            DocumentType.POWERPOINT -> slides()
            else -> word()
        }
        return tabs
            .map { tab -> tab.copy(items = tab.items.filter { it.action == null || editor.isAvailable(it.action) }) }
            .filter { it.items.isNotEmpty() }
    }

    private fun icon(action: EditAction, danger: Boolean = false) = RibbonItem(RibbonLook.Icon, action, danger = danger)
    private fun icons(vararg actions: EditAction) = actions.map { icon(it) }
    private fun chip(action: EditAction, @StringRes label: Int) = RibbonItem(RibbonLook.Chip, action, label = label)
    private fun tile(action: EditAction) = RibbonItem(RibbonLook.Tile, action)

    /** Cut, copy and paste lead every Home tab, where they are found first. */
    private fun clipboard() = icons(EditAction.CUT, EditAction.COPY, EditAction.PASTE)
    private fun tile(extra: RibbonExtra, @DrawableRes icon: Int, @StringRes label: Int) =
        RibbonItem(RibbonLook.Tile, extra = extra, icon = icon, label = label)

    private fun word() = listOf(
        RibbonTab(
            R.string.ribbon_home,
            clipboard() +
                    listOf(chip(EditAction.FONT, R.string.ribbon_font), chip(EditAction.FONT_SIZE, R.string.ribbon_size)) +
                    icons(EditAction.BOLD, EditAction.ITALIC, EditAction.UNDERLINE) +
                    RibbonItem(RibbonLook.Icon, extra = RibbonExtra.Format, icon = R.drawable.ic_app_palette, label = R.string.ribbon_format) +
                    icons(EditAction.TEXT_COLOR, EditAction.HIGHLIGHT, EditAction.BULLETS, EditAction.NUMBERING)
        ),
        RibbonTab(
            R.string.ribbon_insert,
            listOf(
                RibbonItem(RibbonLook.Tile, EditAction.INSERT_TABLE, RibbonExtra.Table, label = R.string.ribbon_table),
                tile(EditAction.INSERT_PICTURE),
                RibbonItem(RibbonLook.Tile, EditAction.INSERT_PICTURE, RibbonExtra.Camera, R.drawable.ic_fo_camera, R.string.ribbon_camera),
                tile(EditAction.INSERT_TEXT),
                tile(EditAction.NEW_LINE)
            )
        ),
        RibbonTab(
            R.string.ribbon_layout,
            icons(
                EditAction.ALIGN_LEFT, EditAction.ALIGN_CENTER, EditAction.ALIGN_RIGHT, EditAction.ALIGN_JUSTIFY,
                EditAction.INDENT_LESS, EditAction.INDENT_MORE, EditAction.LINE_SPACING, EditAction.PARAGRAPH
            )
        ),
        RibbonTab(
            R.string.ribbon_review,
            icons(
                EditAction.FIND_REPLACE, EditAction.SELECT_WORD, EditAction.SELECT_PARAGRAPH, EditAction.SELECT_ALL
            ) + icon(EditAction.DELETE, danger = true)
        ),
        RibbonTab(
            R.string.ribbon_table,
            icons(
                EditAction.SELECT_TABLE, EditAction.ROW_ABOVE, EditAction.ROW_BELOW,
                EditAction.COLUMN_LEFT, EditAction.COLUMN_RIGHT
            ) +
                    icon(EditAction.DELETE_ROW, danger = true) + icon(EditAction.DELETE_COLUMN, danger = true)
        )
    )

    private fun excel() = listOf(
        RibbonTab(
            R.string.ribbon_home,
            clipboard() +
                    icons(EditAction.BOLD, EditAction.ITALIC, EditAction.UNDERLINE, EditAction.FILL_COLOR, EditAction.TEXT_COLOR) +
                    RibbonItem(RibbonLook.Icon, extra = RibbonExtra.Format, icon = R.drawable.ic_app_palette, label = R.string.cell_format) +
                    icons(
                        EditAction.ALIGN_LEFT, EditAction.ALIGN_CENTER, EditAction.ALIGN_RIGHT,
                        EditAction.MERGE_CELLS, EditAction.FONT_SIZE
                    )
        ),
        RibbonTab(
            R.string.ribbon_insert,
            listOf(
                tile(EditAction.INSERT_PICTURE), tile(EditAction.ROW_ABOVE), tile(EditAction.ROW_BELOW),
                tile(EditAction.COLUMN_LEFT), tile(EditAction.COLUMN_RIGHT)
            )
        ),
        RibbonTab(
            R.string.ribbon_cells,
            icons(
                EditAction.MERGE_CELLS, EditAction.ROW_HEIGHT, EditAction.COLUMN_WIDTH, EditAction.WRAP_TEXT,
                EditAction.BORDERS, EditAction.TEXT_ROTATION, EditAction.CELL_ALIGNMENT
            ) + icon(EditAction.DELETE_ROW, danger = true) + icon(EditAction.DELETE_COLUMN, danger = true) +
                    icon(EditAction.CLEAR_CELLS, danger = true)
        ),
        RibbonTab(R.string.ribbon_sheet, listOf(tile(EditAction.ADD_SHEET), tile(EditAction.GO_TO_CELL)))
    )

    private fun slides() = listOf(
        RibbonTab(
            R.string.ribbon_home,
            clipboard() + chip(EditAction.FONT_SIZE, R.string.ribbon_size) +
                    icons(
                        EditAction.BOLD, EditAction.ITALIC, EditAction.UNDERLINE, EditAction.TEXT_COLOR,
                        EditAction.ALIGN_LEFT, EditAction.ALIGN_CENTER, EditAction.ALIGN_RIGHT,
                        EditAction.SET_TEXT, EditAction.ROTATE, EditAction.SHAPE_LIST, EditAction.DUPLICATE_SLIDE
                    ) + icon(EditAction.DELETE, danger = true)
        ),
        RibbonTab(
            R.string.ribbon_insert,
            listOf(
                tile(EditAction.ADD_TEXT_BOX), tile(EditAction.INSERT_PICTURE), tile(EditAction.ADD_SHAPE),
                tile(EditAction.ADD_SLIDE), tile(EditAction.DUPLICATE_SLIDE)
            )
        ),
        RibbonTab(R.string.ribbon_animations, listOf(tile(EditAction.ANIMATIONS), tile(EditAction.TRANSITION))),
        RibbonTab(
            R.string.slideshow,
            listOf(
                tile(RibbonExtra.Slideshow, R.drawable.ic_fo_play, R.string.slideshow),
                tile(EditAction.EXPORT_PNG), tile(EditAction.EXPORT_PDF)
            )
        )
    )
}

class RibbonAdapter : BaseAdapter<RibbonItem, ItemRibbonBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemRibbonBinding.inflate(inflater, parent, false)

    private var onItemClick: ((RibbonItem) -> Unit) = {}
    fun onItemClick(listener: (RibbonItem) -> Unit) = apply {
        onItemClick = listener
    }

    /** Which commands are on at the caret or selection (bold…). */
    var isActive: (EditAction) -> Boolean = { false }

    /** Commands that cannot run now (paste with nothing copied…) are dimmed. */
    var isEnabled: (EditAction) -> Boolean = { true }

    /** What a chip shows instead of its name, once known: the font or size picked. */
    var chipText: (EditAction) -> String? = { null }

    /** The file type's colour and its pale tint: a command that is on shows in them. */
    @ColorInt var accent: Int = Color.BLUE
    @ColorInt var accentSoft: Int = Color.LTGRAY

    override fun ItemRibbonBinding.bind(item: RibbonItem, position: Int) {
        val context = root.context
        val res = context.resources
        val active = item.extra == null && item.action?.let(isActive) == true
        val tile = item.look == RibbonLook.Tile
        val chip = item.look == RibbonLook.Chip

        this.item.orientation = if (tile) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        this.item.updateLayoutParams<ViewGroup.LayoutParams> {
            height = res.getDimensionPixelSize(if (tile) com.intuit.sdp.R.dimen._40sdp else com.intuit.sdp.R.dimen._28sdp)
            width = when {
                tile || chip -> ViewGroup.LayoutParams.WRAP_CONTENT
                else -> res.getDimensionPixelSize(com.intuit.sdp.R.dimen._28sdp)
            }
        }
        // A tile is as wide as its label (no less than a square), so "Insert row above" reads whole.
        val side = when {
            chip -> res.getDimensionPixelSize(com.intuit.sdp.R.dimen._9sdp)
            tile -> res.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
            else -> 0
        }
        this.item.minimumWidth = res.getDimensionPixelSize(if (tile) com.intuit.sdp.R.dimen._40sdp else com.intuit.sdp.R.dimen._28sdp)
        this.item.updatePadding(left = side, right = side)
        // Flat commands on the docked bar; only a chip (a value to pick) keeps its box.
        this.item.setFillColor(when {
            active -> accentSoft
            chip -> ContextCompat.getColor(context, R.color.color_surface_variant)
            else -> Color.TRANSPARENT
        })
        this.item.invalidate()

        val tint = when {
            item.danger -> ContextCompat.getColor(context, R.color.color_error)
            active -> accent
            else -> ContextCompat.getColor(context, R.color.text_primary)
        }
        ivIcon.visibleOrGone(!chip && item.icon != 0)
        if (item.icon != 0) ivIcon.setImageResource(item.icon)
        ImageViewCompat.setImageTintList(ivIcon, ColorStateList.valueOf(tint))
        ivIcon.contentDescription = if (item.label != 0) context.getString(item.label) else null

        tvLabel.visibleOrGone(tile || chip)
        if (item.label != 0) tvLabel.text = item.action?.takeIf { chip }?.let(chipText) ?: context.getString(item.label)
        tvLabel.setTextColor(tint)
        tvLabel.textSize = if (tile) TILE_TEXT_SP else CHIP_TEXT_SP
        tvLabel.maxWidth = res.getDimensionPixelSize(
            if (tile) com.intuit.sdp.R.dimen._90sdp else com.intuit.sdp.R.dimen._77sdp
        )
        ivChevron.visibleOrGone(chip)

        root.alpha = if (item.action?.let(isEnabled) == false) DISABLED_ALPHA else 1f
        root.setOnClickListener { onItemClick.invoke(item) }
    }

    private companion object {
        const val TILE_TEXT_SP = 10f
        const val CHIP_TEXT_SP = 11.5f
        const val DISABLED_ALPHA = 0.4f
    }
}
