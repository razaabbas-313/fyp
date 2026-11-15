# FotoFetch AI — Production Deployment Guide

This guide details how to deploy **FotoFetch AI** to production with:
- **Backend**: Spring Boot 3 (Java 21) on [Render](https://render.com)
- **Database**: Managed PostgreSQL on Render
- **Frontend**: React + Vite + Tailwind CSS on [Vercel](https://vercel.com)
- **Cloud & AI**: AWS S3 & AWS Rekognition

---

## Architecture Overview

```mermaid
flowchart LR
    User[Guest / Photographer] -->|HTTPS| Vercel[Vercel: React SPA]
    Vercel -->|REST API| Render[Render: Spring Boot Backend]
    Render -->|JDBC| DB[(Render PostgreSQL)]
    Render -->|Presigned URLs / S3 Stream| S3[(AWS S3 Bucket)]
    Render -->|Index & Search Faces| Rekognition[AWS Rekognition]
    Vercel -->|Direct Upload via Presigned URL| S3
```

---

## 1. Deploy Backend on Render

You can deploy the backend using either **Method A (Render Blueprint / Recommended)** or **Method B (Manual Setup)**.

### Method A: 1-Click Render Blueprint (Recommended)

1. Push your code to your GitHub / GitLab repository.
2. Log in to [Render Dashboard](https://dashboard.render.com).
3. Click **New +** $\rightarrow$ **Blueprint**.
4. Select your FotoFetch repository.
5. Render will automatically detect `render.yaml` and configure:
   - A managed PostgreSQL database (`fotofetch-db`)
   - A Dockerized Spring Boot Web Service (`fotofetch-backend`) linked to the database
6. In the environment variables section, fill in your AWS credentials:
   - `AWS_REGION`: `ap-south-1` (or your AWS region)
   - `AWS_ACCESS_KEY_ID`: Your AWS IAM Access Key ID
   - `AWS_SECRET_ACCESS_KEY`: Your AWS IAM Secret Access Key
   - `AWS_S3_BUCKET`: Your AWS S3 bucket name
7. Click **Apply**. Render will build and deploy the database and backend.
8. Note your backend service URL (e.g., `https://fotofetch-backend.onrender.com`).

---

### Method B: Manual Render Setup

If you prefer setting up services individually:

#### Step 1: Create PostgreSQL Database on Render
1. Go to Render Dashboard $\rightarrow$ **New +** $\rightarrow$ **PostgreSQL**.
2. **Name**: `fotofetch-db`
3. **Database**: `fotofetch`
4. **User**: `fotofetch`
5. **Region**: Choose closest to your AWS region (e.g., `Oregon` or `Frankfurt`).
6. **Plan**: Free.
7. Click **Create Database**.
8. After creation, copy the **Internal Database URL** (or **External Database URL** if deploying across regions).

#### Step 2: Create Web Service on Render
1. Go to Render Dashboard $\rightarrow$ **New +** $\rightarrow$ **Web Service**.
2. Connect your GitHub repository.
3. Configure settings:
   - **Name**: `fotofetch-backend`
   - **Region**: Same region as your database
   - **Branch**: `main`
   - **Root Directory**: `backend` (or leave blank if using root Dockerfile)
   - **Runtime**: `Docker`
   - **Dockerfile Path**: `Dockerfile` (or `./backend/Dockerfile`)
   - **Instance Type**: Free
4. Add **Environment Variables**:
   | Key | Value | Notes |
   |---|---|---|
   | `DATABASE_URL` | `postgres://user:pass@host/dbname` | Paste the connection string from your Render database |
   | `SPRING_PROFILES_ACTIVE` | `prod` | Production profile |
   | `JWT_SECRET` | *(64+ random characters)* | Used to sign JWT authentication tokens |
   | `AWS_REGION` | `ap-south-1` | Your AWS region |
   | `AWS_ACCESS_KEY_ID` | `AKIA...` | AWS IAM credentials with S3 & Rekognition permissions |
   | `AWS_SECRET_ACCESS_KEY` | `...` | AWS IAM credentials |
   | `AWS_S3_BUCKET` | `fotofetch-ai-photos-...` | Your private S3 bucket name |
5. Click **Create Web Service**.
6. Render will automatically build the multi-stage Docker image and launch the service.
7. Health check path: `/api/health` (returns `{"status":"UP"}`).

---

## 2. Deploy Frontend on Vercel

1. Log in to [Vercel](https://vercel.com).
2. Click **Add New...** $\rightarrow$ **Project**.
3. Import your FotoFetch repository.
4. In **Project Configuration**:
   - **Framework Preset**: `Vite`
   - **Root Directory**: Click *Edit* and select `frontend`
   - **Build Command**: `npm run build` (detected automatically)
   - **Output Directory**: `dist` (detected automatically)
5. Under **Environment Variables**, add:
   | Variable Name | Value |
   |---|---|
   | `VITE_API_URL` | `https://fotofetch-backend.onrender.com` |

   *(Note: You can supply either `https://fotofetch-backend.onrender.com` or `https://fotofetch-backend.onrender.com/api`. The application automatically normalizes the endpoint).*

6. Click **Deploy**.
7. Vercel will install dependencies, compile the production bundle with Vite, and deploy to a public URL (e.g. `https://fotofetch.vercel.app`).

---

## 3. AWS S3 & Rekognition Configuration

Ensure your AWS S3 bucket and IAM user are configured properly:

### S3 CORS Configuration (Required for Direct Browser Uploads)
Because guests and photographers upload photos directly to S3 via presigned PUT URLs, your S3 bucket must have CORS enabled:

1. Open **AWS S3 Console** $\rightarrow$ Click your bucket $\rightarrow$ **Permissions** tab.
2. Scroll down to **Cross-origin resource sharing (CORS)** $\rightarrow$ Click **Edit**.
3. Paste the following JSON:
```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "PUT", "POST", "HEAD", "DELETE"],
    "AllowedOrigins": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```
4. Click **Save changes**.

> [!IMPORTANT]
> **Why is this necessary?** The web app on Vercel uploads photos directly from the user's browser to AWS S3 using presigned PUT URLs for maximum speed and zero server bottleneck. If CORS is not configured in the AWS S3 Console, the browser will block the upload preflight `OPTIONS` request.

### IAM User Least-Privilege Policy (Recommended)
Attach the following inline IAM policy to your AWS IAM user instead of broad managed policies like `AdministratorAccess` or `AmazonS3FullAccess`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "FotoFetchS3BucketAccess",
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": "arn:aws:s3:::<your-fotofetch-bucket>"
    },
    {
      "Sid": "FotoFetchS3ObjectAccess",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:HeadObject"
      ],
      "Resource": "arn:aws:s3:::<your-fotofetch-bucket>/*"
    },
    {
      "Sid": "FotoFetchRekognitionOperations",
      "Effect": "Allow",
      "Action": [
        "rekognition:CreateCollection",
        "rekognition:DeleteCollection",
        "rekognition:IndexFaces",
        "rekognition:SearchFacesByImage",
        "rekognition:DeleteFaces",
        "rekognition:ListFaces"
      ],
      "Resource": "arn:aws:rekognition:*:*:collection/fotofetch-event-*"
    }
  ]
}
```

---

## 4. Verification Checklist

After deploying both services:

- [ ] **Health Check**: Open `https://your-backend.onrender.com/api/health` in your browser. Verify it responds with `{"status":"UP"}`.
- [ ] **Vercel Routing**: Visit `https://your-app.vercel.app/login` and refresh the page. Verify it does not show a 404 error (handled by `vercel.json`).
- [ ] **Registration & Login**: Register a new photographer account and log in.
- [ ] **Event Creation & QR Code**: Create an event and click the **QR Code** button. Verify the QR code displays immediately.
- [ ] **Photo Upload Pipeline**: Upload event photos and verify that thumbnails are generated and Rekognition indexing finishes.
- [ ] **Guest Face Search**: Open the guest link in an incognito window, take/upload a selfie, and verify facial recognition search returns matching photos.
- [ ] **Multi-select ZIP Download**: Select matching photos and verify the streaming ZIP download works.
