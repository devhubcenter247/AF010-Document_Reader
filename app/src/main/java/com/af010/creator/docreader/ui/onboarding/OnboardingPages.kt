package com.af010.creator.docreader.ui.onboarding

import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.af010.creator.docreader.R

/**
 * What one onboarding slide says, and its full-screen picture. The pictures keep their
 * lower part plain because the texts and Next are drawn over it (see `store-assets/reference/PROMPTS.md`).
 */
enum class OnboardingSlide(
    @param:StringRes val kicker: Int,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    @param:DrawableRes val picture: Int
) {
    Open(R.string.fo_onboarding_kicker_1, R.string.fo_onboarding_title_1, R.string.fo_onboarding_body_1, R.drawable.img_onboarding_1),
    Sign(R.string.fo_onboarding_kicker_2, R.string.fo_onboarding_title_2, R.string.fo_onboarding_body_2, R.drawable.img_onboarding_2),
    Scan(R.string.fo_onboarding_kicker_3, R.string.fo_onboarding_title_3, R.string.fo_onboarding_body_3, R.drawable.img_onboarding_3),
    Tools(R.string.fo_onboarding_kicker_4, R.string.fo_onboarding_title_4, R.string.fo_onboarding_body_4, R.drawable.img_onboarding_4)
}

/** The pictures only: the text over the pager changes with the page. */
class OnboardingPictures : RecyclerView.Adapter<OnboardingPictures.Holder>() {

    class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)

    override fun getItemCount() = OnboardingSlide.entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Cut the sides on long screens; the pictures keep their subject in the middle.
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.image.setImageResource(OnboardingSlide.entries[position].picture)
}
