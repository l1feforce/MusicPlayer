package gusev.spbstu.org.musicplayer.ui.adapters

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager.widget.PagerAdapter
import gusev.spbstu.org.musicplayer.R


class CoverAdapter(val context: Context, val covers: List<Bitmap>) : PagerAdapter() {
    private val layoutInflater = LayoutInflater.from(context)!!

    override fun getCount(): Int {
        return covers.size
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view === obj as ConstraintLayout
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val itemView = layoutInflater.inflate(R.layout.view_pager_cover_item, container, false)

        val imageView = itemView.findViewById(R.id.img_cover) as ImageView
        imageView.setImageBitmap(covers[position])
        container.addView(itemView)

        return itemView
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as ConstraintLayout)
    }
}