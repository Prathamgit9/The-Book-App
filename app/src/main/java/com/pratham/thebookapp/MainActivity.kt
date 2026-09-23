package com.pratham.thebookapp
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private val Ink=Color(0xFF171613); private val Forest=Color(0xFF202C25); private val Paper=Color(0xFFF1E6D2); private val OldPaper=Color(0xFFE4D4B9); private val Brass=Color(0xFFC7A46A); private val Slate=Color(0xFF99958A)
data class Book(val id:String,val title:String,val author:String,val pages:Int,val cover:String,val status:String="TBR",val current:Int=0,val started:String="",val finished:String="",val rating:Int=0,val notes:String="")
class Store(c:Context){ private val p=c.getSharedPreferences("books",0); fun load():List<Book>{val a=JSONArray(p.getString("data","[]"));return(0 until a.length()).map{val o=a.getJSONObject(it);Book(o.getString("id"),o.getString("title"),o.optString("author"),o.optInt("pages"),o.optString("cover"),o.optString("status"),o.optInt("current"),o.optString("started"),o.optString("finished"),o.optInt("rating"),o.optString("notes"))}}; fun save(b:List<Book>){val a=JSONArray();b.forEach{val o=JSONObject();o.put("id",it.id);o.put("title",it.title);o.put("author",it.author);o.put("pages",it.pages);o.put("cover",it.cover);o.put("status",it.status);o.put("current",it.current);o.put("started",it.started);o.put("finished",it.finished);o.put("rating",it.rating);o.put("notes",it.notes);a.put(o)};p.edit().putString("data",a.toString()).apply()}}
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{App(Store(this))}}}

@Composable fun App(store:Store){var books by remember{mutableStateOf(store.load())};var tab by remember{mutableIntStateOf(0)};var selected by remember{mutableStateOf<Book?>(null)};fun save(x:List<Book>){books=x;store.save(x)};MaterialTheme(colorScheme=darkColorScheme(background=Ink,surface=Forest,primary=Brass,onPrimary=Ink,onBackground=Paper,onSurface=Paper)){Surface(Modifier.fillMaxSize(),color=Ink){if(selected!=null)Detail(selected!!,{selected=null},{b->save(books.map{if(it.id==b.id)b else it});selected=b})else Column(Modifier.fillMaxSize()){Box(Modifier.weight(1f)){when(tab){0->Home(books,{selected=it});1->Library(books,{selected=it});2->Lists(books,{b->if(books.none{it.id==b.id})save(books+b)});3->Stats(books)}};NavigationBar(containerColor=Forest){
 NavigationBarItem(tab==0,{tab=0},icon={Text("⌂")},label={Text("Home")})
 NavigationBarItem(tab==1,{tab=1},icon={Text("▤")},label={Text("Library")})
 NavigationBarItem(tab==2,{tab=2},icon={Text("≡")},label={Text("Lists")})
 NavigationBarItem(tab==3,{tab=3},icon={Text("◷")},label={Text("Stats")})
}}}}}
@Composable fun Header(t:String,s:String=""){Text(t,fontFamily=FontFamily.Serif,fontSize=32.sp,fontWeight=FontWeight.Bold);if(s.isNotBlank())Text(s,color=Slate,modifier=Modifier.padding(top=3.dp))}
@Composable fun Label(t:String){Text(t,fontSize=11.sp,color=Brass,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp)}
@Composable fun Img(b:Book,w:Int,h:Int){AsyncImage(b.cover,b.title,Modifier.width(w.dp).height(h.dp).clip(RoundedCornerShape(9.dp)),contentScale=ContentScale.Crop)}
@Composable fun Home(bs:List<Book>,open:(Book)->Unit){val r=bs.firstOrNull{it.status=="READING"};val next=bs.filter{it.status=="TBR"};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){Text("Good evening.",fontFamily=FontFamily.Serif,fontSize=30.sp,fontWeight=FontWeight.Bold);Text("A quieter place for your stories.",color=OldPaper,modifier=Modifier.padding(top=3.dp,bottom=26.dp));Label("CURRENTLY READING");Spacer(Modifier.height(10.dp));if(r==null)Text("Nothing open right now.",fontFamily=FontFamily.Serif,fontSize=22.sp)else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Forest).clickable{open(r)}.padding(16.dp)){Img(r,106,158);Spacer(Modifier.width(16.dp));Column{Text(r.title,fontFamily=FontFamily.Serif,fontSize=23.sp,fontWeight=FontWeight.Bold);Text(r.author,color=OldPaper);Spacer(Modifier.height(16.dp));LinearProgressIndicator({if(r.pages>0)r.current.toFloat()/r.pages else 0f},color=Brass,trackColor=Ink,modifier=Modifier.fillMaxWidth());Text(r.current.toString()+" / "+r.pages+" pages",fontSize=11.sp,color=Slate)}};Spacer(Modifier.height(28.dp));Label("UP NEXT");Spacer(Modifier.height(10.dp));LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp)){items(next.take(8)){b->Column(Modifier.width(118.dp).clickable{open(b)}){Img(b,118,177);Text(b.title,maxLines=1,fontFamily=FontFamily.Serif,modifier=Modifier.padding(top=6.dp));Text(b.author,maxLines=1,fontSize=11.sp,color=Slate)}}};Spacer(Modifier.height(28.dp));Header("Your shelf",bs.size.toString()+" books · "+bs.count{it.status=="COMPLETED"}+" completed")}}
@Composable fun Library(bs:List<Book>,open:(Book)->Unit){var q by remember{mutableStateOf("")};var f by remember{mutableStateOf("ALL")};val shown=bs.filter{(f=="ALL"||it.status==f)&&("${it.title} ${it.author}").contains(q,true)};Column(Modifier.fillMaxSize().padding(20.dp)){Header("Library","The books you have gathered.");OutlinedTextField(q,{q=it},placeholder={Text("Search title or author")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(vertical=14.dp));LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
 for (status in listOf("ALL","READING","TBR","ON HOLD","COMPLETED","DNF")) {
  FilterChip(selected=f==status,onClick={f=status},label={Text(status)})
 }
};Spacer(Modifier.height(14.dp));LazyVerticalGrid(columns=GridCells.Fixed(2),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(18.dp),contentPadding=PaddingValues(bottom=20.dp)){items(shown){b->Column(Modifier.clickable{open(b)}){Img(b,170,255);Text(b.title,fontFamily=FontFamily.Serif,fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=6.dp));Text(b.author,color=Slate,fontSize=12.sp)}}}}}
@Composable fun Lists(bs:List<Book>,add:(Book)->Unit){var show by remember{mutableStateOf(false)};var q by remember{mutableStateOf("")};var results by remember{mutableStateOf<List<Book>>(emptyList())};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Header("Lists","Keep intention separate from history.");TextButton({show=true}){Text("+ ADD",color=Brass)}};Spacer(Modifier.height(16.dp));for (st in listOf("READING","TBR","ON HOLD","COMPLETED","DNF")) {
 Label(st)
 for (b in bs.filter{it.status==st}.take(5)) {
  Row(Modifier.fillMaxWidth().padding(vertical=8.dp)){Img(b,48,72);Column(Modifier.padding(start=10.dp)){Text(b.title,fontFamily=FontFamily.Serif,fontSize=18.sp);Text(b.author,color=Slate,fontSize=12.sp)}}
 }
 Spacer(Modifier.height(10.dp))
}};if(show)AlertDialog(onDismissRequest={show=false},title={Text("Find a book")},text={Column{OutlinedTextField(q,{q=it},label={Text("Title, author or ISBN")});results.forEach{b->Row(Modifier.fillMaxWidth().clickable{add(b);show=false}.padding(6.dp)){Img(b,42,63);Column(Modifier.padding(start=8.dp)){Text(b.title);Text(b.author,color=Slate)}}}}},confirmButton={TextButton({CoroutineScope(Dispatchers.Main).launch{results=search(q)}}){Text("SEARCH")}},dismissButton={TextButton({show=false}){Text("CLOSE")}})}
@Composable fun Detail(b0:Book,back:()->Unit,update:(Book)->Unit){var b by remember{mutableStateOf(b0)};var page by remember{mutableIntStateOf(b0.current)};var notes by remember{mutableStateOf(b0.notes)};var rating by remember{mutableIntStateOf(b0.rating)};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){Text("‹  Library",color=Brass,modifier=Modifier.clickable{back()});Spacer(Modifier.height(14.dp));Row{Img(b,145,218);Spacer(Modifier.width(18.dp));Column{Text(b.title,fontFamily=FontFamily.Serif,fontSize=25.sp,fontWeight=FontWeight.Bold);Text(b.author,color=OldPaper);Spacer(Modifier.height(12.dp));Label(b.status);Text(b.pages.toString()+" pages",color=Slate)}};Spacer(Modifier.height(22.dp));Label("READING PROGRESS");Slider(page.toFloat(),{page=it.toInt()},valueRange=0f..b.pages.coerceAtLeast(1).toFloat());Text(page.toString()+" / "+b.pages+" pages",color=Slate);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({b=b.copy(status="READING",started=if(b.started.isBlank())LocalDate.now().toString() else b.started);update(b)}){Text("READING")};OutlinedButton({page=b.pages;b=b.copy(status="COMPLETED",current=b.pages,finished=LocalDate.now().toString());update(b)}){Text("FINISH")}};Spacer(Modifier.height(16.dp));Label("RATING");Row{(1..5).forEach{i->Text(if(i<=rating)"★" else "☆",fontSize=30.sp,color=Brass,modifier=Modifier.clickable{rating=i})}};Label("NOTES");OutlinedTextField(notes,{notes=it},minLines=4,modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp));Button({update(b.copy(current=page,rating=rating,notes=notes));back()},Modifier.fillMaxWidth()){Text("SAVE CHANGES")}}}
@Composable fun Stats(bs:List<Book>){val d=bs.filter{it.status=="COMPLETED"};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)){Header("Your reading","A record, not a competition.");Spacer(Modifier.height(25.dp));Metric("Books completed",d.size.toString());Metric("Pages completed",d.sumOf{it.pages}.toString());Metric("Currently reading",bs.count{it.status=="READING"}.toString());Metric("Books in TBR",bs.count{it.status=="TBR"}.toString())}}
@Composable fun Metric(a:String,b:String){Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(a,color=OldPaper);Text(b,fontFamily=FontFamily.Serif,fontSize=27.sp,color=Brass)}}
suspend fun search(q:String):List<Book> =withContext(Dispatchers.IO){try{val c=URL("https://openlibrary.org/search.json?q="+Uri.encode(q)+"&limit=8").openConnection() as HttpURLConnection;c.connectTimeout=8000;c.readTimeout=8000;val j=JSONObject(c.inputStream.bufferedReader().readText());val d=j.getJSONArray("docs");(0 until d.length()).mapNotNull{val o=d.getJSONObject(it);val title=o.optString("title");if(title.isBlank())return@mapNotNull null;val a=o.optJSONArray("author_name");val author=if(a!=null&&a.length()>0)a.getString(0) else "Unknown author";val id=o.optString("key");val cover=o.optInt("cover_i");Book(id,title,author,o.optInt("number_of_pages_median"),if(cover>0)"https://covers.openlibrary.org/b/id/"+cover+"-L.jpg" else "")}}catch(_:Throwable){emptyList()}}