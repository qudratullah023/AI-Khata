package com.aikhata.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class MainActivity : Activity() {
 private lateinit var db:KhataDatabase
 private lateinit var root:LinearLayout
 private var unlocked=false
 private val green=Color.rgb(22,123,98)
 private val ink=Color.rgb(29,45,39)
 private val muted=Color.rgb(103,117,109)
 private val paper=Color.rgb(247,248,245)
 private val prefs by lazy{getSharedPreferences("ai_khata_secure",MODE_PRIVATE)}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);db=KhataDatabase(this);window.statusBarColor=paper;window.navigationBarColor=paper;window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;if(db.isSetUp())showUnlock()else showSetup()}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun money(n:Double)="Rs. "+String.format(Locale.US,"%,.2f",n)
 private fun today()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
 private fun text(s:String,size:Int=16,color:Int=ink,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size.toFloat();setTextColor(color);if(bold)setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,dp(5),0,dp(5))}
 private fun input(h:String,password:Boolean=false)=EditText(this).apply{hint=h;textSize=16f;setSingleLine(true);inputType=if(password)InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT;setPadding(dp(13),dp(8),dp(13),dp(8));background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(12).toFloat();setStroke(dp(1),Color.rgb(222,228,223))}}
 private fun button(label:String,action:()->Unit,primary:Boolean=false)=Button(this).apply{text=label;isAllCaps=false;textSize=15f;setTextColor(if(primary)Color.WHITE else green);background=android.graphics.drawable.GradientDrawable().apply{setColor(if(primary)green else Color.WHITE);cornerRadius=dp(13).toFloat();if(!primary)setStroke(dp(1),Color.rgb(211,226,218))};setOnClickListener{action()};layoutParams=LinearLayout.LayoutParams(-1,dp(52)).apply{topMargin=dp(9)}}
 private fun page(title:String,subtitle:String?=null){
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(paper);setPadding(dp(20),dp(18),dp(20),dp(20))}
  val scroll=ScrollView(this);scroll.addView(root);setContentView(scroll)
  root.addView(text(title,27,ink,true));if(!subtitle.isNullOrBlank())root.addView(text(subtitle,14,muted))
 }
 private fun card(title:String,value:String,detail:String=""){
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(15),dp(11),dp(15),dp(11));background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(15).toFloat()}}
  box.addView(text(title,13,muted));box.addView(text(value,22,ink,true));if(detail.isNotBlank())box.addView(text(detail,12,muted))
  root.addView(box,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
 }
 private fun gap(n:Int=12){root.addView(View(this),LinearLayout.LayoutParams(1,dp(n)))}
 private fun showSetup(){
  page("Welcome to AI Khata","Your shop ledger stays on this phone. No account or server required.")
  val shop=input("Shop name");val pin=input("Create a 6-digit PIN",true);val confirm=input("Confirm PIN",true)
  root.addView(shop);gap();root.addView(pin);gap();root.addView(confirm)
  root.addView(button("Create my private khata",{
   val p=pin.text.toString()
   if(shop.text.isNullOrBlank()||p.length<6||p!=confirm.text.toString())toast("Enter a shop name and matching PIN of at least 6 digits.")
   else{db.createOwner(shop.text.toString().trim(),p);unlocked=true;showHome()}
  },true))
  root.addView(text("Your PIN is stored as a salted password hash, not plain text.",13,muted))
 }
 private fun showUnlock(){
  unlocked=false;page("AI Khata","This shop is locked. Enter your PIN to open local records.")
  val pin=input("Shop PIN",true);root.addView(pin)
  root.addView(button("Unlock",{if(db.verifyPin(pin.text.toString())){unlocked=true;showHome()}else toast("Incorrect PIN.")},true))
 }
 private fun guard(){if(!unlocked)showUnlock()}
 private fun showHome(){
  guard();val t=db.totals();val month=db.monthTotals(today().take(7))
  page(db.shopName(),"Your private shop ledger · stored on this phone")
  card("Total outstanding",money(t.first-t.second),"Credit "+money(t.first)+" · Payments "+money(t.second))
  card("This month's activity","Credit "+money(month.first),"Payments "+money(month.second))
  card("Customers & records",db.customers().size.toString()+" customers",t.third.toString()+" transactions")
  gap()
  root.addView(button("Customers",{showCustomers()},true))
  root.addView(button("Add customer",{addCustomerDialog()}))
  root.addView(button("Monthly reports",{showReports()}))
  root.addView(button("Scan handwritten khata with AI",{chooseImage()}))
  root.addView(button("Settings & AI key",{showSettings()}))
  root.addView(button("Lock AI Khata",{unlocked=false;showUnlock()}))
  gap();root.addView(text("Recent customer activity",18,ink,true))
  db.customers().take(8).forEach{c->val tx=db.transactions(c.id).firstOrNull();if(tx!=null)root.addView(text(c.name+" · "+tx.type.uppercase(Locale.US)+" "+money(tx.amount),14,muted))}
 }
 private fun showCustomers(query:String=""){
  guard();page("Customers","Search customers and open their running balances.")
  val q=input("Search by name or phone").apply{setText(query)};root.addView(q)
  root.addView(button("Search",{showCustomers(q.text.toString())},true));root.addView(button("+ Add customer",{addCustomerDialog()}))
  db.customers(query).forEach{c->root.addView(button(c.name+" · "+money(c.balance)+"\n"+c.phone,{showCustomer(c.id)}))}
  root.addView(button("← Home",{showHome()}))
 }
 private fun addCustomerDialog(){
  guard();val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),0)}
  val name=input("Customer name");val phone=input("Phone (optional)");box.addView(name);box.addView(phone)
  val d=AlertDialog.Builder(this).setTitle("Add customer").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
  d.setOnShowListener{d.getButton(-1).setOnClickListener{if(name.text.isNullOrBlank())toast("Customer name is required.")else{val id=db.addCustomer(name.text.toString(),phone.text.toString());d.dismiss();showCustomer(id)}}};d.show()
 }
 private fun showCustomer(id:Long){
  guard();val c=db.customer(id)?:return showCustomers();val tx=db.transactions(id)
  val credit=tx.filter{it.type=="credit"}.sumOf{it.amount};val paid=tx.filter{it.type=="payment"}.sumOf{it.amount}
  page(c.name,c.phone);card("Outstanding balance",money(credit-paid),"Credits "+money(credit)+" · Payments "+money(paid))
  root.addView(button("+ Add credit / sale",{addTransactionDialog(id,"credit")},true))
  root.addView(button("Record payment",{addTransactionDialog(id,"payment")}))
  gap();root.addView(text("Transaction history",18,ink,true))
  if(tx.isEmpty())root.addView(text("No transactions yet.",14,muted))
  tx.forEach{row->root.addView(text(row.date+" · "+row.type.uppercase(Locale.US)+" · "+money(row.amount),15,ink,true));if(row.description.isNotBlank())root.addView(text(row.description,13,muted));root.addView(View(this).apply{setBackgroundColor(Color.rgb(225,230,226))},LinearLayout.LayoutParams(-1,dp(1)))}
  root.addView(button("← Customers",{showCustomers()}));root.addView(button("Home",{showHome()}))
 }
 private fun addTransactionDialog(id:Long,type:String){
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),0)}
  val amount=input("Amount in rupees").apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}
  val description=input("Description / item");val date=input("Date YYYY-MM-DD").apply{setText(today())}
  box.addView(amount);box.addView(description);box.addView(date)
  val d=AlertDialog.Builder(this).setTitle(if(type=="credit")"Add credit / sale" else "Record payment").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
  d.setOnShowListener{d.getButton(-1).setOnClickListener{
   val n=amount.text.toString().toDoubleOrNull();val day=date.text.toString()
   if(n==null||n<=0||!n.isFinite()||!Regex("\\d{4}-\\d{2}-\\d{2}").matches(day))toast("Enter a valid amount and date.")
   else{try{db.addTransaction(id,type,n,description.text.toString(),day);d.dismiss();showCustomer(id)}catch(e:Exception){toast("Could not save transaction.")}}
  }};d.show()
 }
 private fun showReports(){
  guard();val t=db.totals();val m=db.monthTotals(today().take(7))
  page("Reports","Summary calculated from records stored on this phone.")
  card("All-time credit",money(t.first));card("All-time payments",money(t.second));card("Current outstanding",money(t.first-t.second))
  card("This month · credit",money(m.first));card("This month · payments",money(m.second))
  root.addView(button("← Home",{showHome()}))
 }
 private fun showSettings(){
  guard();page("Settings","Your records remain local. AI is optional and needs internet only when scanning.")
  val shop=input("Shop name").apply{setText(db.shopName())};root.addView(shop)
  root.addView(button("Save shop name",{if(shop.text.isNotBlank()){db.updateShopName(shop.text.toString().trim());toast("Shop name saved.");showSettings()}},true))
  val pin=input("New 6-digit PIN",true);val confirm=input("Confirm new PIN",true);root.addView(pin);root.addView(confirm)
  root.addView(button("Change PIN",{if(pin.text.length<6||pin.text.toString()!=confirm.text.toString())toast("PINs must match and contain at least 6 digits.")else{db.changePin(pin.text.toString());toast("PIN updated.");showSettings()}}))
  val api=input("Gemini API key (optional)");api.setText(if(hasApiKey())"••••••••••••••••" else "");root.addView(api)
  root.addView(text("AI key is encrypted with Android Keystore. Only the selected photo is sent to Gemini when you scan.",13,muted))
  root.addView(button("Save AI key",{val v=api.text.toString().trim();if(v.isNotBlank()&&!v.startsWith("••")){saveApiKey(v);toast("AI key saved securely.")}else toast("Enter a new API key.")}))
  root.addView(button("Remove saved AI key",{deleteApiKey();toast("AI key removed.");showSettings()}))
  root.addView(text("Local database backup",18,ink,true))
  root.addView(text("Export a copy before changing phones or uninstalling. Restoring replaces the current customer and transaction records.",13,muted))
  root.addView(button("Export backup file",{exportBackup()}))
  root.addView(button("Restore backup file",{importBackup()}))
  root.addView(button("← Home",{showHome()}))
 }
 private fun chooseImage(){
  guard()
  if(!hasApiKey()){AlertDialog.Builder(this).setTitle("AI key required").setMessage("All khata records work offline. To read a photo, add your Gemini API key in Settings. Only the selected photo is sent to Gemini.").setPositiveButton("Open settings"){_,_->showSettings()}.setNegativeButton("Cancel",null).show();return}
  startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},77)
 }
 @Deprecated("Uses legacy result API for Android 10 compatibility")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(resultCode!=RESULT_OK || data?.data==null) return
  when(requestCode) {
   77 -> runAiExtraction(data.data!!)
   78 -> try {
    contentResolver.openOutputStream(data.data!!)?.use { it.write(db.exportBackup().toByteArray(Charsets.UTF_8)) }
    toast("Backup exported. Keep that file somewhere safe.")
   } catch(e:Exception) { toast("Backup export failed: "+e.message) }
   79 -> try {
    val json=contentResolver.openInputStream(data.data!!)!!.bufferedReader().use{it.readText()}
    val parsed=JSONObject(json)
    if(parsed.optString("format")!="ai-khata-backup") throw Exception("This file is not an AI Khata backup.")
    AlertDialog.Builder(this).setTitle("Restore this backup?")
     .setMessage("Restoring replaces the current customer and transaction records on this phone. Your current PIN stays unchanged. Export a backup of the current data first if you need it.")
     .setNegativeButton("Cancel",null).setPositiveButton("Restore"){_,_->
      try{db.restoreBackup(json);toast("Backup restored successfully.");showHome()}catch(e:Exception){toast("Restore failed: "+e.message)}
     }.show()
   } catch(e:Exception) { toast("Could not read backup: "+e.message) }
  }
 }
 private fun runAiExtraction(uri:Uri){
  val progress=ProgressDialog(this);progress.show()
  Thread{
   try{
    val bytes=contentResolver.openInputStream(uri)!!.use{it.readBytes()};require(bytes.size<=10*1024*1024){"Image is too large (10 MB maximum)."}
    val mime=contentResolver.getType(uri)?: "image/jpeg";require(mime in listOf("image/jpeg","image/png","image/webp")){"Choose a JPG, PNG or WEBP image."}
    val prompt="Read this handwritten shop ledger. Return ONLY JSON with entries array. Each entry: customer_name, date YYYY-MM-DD, description, type credit or payment, amount number. Do not guess unreadable amounts; omit uncertain rows."
    val parts=JSONArray().put(JSONObject().put("text",prompt)).put(JSONObject().put("inline_data",JSONObject().put("mime_type",mime).put("data",Base64.getEncoder().encodeToString(bytes))))
    val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",parts)))
    val url=URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key="+apiKey())
    val conn=url.openConnection() as HttpURLConnection;conn.requestMethod="POST";conn.connectTimeout=25000;conn.readTimeout=45000;conn.setRequestProperty("Content-Type","application/json");conn.doOutput=true
    conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
    val response=(if(conn.responseCode in 200..299)conn.inputStream else conn.errorStream).bufferedReader().use{it.readText()}
    if(conn.responseCode !in 200..299)throw Exception("Gemini HTTP "+conn.responseCode+": "+response.take(180))
    val answer=JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    val start=answer.indexOf('{');val end=answer.lastIndexOf('}');require(start>=0&&end>start){"AI did not return JSON."}
    val entries=JSONObject(answer.substring(start,end+1)).optJSONArray("entries")?:JSONArray()
    runOnUiThread{progress.dismiss();reviewAiEntries(entries)}
   }catch(e:Exception){runOnUiThread{progress.dismiss();toast("AI scan failed: "+(e.message?: "Check internet and API key."))}}
  }.start()
 }
 private fun reviewAiEntries(entries:JSONArray){
  val rows=ArrayList<JSONObject>();val lines=ArrayList<String>()
  for(i in 0 until entries.length()){
   val e=entries.optJSONObject(i)?:continue;val name=e.optString("customer_name").trim();val amount=e.optDouble("amount",Double.NaN)
   if(name.isBlank()||!amount.isFinite()||amount<=0)continue
   val type=if(e.optString("type")=="payment")"payment" else "credit"
   rows.add(JSONObject().put("name",name).put("amount",amount).put("type",type).put("date",e.optString("date",today())).put("description",e.optString("description","")))
   lines.add(name+": "+type+" "+money(amount)+" · "+e.optString("description",""))
  }
  if(rows.isEmpty()){toast("No clear ledger entries were found.");return}
  AlertDialog.Builder(this).setTitle("Review "+rows.size+" extracted rows").setMessage(lines.joinToString("\n\n")+"\n\nNothing is saved until you confirm. Verify every name and amount.")
   .setNegativeButton("Discard",null).setPositiveButton("Confirm & save"){_,_->
    rows.forEach{e->val name=e.getString("name");val existing=db.customers().firstOrNull{it.name.equals(name,true)};val id=existing?.id?:db.addCustomer(name,"");db.addTransaction(id,e.getString("type"),e.getDouble("amount"),e.getString("description"),e.getString("date"))}
    toast("Confirmed entries saved on this phone.");showHome()
   }.show()
 }
 private fun exportBackup(){
  val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"ai-khata-backup.json")}
  startActivityForResult(intent,78)
 }
 private fun importBackup(){
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json"}
  startActivityForResult(intent,79)
 }
 private fun hasApiKey()=prefs.contains("gemini_key")
 private fun saveApiKey(value:String){
  val key=encryptionKey();val cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,key);val encrypted=cipher.doFinal(value.toByteArray())
  prefs.edit().putString("gemini_iv",Base64.getEncoder().encodeToString(cipher.iv)).putString("gemini_key",Base64.getEncoder().encodeToString(encrypted)).apply()
 }
 private fun apiKey():String{
  val cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");cipher.init(javax.crypto.Cipher.DECRYPT_MODE,encryptionKey(),GCMParameterSpec(128,Base64.getDecoder().decode(prefs.getString("gemini_iv",""))))
  return String(cipher.doFinal(Base64.getDecoder().decode(prefs.getString("gemini_key",""))))
 }
 private fun deleteApiKey(){prefs.edit().remove("gemini_iv").remove("gemini_key").apply()}
 private fun encryptionKey():javax.crypto.SecretKey{
  val ks=java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null)};val alias="AIKhataGeminiKey"
  if(ks.containsAlias(alias))return (ks.getEntry(alias,null) as java.security.KeyStore.SecretKeyEntry).secretKey
  val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  gen.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  return gen.generateKey()
 }
}
