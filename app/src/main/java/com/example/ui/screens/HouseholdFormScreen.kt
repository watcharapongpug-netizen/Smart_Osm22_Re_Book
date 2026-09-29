package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DataStatus
import com.example.data.Household
import com.example.data.HouseholdRole
import com.example.data.Person
import com.example.ui.components.IdCardScannerDialog
import com.example.ui.theme.*
import com.example.utils.IdCardScanResult
import com.example.utils.NationalIdBarcodeParser
import com.example.viewmodel.PersonViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HouseholdFormScreen(
    viewModel: PersonViewModel,
    householdId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    val activeVillage by viewModel.villageFilter.collectAsState()
    
    var houseNo by remember { mutableStateOf("") }
    var houseId by remember { mutableStateOf("") }
    var villageNo by remember { mutableStateOf(activeVillage ?: "") }
    var villageName by remember { mutableStateOf("") }
    var subdistrict by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    
    // Real list for Subdistrict Pa Kha, District Ban Na, Province Nakhon Nayok
    val villageOptions = listOf(
        "1" to "หมู่ 1 บ้านหนองเคี่ยม",
        "2" to "หมู่ 2 บ้านคลองผักหนาม",
        "3" to "หมู่ 3 บ้านป่าขะ",
        "4" to "หมู่ 4 บ้านท่ามะเฟือง",
        "5" to "หมู่ 5 บ้านโคกประเสริฐ",
        "6" to "หมู่ 6 บ้านหนองยาง",
        "7" to "หมู่ 7 บ้านกร่างประตูวัง",
        "8" to "หมู่ 8 บ้านคลองส่ง",
        "9" to "หมู่ 9 บ้านคลองกระโดน",
        "10" to "หมู่ 10 บ้านต้นกระบก",
        "11" to "หมู่ 11 บ้านดงขี้พุก",
        "12" to "หมู่ 12 บ้านทุ่งกระโปรง",
        "13" to "หมู่ 13 บ้านคลองนางหงษ์"
    )
    
    // Head of household fields
    var headNationalId by remember { mutableStateOf("") }
    var headName by remember { mutableStateOf("") }
    
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var locationAccuracy by remember { mutableStateOf<Float?>(null) }
    var locationCapturedAt by remember { mutableStateOf<Long?>(null) }
    var locationProvider by remember { mutableStateOf<String?>(null) }
    var dataStatus by remember { mutableStateOf(DataStatus.NEEDS_REVIEW) }
    
    // Scanner Dialog state
    var showScannerDialog by remember { mutableStateOf(false) }
    var lastScanResult by remember { mutableStateOf<IdCardScanResult?>(null) }
    
    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(activeVillage, villageNo) {
        if (villageName.isBlank() && !villageNo.isBlank()) {
            villageName = villageOptions.find { it.first == villageNo }?.second ?: "หมู่ที่ $villageNo"
        }
    }

    LaunchedEffect(householdId) {
        if (householdId != -1L) {
            val household = viewModel.getHouseholdById(householdId)
            household?.let {
                houseNo = it.houseNo
                houseId = it.houseId ?: ""
                villageNo = it.villageNo
                villageName = villageOptions.find { opt -> opt.first == it.villageNo }?.second ?: "หมู่ที่ ${it.villageNo}"
                subdistrict = it.subdistrict
                district = it.district
                province = it.province
                latitude = it.latitude
                longitude = it.longitude
                locationAccuracy = it.locationAccuracy
                locationCapturedAt = it.locationCapturedAt
                locationProvider = it.locationProvider
                dataStatus = it.dataStatus
            }
        }
    }

    if (showScannerDialog) {
        IdCardScannerDialog(
            onDismiss = { showScannerDialog = false },
            onScanned = { result ->
                if (!result.houseNo.isNullOrBlank()) houseNo = result.houseNo
                if (!result.villageNo.isNullOrBlank()) villageNo = result.villageNo
                if (!result.subdistrict.isNullOrBlank()) subdistrict = result.subdistrict
                if (!result.district.isNullOrBlank()) district = result.district
                if (!result.province.isNullOrBlank()) province = result.province
                if (!result.fullName.isNullOrBlank()) headName = result.fullName
                if (!result.nationalId.isNullOrBlank()) headNationalId = result.nationalId
                
                lastScanResult = result
                Toast.makeText(context, "กรอกข้อมูลอัตโนมัติจากการสแกนสำเร็จ", Toast.LENGTH_SHORT).show()
            },
            viewModel = viewModel
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (householdId == -1L) "เพิ่มครัวเรือนใหม่" else "แก้ไขข้อมูลครัวเรือน",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ย้อนกลับ", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showScannerDialog = true }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "สแกนบัตรประชาชน", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldPrimary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (houseNo.isNotBlank()) {
                        val household = Household(
                            id = if (householdId == -1L) 0 else householdId,
                            houseNo = houseNo.trim(),
                            houseId = houseId.trim().ifBlank { null },
                            villageNo = villageNo.trim(),
                            subdistrict = subdistrict.trim(),
                            district = district.trim(),
                            province = province.trim(),
                            latitude = latitude,
                            longitude = longitude,
                            locationAccuracy = locationAccuracy,
                            locationCapturedAt = locationCapturedAt,
                            locationProvider = locationProvider,
                            dataStatus = dataStatus
                        )
                        if (householdId == -1L) {
                            viewModel.insertHousehold(household) { newId ->
                                // If head of household information was provided, create and link person record
                                if (headName.isNotBlank() || headNationalId.isNotBlank()) {
                                    val headPerson = Person(
                                        householdId = newId,
                                        nationalId = headNationalId.trim().ifBlank { null },
                                        fullName = headName.trim().ifBlank { "หัวหน้าครัวเรือน $houseNo" },
                                        houseStatus = HouseholdRole.HEAD,
                                        lastModified = System.currentTimeMillis()
                                    )
                                    viewModel.insert(headPerson)
                                }
                                onNavigateToDetail(newId)
                            }
                        } else {
                            viewModel.updateHousehold(household)
                            onNavigateBack()
                        }
                    } else {
                        Toast.makeText(context, "กรุณากรอกบ้านเลขที่", Toast.LENGTH_SHORT).show()
                    }
                },
                icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                text = { Text("บันทึกครัวเรือน", fontWeight = FontWeight.Bold) },
                containerColor = EmeraldPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(16.dp), spotColor = CardShadowTint)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Action Card: Barcode Scanner Auto-Fill
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = CardShadowTint),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldPrimary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.QrCodeScanner,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    "สแกนบาร์โค้ดบัตรประชาชน",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "ML Kit Smart Auto-fill",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Text(
                        "สแกนบาร์โค้ด Code 128 ด้านหลังบัตร หรือ QR Code เพื่อกรอกข้อมูลบ้านเลขที่ ที่อยู่ และหัวหน้าครัวเรือนอัตโนมัติ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { showScannerDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("เปิดกล้องสแกนบัตรประชาชน", fontWeight = FontWeight.Bold)
                    }

                    AnimatedVisibility(visible = lastScanResult != null) {
                        lastScanResult?.let { res ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "ข้อมูลจากการสแกน: ${res.displaySummary}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Card 1: Address Info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(18.dp), spotColor = CardShadowTint),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp, 16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(EmeraldPrimary)
                        )
                        Text("ข้อมูลที่อยู่ครัวเรือน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = houseNo,
                            onValueChange = { houseNo = it },
                            label = { Text("บ้านเลขที่ *") },
                            placeholder = { Text("เช่น 123/4") },
                            leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null, tint = EmeraldPrimary) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = houseId,
                            onValueChange = { houseId = it },
                            label = { Text("รหัสประจำบ้าน") },
                            placeholder = { Text("11 หลัก") },
                            leadingIcon = { Icon(Icons.Filled.Numbers, contentDescription = null, tint = EmeraldPrimary) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = villageName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("หมู่ที่ / หมู่บ้าน") },
                            leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, tint = EmeraldPrimary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            villageOptions.forEach { (no, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        villageNo = no
                                        villageName = name
                                        expanded = false
                                        
                                        // Auto-fill address details for the subdistrict if empty
                                        if (subdistrict.isBlank()) subdistrict = "ต.ป่าขะ"
                                        if (district.isBlank()) district = "อ.บ้านนา"
                                        if (province.isBlank()) province = "จ.นครนายก"
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = subdistrict,
                            onValueChange = { subdistrict = it },
                            label = { Text("ตำบล") },
                            placeholder = { Text("ตำบล") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = district,
                            onValueChange = { district = it },
                            label = { Text("อำเภอ") },
                            placeholder = { Text("อำเภอ") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = province,
                        onValueChange = { province = it },
                        label = { Text("จังหวัด") },
                        placeholder = { Text("จังหวัด เช่น เชียงใหม่") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Card 2: Head of Household Info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(18.dp), spotColor = CardShadowTint),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp, 16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MintAccent)
                        )
                        Text("ข้อมูลหัวหน้าครัวเรือน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    OutlinedTextField(
                        value = headNationalId,
                        onValueChange = { headNationalId = it },
                        label = { Text("เลขประจำตัวประชาชน 13 หลัก") },
                        placeholder = { Text("เช่น 1509900123456") },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = EmeraldPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = headName,
                        onValueChange = { headName = it },
                        label = { Text("ชื่อ-นามสกุล หัวหน้าครัวเรือน") },
                        placeholder = { Text("เช่น นายสมชาย ใจดี") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = EmeraldPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        "เมื่อบันทึก ระบบจะลงทะเบียนบุคคลนี้เป็นหัวหน้าครัวเรือนให้อัตโนมัติ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Card 3: GPS Telemetry
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(18.dp), spotColor = CardShadowTint),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp, 16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TealSecondary)
                        )
                        Text("พิกัดแผนที่ (GPS Tracking)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    if (latitude != null && longitude != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StatusVerifiedBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = StatusVerifiedFg, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Lat: $latitude, Lon: $longitude", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                locationAccuracy?.let { acc ->
                                    val isGood = acc <= 20f
                                    Text(
                                        "ความแม่นยำ: ±${acc}m (${if (isGood) "ระดับสูง" else "ปานกลาง"})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isGood) StatusVerifiedFg else StatusNeedsReviewFg,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ยังไม่ได้บันทึกพิกัดตำแหน่งบ้าน", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (locationPermissionState.status.isGranted) {
                                coroutineScope.launch {
                                    try {
                                        Toast.makeText(context, "กำลังระบุพิกัดความแม่นยำสูง...", Toast.LENGTH_SHORT).show()
                                        @SuppressLint("MissingPermission")
                                        val locationRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
                                            .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                                            .build()
                                        @SuppressLint("MissingPermission")
                                        val location = fusedLocationClient.getCurrentLocation(locationRequest, null).await()
                                        if (location != null) {
                                            latitude = location.latitude
                                            longitude = location.longitude
                                            locationAccuracy = if (location.hasAccuracy()) location.accuracy else null
                                            locationCapturedAt = location.time
                                            locationProvider = location.provider
                                            Toast.makeText(context, "บันทึกพิกัดสำเร็จ", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "ไม่สามารถหาตำแหน่งได้", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                locationPermissionState.launchPermissionRequest()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (latitude == null) "ดึงพิกัด GPS ปัจจุบัน" else "อัปเดตพิกัดใหม่", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
