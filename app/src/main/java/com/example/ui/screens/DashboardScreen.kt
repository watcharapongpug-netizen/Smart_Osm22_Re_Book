package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.data.VhvAgeGroup
import com.example.R
import com.example.data.DataStatus
import com.example.data.Gender
import com.example.data.Household
import com.example.data.HouseholdRole
import com.example.data.Person
import com.example.data.PersonStatus
import com.example.ui.components.ThemeQuickToggleButton
import com.example.ui.theme.*
import com.example.viewmodel.PersonViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: PersonViewModel,
    authViewModel: com.example.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToHouseholds: () -> Unit = {},
    onNavigateToNewHousehold: () -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    onNavigateToInfo: () -> Unit = {},
    onNavigateToPlanOfWork: () -> Unit = {},
    onNavigateToHouseDetail: (Long) -> Unit = {},
    onNavigateToQrScan: () -> Unit = {},
    onNavigateToScreening: (Long) -> Unit = {},
    onNavigateToMonthlyReport: () -> Unit = {},
    onNavigateToVideoTutorials: (String?) -> Unit = {}
) {
    val allPersons by viewModel.allPersons.collectAsStateWithLifecycle()
    val allHouseholdsWithPersons by viewModel.allHouseholdsWithPersons.collectAsStateWithLifecycle()
    val totalPersonsCount by viewModel.totalPersonsCount.collectAsStateWithLifecycle()
    val totalHouseholdsCount by viewModel.totalHouseholdsCount.collectAsStateWithLifecycle()
    val ageGroupSummary by viewModel.ageGroupSummary.collectAsStateWithLifecycle()
    val allScreenings by viewModel.allScreenings.collectAsStateWithLifecycle()
    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()

    var selectedStatsTab by remember { mutableStateOf(0) } // 0: Age, 1: NCDs, 2: Gender
    val isDark = isSystemInDarkTheme()

    var showNotificationDialog by remember { mutableStateOf(false) }
    var notificationTitle by remember { mutableStateOf("") }
    var notificationMessage by remember { mutableStateOf("") }

    var selectedAgeGroupDetail by remember { mutableStateOf<VhvAgeGroup?>(null) }
    val dashboardContext = LocalContext.current

    if (selectedAgeGroupDetail != null) {
        val targetGroup = selectedAgeGroupDetail!!
        val personsInGroup = remember(allHouseholdsWithPersons, targetGroup) {
            allHouseholdsWithPersons.flatMap { hw ->
                hw.persons.filter { p ->
                    if (p.personStatus != PersonStatus.ALIVE) return@filter false
                    val age = viewModel.calculateAge(p.birthDate, p.personStatus)
                    VhvAgeGroup.fromAge(age) == targetGroup
                }.map { p -> p to hw.household.houseNo }
            }.sortedBy { it.first.fullName }
        }

        val targetInfo = when (targetGroup) {
            VhvAgeGroup.EARLY_CHILD -> Triple("เด็กปฐมวัย (0-5 ปี)", Icons.Filled.ChildCare, "ตรวจพัฒนาการสมวัย (DSPM) และบันทึกการรับวัคซีนพื้นฐาน")
            VhvAgeGroup.SCHOOL_AGE -> Triple("เด็กวัยเรียน (6-12 ปี)", Icons.Filled.School, "ประเมินการเจริญเติบโต โภชนาการ และคัดกรองสายตา")
            VhvAgeGroup.TEENAGER -> Triple("วัยรุ่น (13-20 ปี)", Icons.Filled.SelfImprovement, "ให้คำปรึกษาสุขภาวะทางเพศ ป้องกันยาเสพติด และดูแลสุขภาพจิต")
            VhvAgeGroup.WORKING_AGE -> Triple("วัยทำงาน (21-59 ปี)", Icons.Filled.Work, "ตรวจคัดกรองความดันโลหิตและเบาหวาน (NCDs) ป้องกันโรคไม่ติดต่อ")
            VhvAgeGroup.ELDERLY -> Triple("ผู้สูงอายุ (60 ปีขึ้นไป)", Icons.Filled.Elderly, "ประเมินความสามารถในการทำกิจวัตร (ADL) และคัดกรองภาวะสมองเสื่อม")
            VhvAgeGroup.UNKNOWN -> Triple("ไม่ระบุช่วงอายุ", Icons.Filled.Person, "ประชากรที่ยังไม่มีข้อมูลวันเกิดในระบบ")
        }

        AlertDialog(
            onDismissRequest = { selectedAgeGroupDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(targetInfo.second, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(targetInfo.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${personsInGroup.size} คน ในพื้นที่รับผิดชอบ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "ภารกิจ อสม.: ${targetInfo.third}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (personsInGroup.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ไม่มีประชากรในกลุ่มวัยนี้ในพื้นที่", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(personsInGroup, key = { it.first.id }) { (person, houseNo) ->
                                val age = viewModel.calculateAge(person.birthDate, person.personStatus)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = person.fullName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "บ้านเลขที่ $houseNo | อายุ ${age ?: "-"} ปี",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!person.phoneNumber.isNullOrBlank()) {
                                                Text(
                                                    text = "โทร: ${person.phoneNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        // Call button
                                        if (!person.phoneNumber.isNullOrBlank()) {
                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${person.phoneNumber}"))
                                                    dashboardContext.startActivity(intent)
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Filled.Phone, contentDescription = "โทร", tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        // Health Screening button
                                        IconButton(
                                            onClick = {
                                                val pId = person.id
                                                selectedAgeGroupDetail = null
                                                onNavigateToScreening(pId)
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Filled.MonitorHeart, contentDescription = "คัดกรอง", tint = GoldenAmber, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedAgeGroupDetail = null }) {
                    Text("ปิด")
                }
            }
        )
    }

    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = { Text(notificationTitle, fontWeight = FontWeight.Bold) },
            text = { Text(notificationMessage) },
            confirmButton = {
                Button(onClick = { showNotificationDialog = false }) {
                    Text("รับทราบ")
                }
            }
        )
    }

    // Calculated citizen summaries
    val maleCount = remember(allPersons) { allPersons.count { it.gender == Gender.MALE } }
    val femaleCount = remember(allPersons) { allPersons.count { it.gender == Gender.FEMALE } }

    val aliveCount = remember(allPersons) { allPersons.count { it.personStatus == PersonStatus.ALIVE } }
    val deadCount = remember(allPersons) { allPersons.count { it.personStatus == PersonStatus.DEAD } }
    val movedCount = remember(allPersons) { allPersons.count { it.personStatus == PersonStatus.MOVED } }

    val verifiedCount = remember(allPersons) { allPersons.count { it.dataStatus == DataStatus.VERIFIED } }
    val needsReviewCount = remember(allPersons) { allPersons.count { it.dataStatus == DataStatus.NEEDS_REVIEW } }

    val needsReviewHouseholdsCount = remember(allHouseholdsWithPersons) {
        allHouseholdsWithPersons.count { it.household.dataStatus == DataStatus.NEEDS_REVIEW }
    }

    val seniorsCount = remember(allPersons) {
        allPersons.count { p ->
            if (p.personStatus != PersonStatus.ALIVE) return@count false
            val age = viewModel.calculateAge(p.birthDate, p.personStatus)
            VhvAgeGroup.fromAge(age) == VhvAgeGroup.ELDERLY
        }
    }
    val childrenCount = remember(allPersons) {
        allPersons.count { p ->
            if (p.personStatus != PersonStatus.ALIVE) return@count false
            val age = viewModel.calculateAge(p.birthDate, p.personStatus)
            VhvAgeGroup.fromAge(age) == VhvAgeGroup.EARLY_CHILD
        }
    }

    val gpsHouseholdsCount = remember(allHouseholdsWithPersons) {
        allHouseholdsWithPersons.count { it.household.latitude != null && it.household.longitude != null }
    }

    // Up to 5 most recently registered citizens paired with household info
    val recentCitizens = remember(allHouseholdsWithPersons) {
        allHouseholdsWithPersons.flatMap { hw ->
            hw.persons.map { person -> Pair(person, hw.household) }
        }.sortedByDescending { it.first.id }.take(4)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.HealthAndSafety,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val userName = userProfile?.displayName ?: "ผู้สำรวจ"
                            Text(
                                "SMART OSM",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                color = Color.White
                            )
                            Text(
                                "สวัสดี, $userName",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToVideoTutorials(null) }) {
                        Icon(Icons.Filled.SmartDisplay, contentDescription = "วิดีโอสอนใช้งาน", tint = Color.White)
                    }
                    IconButton(onClick = {
                        notificationTitle = "แจ้งเตือนงานสาธารณสุขชุมชน (อสม.)"
                        notificationMessage = "• สำรวจผู้สูงอายุติดบ้าน/ติดเตียงประจำเดือน\n• ตรวจคัดกรองโรคความดันโลหิตและเบาหวาน\n• บันทึกข้อมูลครัวเรือนที่ยังขาดพิกัด GPS"
                        showNotificationDialog = true
                    }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "การแจ้งเตือน", tint = Color.White)
                    }
                    IconButton(onClick = onNavigateToQrScan) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "สแกน QR Code", tint = Color.White)
                    }
                    ThemeQuickToggleButton(iconTint = Color.White)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Hero Image Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .shadow(8.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Box {
                        Image(
                            painter = painterResource(id = R.drawable.img_dashboard_hero),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                "ร่วมสร้างชุมชนสุขภาพดี",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "เคียงข้างชาวบ้าน ด้วยหัวใจ อสม.",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // 0.5 Quick Video Tutorial Banner (1-Minute Easy Guide for VHV)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToVideoTutorials(null) }
                        .shadow(4.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "เล่นวิดีโอ",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "วิดีโอสั้นสอนวิธีใช้งาน",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EmeraldPrimary
                                    ) {
                                        Text(
                                            "1 นาทีเข้าใจ",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    "สอนลงทะเบียน, สำรวจบ้าน, คัดกรอง NCDs & ซิงค์คลาวด์",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EmeraldPrimary)
                    }
                }
            }

            // 1. Atmospheric Hero Card: Population & Community Overview
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = CardShadowTint
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(HeroGradientBrush)
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.wrapContentSize()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MintAccent)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "พื้นที่รับผิดชอบ อสม.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "ประชากรที่ลงทะเบียนแล้ว",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = "$totalPersonsCount",
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "คน",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Groups,
                                    contentDescription = null,
                                    tint = MintAccent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Secondary summary indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Home,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$totalHouseholdsCount ครัวเรือน",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MintAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "พิกัด GPS $gpsHouseholdsCount หลัง",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFFA7F3D0),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ตรวจแล้ว $verifiedCount คน",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Reusable BMI Calculator Component
            item {
                com.example.ui.components.BmiCalculatorCard()
            }

            // 3. Key App Functions (Quick Navigation Grid)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ฟังก์ชันหลักของระบบ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "เมนูด่วน",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // 2x2 Bento Action Cards Grid
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                title = "สำรวจครัวเรือน",
                                subtitle = "ค้นหา & สมาชิก $totalHouseholdsCount หลัง",
                                icon = Icons.Filled.Home,
                                iconBgColor = if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5),
                                iconTintColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                                onClick = onNavigateToHouseholds
                            )
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                title = "เพิ่มบ้านใหม่",
                                subtitle = "ลงทะเบียน + พิกัด GPS",
                                icon = Icons.Filled.AddHome,
                                iconBgColor = if (isDark) Color(0xFF134E4A) else Color(0xFFCCFBF1),
                                iconTintColor = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488),
                                onClick = onNavigateToNewHousehold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                title = "แผนปฏิบัติงาน",
                                subtitle = "ตารางงาน อสม. & เยี่ยมบ้าน",
                                icon = Icons.Filled.FactCheck,
                                iconBgColor = if (isDark) Color(0xFF1E3A8A) else Color(0xFFDBEAFE),
                                iconTintColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                onClick = onNavigateToPlanOfWork
                            )
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                title = "ข้อมูล อสม. & ตั้งค่า",
                                subtitle = "ข้อมูลอาสาสมัคร & ธีม",
                                icon = Icons.Filled.Settings,
                                iconBgColor = if (isDark) Color(0xFF3B0764) else Color(0xFFF3E8FF),
                                iconTintColor = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED),
                                onClick = onNavigateToInfo
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                title = "รายงาน อสม. 1 & 2",
                                subtitle = "สรุปผลงาน & พิมพ์ A4 PDF",
                                icon = Icons.Filled.Assessment,
                                iconBgColor = if (isDark) Color(0xFF1E3A8A) else Color(0xFFE0E7FF),
                                iconTintColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA),
                                onClick = onNavigateToMonthlyReport
                            )
                            QuickActionCard(
                                modifier = Modifier.weight(1f),
                                title = "แผนที่พิกัดบ้าน",
                                subtitle = "GIS $gpsHouseholdsCount หลัง",
                                icon = Icons.Filled.LocationOn,
                                iconBgColor = if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5),
                                iconTintColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                                onClick = onNavigateToMap
                            )
                        }
                    }
                }
            }

            // 3. Registered Citizens Summary (Gender & Priority Target Groups)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "สรุปข้อมูลประชากร",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "กลุ่มเพศและกลุ่มเป้าหมาย",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 4-Card Demographic KPI Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CitizenStatCard(
                            modifier = Modifier.weight(1f),
                            title = "เพศชาย",
                            count = maleCount,
                            unit = "คน",
                            icon = Icons.Filled.Male,
                            tint = Color(0xFF0284C7),
                            bg = if (isDark) Color(0xFF0C3348) else Color(0xFFE0F2FE)
                        )
                        CitizenStatCard(
                            modifier = Modifier.weight(1f),
                            title = "เพศหญิง",
                            count = femaleCount,
                            unit = "คน",
                            icon = Icons.Filled.Female,
                            tint = Color(0xFFDB2777),
                            bg = if (isDark) Color(0xFF451528) else Color(0xFFFCE7F3)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CitizenStatCard(
                            modifier = Modifier.weight(1f),
                            title = "ผู้สูงอายุ (60+)",
                            count = seniorsCount,
                            unit = "คน",
                            badge = "เยี่ยมบ้าน",
                            icon = Icons.Filled.Elderly,
                            tint = Color(0xFFD97706),
                            bg = if (isDark) Color(0xFF452205) else Color(0xFFFEF3C7),
                            onClick = { selectedAgeGroupDetail = VhvAgeGroup.ELDERLY }
                        )
                        CitizenStatCard(
                            modifier = Modifier.weight(1f),
                            title = "เด็กปฐมวัย (0-5)",
                            count = childrenCount,
                            unit = "คน",
                            badge = "วัคซีน",
                            icon = Icons.Filled.ChildCare,
                            tint = Color(0xFF0D9488),
                            bg = if (isDark) Color(0xFF0F3836) else Color(0xFFCCFBF1),
                            onClick = { selectedAgeGroupDetail = VhvAgeGroup.EARLY_CHILD }
                        )
                    }
                }
            }

            // 4. Vital Status & Quality Check
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(18.dp), spotColor = CardShadowTint),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "สถานะการมีชีวิต & ความถูกต้องข้อมูล",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusItemPill(
                                modifier = Modifier.weight(1f),
                                label = "มีชีวิต",
                                count = aliveCount,
                                color = if (isDark) StatusVerifiedFgDark else StatusVerifiedFg,
                                bgColor = if (isDark) StatusVerifiedBgDark else StatusVerifiedBg
                            )
                            StatusItemPill(
                                modifier = Modifier.weight(1f),
                                label = "เสียชีวิต",
                                count = deadCount,
                                color = if (isDark) StatusDeadFgDark else StatusDeadFg,
                                bgColor = if (isDark) StatusDeadBgDark else StatusDeadBg
                            )
                            StatusItemPill(
                                modifier = Modifier.weight(1f),
                                label = "ย้ายถิ่น",
                                count = movedCount,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                bgColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusItemPill(
                                modifier = Modifier.weight(1f),
                                label = "รอตรวจคน",
                                count = needsReviewCount,
                                color = if (isDark) StatusNeedsReviewFgDark else StatusNeedsReviewFg,
                                bgColor = if (isDark) StatusNeedsReviewBgDark else StatusNeedsReviewBg
                            )
                            StatusItemPill(
                                modifier = Modifier.weight(1f),
                                label = "รอตรวจบ้าน",
                                count = needsReviewHouseholdsCount,
                                color = if (isDark) Color(0xFFFDE68A) else Color(0xFFD97706),
                                bgColor = if (isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)
                            )
                        }

                        // Verification Progress Bar
                        val verifyPercent = if (totalPersonsCount > 0) (verifiedCount.toFloat() / totalPersonsCount.toFloat()) else 0f
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ความสมบูรณ์ของฐานข้อมูล",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(verifyPercent * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(verifyPercent.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(EmeraldPrimary)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Demographic & Health Analytical Dashboard (Interactive Segment)
            item {
                Column(modifier = Modifier.padding(top = 2.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "รายงานวิเคราะห์สุขภาวะชุมชน",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Interactive Tab Row
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("ช่วงวัยประชากร", "ความเสี่ยง NCDs", "สัดส่วนเพศ").forEachIndexed { index, title ->
                                val isSelected = selectedStatsTab == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) EmeraldPrimary else Color.Transparent)
                                        .clickable { selectedStatsTab = index }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(20.dp),
                                spotColor = CardShadowTint
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (selectedStatsTab == 0) {
                                // --- TAB 0: AGE STRUCTURE ---
                                if (ageGroupSummary.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.Analytics,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "ยังไม่มีข้อมูลวันเกิดประชากรในระบบ",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    val standardGroups = listOf(
                                        "0-5 ปี",
                                        "6-12 ปี",
                                        "13-20 ปี",
                                        "21-59 ปี",
                                        "60 ปีขึ้นไป"
                                    )
                                    val dataList = listOf(
                                        ageGroupSummary[VhvAgeGroup.EARLY_CHILD.value] ?: 0,
                                        ageGroupSummary[VhvAgeGroup.SCHOOL_AGE.value] ?: 0,
                                        ageGroupSummary[VhvAgeGroup.TEENAGER.value] ?: 0,
                                        ageGroupSummary[VhvAgeGroup.WORKING_AGE.value] ?: 0,
                                        ageGroupSummary[VhvAgeGroup.ELDERLY.value] ?: 0
                                    )

                                    val chartEntryModel = com.patrykandpatrick.vico.core.entry.entryModelOf(*dataList.map { it.toFloat() }.toTypedArray())

                                    com.patrykandpatrick.vico.compose.chart.Chart(
                                        chart = com.patrykandpatrick.vico.compose.chart.column.columnChart(
                                            columns = listOf(com.patrykandpatrick.vico.compose.component.lineComponent(
                                                color = EmeraldPrimary,
                                                thickness = 16.dp,
                                                shape = com.patrykandpatrick.vico.core.component.shape.Shapes.roundedCornerShape(topLeftPercent = 50, topRightPercent = 50)
                                            ))
                                        ),
                                        model = chartEntryModel,
                                        startAxis = com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis(
                                            valueFormatter = { value, _ -> value.toInt().toString() }
                                        ),
                                        bottomAxis = com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis(
                                            valueFormatter = { value, _ -> standardGroups.getOrNull(value.toInt()) ?: "" },
                                            labelRotationDegrees = -45f
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(220.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "ภารกิจคัดกรอง 5 กลุ่มวัย (อสม.)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "แตะดูรายชื่อ & คัดกรอง",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val totalLiving = remember(allPersons) { allPersons.count { it.personStatus == PersonStatus.ALIVE } }

                                    val vhvAgeCards = listOf(
                                        Triple(VhvAgeGroup.EARLY_CHILD, "เด็กปฐมวัย (0-5 ปี)", Pair(Color(0xFFD97706), if (isDark) Color(0xFF451A03) else Color(0xFFFEF3C7))) to Pair(Icons.Filled.ChildCare, "ตรวจพัฒนาการสมวัย (DSPM) & วัคซีนพื้นฐาน"),
                                        Triple(VhvAgeGroup.SCHOOL_AGE, "เด็กวัยเรียน (6-12 ปี)", Pair(Color(0xFF0284C7), if (isDark) Color(0xFF082F49) else Color(0xFFE0F2FE))) to Pair(Icons.Filled.School, "ภาวะโภชนาการ สมส่วน & คัดกรองสายตา/ฟัน"),
                                        Triple(VhvAgeGroup.TEENAGER, "วัยรุ่น (13-20 ปี)", Pair(Color(0xFF7C3AED), if (isDark) Color(0xFF2E1065) else Color(0xFFEDE9FE))) to Pair(Icons.Filled.SelfImprovement, "สุขภาวะทางเพศ & สุขภาพจิต ป้องกันพฤติกรรมเสี่ยง"),
                                        Triple(VhvAgeGroup.WORKING_AGE, "วัยทำงาน (21-59 ปี)", Pair(Color(0xFF0D9488), if (isDark) Color(0xFF042F2E) else Color(0xFFCCFBF1))) to Pair(Icons.Filled.Work, "คัดกรองความดัน-เบาหวาน NCDs & รอบเอว"),
                                        Triple(VhvAgeGroup.ELDERLY, "ผู้สูงอายุ (60 ปีขึ้นไป)", Pair(Color(0xFFE11D48), if (isDark) Color(0xFF4C0519) else Color(0xFFFFE4E6))) to Pair(Icons.Filled.Elderly, "ประเมิน ADL ติดบ้าน/ติดเตียง & คัดกรองสมองเสื่อม")
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        vhvAgeCards.forEach { (groupData, details) ->
                                            val (group, title, colors) = groupData
                                            val (icon, mission) = details
                                            val count = ageGroupSummary[group.value] ?: 0
                                            val pct = if (totalLiving > 0) (count.toFloat() / totalLiving.toFloat() * 100).toInt() else 0
                                            
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedAgeGroupDetail = group },
                                                shape = RoundedCornerShape(12.dp),
                                                color = colors.second.copy(alpha = if (isDark) 0.5f else 0.6f),
                                                border = BorderStroke(1.dp, colors.first.copy(alpha = 0.25f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(CircleShape)
                                                            .background(colors.first.copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = null,
                                                            tint = colors.first,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = title,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = colors.first.copy(alpha = 0.15f)
                                                            ) {
                                                                Text(
                                                                    text = "$count คน ($pct%)",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = colors.first,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = mission,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                        contentDescription = "ดูรายชื่อ",
                                                        tint = colors.first.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (selectedStatsTab == 1) {
                                // --- TAB 1: NCDs CLINIC RISK PROFILE ---
                                val validBpScreenings = allScreenings.filter { it.systolic != null && it.diastolic != null }
                                val validSugarScreenings = allScreenings.filter { it.bloodSugar != null }
                                val validBmiScreenings = allScreenings.filter { it.bmi != null }

                                if (allScreenings.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.MonitorHeart,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "ยังไม่มีข้อมูลบันทึกการคัดกรองในหมู่บ้าน",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text("กลุ่มเสี่ยงและระดับความเสี่ยงของประชากร", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                    // BP breakdown
                                    val bpHigh = validBpScreenings.count { it.systolic!! >= 140 || it.diastolic!! >= 90 }
                                    val bpPreHigh = validBpScreenings.count { (it.systolic!! in 130..139) || (it.diastolic!! in 85..89) }
                                    val bpNormal = validBpScreenings.size - bpHigh - bpPreHigh

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("ระดับความดันโลหิต (คัดกรองทั้งหมด ${validBpScreenings.size} คน)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        CustomStackedMeter(
                                            greenValue = bpNormal,
                                            orangeValue = bpPreHigh,
                                            redValue = bpHigh,
                                            greenLabel = "ปกติ",
                                            orangeLabel = "เริ่มสูง",
                                            redLabel = "สูงอันตราย"
                                        )
                                    }

                                    // Sugar breakdown
                                    val sugarHigh = validSugarScreenings.count { it.bloodSugar!! >= 126 }
                                    val sugarPreHigh = validSugarScreenings.count { it.bloodSugar!! in 100..125 }
                                    val sugarNormal = validSugarScreenings.size - sugarHigh - sugarPreHigh

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("ระดับน้ำตาลในเลือด (คัดกรองทั้งหมด ${validSugarScreenings.size} คน)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        CustomStackedMeter(
                                            greenValue = sugarNormal,
                                            orangeValue = sugarPreHigh,
                                            redValue = sugarHigh,
                                            greenLabel = "ปกติ",
                                            orangeLabel = "เริ่มสูง",
                                            redLabel = "เสี่ยงเบาหวาน"
                                        )
                                    }

                                    // BMI breakdown
                                    val bmiObese = validBmiScreenings.count { it.bmi!! >= 25.0 }
                                    val bmiOverweight = validBmiScreenings.count { it.bmi!! >= 23.0 && it.bmi!! < 25.0 }
                                    val bmiNormal = validBmiScreenings.size - bmiObese - bmiOverweight

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("ดัชนีมวลกาย BMI (คัดกรองทั้งหมด ${validBmiScreenings.size} คน)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        CustomStackedMeter(
                                            greenValue = bmiNormal,
                                            orangeValue = bmiOverweight,
                                            redValue = bmiObese,
                                            greenLabel = "สมส่วน",
                                            orangeLabel = "ท้วม",
                                            redLabel = "อ้วน"
                                        )
                                    }
                                }
                            } else {
                                // --- TAB 2: GENDER DISTRIBUTION ---
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    val totalCount = maleCount + femaleCount
                                    val malePercent = if (totalCount > 0) (maleCount.toFloat() / totalCount) else 0f
                                    val femalePercent = if (totalCount > 0) (femaleCount.toFloat() / totalCount) else 0f

                                    Text(
                                        text = "อัตราส่วนเพศในพื้นที่รับผิดชอบ",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Male details
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Male, contentDescription = "ชาย", tint = Color(0xFF0284C7), modifier = Modifier.size(28.dp))
                                            Text("เพศชาย", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$maleCount คน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                            Text("${(malePercent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }

                                        // Female details
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Female, contentDescription = "หญิง", tint = Color(0xFFDB2777), modifier = Modifier.size(28.dp))
                                            Text("เพศหญิง", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$femaleCount คน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFDB2777))
                                            Text("${(femalePercent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Visual Segmented Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            if (malePercent > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .weight(malePercent)
                                                        .background(Color(0xFF0284C7))
                                                )
                                            }
                                            if (femalePercent > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .weight(femalePercent)
                                                        .background(Color(0xFFDB2777))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Recently Registered Citizens
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ผู้ลงทะเบียนล่าสุด",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ดูทั้งหมด (${totalPersonsCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onNavigateToHouseholds() }
                                .padding(4.dp)
                        )
                    }

                    if (recentCitizens.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "ยังไม่มีข้อมูลประชากรที่ลงทะเบียน",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = onNavigateToNewHousehold,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ลงทะเบียนครัวเรือนแรก")
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recentCitizens.forEach { (person, household) ->
                                val age = viewModel.calculateAge(person.birthDate, person.personStatus)
                                val isHead = person.houseStatus == HouseholdRole.HEAD

                                Card(
                                    onClick = { onNavigateToHouseDetail(household.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = CardShadowTint),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (person.gender == Gender.MALE) (if (isDark) Color(0xFF0C3348) else Color(0xFFE0F2FE))
                                                    else (if (isDark) Color(0xFF451528) else Color(0xFFFCE7F3))
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.Person,
                                                contentDescription = null,
                                                tint = if (person.gender == Gender.MALE) (if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                                                else (if (isDark) Color(0xFFF472B6) else Color(0xFFDB2777)),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = person.fullName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isHead) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isDark) StatusVerifiedBgDark else StatusVerifiedBg
                                                    ) {
                                                        Text(
                                                            text = "เจ้าบ้าน",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isDark) StatusVerifiedFgDark else StatusVerifiedFg,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(3.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "บ้านเลขที่ ${household.houseNo}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = " • ",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "อายุ: ${age ?: "-"} ปี",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "ดูรายละเอียด",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

/**
 * High-touch Quick Action Card for key app navigation destinations
 */
@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTintColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(18.dp), spotColor = CardShadowTint),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(24.dp))
                }

                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact Demographic KPI Card
 */
@Composable
fun CitizenStatCard(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    unit: String,
    badge: String? = null,
    icon: ImageVector,
    tint: Color,
    bg: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) {
            modifier
                .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = CardShadowTint)
                .clickable { onClick() }
        } else {
            modifier.shadow(2.dp, RoundedCornerShape(16.dp), spotColor = CardShadowTint)
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    badge?.let {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = tint.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = tint,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact pill for citizen status
 */
@Composable
fun StatusItemPill(
    label: String,
    count: Int,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = color
            )
        }
    }
}

@Composable
fun CustomStackedMeter(
    greenValue: Int,
    orangeValue: Int,
    redValue: Int,
    greenLabel: String,
    orangeLabel: String,
    redLabel: String,
    modifier: Modifier = Modifier
) {
    val total = (greenValue + orangeValue + redValue).coerceAtLeast(1)
    val greenWeight = greenValue.toFloat() / total
    val orangeWeight = orangeValue.toFloat() / total
    val redWeight = redValue.toFloat() / total

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (greenWeight > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(greenWeight)
                            .background(Color(0xFF2E7D32))
                    )
                }
                if (orangeWeight > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(orangeWeight)
                            .background(Color(0xFFF57C00))
                    )
                }
                if (redWeight > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(redWeight)
                            .background(Color(0xFFD32F2F))
                    )
                }
            }
        }

        // Legends with counts & percentages
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Green legend
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$greenLabel: $greenValue (${(greenWeight * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Orange legend
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF57C00)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$orangeLabel: $orangeValue (${(orangeWeight * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Red legend
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFD32F2F)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$redLabel: $redValue (${(redWeight * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
