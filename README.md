# Smart OSM (Public Health Volunteer Management)

Modern Android application for Population Registration, Household Management, and NCDs Health Screening.

## Features
- **Identity First:** Secure identity management for Households and Persons using UUIDs.
- **Offline First:** Full functionality without internet using Room Database.
- **Secure Sync:** Bi-directional sync with Cloud Firestore.
- **Advanced GIS:** 100m Epidemic Buffer and Optimized Visit Routes.
- **Health AI:** Local AI-powered health advice for NCDs screening.
- **Privacy:** Database encryption (SQLCipher), PIN Lock with Lockout, and data anonymization for AI.

## Security
- Data encrypted at rest (SQLCipher).
- PIN hashing and lockout mechanism.
- Android Keystore for sensitive credentials.
- Strict Firestore security rules (Village-level isolation).

## Getting Started

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17
- Gradle 9.3.1

### Environment Setup
1. Create a `secrets.properties` file in the root directory (copy from `secrets.properties.example`).
2. Add your API keys:
   - `MAPS_API_KEY`: For Google Maps integration.
   - `GEMINI_API_KEY`: For AI Health screening advice.
3. Place your `google-services.json` in the `app/` directory.

### Building
- **Debug:** `gradle assembleDebug`
- **Release:** `gradle assembleRelease` (Requires keystore configuration)

## CI/CD Workflow
The project includes a master GitHub Actions workflow (`ci-cd.yml`):
- **On Push/PR:** Automatically runs unit tests and builds a Debug APK.
- **Manual Trigger:** Allows building either Debug or Release versions, and optional Android App Bundle (.aab) generation.
- **Security:** CI builds use safe placeholders for API keys to prevent manifest errors while keeping keys private.

## Data Workflow
- **Identity:** All entities use `UUID` for global identity and `Long` (Auto-increment) for local Room relations.
- **Import:** Uses a **Plan-then-Commit** pattern for Excel/Google Sheets imports.
- **Regional Partitioning:** Data is isolated by `villageNo` to ensure privacy and regional management.

## License
MIT License - Copyright (c) 2024
