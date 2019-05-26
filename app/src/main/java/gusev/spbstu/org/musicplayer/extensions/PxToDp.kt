package gusev.spbstu.org.musicplayer.extensions

import android.content.res.Resources

fun Int.toDp() = (this/ Resources.getSystem().displayMetrics.density).toInt()