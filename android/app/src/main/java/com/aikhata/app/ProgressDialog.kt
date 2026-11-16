package com.aikhata.app
import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
class ProgressDialog(activity:Activity) {
 private val dialog=Dialog(activity)
 init {
  val box=LinearLayout(activity).apply {
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(48,36,48,36)
   addView(ProgressBar(activity))
   addView(TextView(activity).apply{text="Reading your page with AI…";textSize=15f;setPadding(0,24,0,0)})
  }
  dialog.setContentView(box);dialog.setCancelable(false)
 }
 fun show(){dialog.show()}
 fun dismiss(){dialog.dismiss()}
}
