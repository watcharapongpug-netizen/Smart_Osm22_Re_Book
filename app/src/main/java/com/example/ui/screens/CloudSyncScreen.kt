package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sync.SyncState
import com.example.ui.components.ThemeQuickToggleButton
import com.example.ui.theme.*
import com.example.viewmodel.PersonViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    viewModel: PersonViewModel,
    authViewModel: com.example.viewmodel.AuthViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val totalPersons by viewModel.totalPersonsCount.collectAsStateWithLifecycle()
    val totalHouseholds by viewModel.totalHouseholdsCount.collectAsStateWithLifecycle()

    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            viewModel.exportExcelData(context, it) { success, message ->
                isError = !success
                actionMessage = message
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "สำรองข้อมูลและซิงค์คลาวด์",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "กลับ", tint = Color.White)
                    }
                },
                actions = {
                    ThemeQuickToggleButton(iconTint = Color.White)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EmeraldPrimary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Status & Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "สถานะฐานข้อมูลในเครื่อง (Room)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Badge(containerColor = EmeraldPrimary) {
                            Text("พร้อมใช้งาน", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = "ครัวเรือนทั้งหมด", count = "$totalHouseholds หลัง")
                        VerticalDivider(modifier = Modifier.height(30.dp))
                        StatItem(label = "ประชากรทั้งหมด", count = "$totalPersons คน")
                    }
                }
            }

            // Sync State Feedback Banner
            when (val state = syncState) {
                is SyncState.Syncing -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(text = state.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                is SyncState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Text(text = "ซิงค์สำเร็จ", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = state.result.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
                        }
                    }
                }
                is SyncState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(text = "เกิดข้อผิดพลาดในการซิงค์", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                else -> {}
            }

            // Action Feedback Banner
            actionMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { actionMessage = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "ปิด")
                        }
                    }
                }
            }

            // Section 1: Multi-Cloud Drive Backup & Export
            Text(
                text = "1. การสำรองไฟล์ขึ้นคลาวด์ไดรฟ์ (Google Drive, OneDrive, Cloud Drives)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "ส่งออกข้อมูลประชากรและครัวเรือนเป็นไฟล์ Excel (.xlsx) และเลือกบันทึกลง คลาวด์ไดรฟ์ (Google Drive, OneDrive, Dropbox, Nextcloud) หรือบันทึกลงในเครื่อง",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Multi-Cloud Share / Save Button
                    Button(
                        onClick = {
                            viewModel.exportAndShareExcelData(context) { success, shareUri, msg ->
                                if (success && shareUri != null) {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "สำรองข้อมูล Smart OSM ทะเบียนประชากร")
                                        putExtra(Intent.EXTRA_TEXT, "ไฟล์สำรองข้อมูลทะเบียนประชากรและครัวเรือน Smart OSM")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooser = Intent.createChooser(sendIntent, "เลือกบันทึกไปยัง คลาวด์ไดรฟ์ หรือส่งต่อ")
                                    context.startActivity(chooser)
                                    actionMessage = "เปิดเมนูเลือกคลาวด์ไดรฟ์เรียบร้อยแล้ว"
                                    isError = false
                                } else {
                                    isError = true
                                    actionMessage = msg
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("บันทึกขึ้น Cloud Drive (Google Drive / OneDrive / อื่นๆ)", fontWeight = FontWeight.Bold)
                    }

                    // Direct Save to Storage (SAF)
                    OutlinedButton(
                        onClick = { exportLauncher.launch("population_backup_${System.currentTimeMillis()}.xlsx") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("บันทึกไฟล์ Excel (.xlsx) ลงพื้นที่เก็บข้อมูลในเครื่อง", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 2: Cloud Firestore Database Sync
            if (authViewModel?.hasPermission(com.example.data.membership.AreaPermission.SYNC_CLOUD) == true) {
                Text(
                    text = "2. การซิงค์ฐานข้อมูลแบบเรียลไทม์ (Google Cloud Firestore)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "เชื่อมต่อและสำรองข้อมูลขึ้นระบบ Google Cloud Firestore เพื่อความปลอดภัย ป้องกันข้อมูลสูญหาย และซิงค์ระหว่างอุปกรณ์",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Push to Cloud Button
                        OutlinedButton(
                            onClick = {
                                viewModel.syncToFirestore { result ->
                                    isError = result.isFailure
                                    actionMessage = if (result.isSuccess) "อัปโหลดข้อมูลขึ้น Cloud สำเร็จ" else "อัปโหลดไม่สำเร็จ: ${result.exceptionOrNull()?.message}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("สำรองข้อมูลขึ้นคลาวด์ (Push to Cloud)", fontWeight = FontWeight.Bold)
                        }

                        // Pull from Cloud Button
                        OutlinedButton(
                            onClick = {
                                viewModel.syncFromFirestore { result ->
                                    isError = result.isFailure
                                    actionMessage = if (result.isSuccess) "ดึงข้อมูลจาก Cloud สำเร็จ" else "ดึงข้อมูลไม่สำเร็จ: ${result.exceptionOrNull()?.message}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("กู้คืนข้อมูลจากคลาวด์ (Pull from Cloud)", fontWeight = FontWeight.Bold)
                        }

                        // Bidirectional Sync Button
                        Button(
                            onClick = {
                                viewModel.bidirectionalSync { result ->
                                    isError = result.isFailure
                                    actionMessage = if (result.isSuccess) "ซิงค์ 2 ทาง (Bidirectional) สำเร็จ" else "ซิงค์ไม่สำเร็จ: ${result.exceptionOrNull()?.message}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintAccent)
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ซิงค์แบบ 2 ทาง (Bidirectional Sync)", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Section 3: Google Sheets Sync & Import
            if (authViewModel?.hasPermission(com.example.data.membership.AreaPermission.IMPORT_EXPORT) == true) {
                Text(
                    text = "3. การนำเข้าและวิเคราะห์ข้อมูลผ่าน Google Sheets (Google Sheets Integration)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )

                var spreadsheetInput by remember { mutableStateOf("") }
                var tokenInput by remember { mutableStateOf("") }
                val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "กรอก Spreadsheet URL หรือ Spreadsheet ID เพื่อนำข้อมูลเข้าสู่ระบบวิเคราะห์ประเมินความถูกต้อง (Validation Plan) ก่อนนำเขียนลงหน่วยความจำจริง",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = spreadsheetInput,
                            onValueChange = { spreadsheetInput = it },
                            label = { Text("Google Spreadsheet URL หรือ ID") },
                            placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.GridOn, contentDescription = null, tint = EmeraldPrimary) }
                        )

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("OAuth Access Token (หากไฟล์ไม่ได้เปิดเผยต่อสาธารณะ / Optional)") },
                            placeholder = { Text("ya29.a0Ac...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.VpnKey, contentDescription = null, tint = EmeraldPrimary) }
                        )

                        Button(
                            onClick = {
                                if (spreadsheetInput.isBlank()) {
                                    isError = true
                                    actionMessage = "กรุณากรอก Spreadsheet URL หรือ ID"
                                    return@Button
                                }
                                viewModel.loadGoogleSheetsImportPlan(
                                    context = context,
                                    spreadsheetId = spreadsheetInput,
                                    accessToken = tokenInput.takeIf { it.isNotBlank() }
                                ) { success, message ->
                                    isError = !success
                                    actionMessage = message
                                }
                            },
                            enabled = !isImporting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("กำลังดาวน์โหลดและตรวจทานข้อมูล...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("นำเข้าและเปิดแผนตรวจสอบความถูกต้อง", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Import Plan Validation Review Dialog
    val importPlan by viewModel.importPlan.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()

    if (importPlan != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportPlan() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("ตรวจสอบแผนการนำเข้า", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "พบคอมมิตข้อมูลทั้งหมด ${importPlan!!.totalRows} แถว กรุณาตรวจสอบแผนการบันทึกก่อนยืนยันเพื่อความถูกต้องของข้อมูล",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = StatusVerifiedBg.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, StatusVerifiedBg)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("เพิ่มใหม่", style = MaterialTheme.typography.labelSmall, color = StatusVerifiedFg)
                                Text("${importPlan!!.insertCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusVerifiedFg)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("อัปเดต", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Text("${importPlan!!.updateCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1.2f),
                            colors = CardDefaults.cardColors(containerColor = StatusNeedsReviewBg.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, StatusNeedsReviewBg)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("ต้องตรวจ", style = MaterialTheme.typography.labelSmall, color = StatusNeedsReviewFg)
                                Text("${importPlan!!.needsReviewCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatusNeedsReviewFg)
                            }
                        }
                    }

                    if (importPlan!!.errors.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StatusDeadBg.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, StatusDeadBg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "พบข้อผิดพลาดร้ายแรง (${importPlan!!.errors.size} รายการ):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusDeadFg,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.heightIn(max = 80.dp)) {
                                    androidx.compose.foundation.lazy.LazyColumn {
                                        items(importPlan!!.errors) { err ->
                                            Text(
                                                "• แถวที่ ${err.rowNumber}: ${err.reason}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = StatusDeadFg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val itemsNeedingReview = importPlan!!.plannedItems.filter { it.action == com.example.domain.ImportAction.NEEDS_REVIEW }
                    if (itemsNeedingReview.isNotEmpty()) {
                        Text(
                            "รายการประชากรที่ติดข้อสงสัยและต้องการการตรวจสอบก่อน:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusNeedsReviewFg
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.heightIn(max = 180.dp)) {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(itemsNeedingReview) { item ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                .padding(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "แถวที่ ${item.rowNum}: ${item.personData.fullName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "บ้านเลขที่ ${item.householdData.houseNo}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = OnSurfaceSecondary
                                                )
                                            }
                                            item.reviewReasons.forEach { reason ->
                                                Text(
                                                    "⚠️ $reason",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StatusNeedsReviewFg,
                                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StatusVerifiedBg.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, StatusVerifiedBg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "🎉 ตรวจสอบแล้วไม่มีปัญหาความขัดแย้งของข้อมูล ทุกรายการสามารถบันทึกได้ทันที",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusVerifiedFg
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val opUid = authViewModel?.currentUid
                        val opName = authViewModel?.userProfile?.value?.safeDisplayName
                        viewModel.commitCurrentImportPlan(opUid, opName) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("ยืนยันการนำเข้า", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearImportPlan() }
                ) {
                    Text("ยกเลิก", color = OnSurfaceSecondary)
                }
            }
        )
    }

    if (importResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportResult() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StatusVerifiedBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("ผลการนำเข้าข้อมูล", style = MaterialTheme.typography.titleLarge)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ทั้งหมด: ${importResult!!.totalRows} รายการ", fontWeight = FontWeight.Bold)
                    Row {
                        Text("• สำเร็จ: ", color = OnSurfaceSecondary)
                        Text("${importResult!!.successCount} รายการ", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                    }
                    Row {
                        Text("• ผิดพลาด: ", color = OnSurfaceSecondary)
                        Text("${importResult!!.failedCount} รายการ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = HairlineBorder)
                    Text("รายละเอียดจำแนกข้อผิดพลาด:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("- ข้อมูลซ้ำ: ${importResult!!.duplicateCount}", style = MaterialTheme.typography.bodySmall)
                    Text("- เลขบัตร ปชช. ไม่ถูกต้อง: ${importResult!!.invalidNationalIdCount}", style = MaterialTheme.typography.bodySmall)
                    Text("- วันเกิดไม่ถูกต้อง: ${importResult!!.invalidBirthDateCount}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearImportResult() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("ตกลง", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun StatItem(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
