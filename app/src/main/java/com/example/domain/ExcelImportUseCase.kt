package com.example.domain

import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.DataStatus
import com.example.data.Gender
import com.example.data.Household
import com.example.data.HouseholdRole
import com.example.data.Person
import com.example.data.PersonHistory
import com.example.data.PersonStatus
import com.example.utils.NationalIdStatus
import com.example.utils.ValidationUtils
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ExcelImportUseCase(
    private val database: AppDatabase
) {
    suspend fun createImportPlan(
        inputStream: InputStream,
        targetVillageNo: String? = null
    ): ImportPlan {
        val errors = mutableListOf<ImportError>()
        val plannedItems = mutableListOf<PlannedPersonImport>()
        var totalRows = 0

        val householdDao = database.householdDao()
        val personDao = database.personDao()

        val existingHouseholds = if (targetVillageNo.isNullOrBlank()) {
            householdDao.getAllHouseholds()
        } else {
            householdDao.getHouseholdsByVillageNoList(targetVillageNo)
        }
        
        val householdsByUuid = existingHouseholds.associateBy { it.householdUuid }
        val householdsGroupedByAddress = existingHouseholds.groupBy { h ->
            buildAddressKey(h.houseNo, h.villageNo, h.subdistrict, h.district, h.province)
        }

        val existingPersons = if (targetVillageNo.isNullOrBlank()) {
            personDao.getAllPersonsList()
        } else {
            val houseIds = existingHouseholds.map { it.id }.toSet()
            personDao.getAllPersonsList().filter { it.householdId in houseIds }
        }

        val personsByUuid = existingPersons.associateBy { it.personUuid }
        val personsByNationalId = existingPersons.filter { !it.nationalId.isNullOrBlank() }.associateBy { it.nationalId!! }

        val seenPersonUuidsInFile = mutableSetOf<String>()
        val seenNationalIdsInFile = mutableSetOf<String>()

        try {
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            // Step 1: Detect Header Row and build Header Mapping
            var headerRowIndex = -1
            val headerColumnMap = mutableMapOf<String, Int>()

            for (r in 0..minOf(5, sheet.lastRowNum)) {
                val row = sheet.getRow(r) ?: continue
                val matched = mutableMapOf<String, Int>()
                for (c in 0 until row.lastCellNum) {
                    val cellVal = getCellValueAsString(row.getCell(c))
                    val key = SmartOsmExcelSchema.resolveColumnKey(cellVal)
                    if (key != null) {
                        matched[key] = c
                    }
                }
                if (matched.containsKey(SmartOsmExcelSchema.COL_HOUSE_NO) ||
                    matched.containsKey(SmartOsmExcelSchema.COL_NATIONAL_ID) ||
                    matched.containsKey(SmartOsmExcelSchema.COL_FULL_NAME) ||
                    matched.containsKey(SmartOsmExcelSchema.COL_SCHEMA_VERSION)) {
                    headerRowIndex = r
                    headerColumnMap.putAll(matched)
                    break
                }
            }

            val startRow = if (headerRowIndex >= 0) headerRowIndex + 1 else 1

            for (i in startRow..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                totalRows++

                val houseNo = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_HOUSE_NO, defaultIdx = 3).trim()
                val villageNo = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_VILLAGE_NO, defaultIdx = 4).trim()
                val subdistrict = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_SUBDISTRICT, defaultIdx = 5).trim()
                val district = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_DISTRICT, defaultIdx = 6).trim()
                val province = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_PROVINCE, defaultIdx = 7).trim()

                val rawHouseholdUuid = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_HOUSEHOLD_UUID, defaultIdx = 1).trim()
                val rawPersonUuid = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_PERSON_UUID, defaultIdx = 2).trim()

                val rawNatId = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_NATIONAL_ID, defaultIdx = 8).trim()
                val fullName = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_FULL_NAME, defaultIdx = 9).trim()
                val rawGender = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_GENDER, defaultIdx = 10).trim()
                
                val dobCell = getCellObjectByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_BIRTH_DATE, defaultIdx = 11)
                val rawPrecision = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_BIRTH_DATE_PRECISION, defaultIdx = 12).trim()
                val rawHouseStatus = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_HOUSE_STATUS, defaultIdx = 13).trim()
                val rawPersonStatus = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_PERSON_STATUS, defaultIdx = 14).trim()
                val rawDataStatus = getCellByHeader(row, headerColumnMap, SmartOsmExcelSchema.COL_DATA_STATUS, defaultIdx = 15).trim()

                // Skip blank row
                if (houseNo.isBlank() && rawNatId.isBlank() && fullName.isBlank()) {
                    totalRows--
                    continue
                }

                val rowNum = i + 1
                val reviewReasons = mutableListOf<String>()
                var isDuplicateUuid = false
                var isAmbiguousHousehold = false

                if (houseNo.isBlank()) {
                    errors.add(ImportError(rowNum, "ไม่มีข้อมูลบ้านเลขที่"))
                    continue
                }

                // Regional Partitioning Enforcement: Block cross-village imports
                if (!targetVillageNo.isNullOrBlank() && villageNo.isNotBlank() && villageNo != targetVillageNo) {
                    errors.add(ImportError(rowNum, "ไม่สามารถนำเข้าข้อมูลข้ามหมู่บ้านได้ (ข้อมูลในไฟล์คือหมู่ $villageNo แต่คุณรับผิดชอบหมู่ $targetVillageNo)"))
                    continue
                }

                // National ID processing & status
                val normalizedNatId = ValidationUtils.normalizeNationalId(rawNatId).ifBlank { null }
                val nationalIdStatus = ValidationUtils.checkNationalIdStatus(normalizedNatId)
                if (nationalIdStatus == NationalIdStatus.INVALID) {
                    reviewReasons.add("เลขบัตรประชาชนไม่ถูกต้อง ($rawNatId)")
                }

                // Date parsing & precision
                val parsedDateResult = parseDateCell(dobCell)
                val birthDate = parsedDateResult.first
                val isBirthYearOnly = if (rawPrecision.equals("YEAR", ignoreCase = true) || rawPrecision.equals("ปี", ignoreCase = true)) {
                    true
                } else if (rawPrecision.equals("DAY", ignoreCase = true) || rawPrecision.equals("วัน", ignoreCase = true)) {
                    false
                } else {
                    parsedDateResult.second
                }

                if (birthDate == null) {
                    reviewReasons.add("วันเกิดไม่ถูกต้องหรือว่างเปล่า")
                }

                // UUID processing & duplicate detection
                // Requirement 5: Duplicate UUID -> NEEDS_REVIEW ห้ามสร้าง UUID ใหม่
                val personUuid = if (rawPersonUuid.isNotBlank()) {
                    if (!seenPersonUuidsInFile.add(rawPersonUuid)) {
                        isDuplicateUuid = true
                        reviewReasons.add("Duplicate personUuid within file: $rawPersonUuid (ห้ามสร้าง UUID ใหม่)")
                    }
                    rawPersonUuid
                } else {
                    UUID.randomUUID().toString().also { seenPersonUuidsInFile.add(it) }
                }

                val householdUuid = rawHouseholdUuid.ifBlank {
                    UUID.randomUUID().toString()
                }

                // Duplicate National ID in file
                if (normalizedNatId != null) {
                    if (!seenNationalIdsInFile.add(normalizedNatId)) {
                        reviewReasons.add("เลขบัตรประชาชนซ้ำในไฟล์ Excel ($normalizedNatId)")
                    }
                }

                // Household Matching
                // Requirement 8: Household ambiguous match -> NEEDS_REVIEW
                var targetHouseholdId: Long = 0
                var isNewHousehold = false
                val addressKey = buildAddressKey(houseNo, villageNo, subdistrict, district, province)

                val matchedByUuid = if (rawHouseholdUuid.isNotBlank()) householdsByUuid[rawHouseholdUuid] else null
                val householdCandidate: Household = if (matchedByUuid != null) {
                    targetHouseholdId = matchedByUuid.id
                    matchedByUuid
                } else {
                    val matchingAddressList = householdsGroupedByAddress[addressKey] ?: emptyList()
                    if (matchingAddressList.size == 1) {
                        val h = matchingAddressList.first()
                        targetHouseholdId = h.id
                        h
                    } else if (matchingAddressList.size > 1) {
                        isAmbiguousHousehold = true
                        reviewReasons.add("พบครัวเรือนที่ตรงกับที่อยู่นี้มากกว่า 1 รายการ (${matchingAddressList.size} หลัง) — Ambiguous Match")
                        matchingAddressList.first()
                    } else {
                        isNewHousehold = true
                        Household(
                            householdUuid = householdUuid,
                            houseNo = houseNo,
                            villageNo = villageNo,
                            subdistrict = subdistrict,
                            district = district,
                            province = province,
                            dataStatus = if (reviewReasons.isEmpty()) DataStatus.VERIFIED else DataStatus.NEEDS_REVIEW
                        )
                    }
                }

                // Person Matching
                val existingPerson = personsByUuid[personUuid]
                    ?: (if (normalizedNatId != null) personsByNationalId[normalizedNatId] else null)

                // Detect DB-level UUID conflicts
                if (existingPerson != null && existingPerson.personUuid != personUuid && rawPersonUuid.isNotBlank()) {
                    isDuplicateUuid = true
                    reviewReasons.add("personUuid ขัดแย้งกับบุคคลที่มีอยู่แล้วในระบบ")
                }

                // Determine Action
                val action: ImportAction = when {
                    isDuplicateUuid || isAmbiguousHousehold || reviewReasons.isNotEmpty() -> {
                        ImportAction.NEEDS_REVIEW
                    }
                    existingPerson != null -> {
                        ImportAction.UPDATE
                    }
                    else -> {
                        ImportAction.INSERT
                    }
                }

                val finalDataStatus = if (action == ImportAction.NEEDS_REVIEW) {
                    DataStatus.NEEDS_REVIEW
                } else {
                    DataStatus.fromString(rawDataStatus).takeIf { it != DataStatus.NEEDS_REVIEW } ?: DataStatus.VERIFIED
                }

                val person = Person(
                    id = existingPerson?.id ?: 0,
                    personUuid = personUuid,
                    householdId = targetHouseholdId,
                    nationalId = normalizedNatId,
                    fullName = fullName,
                    gender = Gender.fromString(rawGender),
                    birthDate = birthDate,
                    isBirthYearOnly = isBirthYearOnly,
                    houseStatus = HouseholdRole.fromString(rawHouseStatus),
                    personStatus = PersonStatus.fromString(rawPersonStatus),
                    dataStatus = finalDataStatus
                )

                plannedItems.add(
                    PlannedPersonImport(
                        rowNum = rowNum,
                        action = action,
                        personData = person,
                        householdData = householdCandidate,
                        isNewHousehold = isNewHousehold,
                        nationalIdStatus = nationalIdStatus,
                        isAmbiguousHousehold = isAmbiguousHousehold,
                        isDuplicateUuid = isDuplicateUuid,
                        reviewReasons = reviewReasons
                    )
                )
            }
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
            errors.add(ImportError(0, "เกิดข้อผิดพลาดในการอ่านไฟล์: ${e.message}"))
        }

        return ImportPlan(
            plannedItems = plannedItems,
            totalRows = totalRows,
            insertCount = plannedItems.count { it.action == ImportAction.INSERT },
            updateCount = plannedItems.count { it.action == ImportAction.UPDATE },
            skipCount = plannedItems.count { it.action == ImportAction.SKIP },
            needsReviewCount = plannedItems.count { it.action == ImportAction.NEEDS_REVIEW },
            errors = errors
        )
    }

    suspend fun commitImportPlan(
        plan: ImportPlan,
        operatorUid: String? = null,
        operatorName: String? = null
    ): ExcelImportResult {
        var successCount = 0
        var failedCount = plan.errors.size
        var duplicateCount = 0
        var invalidNationalIdCount = 0
        var invalidBirthDateCount = 0
        var needsReviewCount = 0
        val errors = plan.errors.toMutableList()

        database.withTransaction {
            val householdDao = database.householdDao()
            val personDao = database.personDao()
            val historyDao = database.personHistoryDao()

            val existingHouseholds = householdDao.getAllHouseholds().associateBy { it.householdUuid }.toMutableMap()
            val existingPersonsByUuid = personDao.getAllPersonsList().associateBy { it.personUuid }.toMutableMap()
            val existingPersonsByNatId = personDao.getAllPersonsList().filter { !it.nationalId.isNullOrBlank() }.associateBy { it.nationalId!! }.toMutableMap()

            for (item in plan.plannedItems) {
                if (item.action == ImportAction.SKIP) continue

                // Strict Identity Separation Assertion: user.uid can NEVER substitute personUuid or householdUuid
                if (!operatorUid.isNullOrBlank()) {
                    require(item.personData.personUuid != operatorUid) {
                        "Data Integrity Error: user.uid cannot substitute personUuid (${item.personData.personUuid} == $operatorUid)"
                    }
                    require(item.householdData.householdUuid != operatorUid) {
                        "Data Integrity Error: user.uid cannot substitute householdUuid (${item.householdData.householdUuid} == $operatorUid)"
                    }
                }

                if (item.isDuplicateUuid) {
                    duplicateCount++
                }
                if (item.nationalIdStatus == NationalIdStatus.INVALID) {
                    invalidNationalIdCount++
                }
                if (item.personData.birthDate == null) {
                    invalidBirthDateCount++
                }
                if (item.action == ImportAction.NEEDS_REVIEW) {
                    needsReviewCount++
                }

                try {
                    // Resolve household ID
                    var householdId = item.personData.householdId
                    if (householdId == 0L || item.isNewHousehold) {
                        val existingH = existingHouseholds[item.householdData.householdUuid]
                        if (existingH != null) {
                            householdId = existingH.id
                        } else {
                            val householdToInsert = item.householdData.copy(lastModified = System.currentTimeMillis())
                            val newHId = householdDao.insert(householdToInsert)
                            householdId = newHId
                            existingHouseholds[item.householdData.householdUuid] = householdToInsert.copy(id = newHId)
                        }
                    }

                    val personToSave = item.personData.copy(householdId = householdId)

                    val existingPerson = existingPersonsByUuid[personToSave.personUuid]
                        ?: (if (personToSave.nationalId != null) existingPersonsByNatId[personToSave.nationalId] else null)

                    if (existingPerson != null) {
                        val updated = existingPerson.copy(
                            householdId = householdId,
                            nationalId = personToSave.nationalId ?: existingPerson.nationalId,
                            fullName = personToSave.fullName.ifBlank { existingPerson.fullName },
                            gender = personToSave.gender,
                            birthDate = personToSave.birthDate ?: existingPerson.birthDate,
                            isBirthYearOnly = personToSave.isBirthYearOnly,
                            houseStatus = personToSave.houseStatus,
                            personStatus = personToSave.personStatus,
                            dataStatus = personToSave.dataStatus,
                            lastModified = System.currentTimeMillis()
                        )
                        personDao.updatePerson(updated)
                        historyDao.insert(
                            PersonHistory(
                                personId = existingPerson.id,
                                action = "UPDATE_EXCEL",
                                oldValue = existingPerson.toString(),
                                newValue = updated.toString(),
                                operatorId = operatorUid ?: "IMPORT_USER",
                                operatorName = operatorName ?: "Excel Importer",
                                role = "ADMIN",
                                deviceId = "local",
                                source = "EXCEL_IMPORT"
                            )
                        )
                        successCount++
                    } else {
                        val personToInsert = personToSave.copy(lastModified = System.currentTimeMillis())
                        val newId = personDao.insertPerson(personToInsert)
                        val insertedPerson = personToInsert.copy(id = newId)
                        existingPersonsByUuid[insertedPerson.personUuid] = insertedPerson
                        if (insertedPerson.nationalId != null) {
                            existingPersonsByNatId[insertedPerson.nationalId] = insertedPerson
                        }
                        historyDao.insert(
                            PersonHistory(
                                personId = newId,
                                action = "CREATE_EXCEL",
                                oldValue = null,
                                newValue = insertedPerson.toString(),
                                operatorId = operatorUid ?: "IMPORT_USER",
                                operatorName = operatorName ?: "Excel Importer",
                                role = "ADMIN",
                                deviceId = "local",
                                source = "EXCEL_IMPORT"
                            )
                        )
                        successCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                    errors.add(ImportError(item.rowNum, "บันทึกข้อมูลล้มเหลว: ${e.message}"))
                }
            }
        }

        return ExcelImportResult(
            totalRows = plan.totalRows,
            successCount = successCount,
            failedCount = failedCount,
            duplicateCount = duplicateCount,
            invalidNationalIdCount = invalidNationalIdCount,
            invalidBirthDateCount = invalidBirthDateCount,
            invalidHouseNoCount = errors.count { it.reason.contains("บ้านเลขที่") },
            needsReviewCount = needsReviewCount,
            errors = errors
        )
    }

    suspend operator fun invoke(inputStream: InputStream): ExcelImportResult {
        val plan = createImportPlan(inputStream)
        return commitImportPlan(plan)
    }

    private fun buildAddressKey(houseNo: String, villageNo: String, subdistrict: String, district: String, province: String): String {
        return "${houseNo.trim()}|${villageNo.trim()}|${subdistrict.trim()}|${district.trim()}|${province.trim()}".lowercase()
    }

    private fun getCellByHeader(row: Row, headerMap: Map<String, Int>, key: String, defaultIdx: Int): String {
        val idx = headerMap[key] ?: defaultIdx
        return if (idx in 0 until row.lastCellNum) {
            getCellValueAsString(row.getCell(idx))
        } else {
            ""
        }
    }

    private fun getCellObjectByHeader(row: Row, headerMap: Map<String, Int>, key: String, defaultIdx: Int): Cell? {
        val idx = headerMap[key] ?: defaultIdx
        return if (idx in 0 until row.lastCellNum) {
            row.getCell(idx)
        } else {
            null
        }
    }

    private fun getCellValueAsString(cell: Cell?): String {
        if (cell == null) return ""
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue.trim()
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cell.dateCellValue.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    } else {
                        BigDecimal(cell.numericCellValue).toPlainString()
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    when (cell.cachedFormulaResultType) {
                        CellType.STRING -> cell.richStringCellValue.string.trim()
                        CellType.NUMERIC -> BigDecimal(cell.numericCellValue).toPlainString()
                        else -> ""
                    }
                }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseDateCell(cell: Cell?): Pair<LocalDate?, Boolean> {
        if (cell == null) return Pair(null, false)
        return try {
            if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                val date = cell.dateCellValue
                Pair(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(), false)
            } else {
                val str = getCellValueAsString(cell)
                ValidationUtils.parseThaiDate(str)
            }
        } catch (e: Exception) {
            Pair(null, false)
        }
    }
}
