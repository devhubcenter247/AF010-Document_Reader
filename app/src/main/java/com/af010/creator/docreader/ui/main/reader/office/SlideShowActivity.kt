package com.af010.creator.docreader.ui.main.reader.office

import android.content.Intent
import android.content.res.ColorStateList
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivitySlideShowBinding
import com.editor.docsdk.SlideShowView
import com.ui.baselib.extensions.click

/**
 * The slideshow of the presentation open in [OfficeActivity], in landscape. The SDK's
 * [SlideShowView] plays it (transitions, animations, links, pen and eraser); this screen is the
 * bar over it. The slides are read from the document view of [OfficeActivity], which stays open
 * underneath.
 */
class SlideShowActivity : BaseAppActivity<ActivitySlideShowBinding>(ActivitySlideShowBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private val show: SlideShowView get() = binding.showView

    override fun backPressed() {
        leave()
    }

    override fun initialize() {
    }

    override fun ActivitySlideShowBinding.setData() {
        // Gone after the process was killed: the slides cannot be read without the document.
        val source = source ?: return finish()
        showView.inkColor = ContextCompat.getColor(this@SlideShowActivity, R.color.color_error)
        showView.listener = object : SlideShowView.Listener {
            override fun onSlideChanged(slide: Int, count: Int) {
                tvShowPage.text = getString(R.string.page_count_short, slide + 1, count)
            }

            // Past the last slide there is nothing left to show.
            override fun onEnded(ended: Boolean) {
                if (ended) leave()
            }

            override fun onLongPress() {
                layoutShowControls.isVisible = !layoutShowControls.isVisible
            }

            override fun onSlideNotReady(slide: Int) {
                toast(R.string.slideshow_unavailable)
            }
        }
        renderInk()
        showView.start(source)
    }

    override fun ActivitySlideShowBinding.onClick() {
        btnShowExit.click { leave() }
        btnShowPrevious.click { showView.previous() }
        btnShowNext.click { showView.next() }
        btnShowPen.click { toggleInk(SlideShowView.Ink.PEN) }
        btnShowEraser.click { toggleInk(SlideShowView.Ink.ERASER) }
    }

    // Volume keys, arrows and presentation remotes move through the show.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        show.handleKey(event) || super.dispatchKeyEvent(event)

    /** While the pen or the eraser is on, a finger draws instead of moving to the next slide. */
    private fun toggleInk(ink: SlideShowView.Ink) {
        show.ink = if (show.ink == ink) SlideShowView.Ink.NONE else ink
        renderInk()
    }

    private fun renderInk() = with(binding) {
        btnShowPen.isSelected = show.ink == SlideShowView.Ink.PEN
        btnShowEraser.isSelected = show.ink == SlideShowView.Ink.ERASER
        // On the light blue of the picked tool the icon turns dark.
        listOf(btnShowPen, btnShowEraser).forEach {
            it.imageTintList = ColorStateList.valueOf(getColor(if (it.isSelected) R.color.ink_on_accent else R.color.white))
        }
    }

    /** Back to the document, at the slide the show was on. */
    private fun leave() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_SLIDE, show.currentSlide))
        finish()
    }

    override fun onDestroy() {
        if (isFinishing) source = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SLIDE = "extra_slide"

        /** What to play: it reads from an open document view, so it cannot go through an Intent. */
        var source: SlideShowView.Source? = null
    }
}
