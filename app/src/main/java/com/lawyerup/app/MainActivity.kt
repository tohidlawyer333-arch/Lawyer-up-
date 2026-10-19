package com.lawyerup.app
import android.Manifest
import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import androidx.activity.*
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.lawyerup.app.data.local.*
import com.lawyerup.app.ui.theme.LawyerUpTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.*
import java.util.concurrent.TimeUnit

class MainActivity:ComponentActivity(){ private lateinit var db:PracticeDatabase
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
 override fun onCreate(b:Bundle?){super.onCreate(b); db=PracticeDatabase.get(this); if(Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS); setContent{LawyerUpTheme{Assistant(db.dao())}}}}
@Composable fun Assistant(dao:PracticeDao){ var tab by remember{mutableIntStateOf(0)}; Scaffold(topBar={TopAppBar(title={Text("دستیار حقوقی LawyerUp")})},bottomBar={NavigationBar{listOf("خانه","پرونده‌ها","مواعد","پیش‌نویس‌ها").forEachIndexed{i,t->NavigationBarItem(i==tab,{tab=i},icon={Icon(if(i==0)Icons.Default.Home else if(i==1)Icons.Default.Folder else if(i==2)Icons.Default.Event else Icons.Default.Description,t)},label={Text(t)})}}}){p->Box(Modifier.padding(p).fillMaxSize()){when(tab){0->Home(dao);1->Cases(dao);2->Deadlines(dao);3->Drafts(dao)}}}}
@Composable fun Home(dao:PracticeDao){val cases by dao.cases().collectAsState(emptyList()); val ds by dao.deadlines().collectAsState(emptyList()); LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("نمای کلی",style=MaterialTheme.typography.headlineSmall)};item{Text("پرونده‌ها: ${cases.size}   |   مواعد باز: ${ds.count{!it.completed}}")};item{Card{Column(Modifier.padding(16.dp)){Text("محاسبه‌گر موعد");Text("تاریخ‌ها تقریبی و برای نظم کاری هستند؛ مقررات دادگاه، تعطیلات، شیوه ابلاغ و قانون جاری باید توسط وکیل بررسی شود.")}}};item{PortalCard()}}}
@Composable fun PortalCard(){val c=androidx.compose.ui.platform.LocalContext.current; Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("پرتال قضایی");Text("ورود و کار با ثنا فقط با اقدام مستقیم شما و مرورگر خارجی انجام می‌شود. اطلاعات ورود در برنامه ذخیره نمی‌شود.");Button({c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://adliran.ir")))}){Text("بازکردن پرتال در مرورگر")}}}}
@Composable fun Cases(dao:PracticeDao){val xs by dao.cases().collectAsState(emptyList());var show by remember{mutableStateOf(false)};Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("پرونده‌ها",style=MaterialTheme.typography.headlineSmall);Button({show=true}){Text("پرونده جدید")}};if(xs.isEmpty()) Text("هنوز پرونده‌ای ثبت نشده است.",Modifier.padding(top=24.dp));LazyColumn{items(xs){x->Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){Text(x.title);Text("موکل: ${x.client}");Text("مرجع: ${x.court}");Row{TextButton({}){Text("ویرایش")};TextButton({}){Text("حذف")}}}}}}};if(show) CaseDialog(dao){show=false}}}
@Composable fun CaseDialog(dao:PracticeDao,close:()->Unit){var title by remember{mutableStateOf("")};var client by remember{mutableStateOf("")};var court by remember{mutableStateOf("")};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=close,title={Text("پرونده جدید")},text={Column{Field("عنوان پرونده",title){title=it};Field("موکل",client){client=it};Field("شعبه / مرجع",court){court=it}}},confirmButton={Button({if(title.isNotBlank()){scope.launch{dao.addCase(PracticeCase(title=title,client=client,court=court));close()}}}){Text("ذخیره")}},dismissButton={TextButton(close){Text("انصراف")}})}
@Composable fun Deadlines(dao:PracticeDao){val xs by dao.deadlines().collectAsState(emptyList());val scope=rememberCoroutineScope();var show by remember{mutableStateOf(false)};Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("مواعد",style=MaterialTheme.typography.headlineSmall);Button({show=true}){Text("موعد جدید")}};Text("مبدأ محاسبه: روز بعد از تاریخ محرک؛ سررسید قابل ویرایش است.");if(xs.isEmpty())Text("موعدی ثبت نشده است.",Modifier.padding(top=20.dp));LazyColumn{items(xs){x->Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(x.title);Text("سررسید: ${x.dueDate}");Text(if(x.completed)"انجام شد" else "باز")};Button({scope.launch{dao.updateDeadline(x.copy(completed=!x.completed))}}){Text(if(x.completed)"بازکردن" else "انجام شد")}}}}}};if(show)DeadlineDialog(dao){show=false}}}
@Composable fun DeadlineDialog(dao:PracticeDao,close:()->Unit){var title by remember{mutableStateOf("")};var trigger by remember{mutableStateOf("2024-01-01")};var due by remember{mutableStateOf("2024-01-04")};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=close,title={Text("ثبت موعد")},text={Column{Field("عنوان",title){title=it};Field("تاریخ محرک YYYY-MM-DD",trigger){trigger=it};Field("سررسید YYYY-MM-DD",due){due=it};Text("این محاسبه راهنمای کاری است و جایگزین بررسی وکیل نیست.")}},confirmButton={Button({if(title.isNotBlank()){scope.launch{dao.addDeadline(Deadline(caseId=0,title=title,triggerDate=trigger,dueDate=due));close()}}}){Text("ذخیره")}},dismissButton={TextButton(close){Text("انصراف")}})}
@Composable fun Drafts(dao:PracticeDao){val xs by dao.drafts().collectAsState(emptyList());val c=androidx.compose.ui.platform.LocalContext.current;val scope=rememberCoroutineScope();var show by remember{mutableStateOf(false)};Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("پیش‌نویس‌ها",style=MaterialTheme.typography.headlineSmall);Button({show=true}){Text("پیش‌نویس جدید")}};LazyColumn{items(xs){x->Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Row(Modifier.padding(12.dp)){Text(x.title,Modifier.weight(1f));IconButton({c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,x.body)},"اشتراک‌گذاری پیش‌نویس"))}){Icon(Icons.Default.Share,"اشتراک")}}}}}};if(show)DraftDialog(dao){show=false}}}
@Composable fun DraftDialog(dao:PracticeDao,close:()->Unit){var title by remember{mutableStateOf("")};var body by remember{mutableStateOf("")};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest=close,title={Text("پیش‌نویس")},text={Column{Field("عنوان",title){title=it};Field("متن",body){body=it;}}},confirmButton={Button({if(title.isNotBlank()){scope.launch{dao.addDraft(Draft(title=title,body=body,caseId=null));close()}}}){Text("ذخیره")}},dismissButton={TextButton(close){Text("انصراف")}})}
@Composable fun Field(label:String,value:String,onChange:(String)->Unit){OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),singleLine=label!="متن")}
