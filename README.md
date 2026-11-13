# FotoFetch AI — Your Moments. Found Instantly.

**FotoFetch AI** is an AI-powered event photography platform built for university final year project demonstrations and real-world deployment. Photographers can create events and upload thousands of high-resolution photographs directly to AWS S3, while guests can scan a QR code, snap a selfie on their phone or laptop, and instantly find all their pictures using Amazon Rekognition facial recognition.

---

## 1. System Architecture & Tech Stack

### Backend
- **Java 21 & Spring Boot 3.3.5**
- **Spring Security & Stateless JWT Authentication**
- **Spring Data JPA & PostgreSQL**
- **AWS SDK for Java v2** (`software.amazon.awssdk:s3`, `software.amazon.awssdk:rekognition`)
- **Direct Browser-to-S3 Presigned Uploads** (High performance, avoids server bandwidth bottlenecks)
- **Net.coobird Thumbnailator** (Asynchronous 400x400 JPEG thumbnail generation)
- **Streaming ZIP Service** (Streams matched photos on-the-fly directly to the response output stream without memory overload)
- **Maven 3.9+**

### Frontend
- **React 18 & Vite**
- **Tailwind CSS** (Dark, minimal, luxury SaaS design system)
- **Lucide React Icons**
- **HTML5 Camera API & Canvas** (In-browser selfie capture with mirror flip and oval face guide)
- **Axios & React Router v6**

### Cloud Infrastructure
- **Amazon S3**: Private encrypted bucket for original HD photographs and generated thumbnails.
- **Amazon Rekognition**: Dedicated collections per event (`fotofetch-event-{publicId}`) for strict event-level privacy and isolation.
- **PostgreSQL**: Metadata storage for photographers, events, photo statuses, and facial counts.

---

## 2. Project Directory Structure

```
fyp 1/
├── backend/
│   ├── pom.xml
│   ├── .env.example
│   └── src/main/
│       ├── java/com/fotofetch/
│       │   ├── FotoFetchApplication.java
│       │   ├── common/
│       │   │   ├── ApiResponse.java
│       │   │   ├── BadRequestException.java
│       │   │   ├── ResourceNotFoundException.java
│       │   │   └── GlobalExceptionHandler.java
│       │   ├── config/
│       │   │   ├── AwsConfig.java
│       │   │   ├── SecurityConfig.java
│       │   │   └── AsyncConfig.java
│       │   ├── user/
│       │   │   ├── Role.java
│       │   │   ├── User.java
│       │   │   └── UserRepository.java
│       │   ├── auth/
│       │   │   ├── JwtTokenProvider.java
│       │   │   ├── JwtAuthenticationFilter.java
│       │   │   ├── AuthService.java
│       │   │   ├── AuthController.java
│       │   │   └── dto/
│       │   ├── event/
│       │   │   ├── Event.java
│       │   │   ├── EventRepository.java
│       │   │   ├── EventService.java
│       │   │   ├── EventController.java
│       │   │   └── dto/
│       │   ├── photo/
│       │   │   ├── Photo.java
│       │   │   ├── PhotoRepository.java
│       │   │   ├── PhotoService.java
│       │   │   ├── PhotoController.java
│       │   │   ├── UploadStatus.java
│       │   │   ├── ThumbnailStatus.java
│       │   │   ├── IndexingStatus.java
│       │   │   └── dto/
│       │   ├── storage/
│       │   │   ├── S3StorageService.java
│       │   │   └── ThumbnailService.java
│       │   ├── ai/
│       │   │   └── RekognitionService.java
│       │   ├── download/
│       │   │   ├── ZipService.java
│       │   │   └── dto/
│       │   └── guest/
│       │       └── GuestController.java
│       └── resources/
│           └── application.yml
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   ├── tailwind.config.js
│   ├── postcss.config.js
│   ├── index.html
│   ├── .env.example
│   └── src/
│       ├── main.jsx
│       ├── App.jsx
│       ├── index.css
│       ├── api/
│       │   ├── client.js
│       │   ├── authApi.js
│       │   ├── eventApi.js
│       │   ├── photoApi.js
│       │   └── guestApi.js
│       ├── context/
│       │   └── AuthContext.jsx
│       ├── components/
│       │   ├── Navbar.jsx
│       │   ├── Footer.jsx
│       │   ├── Modal.jsx
│       │   ├── Toast.jsx
│       │   ├── CameraCapture.jsx
│       │   ├── UploadModal.jsx
│       │   └── QrCodeModal.jsx
│       └── pages/
│           ├── LandingPage.jsx
│           ├── LoginPage.jsx
│           ├── RegisterPage.jsx
│           ├── DashboardPage.jsx
│           ├── EventManagePage.jsx
│           ├── GuestEventPage.jsx
│           └── GuestSearchResultsPage.jsx
├── docker-compose.yml
└── README.md
```

---

## 3. Database Setup (PostgreSQL)

You can use either your local PostgreSQL server or Docker.

### Option A: Local PostgreSQL
1. Open pgAdmin or `psql`:
   ```sql
   CREATE DATABASE fotofetch;
   ```
2. Verify credentials match `backend/src/main/resources/application.yml` or set environment variables:
   - Default URL: `jdbc:postgresql://localhost:5432/fotofetch`
   - Default User: `postgres`
   - Default Password: `postgres`

### Option B: Docker Compose
Run in the root folder:
```bash
docker compose up -d
```

---

## 4. AWS Cloud Configuration

FotoFetch AI connects to **real AWS services**. Follow these steps to configure your AWS environment.

### A. AWS IAM Permissions
Create an IAM User in the AWS Management Console with programmatic access (Access Key ID and Secret Access Key). Attach a custom policy with these exact permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "S3ObjectAndBucketAccess",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::YOUR_BUCKET_NAME",
        "arn:aws:s3:::YOUR_BUCKET_NAME/*"
      ]
    },
    {
      "Sid": "RekognitionCollectionAndSearchAccess",
      "Effect": "Allow",
      "Action": [
        "rekognition:CreateCollection",
        "rekognition:DeleteCollection",
        "rekognition:DescribeCollection",
        "rekognition:IndexFaces",
        "rekognition:SearchFacesByImage",
        "rekognition:DeleteFaces"
      ],
      "Resource": "*"
    }
  ]
}
```

### B. AWS S3 Bucket Setup & CORS Configuration
1. Create an S3 Bucket (e.g. `fotofetch-photos-YOURNAME`).
2. Keep **Block Public Access enabled** (Bucket remains 100% private; all uploads and downloads use time-limited presigned URLs).
3. Under the bucket **Permissions** tab, configure the **CORS (Cross-Origin Resource Sharing)** so browsers can upload directly via presigned PUT URLs:

```json
[
  {
    "AllowedHeaders": [
      "*"
    ],
    "AllowedMethods": [
      "PUT",
      "POST",
      "GET",
      "HEAD"
    ],
    "AllowedOrigins": [
      "http://localhost:5173",
      "http://localhost:3000",
      "http://localhost:8080"
    ],
    "ExposeHeaders": [
      "ETag"
    ],
    "MaxAgeSeconds": 3600
  }
]
```

---

## 5. Environment Variables Configuration

### Backend
In `backend`, copy `.env.example` to `.env` or set system environment variables:

| Variable | Description | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/fotofetch` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password | `postgres` |
| `JWT_SECRET` | Secret key for JWT signing (256-bit) | `fotofetch-super-secret-key...` |
| `AWS_REGION` | AWS Region | `us-east-1` |
| `AWS_ACCESS_KEY_ID` | Your IAM Access Key ID | (empty) |
| `AWS_SECRET_ACCESS_KEY` | Your IAM Secret Access Key | (empty) |
| `AWS_S3_BUCKET` | Your private S3 Bucket name | `fotofetch-photos-bucket` |

*Note: If `AWS_ACCESS_KEY_ID` is not explicitly set, the AWS SDK will automatically look in standard AWS locations: environment variables (`AWS_ACCESS_KEY_ID`), `~/.aws/credentials`, or EC2/ECS IAM Instance Roles.*

### Frontend
In `frontend`, check `.env.example`:
```env
VITE_API_URL=/api
```
*(The Vite development server automatically proxies `/api` requests to `http://localhost:8080`).*

---

## 6. How to Run

### Step 1: Start PostgreSQL
Ensure your PostgreSQL instance is running on port 5432 and the `fotofetch` database exists.

### Step 2: Start Backend (Spring Boot)
Open a terminal in the `backend` folder:
```powershell
mvn spring-boot:run
```
Backend will start on `http://localhost:8080`.

### Step 3: Start Frontend (React + Vite)
Open a second terminal in the `frontend` folder:
```powershell
npm.cmd run dev
```
Frontend will be available at `http://localhost:5173`.

---

## 7. End-to-End Acceptance Test Workflow

Follow this checklist to demonstrate the full application for your final year project presentation:

1. **Landing Page Demonstration**:
   - Open `http://localhost:5173`.
   - Review the SaaS hero, 3-step workflow, capability cards, and photographer value propositions.
2. **Photographer Registration & Login**:
   - Click **Get Started**.
   - Register a new photographer account (e.g., `Alex Morgan`, `alex@studio.com`, password).
   - Verify JWT authentication and redirection to the **Photographer Dashboard**.
3. **Event Creation**:
   - Click **Create Event**.
   - Enter name (e.g. `University Graduation 2026`) and date.
   - Verify that the backend creates a dedicated AWS Rekognition collection (`fotofetch-event-...`) and generates a unique public link (e.g. `/guest/graduation-2026-x8k3`).
4. **Direct Browser Bulk Upload**:
   - Click **Upload Photos**. Select multiple sample portraits/event photos.
   - Watch the concurrency queue upload photos directly to AWS S3 via presigned PUT URLs with individual percentage tracking.
   - Verify that the backend triggers background thumbnail generation (400x400 JPEG) and AWS Rekognition `IndexFaces`.
   - Click **Refresh** to see photo thumbnails and face count badges (`1 face`, `2 faces`).
5. **QR Code & Public Link**:
   - Click **QR Code** on the event page. Verify that a QR code is rendered and that the guest link can be copied with one click.
6. **Guest Selfie Search (Incognito / Mobile)**:
   - Open the copied guest link in a new incognito browser window (simulating a guest on their phone).
   - Notice: No login or account is required for guests.
   - Either take a live selfie using your webcam (with the face guide) or upload a portrait selfie.
   - Click **Find My Photos**.
7. **Facial Recognition Results**:
   - AWS Rekognition executes `SearchFacesByImage` against the specific event collection.
   - The matching photographs appear with match confidence badges (e.g. `98% Match`).
8. **HD Downloads & Streaming ZIP**:
   - Click the download icon on any photo to verify direct HD original download via presigned GET.
   - Click **Download All as ZIP** to stream all matching originals into a single ZIP archive without overloading server memory.

---

## 8. Security & Privacy Guarantees

- **No Public S3 Bucket**: Images cannot be scraped; access is granted only via short-lived AWS presigned URLs.
- **Event-Level Biometric Isolation**: Rekognition searches are strictly scoped to the specific event's collection ID. No guest faces are ever searched against other events.
- **Ephemeral Guest Processing**: Guest selfies are processed in memory and are discarded immediately after the Rekognition search call completes.
- **Stateless JWT Security**: Photographer endpoints are guarded with BCrypt hashing and signed JWT bearer tokens.

---

## 9. Known Limitations & Production Considerations

- **AWS Free Tier / Costs**: AWS Rekognition provides 5,000 free face search API calls per month for the first 12 months. Ensure you monitor your AWS billing console.
- **Unsupported Image Formats**: Rekognition supports PNG and JPEG. Non-standard RAW formats (e.g. `.CR2`, `.NEF`) should be exported as JPG before upload.
- **Extreme Angle / Occlusion**: As with all computer vision systems, faces turned more than 45 degrees or heavily covered by sunglasses/masks may exhibit lower match confidence.
#   f y p  
 