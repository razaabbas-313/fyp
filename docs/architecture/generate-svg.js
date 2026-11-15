// FotoFetch AI — System Architecture SVG Generator
// Generates a comprehensive, publication-quality vector architecture diagram.

import fs from 'fs';
import path from 'path';

const WIDTH = 2600;
const HEIGHT = 1860;

function createSvg() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${WIDTH} ${HEIGHT}" width="${WIDTH}" height="${HEIGHT}" style="background-color: #0F172A; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
  <defs>
    <!-- Gradients -->
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0B0F19" />
      <stop offset="100%" stop-color="#111827" />
    </linearGradient>

    <linearGradient id="headerGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#1E293B" />
      <stop offset="100%" stop-color="#0F172A" />
    </linearGradient>

    <!-- Layer Cards Gradients -->
    <linearGradient id="cardGradDark" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#1E293B" stop-opacity="0.9" />
      <stop offset="100%" stop-color="#0F172A" stop-opacity="0.9" />
    </linearGradient>

    <!-- Component Fill Gradients -->
    <linearGradient id="implGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#064E3B" stop-opacity="0.85" />
      <stop offset="100%" stop-color="#022C22" stop-opacity="0.95" />
    </linearGradient>

    <linearGradient id="partialGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#78350F" stop-opacity="0.85" />
      <stop offset="100%" stop-color="#451A03" stop-opacity="0.95" />
    </linearGradient>

    <linearGradient id="proposedGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#4C1D95" stop-opacity="0.8" />
      <stop offset="100%" stop-color="#2E1065" stop-opacity="0.9" />
    </linearGradient>

    <linearGradient id="storeGrad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#1E3A8A" stop-opacity="0.85" />
      <stop offset="100%" stop-color="#0F172A" stop-opacity="0.95" />
    </linearGradient>

    <linearGradient id="pipelineGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#831843" stop-opacity="0.85" />
      <stop offset="100%" stop-color="#500724" stop-opacity="0.95" />
    </linearGradient>

    <!-- Drop Shadows -->
    <filter id="shadow" x="-5%" y="-5%" width="110%" height="115%" filterUnits="userSpaceOnUse">
      <feDropShadow dx="0" dy="4" stdDeviation="6" flood-color="#000000" flood-opacity="0.5"/>
    </filter>
    <filter id="glowGreen" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="0" stdDeviation="4" flood-color="#10B981" flood-opacity="0.4"/>
    </filter>

    <!-- Markers for Arrows -->
    <marker id="arrowBlue" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 1 L 10 5 L 0 9 z" fill="#38BDF8" />
    </marker>
    <marker id="arrowGreen" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 1 L 10 5 L 0 9 z" fill="#34D399" />
    </marker>
    <marker id="arrowAmber" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 1 L 10 5 L 0 9 z" fill="#FBBF24" />
    </marker>
    <marker id="arrowPurple" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 1 L 10 5 L 0 9 z" fill="#C084FC" />
    </marker>
    <marker id="arrowPink" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
      <path d="M 0 1 L 10 5 L 0 9 z" fill="#F472B6" />
    </marker>
  </defs>

  <!-- Background Base -->
  <rect width="${WIDTH}" height="${HEIGHT}" fill="url(#bgGrad)" />

  <!-- Subtle Blueprint Grid Pattern -->
  <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
    <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#334155" stroke-width="0.5" stroke-opacity="0.25" />
  </pattern>
  <rect width="${WIDTH}" height="${HEIGHT}" fill="url(#grid)" />

  <!-- ======================================================================== -->
  <!-- HEADER & BANNER                                                          -->
  <!-- ======================================================================== -->
  <g transform="translate(40, 30)">
    <!-- Header Box -->
    <rect width="2520" height="100" rx="12" fill="url(#headerGrad)" stroke="#334155" stroke-width="1.5" filter="url(#shadow)" />

    <!-- Project Title & University Tag -->
    <text x="30" y="42" font-size="26" font-weight="800" fill="#F8FAFC" letter-spacing="0.5">FotoFetch AI — Comprehensive System Architecture Diagram</text>
    <text x="30" y="72" font-size="14" font-weight="500" fill="#94A3B8">
      Production-Grade Multi-Tenant AI Platform for Event Facial Recognition &amp; Photo Retrieval • Final Year Project (Batch 23SW) • Mehran University of Engineering &amp; Technology (MUET)
    </text>

    <!-- Supervisors & Team Info -->
    <rect x="1560" y="18" width="430" height="64" rx="8" fill="#1E293B" stroke="#475569" stroke-width="1" />
    <text x="1575" y="40" font-size="12" font-weight="700" fill="#CBD5E1">Supervision &amp; Affiliation:</text>
    <text x="1575" y="58" font-size="11.5" font-weight="500" fill="#94A3B8">Academic: <tspan fill="#F8FAFC" font-weight="600">Ma'am Soonh Taj</tspan> (Dept of SE, MUET)</text>
    <text x="1575" y="73" font-size="11.5" font-weight="500" fill="#94A3B8">Industry Co-Supervisor: <tspan fill="#F8FAFC" font-weight="600">Syed Jari Abbas</tspan> (Habib Metro Bank)</text>

    <!-- Legend -->
    <rect x="2010" y="18" width="480" height="64" rx="8" fill="#1E293B" stroke="#475569" stroke-width="1" />
    <text x="2025" y="38" font-size="12" font-weight="700" fill="#CBD5E1">Component Implementation Status:</text>
    
    <!-- Legend Items -->
    <rect x="2025" y="48" width="14" height="14" rx="3" fill="#064E3B" stroke="#10B981" stroke-width="1.5" />
    <text x="2045" y="60" font-size="11" font-weight="600" fill="#34D399">Implemented (Verified in Code)</text>

    <rect x="2230" y="48" width="14" height="14" rx="3" fill="#78350F" stroke="#F59E0B" stroke-width="1.5" stroke-dasharray="3 2" />
    <text x="2250" y="60" font-size="11" font-weight="600" fill="#FBBF24">Partially Implemented</text>

    <rect x="2380" y="48" width="14" height="14" rx="3" fill="#4C1D95" stroke="#A855F7" stroke-width="1.5" stroke-dasharray="4 2" />
    <text x="2400" y="60" font-size="11" font-weight="600" fill="#C084FC">Proposed (In FYP Specs)</text>
  </g>

  <!-- ======================================================================== -->
  <!-- LAYER 1: USER & ACCESS LAYER                                             -->
  <!-- ======================================================================== -->
  <g transform="translate(40, 150)">
    <rect width="2520" height="145" rx="12" fill="url(#cardGradDark)" stroke="#334155" stroke-width="1.5" filter="url(#shadow)" />
    <rect x="0" y="0" width="8" height="145" rx="4" fill="#38BDF8" />
    <text x="25" y="30" font-size="15" font-weight="800" fill="#38BDF8" letter-spacing="1">LAYER 1: USER &amp; ACCESS LAYER (5 ROLES &amp; TRUST BOUNDARIES)</text>

    <!-- Sub-Boundary: Authenticated Studio Actors -->
    <rect x="25" y="45" width="1600" height="85" rx="8" fill="#1E293B" fill-opacity="0.6" stroke="#475569" stroke-width="1" stroke-dasharray="4 4" />
    <text x="35" y="60" font-size="10.5" font-weight="700" fill="#94A3B8" letter-spacing="0.5">AUTHENTICATED ACTORS (JWT SECURED / MULTI-TENANT ROLE-BASED ACCESS CONTROL)</text>

    <!-- Role 1: Super Admin (Proposed) -->
    <g transform="translate(45, 68)">
      <rect width="365" height="52" rx="6" fill="url(#proposedGrad)" stroke="#A855F7" stroke-width="1.5" stroke-dasharray="4 2" />
      <circle cx="22" cy="26" r="12" fill="#2E1065" stroke="#A855F7" stroke-width="1" />
      <text x="22" y="30" font-size="12" text-anchor="middle" fill="#E9D5FF">👑</text>
      <text x="42" y="22" font-size="12" font-weight="700" fill="#F3E8FF">Super Admin (Platform Overseer)</text>
      <text x="42" y="38" font-size="10" fill="#D8B4FE">Cross-studio governance • Platform billing &amp; system health</text>
    </g>

    <!-- Role 2: Studio Admin (Proposed) -->
    <g transform="translate(435, 68)">
      <rect width="365" height="52" rx="6" fill="url(#proposedGrad)" stroke="#A855F7" stroke-width="1.5" stroke-dasharray="4 2" />
      <circle cx="22" cy="26" r="12" fill="#2E1065" stroke="#A855F7" stroke-width="1" />
      <text x="22" y="30" font-size="12" text-anchor="middle" fill="#E9D5FF">🏢</text>
      <text x="42" y="22" font-size="12" font-weight="700" fill="#F3E8FF">Studio Admin (Studio Owner)</text>
      <text x="42" y="38" font-size="10" fill="#D8B4FE">Manages photographers &amp; events • Prepaid wallet funding</text>
    </g>

    <!-- Role 3: Photographer (Implemented) -->
    <g transform="translate(825, 68)">
      <rect width="395" height="52" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.8" />
      <circle cx="22" cy="26" r="12" fill="#022C22" stroke="#10B981" stroke-width="1" />
      <text x="22" y="30" font-size="12" text-anchor="middle" fill="#A7F3D0">📷</text>
      <text x="42" y="22" font-size="12" font-weight="700" fill="#ECFDF5">Photographer (Verified in Code)</text>
      <text x="42" y="38" font-size="10" fill="#6EE7B7">Creates events • Generates QR • Direct S3 high-res photo upload</text>
    </g>

    <!-- Role 4: Event Organizer (Proposed) -->
    <g transform="translate(1245, 68)">
      <rect width="365" height="52" rx="6" fill="url(#proposedGrad)" stroke="#A855F7" stroke-width="1.5" stroke-dasharray="4 2" />
      <circle cx="22" cy="26" r="12" fill="#2E1065" stroke="#A855F7" stroke-width="1" />
      <text x="22" y="30" font-size="12" text-anchor="middle" fill="#E9D5FF">📋</text>
      <text x="42" y="22" font-size="12" font-weight="700" fill="#F3E8FF">Event Organizer (Client/Host)</text>
      <text x="42" y="38" font-size="10" fill="#D8B4FE">Scoped strictly to assigned event • Upload &amp; gallery review</text>
    </g>

    <!-- Sub-Boundary: Account-Free Public Guests -->
    <rect x="1650" y="45" width="845" height="85" rx="8" fill="#1E293B" fill-opacity="0.6" stroke="#10B981" stroke-width="1" />
    <text x="1660" y="60" font-size="10.5" font-weight="700" fill="#34D399" letter-spacing="0.5">PUBLIC ACTOR (PRIVACY-PRESERVING ACCOUNT-FREE ACCESS)</text>

    <!-- Role 5: Guest / Attendee (Implemented) -->
    <g transform="translate(1665, 68)">
      <rect width="815" height="52" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.8" />
      <circle cx="24" cy="26" r="14" fill="#022C22" stroke="#10B981" stroke-width="1" />
      <text x="24" y="30" font-size="13" text-anchor="middle" fill="#A7F3D0">👥</text>
      <text x="48" y="22" font-size="12.5" font-weight="700" fill="#ECFDF5">Guest / Event Attendee (Fully Implemented &amp; Account-Free)</text>
      <text x="48" y="38" font-size="10.5" fill="#6EE7B7">Scans QR/Link • Submits selfie search • Tags name/username (@guest) • Subsequent text retrieval • Downloads single HD &amp; ZIP</text>
    </g>
  </g>

  <!-- ======================================================================== -->
  <!-- LAYER 2: FRONTEND / PRESENTATION LAYER (React.js SPA)                    -->
  <!-- ======================================================================== -->
  <g transform="translate(40, 315)">
    <rect width="2520" height="175" rx="12" fill="url(#cardGradDark)" stroke="#334155" stroke-width="1.5" filter="url(#shadow)" />
    <rect x="0" y="0" width="8" height="175" rx="4" fill="#60A5FA" />
    <text x="25" y="28" font-size="15" font-weight="800" fill="#60A5FA" letter-spacing="1">LAYER 2: FRONTEND / PRESENTATION LAYER (React.js, Vite, Tailwind CSS, Lucide Icons)</text>

    <!-- Left Box: Authenticated Dashboards -->
    <rect x="25" y="40" width="1280" height="120" rx="8" fill="#1E293B" fill-opacity="0.5" stroke="#475569" stroke-width="1" />
    <text x="35" y="55" font-size="10.5" font-weight="700" fill="#94A3B8">AUTHENTICATED DASHBOARDS &amp; MANAGEMENT WORKFLOWS</text>

    <!-- Super Admin Dashboard (Proposed) -->
    <g transform="translate(40, 65)">
      <rect width="230" height="85" rx="6" fill="url(#proposedGrad)" stroke="#A855F7" stroke-width="1.2" stroke-dasharray="4 2" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#F3E8FF">Super Admin Dashboard</text>
      <text x="12" y="40" font-size="9.5" fill="#D8B4FE">• Studio onboarding &amp; limits</text>
      <text x="12" y="55" font-size="9.5" fill="#D8B4FE">• Platform storage &amp; AI usage</text>
      <text x="12" y="70" font-size="9.5" fill="#D8B4FE">• Cross-tenant audit logs</text>
    </g>

    <!-- Studio Admin Dashboard (Proposed) -->
    <g transform="translate(285, 65)">
      <rect width="230" height="85" rx="6" fill="url(#proposedGrad)" stroke="#A855F7" stroke-width="1.2" stroke-dasharray="4 2" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#F3E8FF">Studio Admin Dashboard</text>
      <text x="12" y="40" font-size="9.5" fill="#D8B4FE">• Photographer account roster</text>
      <text x="12" y="55" font-size="9.5" fill="#D8B4FE">• Studio prepaid wallet balance</text>
      <text x="12" y="70" font-size="9.5" fill="#D8B4FE">• Consolidated studio events</text>
    </g>

    <!-- Photographer Dashboard & Event Manage (Implemented) -->
    <g transform="translate(530, 65)">
      <rect width="260" height="85" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.8" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#ECFDF5">Photographer Dashboard &amp; Event</text>
      <text x="12" y="40" font-size="9.5" fill="#6EE7B7">• Event creation &amp; QR Modal</text>
      <text x="12" y="55" font-size="9.5" fill="#6EE7B7">• Real-time photo grid &amp; statuses</text>
      <text x="12" y="70" font-size="9.5" fill="#6EE7B7">• Bulk photo delete &amp; manual retry</text>
    </g>

    <!-- Direct S3 Upload Component (Implemented) -->
    <g transform="translate(805, 65)">
      <rect width="250" height="85" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.8" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#ECFDF5">Direct S3 Presigned Uploader</text>
      <text x="12" y="40" font-size="9.5" fill="#6EE7B7">• Client MIME &amp; &le;25MB size check</text>
      <text x="12" y="55" font-size="9.5" fill="#6EE7B7">• Direct XHR PUT with onprogress</text>
      <text x="12" y="70" font-size="9.5" fill="#6EE7B7">• POST /confirm triggers backend</text>
    </g>

    <!-- Event Organizer UI & Web Worker (Proposed) -->
    <g transform="translate(1070, 65)">
      <rect width="220" height="85" rx="6" fill="url(#proposedGrad)" stroke="#A855F7" stroke-width="1.2" stroke-dasharray="4 2" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#F3E8FF">Organizer UI &amp; Web Workers</text>
      <text x="12" y="40" font-size="9.5" fill="#D8B4FE">• Scoped organizer event view</text>
      <text x="12" y="55" font-size="9.5" fill="#D8B4FE">• Background client image hashing</text>
      <text x="12" y="70" font-size="9.5" fill="#D8B4FE">• Client-side EXIF/rotation worker</text>
    </g>

    <!-- Right Box: Guest Public Interfaces -->
    <rect x="1325" y="40" width="1170" height="120" rx="8" fill="#1E293B" fill-opacity="0.5" stroke="#10B981" stroke-width="1" />
    <text x="1335" y="55" font-size="10.5" font-weight="700" fill="#34D399">PUBLIC GUEST INTERFACES (NO ACCOUNT REQUIRED)</text>

    <!-- Guest Event Page (Implemented) -->
    <g transform="translate(1340, 65)">
      <rect width="215" height="85" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.8" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#ECFDF5">Public Event Portal</text>
      <text x="12" y="40" font-size="9.5" fill="#6EE7B7">• QR-code / URL resolution</text>
      <text x="12" y="55" font-size="9.5" fill="#6EE7B7">• Event metadata display</text>
      <text x="12" y="70" font-size="9.5" fill="#6EE7B7">• Privacy consent checkpoint</text>
    </g>

    <!-- Selfie Search & Camera (Implemented) -->
    <g transform="translate(1570, 65)">
      <rect width="225" height="85" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.8" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#ECFDF5">Selfie Capture &amp; Search</text>
      <text x="12" y="40" font-size="9.5" fill="#6EE7B7">• Live webcam capture</text>
      <text x="12" y="55" font-size="9.5" fill="#6EE7B7">• Drag &amp; drop file upload</text>
      <text x="12" y="70" font-size="9.5" fill="#6EE7B7">• Threshold slider [70-99%]</text>
    </g>

    <!-- Name / Username Search (Implemented) -->
    <g transform="translate(1810, 65)">
      <rect width="215" height="85" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.8" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#ECFDF5">Returning Name Search</text>
      <text x="12" y="40" font-size="9.5" fill="#6EE7B7">• Instant text lookup</text>
      <text x="12" y="55" font-size="9.5" fill="#6EE7B7">• Searches exact @username</text>
      <text x="12" y="70" font-size="9.5" fill="#6EE7B7">• No selfie required on return</text>
    </g>

    <!-- Results Gallery & Lightbox (Implemented) -->
    <g transform="translate(2040, 65)">
      <rect width="215" height="85" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.8" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#ECFDF5">Results Gallery &amp; Tagging</text>
      <text x="12" y="40" font-size="9.5" fill="#6EE7B7">• Lightbox photo viewer</text>
      <text x="12" y="55" font-size="9.5" fill="#6EE7B7">• Face match % badge</text>
      <text x="12" y="70" font-size="9.5" fill="#6EE7B7">• Tag Name modal dialog</text>
    </g>

    <!-- Download Controls (Implemented) -->
    <g transform="translate(2270, 65)">
      <rect width="210" height="85" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.8" />
      <text x="12" y="22" font-size="11.5" font-weight="700" fill="#ECFDF5">Download Manager</text>
      <text x="12" y="40" font-size="9.5" fill="#6EE7B7">• Single HD photo download</text>
      <text x="12" y="55" font-size="9.5" fill="#6EE7B7">• Batch ZIP download (&le;50)</text>
      <text x="12" y="70" font-size="9.5" fill="#6EE7B7">• Session token header pass</text>
    </g>
  </g>

  <!-- ======================================================================== -->
  <!-- LAYER 3: BACKEND APPLICATION LAYER (Spring Boot 3 / Java 17/21)          -->
  <!-- ======================================================================== -->
  <g transform="translate(40, 510)">
    <rect width="2520" height="375" rx="12" fill="url(#cardGradDark)" stroke="#334155" stroke-width="1.5" filter="url(#shadow)" />
    <rect x="0" y="0" width="8" height="375" rx="4" fill="#34D399" />
    <text x="25" y="28" font-size="15" font-weight="800" fill="#34D399" letter-spacing="1">LAYER 3: BACKEND APPLICATION LAYER (Spring Boot 3.3.x, Java 17/21, Spring Data JPA, Spring Security, Spring Async)</text>

    <!-- Row 1: Core Submodules A, B, C, D -->
    <!-- Module A: Authentication, Security & Guest Guard -->
    <g transform="translate(25, 45)">
      <rect width="595" height="150" rx="8" fill="#1E293B" fill-opacity="0.6" stroke="#475569" stroke-width="1" />
      <text x="15" y="22" font-size="12" font-weight="700" fill="#F8FAFC">A. Authentication, Security &amp; Guest Session Guard</text>
      
      <!-- Component: Spring Security & JWT Filter (Implemented) -->
      <g transform="translate(15, 35)">
        <rect width="270" height="98" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">Spring Security &amp; JWT Filter</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• Stateless SessionPolicy</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• BCryptPasswordEncoder</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• Bearer token parsing &amp; claims</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Strict CORS origin filtering</text>
      </g>

      <!-- Component: Guest Session Guard & RBAC (Implemented + Partial) -->
      <g transform="translate(300, 35)">
        <rect width="280" height="98" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">GuestSessionService &amp; Authorizer</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• 32-byte SecureRandom raw token</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• SHA-256 hash storage (DB safe)</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• 120-min TTL &amp; revocation check</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Strict photo download authorization</text>
      </g>
    </g>

    <!-- Module B: Studio & Tenant Management -->
    <g transform="translate(635, 45)">
      <rect width="595" height="150" rx="8" fill="#1E293B" fill-opacity="0.6" stroke="#475569" stroke-width="1" />
      <text x="15" y="22" font-size="12" font-weight="700" fill="#F8FAFC">B. Studio &amp; Multi-Tenant Management</text>

      <!-- Component: Current User-Scoped Isolation (Partial) -->
      <g transform="translate(15, 35)">
        <rect width="270" height="98" rx="6" fill="url(#partialGrad)" stroke="#F59E0B" stroke-width="1.5" stroke-dasharray="3 2" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#FEF3C7">Tenant Scoping (Active)</text>
        <text x="10" y="38" font-size="9.5" fill="#FDE68A">• Photographer-scoped events</text>
        <text x="10" y="53" font-size="9.5" fill="#FDE68A">• user_id foreign key constraint</text>
        <text x="10" y="68" font-size="9.5" fill="#FDE68A">• getEventEntityByIdAndUser guard</text>
        <text x="10" y="83" font-size="9.5" fill="#FDE68A">• Studio A / B data leak prevention</text>
      </g>

      <!-- Component: Studio Entity & Billing Wallet (Proposed) -->
      <g transform="translate(300, 35)">
        <rect width="280" height="98" rx="6" fill="url(#proposedGrad)" stroke="#A855F7" stroke-width="1.5" stroke-dasharray="4 2" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#F3E8FF">Studio Entity &amp; Prepaid Wallet</text>
        <text x="10" y="38" font-size="9.5" fill="#D8B4FE">• Dedicated Studio tenant entity</text>
        <text x="10" y="53" font-size="9.5" fill="#D8B4FE">• Multi-user studio membership</text>
        <text x="10" y="68" font-size="9.5" fill="#D8B4FE">• Prepaid credit wallet deduction</text>
        <text x="10" y="83" font-size="9.5" fill="#D8B4FE">• Per-event AI indexing quota</text>
      </g>
    </g>

    <!-- Module C: Event Management -->
    <g transform="translate(1245, 45)">
      <rect width="615" height="150" rx="8" fill="#1E293B" fill-opacity="0.6" stroke="#475569" stroke-width="1" />
      <text x="15" y="22" font-size="12" font-weight="700" fill="#F8FAFC">C. Event Lifecycle &amp; Public Access Gateway</text>

      <!-- Component: EventService & Controller (Implemented) -->
      <g transform="translate(15, 35)">
        <rect width="280" height="98" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">EventService &amp; Lifecycle</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• UUID primary &amp; 32-char public_id</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• Rekognition collection creation</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• Cascading event photo cleanup</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Public event info DTO mapping</text>
      </g>

      <!-- Component: QR Code & Organizer Delegation (Implemented + Proposed) -->
      <g transform="translate(310, 35)">
        <rect width="290" height="98" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">QR Code &amp; Access Routing</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• Dynamic QR code generation</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• Deep-link /guest/{publicId}</text>
        <text x="10" y="68" font-size="9.5" fill="#D8B4FE">• Organizer access token (Proposed)</text>
        <text x="10" y="83" font-size="9.5" fill="#D8B4FE">• Client gallery view scoping (Proposed)</text>
      </g>
    </g>

    <!-- Module D: Photo Ingestion & S3 Controller -->
    <g transform="translate(1875, 45)">
      <rect width="620" height="150" rx="8" fill="#1E293B" fill-opacity="0.6" stroke="#475569" stroke-width="1" />
      <text x="15" y="22" font-size="12" font-weight="700" fill="#F8FAFC">D. Photo Ingestion &amp; Storage Control</text>

      <!-- Component: Presign & Upload Validation (Implemented) -->
      <g transform="translate(15, 35)">
        <rect width="285" height="98" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">Presigned URL Generator</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• events/{id}/originals/{uuid}-{file}</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• 15-min PUT presigned S3 URLs</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• Generates assigned photo UUIDs</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Initial DB record in PENDING state</text>
      </g>

      <!-- Component: Upload Confirmation & HeadObject (Implemented) -->
      <g transform="translate(315, 35)">
        <rect width="290" height="98" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">Upload Confirmation Engine</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• S3 HeadObject existence check</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• Enforces actual size &le; 25MB</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• Sets UploadStatus = UPLOADED</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• afterCommit triggers Async worker</text>
      </g>
    </g>

    <!-- Row 2: Processing & Retrieval Submodules E, F, G -->
    <!-- Module E: Asynchronous Processing & Reliability -->
    <g transform="translate(25, 205)">
      <rect width="780" height="155" rx="8" fill="#1E293B" fill-opacity="0.6" stroke="#475569" stroke-width="1" />
      <text x="15" y="22" font-size="12" font-weight="700" fill="#F8FAFC">E. Asynchronous Processing &amp; Self-Healing Reliability</text>

      <!-- Task Executor & Atomic Claim (Implemented) -->
      <g transform="translate(15, 35)">
        <rect width="365" height="105" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">PhotoProcessingService (@Async)</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• ThreadPoolTaskExecutor (PhotoProcess-)</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• Memory safety: Core=1, Max=2, Queue=100</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• claimPhotoForProcessing (atomic job lock)</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Idempotency guards prevent duplicate runs</text>
        <text x="10" y="98" font-size="9.5" fill="#6EE7B7">• Streams original from S3 to ThumbnailService</text>
      </g>

      <!-- Recovery Scheduler & Retry-with-backoff (Implemented) -->
      <g transform="translate(395, 35)">
        <rect width="370" height="105" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">PhotoRecoveryScheduler (@Scheduled)</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• Periodic self-healing sweep (every 60s)</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• Recovers stuck jobs (PROCESSING > 5 min)</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• Exponential backoff: 30s, 60s, 120s up to 15m</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Max 3 attempts before permanent FAILED mark</text>
        <text x="10" y="98" font-size="9.5" fill="#6EE7B7">• Safe recovery from server restarts &amp; OOMs</text>
      </g>
    </g>

    <!-- Module F: Face Recognition & Identity Management -->
    <g transform="translate(820, 205)">
      <rect width="845" height="155" rx="8" fill="#1E293B" fill-opacity="0.6" stroke="#475569" stroke-width="1" />
      <text x="15" y="22" font-size="12" font-weight="700" fill="#F8FAFC">F. Face Recognition &amp; Guest Identity Management</text>

      <!-- AI Facade & Selfie Matcher (Implemented) -->
      <g transform="translate(15, 35)">
        <rect width="400" height="105" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">RekognitionService &amp; Matcher</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• Strategy pattern facade decoupling engine</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• Threshold clamping: [70.0% &le; T &le; 99.0%]</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• searchFacesBySelfie returns match confidence</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Translates externalImageId to photo UUIDs</text>
        <text x="10" y="98" font-size="9.5" fill="#6EE7B7">• Creates authorized guest download session</text>
      </g>

      <!-- Guest Identity & Persistent Face-to-Name (Implemented) -->
      <g transform="translate(430, 35)">
        <rect width="400" height="105" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">GuestIdentityService (Face-to-Name)</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• Persistent face tagging: name + @username</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• Unique username auto-generation (e.g. raza, raza2)</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• Stores matched photo IDs in guest_identity_photos</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Returning search: exact username &gt; name &gt; partial</text>
        <text x="10" y="98" font-size="9.5" fill="#6EE7B7">• Creates new authorized session on returning search</text>
      </g>
    </g>

    <!-- Module G: Download Management -->
    <g transform="translate(1680, 205)">
      <rect width="815" height="155" rx="8" fill="#1E293B" fill-opacity="0.6" stroke="#475569" stroke-width="1" />
      <text x="15" y="22" font-size="12" font-weight="700" fill="#F8FAFC">G. Secure HD Photo &amp; ZIP Download Delivery</text>

      <!-- Single Photo Downloader (Implemented) -->
      <g transform="translate(15, 35)">
        <rect width="380" height="105" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">Single HD Photo Signer</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• Validates guest session token header</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• Verifies photoId &isin; session.matchedPhotoIds</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• Generates time-limited presigned S3 GET URL</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Content-Disposition attachment header</text>
        <text x="10" y="98" font-size="9.5" fill="#6EE7B7">• Client downloads original HD directly from S3</text>
      </g>

      <!-- Streaming ZipService (Implemented) -->
      <g transform="translate(410, 35)">
        <rect width="390" height="105" rx="6" fill="url(#implGrad)" stroke="#10B981" stroke-width="1.5" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#ECFDF5">Streaming ZipService</text>
        <text x="10" y="38" font-size="9.5" fill="#6EE7B7">• Validates all batch IDs against matched set</text>
        <text x="10" y="53" font-size="9.5" fill="#6EE7B7">• Enforces max &le; 50 photos per ZIP archive</text>
        <text x="10" y="68" font-size="9.5" fill="#6EE7B7">• Streams S3 original objects directly into ZipStream</text>
        <text x="10" y="83" font-size="9.5" fill="#6EE7B7">• Zero disk buffering (constant RAM usage on host)</text>
        <text x="10" y="98" font-size="9.5" fill="#6EE7B7">• Automatic duplicate filename disambiguation</text>
      </g>
    </g>
  </g>

  <!-- ======================================================================== -->
  <!-- LAYER 4 & LAYER 5: AI STRATEGY & DUAL-STREAM PIPELINE (SIDE-BY-SIDE)     -->
  <!-- ======================================================================== -->
  <!-- LAYER 4: AI & FACE RECOGNITION LAYER (Left Side) -->
  <g transform="translate(40, 900)">
    <rect width="1180" height="340" rx="12" fill="url(#cardGradDark)" stroke="#334155" stroke-width="1.5" filter="url(#shadow)" />
    <rect x="0" y="0" width="8" height="340" rx="4" fill="#A855F7" />
    <text x="25" y="28" font-size="15" font-weight="800" fill="#A855F7" letter-spacing="1">LAYER 4: AI &amp; FACE RECOGNITION LAYER (STRATEGY DESIGN PATTERN)</text>

    <!-- Strategy Interface -->
    <g transform="translate(25, 45)">
      <rect width="1130" height="80" rx="8" fill="#1E293B" stroke="#10B981" stroke-width="1.8" />
      <text x="20" y="24" font-size="11" font-weight="700" fill="#A7F3D0">&laquo;interface&raquo;</text>
      <text x="20" y="44" font-size="15" font-weight="800" fill="#ECFDF5">FaceRecognitionProvider</text>
      <text x="20" y="65" font-size="11" fill="#6EE7B7">Methods: <tspan font-family="monospace">createCollection(id)</tspan> • <tspan font-family="monospace">deleteCollection(id)</tspan> • <tspan font-family="monospace">indexPhotoFaces(id, key)</tspan> • <tspan font-family="monospace">searchFacesBySelfie(id, bytes, thr)</tspan> • <tspan font-family="monospace">deleteFaces(id, ids)</tspan></text>
      <rect x="920" y="15" width="190" height="26" rx="4" fill="#064E3B" stroke="#10B981" stroke-width="1" />
      <text x="1015" y="32" font-size="10.5" font-weight="700" fill="#34D399" text-anchor="middle">IMPLEMENTED INTERFACE</text>
    </g>

    <!-- Provider 1: Cloud AI Provider (Implemented) -->
    <g transform="translate(25, 145)">
      <rect width="550" height="175" rx="8" fill="url(#implGrad)" stroke="#10B981" stroke-width="2" />
      <text x="20" y="26" font-size="13" font-weight="800" fill="#ECFDF5">1. Cloud AI Provider: AwsRekognitionProvider</text>
      <rect x="420" y="12" width="115" height="22" rx="4" fill="#064E3B" stroke="#10B981" stroke-width="1" />
      <text x="477" y="27" font-size="10" font-weight="700" fill="#34D399" text-anchor="middle">ACTIVE IN CODE</text>
      
      <text x="20" y="52" font-size="10.5" fill="#A7F3D0">• <tspan font-weight="700" fill="#FFFFFF">Event Collection Isolation:</tspan> One AWS Collection per event (fotofetch_event_{publicId})</text>
      <text x="20" y="72" font-size="10.5" fill="#A7F3D0">• <tspan font-weight="700" fill="#FFFFFF">Thumbnail Indexing:</tspan> Indexes 1080px thumbnail S3 key (low S3 I/O + fast indexing)</text>
      <text x="20" y="92" font-size="10.5" fill="#A7F3D0">• <tspan font-weight="700" fill="#FFFFFF">ExternalImageId Mapping:</tspan> Stores photo UUID directly on Rekognition face records</text>
      <text x="20" y="112" font-size="10.5" fill="#A7F3D0">• <tspan font-weight="700" fill="#FFFFFF">Selfie Search:</tspan> Calls SearchFacesByImageRequest with clamped threshold [70-99%]</text>
      <text x="20" y="132" font-size="10.5" fill="#A7F3D0">• <tspan font-weight="700" fill="#FFFFFF">Automated Cleanup:</tspan> Deletes face records from AWS collection upon photo deletion</text>
      <text x="20" y="155" font-size="10" font-style="italic" fill="#6EE7B7">Note: AWS Rekognition manages feature vectors internally in AWS cloud (not stored in Postgres).</text>
    </g>

    <!-- Provider 2: Self-Hosted Provider (Proposed) -->
    <g transform="translate(605, 145)">
      <rect width="550" height="175" rx="8" fill="url(#proposedGrad)" stroke="#A855F7" stroke-width="2" stroke-dasharray="5 3" />
      <text x="20" y="26" font-size="13" font-weight="800" fill="#F3E8FF">2. Self-Hosted Provider: Open-Source AI (Proposed)</text>
      <rect x="420" y="12" width="115" height="22" rx="4" fill="#2E1065" stroke="#A855F7" stroke-width="1" />
      <text x="477" y="27" font-size="10" font-weight="700" fill="#C084FC" text-anchor="middle">PROPOSED IN FYP</text>

      <text x="20" y="52" font-size="10.5" fill="#E9D5FF">• <tspan font-weight="700" fill="#FFFFFF">Self-Hosted AI Engine:</tspan> Local containerized InsightFace / FaceNet model</text>
      <text x="20" y="72" font-size="10.5" fill="#E9D5FF">• <tspan font-weight="700" fill="#FFFFFF">High-Dimensional Embeddings:</tspan> Generates 512-dimensional facial feature vectors</text>
      <text x="20" y="92" font-size="10.5" fill="#E9D5FF">• <tspan font-weight="700" fill="#FFFFFF">Vector Similarity Search:</tspan> PostgreSQL pgvector extension with Cosine / L2 distance</text>
      <text x="20" y="112" font-size="10.5" fill="#E9D5FF">• <tspan font-weight="700" fill="#FFFFFF">Multi-Tenant Vector Isolation:</tspan> Vectors partitioned by studio_id and event_id</text>
      <text x="20" y="132" font-size="10.5" fill="#E9D5FF">• <tspan font-weight="700" fill="#FFFFFF">Zero Third-Party Cloud AI Cost:</tspan> Full ownership and local offline execution</text>
      <text x="20" y="155" font-size="10" font-style="italic" fill="#D8B4FE">Interface ready: swap fotofetch.ai.provider=selfhosted without rewriting business logic.</text>
    </g>
  </g>

  <!-- LAYER 5: DUAL-STREAM PHOTO PROCESSING PIPELINE (Right Side) -->
  <g transform="translate(1240, 900)">
    <rect width="1320" height="340" rx="12" fill="url(#cardGradDark)" stroke="#334155" stroke-width="1.5" filter="url(#shadow)" />
    <rect x="0" y="0" width="8" height="340" rx="4" fill="#FB923C" />
    <text x="25" y="28" font-size="15" font-weight="800" fill="#FB923C" letter-spacing="1">LAYER 5: DUAL-STREAM PHOTO PROCESSING PIPELINE (PERFORMANCE &amp; ARCHIVAL)</text>

    <!-- Direct Upload Banner -->
    <rect x="25" y="42" width="1270" height="36" rx="6" fill="#1E293B" stroke="#FB923C" stroke-width="1" />
    <text x="35" y="65" font-size="11.5" font-weight="700" fill="#FED7AA">
      PHOTOGRAPHER UPLOAD TRIGGER: <tspan fill="#F8FAFC">Direct S3 Presigned PUT</tspan> &rarr; <tspan fill="#F8FAFC">POST /confirm</tspan> &rarr; <tspan fill="#F8FAFC">HeadObject Validation</tspan> &rarr; <tspan fill="#F8FAFC">DB Commit</tspan> &rarr; <tspan fill="#FBBF24">Dual-Stream Pipeline Invoked</tspan>
    </text>

    <!-- Stream A: Fast Thumbnail Indexing (Implemented) -->
    <g transform="translate(25, 90)">
      <rect width="620" height="230" rx="8" fill="url(#implGrad)" stroke="#10B981" stroke-width="2" />
      <rect x="15" y="12" width="22" height="22" rx="4" fill="#064E3B" stroke="#10B981" stroke-width="1" />
      <text x="26" y="27" font-size="12" font-weight="800" fill="#34D399" text-anchor="middle">A</text>
      <text x="45" y="27" font-size="12.5" font-weight="800" fill="#ECFDF5">STREAM A: FAST THUMBNAIL INDEXING (PRIORITY PATH)</text>

      <!-- Step 1 -->
      <rect x="15" y="42" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="62" font-size="10.5" font-weight="600" fill="#A7F3D0">1. S3 Original Stream: <tspan font-weight="400" fill="#E2E8F0">S3StorageService streams uploaded HD original to worker</tspan></text>

      <!-- Step 2 -->
      <rect x="15" y="78" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="98" font-size="10.5" font-weight="600" fill="#A7F3D0">2. Thumbnail Compression: <tspan font-weight="400" fill="#E2E8F0">Resizes to max 1080px (JPEG 82% quality, ~150-250KB)</tspan></text>

      <!-- Step 3 -->
      <rect x="15" y="114" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="134" font-size="10.5" font-weight="600" fill="#A7F3D0">3. S3 Thumbnail Store: <tspan font-weight="400" fill="#E2E8F0">Uploaded to events/{id}/thumbnails/{photoId}.jpg</tspan></text>

      <!-- Step 4 -->
      <rect x="15" y="150" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="170" font-size="10.5" font-weight="600" fill="#A7F3D0">4. Face Indexing: <tspan font-weight="400" fill="#E2E8F0">FaceRecognitionProvider indexes thumbnail faces into collection</tspan></text>

      <!-- Step 5 -->
      <rect x="15" y="186" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="206" font-size="10.5" font-weight="600" fill="#A7F3D0">5. Instant Availability: <tspan font-weight="400" fill="#E2E8F0">Status &rarr; COMPLETED. Photos immediately searchable by guests!</tspan></text>
    </g>

    <!-- Stream B: Background HD Archival & Reliability (Implemented) -->
    <g transform="translate(675, 90)">
      <rect width="620" height="230" rx="8" fill="url(#implGrad)" stroke="#10B981" stroke-width="2" />
      <rect x="15" y="12" width="22" height="22" rx="4" fill="#064E3B" stroke="#10B981" stroke-width="1" />
      <text x="26" y="27" font-size="12" font-weight="800" fill="#34D399" text-anchor="middle">B</text>
      <text x="45" y="27" font-size="12.5" font-weight="800" fill="#ECFDF5">STREAM B: BACKGROUND HD ARCHIVAL &amp; SELF-HEALING</text>

      <!-- Step 1 -->
      <rect x="15" y="42" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="62" font-size="10.5" font-weight="600" fill="#A7F3D0">1. Original Preservation: <tspan font-weight="400" fill="#E2E8F0">Full-resolution HD untouched in S3 originals/ folder</tspan></text>

      <!-- Step 2 -->
      <rect x="15" y="78" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="98" font-size="10.5" font-weight="600" fill="#A7F3D0">2. Independent Archival: <tspan font-weight="400" fill="#E2E8F0">Archival continues independently without blocking search availability</tspan></text>

      <!-- Step 3 -->
      <rect x="15" y="114" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="134" font-size="10.5" font-weight="600" fill="#A7F3D0">3. Retry-with-Backoff: <tspan font-weight="400" fill="#E2E8F0">Transient failures scheduled for retry: 30s, 60s, 120s (max 3x)</tspan></text>

      <!-- Step 4 -->
      <rect x="15" y="150" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="170" font-size="10.5" font-weight="600" fill="#A7F3D0">4. Stuck Job Recovery: <tspan font-weight="400" fill="#E2E8F0">PhotoRecoveryScheduler re-claims jobs interrupted by crashes</tspan></text>

      <!-- Step 5 -->
      <rect x="15" y="186" width="590" height="32" rx="5" fill="#022C22" stroke="#10B981" stroke-width="0.8" />
      <text x="25" y="206" font-size="10.5" font-weight="600" fill="#A7F3D0">5. HD Download Ready: <tspan font-weight="400" fill="#E2E8F0">Originals reserved exclusively for verified guest HD &amp; ZIP downloads</tspan></text>
    </g>
  </g>

  <!-- ======================================================================== -->
  <!-- LAYER 6: CLOUD INFRASTRUCTURE & STORAGE                                  -->
  <!-- ======================================================================== -->
  <g transform="translate(40, 1260)">
    <rect width="2520" height="235" rx="12" fill="url(#cardGradDark)" stroke="#334155" stroke-width="1.5" filter="url(#shadow)" />
    <rect x="0" y="0" width="8" height="235" rx="4" fill="#3B82F6" />
    <text x="25" y="28" font-size="15" font-weight="800" fill="#3B82F6" letter-spacing="1">LAYER 6: CLOUD INFRASTRUCTURE, OBJECT STORAGE &amp; PERSISTENCE</text>

    <!-- Infra 1: Cloud Object Storage (AWS S3) -->
    <g transform="translate(25, 45)">
      <rect width="780" height="170" rx="8" fill="url(#storeGrad)" stroke="#3B82F6" stroke-width="1.8" />
      <text x="20" y="26" font-size="13" font-weight="800" fill="#DBEAFE">Cloud Object Storage (AWS S3 Private Bucket)</text>
      <rect x="650" y="12" width="115" height="20" rx="4" fill="#1E3A8A" stroke="#60A5FA" stroke-width="1" />
      <text x="707" y="26" font-size="9.5" font-weight="700" fill="#BFDBFE" text-anchor="middle">IMPLEMENTED</text>

      <!-- Folder 1: Originals -->
      <g transform="translate(15, 38)">
        <rect width="365" height="115" rx="6" fill="#0F172A" stroke="#3B82F6" stroke-width="1" />
        <text x="12" y="22" font-size="11.5" font-weight="700" fill="#93C5FD">📁 events/{eventId}/originals/</text>
        <text x="12" y="40" font-size="9.5" fill="#CBD5E1">• Full-resolution original HD image files</text>
        <text x="12" y="55" font-size="9.5" fill="#CBD5E1">• Stored with UUID prefix: {photoId}-{filename}</text>
        <text x="12" y="70" font-size="9.5" fill="#CBD5E1">• Uploaded via 15-min Presigned PUT URLs</text>
        <text x="12" y="85" font-size="9.5" fill="#CBD5E1">• Streamed by ZipService for matching guests</text>
        <text x="12" y="100" font-size="9.5" fill="#93C5FD">🔒 Private bucket (no public GET access allowed)</text>
      </g>

      <!-- Folder 2: Thumbnails -->
      <g transform="translate(395, 38)">
        <rect width="370" height="115" rx="6" fill="#0F172A" stroke="#3B82F6" stroke-width="1" />
        <text x="12" y="22" font-size="11.5" font-weight="700" fill="#93C5FD">📁 events/{eventId}/thumbnails/</text>
        <text x="12" y="40" font-size="9.5" fill="#CBD5E1">• 1080px compressed web thumbnails (~200KB)</text>
        <text x="12" y="55" font-size="9.5" fill="#CBD5E1">• Stored as {photoId}.jpg</text>
        <text x="12" y="70" font-size="9.5" fill="#CBD5E1">• Consumed by AWS Rekognition IndexFaces</text>
        <text x="12" y="85" font-size="9.5" fill="#CBD5E1">• Displayed in photographer &amp; guest galleries</text>
        <text x="12" y="100" font-size="9.5" fill="#93C5FD">⚡ Fast transfer &amp; minimal bandwidth consumption</text>
      </g>
    </g>

    <!-- Infra 2: Relational Database (PostgreSQL) -->
    <g transform="translate(830, 45)">
      <rect width="1100" height="170" rx="8" fill="url(#storeGrad)" stroke="#3B82F6" stroke-width="1.8" />
      <text x="20" y="26" font-size="13" font-weight="800" fill="#DBEAFE">Relational Database (PostgreSQL 15+ Schema &amp; Entities)</text>
      <rect x="965" y="12" width="120" height="20" rx="4" fill="#1E3A8A" stroke="#60A5FA" stroke-width="1" />
      <text x="1025" y="26" font-size="9.5" font-weight="700" fill="#BFDBFE" text-anchor="middle">FLYWAY V1 - V4</text>

      <!-- Tables Grid -->
      <!-- Table: users -->
      <g transform="translate(15, 38)">
        <rect width="200" height="115" rx="6" fill="#0F172A" stroke="#10B981" stroke-width="1" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#A7F3D0">users</text>
        <text x="10" y="36" font-size="9" fill="#94A3B8">• id (UUID PK)</text>
        <text x="10" y="50" font-size="9" fill="#94A3B8">• name, email (UQ)</text>
        <text x="10" y="64" font-size="9" fill="#94A3B8">• password (BCrypt)</text>
        <text x="10" y="78" font-size="9" fill="#94A3B8">• role (PHOTOGRAPHER,</text>
        <text x="10" y="92" font-size="9" fill="#94A3B8">  ADMIN)</text>
        <text x="10" y="106" font-size="8.5" fill="#34D399">[Implemented V1]</text>
      </g>

      <!-- Table: events -->
      <g transform="translate(225, 38)">
        <rect width="210" height="115" rx="6" fill="#0F172A" stroke="#10B981" stroke-width="1" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#A7F3D0">events</text>
        <text x="10" y="36" font-size="9" fill="#94A3B8">• id (UUID PK)</text>
        <text x="10" y="50" font-size="9" fill="#94A3B8">• public_id (32-char UQ)</text>
        <text x="10" y="64" font-size="9" fill="#94A3B8">• user_id (FK &rarr; users)</text>
        <text x="10" y="78" font-size="9" fill="#94A3B8">• rekognition_collection_id</text>
        <text x="10" y="92" font-size="9" fill="#94A3B8">• event_date, name</text>
        <text x="10" y="106" font-size="8.5" fill="#34D399">[Implemented V1]</text>
      </g>

      <!-- Table: photos -->
      <g transform="translate(445, 38)">
        <rect width="210" height="115" rx="6" fill="#0F172A" stroke="#10B981" stroke-width="1" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#A7F3D0">photos</text>
        <text x="10" y="36" font-size="9" fill="#94A3B8">• id (UUID PK), event_id</text>
        <text x="10" y="50" font-size="9" fill="#94A3B8">• original_s3_key, thumb_key</text>
        <text x="10" y="64" font-size="9" fill="#94A3B8">• upload/thumb/index_status</text>
        <text x="10" y="78" font-size="9" fill="#94A3B8">• faces_found, file_size</text>
        <text x="10" y="92" font-size="9" fill="#94A3B8">• processing_attempts, retry</text>
        <text x="10" y="106" font-size="8.5" fill="#34D399">[Implemented V1, V2]</text>
      </g>

      <!-- Table: guest_sessions & photos -->
      <g transform="translate(665, 38)">
        <rect width="210" height="115" rx="6" fill="#0F172A" stroke="#10B981" stroke-width="1" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#A7F3D0">guest_sessions &amp; photos</text>
        <text x="10" y="36" font-size="9" fill="#94A3B8">• session_token_hash (SHA)</text>
        <text x="10" y="50" font-size="9" fill="#94A3B8">• event_id (FK &rarr; events)</text>
        <text x="10" y="64" font-size="9" fill="#94A3B8">• expires_at (120m TTL)</text>
        <text x="10" y="78" font-size="9" fill="#94A3B8">• revoked (boolean)</text>
        <text x="10" y="92" font-size="9" fill="#94A3B8">• session_id + photo_id (Set)</text>
        <text x="10" y="106" font-size="8.5" fill="#34D399">[Implemented V3]</text>
      </g>

      <!-- Table: guest_identities & photos -->
      <g transform="translate(885, 38)">
        <rect width="200" height="115" rx="6" fill="#0F172A" stroke="#10B981" stroke-width="1" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#A7F3D0">guest_identities &amp; photos</text>
        <text x="10" y="36" font-size="9" fill="#94A3B8">• id (UUID PK), event_id</text>
        <text x="10" y="50" font-size="9" fill="#94A3B8">• name, username (@guest)</text>
        <text x="10" y="64" font-size="9" fill="#94A3B8">• UQ(event_id, username)</text>
        <text x="10" y="78" font-size="9" fill="#94A3B8">• identity_id + photo_id (Set)</text>
        <text x="10" y="92" font-size="9" fill="#94A3B8">• persistent name retrieval</text>
        <text x="10" y="106" font-size="8.5" fill="#34D399">[Implemented V4]</text>
      </g>
    </g>

    <!-- Infra 3: AI Engine Infrastructure -->
    <g transform="translate(1950, 45)">
      <rect width="545" height="170" rx="8" fill="url(#storeGrad)" stroke="#3B82F6" stroke-width="1.8" />
      <text x="20" y="26" font-size="13" font-weight="800" fill="#DBEAFE">AI Cloud Services &amp; Vectors</text>
      <rect x="420" y="12" width="110" height="20" rx="4" fill="#1E3A8A" stroke="#60A5FA" stroke-width="1" />
      <text x="475" y="26" font-size="9.5" font-weight="700" fill="#BFDBFE" text-anchor="middle">ACTIVE + ROADMAP</text>

      <!-- Service 1: AWS Rekognition -->
      <g transform="translate(15, 38)">
        <rect width="250" height="115" rx="6" fill="#0F172A" stroke="#10B981" stroke-width="1" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#A7F3D0">AWS Rekognition Service</text>
        <text x="10" y="36" font-size="9" fill="#94A3B8">• Cloud facial feature engine</text>
        <text x="10" y="50" font-size="9" fill="#94A3B8">• Event collection containers</text>
        <text x="10" y="64" font-size="9" fill="#94A3B8">• Cloud-managed index storage</text>
        <text x="10" y="78" font-size="9" fill="#94A3B8">• SearchFacesByImage API</text>
        <text x="10" y="92" font-size="9" fill="#94A3B8">• Server-controlled 80% threshold</text>
        <text x="10" y="106" font-size="8.5" fill="#34D399">[Implemented &amp; Connected]</text>
      </g>

      <!-- Service 2: pgvector / Open Source -->
      <g transform="translate(280, 38)">
        <rect width="250" height="115" rx="6" fill="#0F172A" stroke="#A855F7" stroke-width="1" stroke-dasharray="3 2" />
        <text x="10" y="20" font-size="11" font-weight="700" fill="#E9D5FF">pgvector / Local Vector Store</text>
        <text x="10" y="36" font-size="9" fill="#94A3B8">• Proposed for self-hosted AI</text>
        <text x="10" y="50" font-size="9" fill="#94A3B8">• PostgreSQL vector(512) column</text>
        <text x="10" y="64" font-size="9" fill="#94A3B8">• HNSW / IVFFlat similarity index</text>
        <text x="10" y="78" font-size="9.5" fill="#94A3B8">• Cosine distance SQL queries</text>
        <text x="10" y="92" font-size="9" fill="#94A3B8">• Event-partitioned vectors</text>
        <text x="10" y="106" font-size="8.5" fill="#C084FC">[Proposed Architecture]</text>
      </g>
    </g>
  </g>

  <!-- ======================================================================== -->
  <!-- MULTI-TENANT & PRIVACY SECURITY BOUNDARIES (CALLOUT OVERLAYS)            -->
  <!-- ======================================================================== -->
  <g transform="translate(40, 1515)">
    <rect width="2520" height="150" rx="12" fill="url(#cardGradDark)" stroke="#334155" stroke-width="1.5" filter="url(#shadow)" />
    <rect x="0" y="0" width="8" height="150" rx="4" fill="#F43F5E" />
    <text x="25" y="28" font-size="15" font-weight="800" fill="#F43F5E" letter-spacing="1">FOUR END-TO-END WORKFLOWS &amp; SECURITY BOUNDARIES EXPLAINED</text>

    <!-- Workflow A Card -->
    <g transform="translate(25, 42)">
      <rect width="595" height="92" rx="6" fill="#1E293B" stroke="#38BDF8" stroke-width="1.2" />
      <text x="15" y="22" font-size="11.5" font-weight="800" fill="#38BDF8">Workflow A: Photographer Direct Upload &amp; Dual-Stream Pipeline</text>
      <text x="15" y="40" font-size="9.5" fill="#CBD5E1">1. Photographer logs in with JWT &rarr; Selects photos &rarr; Requests presigned PUT URLs.</text>
      <text x="15" y="55" font-size="9.5" fill="#CBD5E1">2. React client uploads directly to S3 originals/ &rarr; Calls /confirm on backend.</text>
      <text x="15" y="70" font-size="9.5" fill="#CBD5E1">3. Backend verifies S3 HeadObject (&le;25MB) &rarr; Commits DB &rarr; Triggers Async TaskExecutor.</text>
      <text x="15" y="85" font-size="9.5" fill="#38BDF8">Result: Stream A indexes thumbnail in Rekognition (fast search); Stream B archives original HD.</text>
    </g>

    <!-- Workflow B Card -->
    <g transform="translate(635, 42)">
      <rect width="595" height="92" rx="6" fill="#1E293B" stroke="#34D399" stroke-width="1.2" />
      <text x="15" y="22" font-size="11.5" font-weight="800" fill="#34D399">Workflow B: First-Time Guest Selfie Search &amp; Persistent Name Tagging</text>
      <text x="15" y="40" font-size="9.5" fill="#CBD5E1">1. Guest scans QR code &rarr; Opens public event page (no account needed) &rarr; Captures selfie.</text>
      <text x="15" y="55" font-size="9.5" fill="#CBD5E1">2. Backend searches Rekognition collection &rarr; Creates guest session (SHA-256 token hash).</text>
      <text x="15" y="70" font-size="9.5" fill="#CBD5E1">3. Guest tags their name &rarr; Backend generates unique @username &rarr; Saves to guest_identities.</text>
      <text x="15" y="85" font-size="9.5" fill="#34D399">Result: Guest views matched thumbnails; identity is permanently mapped for future visits.</text>
    </g>

    <!-- Workflow C Card -->
    <g transform="translate(1245, 42)">
      <rect width="615" height="92" rx="6" fill="#1E293B" stroke="#FBBF24" stroke-width="1.2" />
      <text x="15" y="22" font-size="11.5" font-weight="800" fill="#FBBF24">Workflow C: Returning Guest Seamless Search by Name</text>
      <text x="15" y="40" font-size="9.5" fill="#CBD5E1">1. Guest re-visits event URL &rarr; Switches to "Search by Name" tab &rarr; Enters name / @username.</text>
      <text x="15" y="55" font-size="9.5" fill="#CBD5E1">2. GuestIdentityService queries exact username &gt; exact name &gt; partial name match.</text>
      <text x="15" y="70" font-size="9.5" fill="#CBD5E1">3. System retrieves mapped photo IDs &rarr; Generates fresh authorized guest session.</text>
      <text x="15" y="85" font-size="9.5" fill="#FBBF24">Result: Guest instantly fetches updated event photos without taking another selfie!</text>
    </g>

    <!-- Workflow D Card -->
    <g transform="translate(1875, 42)">
      <rect width="620" height="92" rx="6" fill="#1E293B" stroke="#F472B6" stroke-width="1.2" />
      <text x="15" y="22" font-size="11.5" font-weight="800" fill="#F472B6">Workflow D: Secure HD Original Photo &amp; Streaming ZIP Download</text>
      <text x="15" y="40" font-size="9.5" fill="#CBD5E1">1. Guest selects photos &rarr; Submits download request with X-Guest-Session token header.</text>
      <text x="15" y="55" font-size="9.5" fill="#CBD5E1">2. Single download: Gatekeeper verifies match &rarr; Generates presigned GET for original HD.</text>
      <text x="15" y="70" font-size="9.5" fill="#CBD5E1">3. Batch download: ZipService validates all IDs &rarr; Streams HD files from S3 to ZIP response.</text>
      <text x="15" y="85" font-size="9.5" fill="#F472B6">Result: Guests receive full-quality original HD photos; unauthorized images strictly blocked.</text>
    </g>
  </g>

  <!-- ======================================================================== -->
  <!-- FOOTER BAR                                                               -->
  <!-- ======================================================================== -->
  <g transform="translate(40, 1685)">
    <rect width="2520" height="145" rx="10" fill="#0B0F19" stroke="#334155" stroke-width="1" />
    <text x="25" y="32" font-size="12" font-weight="700" fill="#E2E8F0">ARCHITECTURE SPECIFICATION SUMMARY &amp; AUDIT TRAIL</text>
    
    <text x="25" y="56" font-size="10.5" fill="#94A3B8">
      • <tspan font-weight="700" fill="#38BDF8">Multi-Tenant Isolation:</tspan> Currently enforced at User/Photographer level via foreign keys. Studio entity and prepaid wallet are planned as multi-studio SaaS expansions.
    </text>
    <text x="25" y="74" font-size="10.5" fill="#94A3B8">
      • <tspan font-weight="700" fill="#34D399">Guest Privacy &amp; Public Access:</tspan> Unauthenticated public access is strictly constrained. Ephemeral SHA-256 session tokens guard all photo downloads. Unmatched photos cannot be browsed.
    </text>
    <text x="25" y="92" font-size="10.5" fill="#FBBF24">
      • <tspan font-weight="700" fill="#F59E0B">AI Strategy Pattern:</tspan> Facade and Strategy interface decouples business logic from AWS Rekognition. Migration to self-hosted open-source vector search requires zero controller changes.
    </text>
    <text x="25" y="110" font-size="10.5" fill="#94A3B8">
      • <tspan font-weight="700" fill="#A855F7">Performance Distinction:</tspan> Dual-stream pipeline prioritizes 1080px compressed thumbnail indexing for near-real-time guest searches while archiving original HD photos for print downloads.
    </text>
    <text x="25" y="128" font-size="10.5" fill="#64748B">
      Project: FotoFetch AI • Department of Software Engineering, Mehran University of Engineering &amp; Technology (MUET), Jamshoro • Batch 23SW Final Year Project Documentation
    </text>
  </g>

</svg>`;
}

const svgContent = createSvg();
const outputPath = path.resolve('docs/architecture/fotofetch-overall-architecture.svg');
fs.writeFileSync(outputPath, svgContent, 'utf8');
console.log('Successfully generated SVG at:', outputPath);
