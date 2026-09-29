package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Person
import com.example.data.PersonRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import android.content.Context
import android.net.Uri
import android.widget.Toast
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Cell

import com.example.data.Household
import com.example.data.HouseholdWithPersons
import java.time.Period
import com.example.utils.ValidationUtils

import com.example.data.VhvAgeGroup
import com.example.data.HouseSummary
import com.example.data.DataStatus
import com.example.data.Gender
import com.example.data.HouseholdRole
import com.example.data.PersonStatus
import com.example.data.PopulationEvent
import com.example.data.PopulationEventType
import com.example.data.sync.RoomFirestoreSyncHelper
import com.example.data.sync.SyncResult
import com.example.data.sync.SyncState

class PersonViewModel(
    private val repository: PersonRepository,
    private val excelImportUseCase: com.example.domain.ExcelImportUseCase,
    private val syncHelper: RoomFirestoreSyncHelper? = null
) : ViewModel() {

    val syncState: StateFlow<SyncState> = syncHelper?.syncState
        ?: kotlinx.coroutines.flow.MutableStateFlow(SyncState.Idle)

    fun syncToFirestore(onComplete: ((Result<SyncResult>) -> Unit)? = null) {
        if (syncHelper == null) return
        viewModelScope.launch {
            val result = syncHelper.syncRoomToFirestore()
            onComplete?.invoke(result)
        }
    }

    fun syncFromFirestore(onComplete: ((Result<SyncResult>) -> Unit)? = null) {
        if (syncHelper == null) return
        viewModelScope.launch {
            val result = syncHelper.syncFirestoreToRoom()
            onComplete?.invoke(result)
        }
    }

    fun bidirectionalSync(onComplete: ((Result<SyncResult>) -> Unit)? = null) {
        if (syncHelper == null) return
        viewModelScope.launch {
            val result = syncHelper.bidirectionalSync()
            onComplete?.invoke(result)
        }
    }

    fun resetSyncState() {
        syncHelper?.resetSyncState()
    }
    
    private val _importResult = kotlinx.coroutines.flow.MutableStateFlow<com.example.domain.ExcelImportResult?>(null)
    val importResult: StateFlow<com.example.domain.ExcelImportResult?> = _importResult
    
    private val _importPlan = kotlinx.coroutines.flow.MutableStateFlow<com.example.domain.ImportPlan?>(null)
    val importPlan: StateFlow<com.example.domain.ImportPlan?> = _importPlan

    private val _isImporting = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    fun clearImportResult() {
        _importResult.value = null
    }

    fun clearImportPlan() {
        _importPlan.value = null
    }

    fun importWorkspaceExcelFile(fileName: String = "ทะเบียนประชากร_หมู่8_รายงานสรุป-1.xlsx", onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            try {
                val possiblePaths = listOf(
                    fileName,
                    "ทะเบียนประชากร_หมู่8_รายงานสรุป.xlsx",
                    "/app/$fileName",
                    "app/$fileName"
                )
                var file: java.io.File? = null
                for (p in possiblePaths) {
                    val f = java.io.File(p)
                    if (f.exists()) {
                        file = f
                        break
                    }
                }

                if (file == null || !file.exists()) {
                    withContext(Dispatchers.Main) {
                        _isImporting.value = false
                        onComplete(false, "ไม่พบไฟล์ $fileName บนเครื่อง")
                    }
                    return@launch
                }

                val inputStream = file.inputStream()
                val plan = excelImportUseCase.createImportPlan(inputStream)
                _importPlan.value = plan
                withContext(Dispatchers.Main) {
                    _isImporting.value = false
                    onComplete(true, "วิเคราะห์โครงสร้างไฟล์และสร้างแผนการนำเข้าเรียบร้อย")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isImporting.value = false
                    onComplete(false, "เกิดข้อผิดพลาดในการนำเข้า: ${e.message}")
                }
            }
        }
    }

    fun commitCurrentImportPlan(
        operatorUid: String? = null,
        operatorName: String? = null,
        onComplete: (Boolean, String) -> Unit
    ) {
        val plan = _importPlan.value
        if (plan == null) {
            onComplete(false, "ไม่พบแผนการนำเข้าที่กำลังรอดำเนินการ")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            try {
                val result = excelImportUseCase.commitImportPlan(plan, operatorUid, operatorName)
                _importResult.value = result
                _importPlan.value = null
                withContext(Dispatchers.Main) {
                    _isImporting.value = false
                    onComplete(true, "นำเข้าข้อมูลสำเร็จ (${result.successCount} รายการ)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isImporting.value = false
                    onComplete(false, "เกิดข้อผิดพลาดในการบันทึกข้อมูล: ${e.message}")
                }
            }
        }
    }
    
    private val _villageFilter = MutableStateFlow<String?>(null)
    val villageFilter: StateFlow<String?> = _villageFilter

    fun setVillageFilter(villageNo: String?) {
        _villageFilter.value = villageNo
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allPersons: StateFlow<List<Person>> = _villageFilter.flatMapLatest { village ->
        if (village.isNullOrBlank()) {
            // Fallback or empty if not filtered? 
            // In a secure app, we should probably return empty if no village is set
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            repository.getAllPersonsByVillage(village)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allHouseholdsWithPersons: StateFlow<List<HouseholdWithPersons>> = _villageFilter.flatMapLatest { village ->
        if (village.isNullOrBlank()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            repository.getAllHouseholdsWithPersonsByVillage(village)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allHouseholds: StateFlow<List<Household>> = allHouseholdsWithPersons
        .map { list -> list.map { it.household } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val totalPersonsCount: StateFlow<Int> = _villageFilter.flatMapLatest { village ->
        if (village.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(0)
        else repository.getTotalPersonsCountByVillage(village)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val totalHouseholdsCount: StateFlow<Int> = _villageFilter.flatMapLatest { village ->
        if (village.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(0)
        else repository.getTotalHouseholdsCountByVillage(village)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val allScreenings: StateFlow<List<com.example.data.HealthScreening>> = repository.getAllScreenings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun insertHousehold(household: Household, onComplete: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repository.insertHousehold(household.copy(lastModified = System.currentTimeMillis()))
            withContext(Dispatchers.Main) {
                onComplete(id)
            }
        }
    }

    fun addNewHouseholdWithHead(
        houseNo: String,
        headName: String,
        latitude: Double?,
        longitude: Double?,
        onComplete: (Long) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uuid = java.util.UUID.randomUUID().toString()
            val household = Household(
                householdUuid = uuid,
                houseNo = houseNo.ifBlank { "000/0" },
                latitude = latitude,
                longitude = longitude,
                locationProvider = if (latitude != null && longitude != null) "MANUAL_DIALOG" else null,
                locationCapturedAt = if (latitude != null && longitude != null) System.currentTimeMillis() else null,
                lastModified = System.currentTimeMillis()
            )
            val houseId = repository.insertHousehold(household)
            
            if (headName.isNotBlank()) {
                val person = Person(
                    personUuid = java.util.UUID.randomUUID().toString(),
                    householdId = houseId,
                    fullName = headName.trim(),
                    houseStatus = HouseholdRole.HEAD,
                    lastModified = System.currentTimeMillis()
                )
                repository.insert(person)
            }
            
            withContext(Dispatchers.Main) {
                onComplete(houseId)
            }
        }
    }
    fun updateHousehold(household: Household) = viewModelScope.launch {
        repository.updateHousehold(household.copy(lastModified = System.currentTimeMillis()))
    }

    fun updateHouseholdDataStatus(household: Household, status: DataStatus) = viewModelScope.launch {
        repository.updateHousehold(household.copy(dataStatus = status, lastModified = System.currentTimeMillis()))
    }

    fun updateHouseholdLocation(
        householdId: Long,
        latitude: Double,
        longitude: Double,
        provider: String = "MANUAL_PIN",
        accuracy: Float? = null,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = repository.getHouseholdById(householdId)
                if (existing == null) {
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(false, "ไม่พบข้อมูลครัวเรือน ID: $householdId")
                    }
                    return@launch
                }
                val updated = existing.copy(
                    latitude = latitude,
                    longitude = longitude,
                    locationAccuracy = accuracy,
                    locationCapturedAt = System.currentTimeMillis(),
                    locationProvider = provider,
                    lastModified = System.currentTimeMillis()
                )
                repository.updateHousehold(updated)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.message ?: "เกิดข้อผิดพลาดในการบันทึกพิกัด")
                }
            }
        }
    }

    fun removeHouseholdLocation(
        householdId: Long,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = repository.getHouseholdById(householdId)
                if (existing == null) {
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(false, "ไม่พบข้อมูลครัวเรือน ID: $householdId")
                    }
                    return@launch
                }
                val updated = existing.copy(
                    latitude = null,
                    longitude = null,
                    locationAccuracy = null,
                    locationCapturedAt = null,
                    locationProvider = null,
                    lastModified = System.currentTimeMillis()
                )
                repository.updateHousehold(updated)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.message ?: "เกิดข้อผิดพลาดในการลบพิกัด")
                }
            }
        }
    }
    fun deleteHousehold(household: Household, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("PersonViewModel", "Starting local-first delete household: id=${household.id}, uuid=${household.householdUuid}")
                
                // Fetch member UUIDs before deletion for Cloud tombstone matching
                val personUuids = repository.getPersonsByHouseholdIdList(household.id).map { it.personUuid }

                // 1. Delete locally in Room Database FIRST (Local-First Architecture)
                val localResult = repository.deleteHousehold(household)
                if (localResult.isFailure) {
                    val ex = localResult.exceptionOrNull()
                    withContext(Dispatchers.Main) {
                        onResult(false, ex?.message ?: "เกิดข้อผิดพลาดในการลบข้อมูลครัวเรือนในเครื่อง")
                    }
                    return@launch
                }

                // 2. Best-effort Cloud Firestore tombstone creation (does not block local deletion if offline)
                if (syncHelper != null && syncHelper.isFirebaseConfigured()) {
                    try {
                        syncHelper.deleteHouseholdFromFirestore(household.householdUuid, personUuids)
                    } catch (e: Exception) {
                        android.util.Log.w("PersonViewModel", "Cloud tombstone write deferred: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    android.util.Log.d("PersonViewModel", "Household deleted successfully (Local & queued Cloud tombstone)")
                    onResult(true, null)
                }
            } catch (e: Exception) {
                android.util.Log.e("PersonViewModel", "Exception deleting household", e)
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "เกิดข้อผิดพลาดที่ไม่คาดคิด")
                }
            }
        }
    }
    
    suspend fun getHouseholdById(id: Long): Household? = repository.getHouseholdById(id)
    fun getHouseholdWithPersonsById(id: Long) = repository.getHouseholdWithPersonsById(id)

    fun exportToCsv(
        context: Context,
        onComplete: (Boolean, Uri?, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val households = repository.getAllHouseholds().associateBy { it.id }
                val persons = repository.getAllPersonsList()

                val exportDir = java.io.File(context.cacheDir, "exports")
                if (!exportDir.exists()) exportDir.mkdirs()
                val fileName = "smart_osm_export_${System.currentTimeMillis()}.csv"
                val file = java.io.File(exportDir, fileName)

                file.bufferedWriter().use { writer ->
                    // Write CSV Header
                    writer.write("HouseholdUUID,PersonUUID,HouseNo,VillageNo,Subdistrict,District,Province,NationalID,FullName,Gender,BirthDate,Status")
                    writer.newLine()
                    
                    persons.forEach { p ->
                        val h = households[p.householdId]
                        val row = listOf(
                            h?.householdUuid ?: "",
                            p.personUuid,
                            h?.houseNo ?: "",
                            h?.villageNo ?: "",
                            h?.subdistrict ?: "",
                            h?.district ?: "",
                            h?.province ?: "",
                            p.nationalId ?: "",
                            p.fullName,
                            p.gender.name,
                            p.birthDate?.toString() ?: "",
                            p.personStatus.name
                        ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
                        writer.write(row)
                        writer.newLine()
                    }
                }

                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                withContext(Dispatchers.Main) {
                    onComplete(true, contentUri, "เตรียมไฟล์ CSV เรียบร้อยแล้ว (${persons.size} รายการ)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, null, "เกิดข้อผิดพลาดในการสร้างไฟล์ CSV: ${e.message}")
                }
            }
        }
    }

    fun insert(person: Person) = viewModelScope.launch { 
        repository.insert(person.copy(lastModified = System.currentTimeMillis())) 
    }
    fun update(person: Person) = viewModelScope.launch { 
        repository.update(person.copy(lastModified = System.currentTimeMillis())) 
    }
    fun delete(person: Person, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Delete locally in Room FIRST (Local-First Architecture)
                repository.delete(person)
                
                // 2. Best-effort Cloud Firestore tombstone creation
                val helper = syncHelper
                if (helper != null && helper.isFirebaseConfigured()) {
                    try {
                        helper.deletePersonFromFirestore(person.personUuid)
                    } catch (e: Exception) {
                        android.util.Log.w("PersonViewModel", "Cloud person tombstone write deferred: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } catch (e: Exception) {
                android.util.Log.e("PersonViewModel", "Exception deleting person", e)
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "เกิดข้อผิดพลาดที่ไม่คาดคิดในการลบข้อมูลบุคคล")
                }
            }
        }
    }
    
    suspend fun getPersonById(id: Long): Person? = repository.getPersonById(id)
    suspend fun getPersonByNationalId(nationalId: String): Person? = repository.getPersonByNationalId(nationalId)
    
    fun validateThaiNationalId(id: String): Boolean = ValidationUtils.isValidThaiNationalId(id)

    fun calculateAge(birthDate: LocalDate?, personStatus: com.example.data.PersonStatus): Int? {
        if (personStatus == com.example.data.PersonStatus.DEAD || birthDate == null) return null
        return Period.between(birthDate, LocalDate.now()).years
    }

    fun getAgeGroup(age: Int?): String {
        return VhvAgeGroup.fromAge(age).value
    }

    val ageGroupSummary: StateFlow<Map<String, Int>> = allPersons.map { persons ->
        val summary = mutableMapOf<String, Int>().withDefault { 0 }
        // Ensure all categories are present even if count is 0
        VhvAgeGroup.entries.forEach { group ->
            if (group != VhvAgeGroup.UNKNOWN) {
                summary[group.value] = 0
            }
        }
        
        persons.forEach { person ->
            if (person.personStatus == PersonStatus.ALIVE) {
                val age = calculateAge(person.birthDate, person.personStatus)
                val group = VhvAgeGroup.fromAge(age)
                if (group != VhvAgeGroup.UNKNOWN) {
                    summary[group.value] = summary.getValue(group.value) + 1
                }
            }
        }
        summary
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val houseSummary: StateFlow<List<HouseSummary>> = _villageFilter.flatMapLatest { village ->
        if (village.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else repository.getHouseSummaryByVillage(village)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allEvents: StateFlow<List<PopulationEvent>> = repository.allEvents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun insertEvent(event: PopulationEvent) = viewModelScope.launch {
        repository.insertEvent(event)
    }

    fun deleteEvent(event: PopulationEvent) = viewModelScope.launch {
        repository.deleteEvent(event)
    }

    fun getHistoryForPerson(personId: Long) = repository.getHistoryForPerson(personId)

    // Health Screening Operations
    fun getScreeningsForPerson(personId: Long) = repository.getScreeningsForPerson(personId)

    fun insertScreening(screening: com.example.data.HealthScreening) = viewModelScope.launch {
        repository.insertScreening(screening)
    }

    fun updateScreening(screening: com.example.data.HealthScreening) = viewModelScope.launch {
        repository.updateScreening(screening)
    }

    fun deleteScreening(screening: com.example.data.HealthScreening) = viewModelScope.launch {
        repository.deleteScreening(screening)
    }

    fun importExcelData(context: Context, uri: Uri) {
        if (_isImporting.value) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            var inputStream: java.io.InputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val plan = excelImportUseCase.createImportPlan(inputStream)
                    _importPlan.value = plan
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "ไม่สามารถเปิดไฟล์ได้", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                inputStream?.close()
                _isImporting.value = false
            }
        }
    }

    private val googleSheetsService = com.example.domain.GoogleSheetsService()

    fun loadGoogleSheetsImportPlan(
        context: Context,
        spreadsheetId: String,
        accessToken: String?,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (_isImporting.value) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val result = googleSheetsService.downloadSpreadsheetAsXlsx(spreadsheetId, accessToken)
                if (result.isSuccess) {
                    val inputStream = result.getOrThrow()
                    val plan = excelImportUseCase.createImportPlan(inputStream)
                    _importPlan.value = plan
                    onComplete(true, "สร้างแผนการนำเข้าจาก Google Sheets สำเร็จ (${plan.plannedItems.size} รายการ)")
                } else {
                    val ex = result.exceptionOrNull()
                    val errorMsg = ex?.message ?: "ไม่สามารถดาวน์โหลดไฟล์ได้"
                    onComplete(false, errorMsg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, "เกิดข้อผิดพลาด: ${e.message}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun exportExcelData(context: Context, uri: Uri, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
                val sheet = workbook.createSheet(com.example.domain.SmartOsmExcelSchema.SHEET_NAME)
                
                val hRow = sheet.createRow(0)
                val headers = com.example.domain.SmartOsmExcelSchema.CANONICAL_COLUMNS
                headers.forEachIndexed { idx, title ->
                    hRow.createCell(idx).setCellValue(title)
                }

                val households = repository.getAllHouseholds().associateBy { it.id }
                val persons = repository.getAllPersonsList()

                persons.forEachIndexed { index, p ->
                    val row = sheet.createRow(index + 1)
                    val h = households[p.householdId]

                    row.createCell(0).setCellValue(com.example.domain.SmartOsmExcelSchema.SCHEMA_VERSION)
                    row.createCell(1).setCellValue(h?.householdUuid ?: "")
                    row.createCell(2).setCellValue(p.personUuid)
                    row.createCell(3).setCellValue(h?.houseNo ?: "")
                    row.createCell(4).setCellValue(h?.villageNo ?: "")
                    row.createCell(5).setCellValue(h?.subdistrict ?: "")
                    row.createCell(6).setCellValue(h?.district ?: "")
                    row.createCell(7).setCellValue(h?.province ?: "")
                    row.createCell(8).setCellValue(p.nationalId ?: "")
                    row.createCell(9).setCellValue(p.fullName)
                    row.createCell(10).setCellValue(p.gender.name)
                    row.createCell(11).setCellValue(p.birthDate?.toString() ?: "")
                    row.createCell(12).setCellValue(if (p.isBirthYearOnly) "YEAR" else "DAY")
                    row.createCell(13).setCellValue(p.houseStatus.name)
                    row.createCell(14).setCellValue(p.personStatus.name)
                    row.createCell(15).setCellValue(p.dataStatus.name)
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    workbook.write(outputStream)
                }
                workbook.close()

                withContext(Dispatchers.Main) {
                    onComplete(true, "ส่งออกข้อมูล Smart_Osm Schema V1 สำเร็จ")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, "เกิดข้อผิดพลาด: ${e.message}")
                }
            }
        }
    }

    /**
     * Exports population registry database as a temporary Excel file and returns a shareable content Uri
     * for opening or saving via Android System Chooser (Google Drive, Microsoft OneDrive, Dropbox, LINE, etc.).
     */
    fun exportAndShareExcelData(
        context: Context,
        onComplete: (Boolean, Uri?, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
                val sheet = workbook.createSheet(com.example.domain.SmartOsmExcelSchema.SHEET_NAME)

                val hRow = sheet.createRow(0)
                val headers = com.example.domain.SmartOsmExcelSchema.CANONICAL_COLUMNS
                headers.forEachIndexed { idx, title ->
                    hRow.createCell(idx).setCellValue(title)
                }

                val households = repository.getAllHouseholds().associateBy { it.id }
                val persons = repository.getAllPersonsList()

                persons.forEachIndexed { index, p ->
                    val row = sheet.createRow(index + 1)
                    val h = households[p.householdId]

                    row.createCell(0).setCellValue(com.example.domain.SmartOsmExcelSchema.SCHEMA_VERSION)
                    row.createCell(1).setCellValue(h?.householdUuid ?: "")
                    row.createCell(2).setCellValue(p.personUuid)
                    row.createCell(3).setCellValue(h?.houseNo ?: "")
                    row.createCell(4).setCellValue(h?.villageNo ?: "")
                    row.createCell(5).setCellValue(h?.subdistrict ?: "")
                    row.createCell(6).setCellValue(h?.district ?: "")
                    row.createCell(7).setCellValue(h?.province ?: "")
                    row.createCell(8).setCellValue(p.nationalId ?: "")
                    row.createCell(9).setCellValue(p.fullName)
                    row.createCell(10).setCellValue(p.gender.name)
                    row.createCell(11).setCellValue(p.birthDate?.toString() ?: "")
                    row.createCell(12).setCellValue(if (p.isBirthYearOnly) "YEAR" else "DAY")
                    row.createCell(13).setCellValue(p.houseStatus.name)
                    row.createCell(14).setCellValue(p.personStatus.name)
                    row.createCell(15).setCellValue(p.dataStatus.name)
                }

                val exportDir = java.io.File(context.cacheDir, "exports")
                if (!exportDir.exists()) exportDir.mkdirs()
                val fileName = "smart_osm_backup_${System.currentTimeMillis()}.xlsx"
                val file = java.io.File(exportDir, fileName)

                file.outputStream().use { fos ->
                    workbook.write(fos)
                }
                workbook.close()

                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                withContext(Dispatchers.Main) {
                    onComplete(true, contentUri, "เตรียมไฟล์สำรองข้อมูลเรียบร้อยแล้ว (${persons.size} รายการ)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, null, "เกิดข้อผิดพลาดในการสร้างไฟล์แชร์: ${e.message}")
                }
            }
        }
    }
}
