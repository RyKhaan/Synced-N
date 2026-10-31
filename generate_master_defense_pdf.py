import os
import sys
from reportlab.lib.pagesizes import letter
from reportlab.lib.units import inch
from reportlab.lib import colors
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether, HRFlowable
)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.pdfgen import canvas

class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super().showPage()
        super().save()

    def draw_page_decorations(self, page_count):
        self.saveState()
        self.setFont("Helvetica-Bold", 8)
        self.setFillColor(colors.HexColor("#0D1B2A"))
        
        # Header (pages > 1)
        if self._pageNumber > 1:
            self.drawString(54, 752, "PROJECT NEXUS — SYNCED N")
            self.setFont("Helvetica", 8)
            self.setFillColor(colors.HexColor("#555555"))
            self.drawString(185, 752, "|  Master in Information Technology (MIT) Defense & Technical Guide")
            self.setStrokeColor(colors.HexColor("#B0BEC5"))
            self.setLineWidth(0.6)
            self.line(54, 745, 558, 745)

        # Footer (all pages)
        self.setStrokeColor(colors.HexColor("#B0BEC5"))
        self.setLineWidth(0.6)
        self.line(54, 42, 558, 42)
        
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#666666"))
        self.drawString(54, 30, "ICCT Colleges Foundation Inc. — College of Engineering and Digital Technology")
        page_str = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(558, 30, page_str)
        self.restoreState()

def generate_pdf():
    pdf_filename = r"C:\Users\RYAN\AndroidStudioProjects\Synced1.0\Synced_N_Master_Technical_Defense_Guide.pdf"
    doc = SimpleDocTemplate(
        pdf_filename,
        pagesize=letter,
        leftMargin=54,
        rightMargin=54,
        topMargin=54,
        bottomMargin=50
    )

    styles = getSampleStyleSheet()

    doc_title = ParagraphStyle(
        'DocTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=18,
        leading=22,
        textColor=colors.HexColor('#0D1B2A'),
        alignment=1,
        spaceAfter=4
    )

    doc_sub = ParagraphStyle(
        'DocSub',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9.5,
        leading=14,
        textColor=colors.HexColor('#333333'),
        alignment=1,
        spaceAfter=12
    )

    h1 = ParagraphStyle(
        'Heading1',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=12,
        leading=16,
        textColor=colors.HexColor('#0D1B2A'),
        spaceBefore=14,
        spaceAfter=6,
        keepWithNext=True
    )

    h2 = ParagraphStyle(
        'Heading2',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=10,
        leading=14,
        textColor=colors.HexColor('#6C4AB6'),
        spaceBefore=10,
        spaceAfter=4,
        keepWithNext=True
    )

    h3 = ParagraphStyle(
        'Heading3',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9,
        leading=13,
        textColor=colors.HexColor('#263238'),
        spaceBefore=6,
        spaceAfter=3,
        keepWithNext=True
    )

    body = ParagraphStyle(
        'BodyDark',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=12.5,
        textColor=colors.HexColor('#212121'),
        spaceAfter=4
    )

    speech = ParagraphStyle(
        'SpeechQuote',
        parent=styles['Normal'],
        fontName='Helvetica-Oblique',
        fontSize=8.5,
        leading=13,
        textColor=colors.HexColor('#1A237E'),
        leftIndent=12,
        rightIndent=12,
        spaceBefore=3,
        spaceAfter=5
    )

    bullet = ParagraphStyle(
        'BulletText',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=12,
        textColor=colors.HexColor('#212121'),
        leftIndent=14,
        firstLineIndent=-9,
        spaceAfter=3
    )

    code_box = ParagraphStyle(
        'CodeBox',
        parent=styles['Normal'],
        fontName='Courier',
        fontSize=7.5,
        leading=10.5,
        textColor=colors.HexColor('#263238'),
        leftIndent=12,
        spaceBefore=3,
        spaceAfter=4
    )

    story = []

    # Title Block
    story.append(Paragraph("PROJECT NEXUS — SYNCED N", doc_title))
    story.append(Paragraph("<b>COMPREHENSIVE TECHNICAL DEFENSE & SYSTEM ARCHITECTURE MANUAL</b><br/>Deep-Dive for Android Studio, Cloud Firestore, Brevo, Cloudinary & MIT Panelist Critiques<br/>Academic Year 2026–2027", doc_sub))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor("#0D1B2A"), spaceAfter=10))

    # SECTION 1: ARCHITECTURAL OVERVIEW & DEEP-DIVE
    story.append(Paragraph("SECTION 1: HIGH-LEVEL ARCHITECTURE & REAL REPOSITORY STRUCTURE", h1))
    story.append(Paragraph("Ang <b>Synced N: Church Music Ministry Schedule & Lineup Management System</b> ay isang native Android application na binuo gamit ang <b>Kotlin 2.0+ (compileSdk: 37, targetSdk: 37, minSdk: 24)</b>. Ang architecture nito ay nakabatay sa <b>Model-View-Controller (MVC)</b> na may <b>Repository Pattern</b> upang maging hiwalay ang UI layer sa backend business logic at cloud services.", body))
    story.append(Spacer(1, 4))

    code_overview = (
        "<b>Repository Package & Core File Mapping:</b><br/>"
        "• <b>Application ID:</b> <code>com.example.syncedn</code> (Min SDK: 24 Android 7.0, Target SDK: 37)<br/>"
        "• <b>Frontend Activities:</b> <code>MainActivity.kt</code> (Auth/OTP), <code>AdminActivity.kt</code> (Leader 7,936 lines), <code>MemberActivity.kt</code> (Member 3,500+ lines)<br/>"
        "• <b>Backend Engine:</b> <code>data/database/TeamRepository.kt</code> (4,913 lines) & <code>FirestoreManager.kt</code> (634 lines)<br/>"
        "• <b>Auth & Email OTP:</b> <code>data/auth/AuthManager.kt</code> & <code>EmailOtpService.kt</code> (Brevo REST API v3)<br/>"
        "• <b>Cloud Media Storage:</b> <code>data/storage/CloudinaryUploader.kt</code> & <code>ImageLoader.kt</code> (Client-side 512px downscaling)<br/>"
        "• <b>Security Rules:</b> <code>firestore.rules</code> (694 lines ng production-grade RBAC validators)<br/>"
        "• <b>Stage Usability:</b> <code>ChordSheet.java</code>, <code>ChordSheetInput.kt</code>, <code>MetronomePcm.java</code>, <code>StableScrollView.kt</code>"
    )
    story.append(Paragraph(code_overview, code_box))
    story.append(Spacer(1, 8))

    # SECTION 2: UML ARCHITECT DEEP-DIVE
    story.append(Paragraph("SECTION 2: UML ARCHITECT & TECHNICAL WRITER (DIZON, JESSA MAE G.)", h1))
    story.append(Paragraph("<i>Eksaktong Paliwanag sa Bawat Isa sa Apat (4) na Diagram sa Chapter 3:</i>", body))
    
    uml_diagrams = [
        ("Diagram 1: Use Case Diagram (Section 3.4.1, Page 27)",
         "\"Ipinapakita sa Use Case Diagram ang hangganan ng system (System Boundary) at ang dalawang pangunahing aktor: ang Ministry Leader (Admin) at ang Ministry Member (Musicians/Singers).\n"
         "1. Role Segregation: Ang Leader ang may administrative use cases tulad ng 'Manage Teams', 'Manage Services', 'Assign Members & Roles', at 'Create/Manage Song Lineups'.\n"
         "2. Relationship Stereotypes (<<include>> vs <<extends>>): Ang paggawa ng service schedule ay may <<include>> relationship sa 'View Schedule' at 'View Song Lineup' dahil bawat schedule ay awtomatikong binubuo ng specific service details at song setlist. Samantala, ang 'Request Substitution' at 'Accept/Decline Substitution' ay may <<extends>> relationship sa 'View Record/History' dahil nag-ti-trigger lang ang substitution flow kapag may emergency conflict o hindi makakarating ang miyembro.\n"
         "3. Member Functions: Ang mga miyembro ay may nakalaang use cases para sa 'Update Availability', 'Request Inclusion/Exclusion', 'Access Song Library', at 'Participate in Group Conversation'.\"",
         ["Actor: Ministry Leader (Primary Initiator para sa Service at Roster Lifecycle)",
          "Actor: Ministry Member (Volunteer Musician, nagmamarka ng availability at tumatanggap ng assignments)",
          "Boundary: Synced N Android App na nag-uugnay ng real-time state mula sa Cloud Firestore"]),
        
        ("Diagram 2: Activity Diagrams (Section 3.4.2, Page 28)",
         "\"Ang Activity Diagrams ng Synced N ay nahahati sa 3 Swimlanes: Ministry Leader, Synced N System (Engine), at Ministry Member. Ito ay nagpapakita ng deterministic logic flow ng aming core workflows:\n"
         "1. Authentication & Team Access Flow: Parehong dadaan sa Login/Registration form. Ibe-verify ng System ang credentials sa Firebase Auth. Kapag verified, magba-branch ang system: Leader Dashboard o Member Dashboard batay sa 'role' field sa /users/{uid}.\n"
         "2. Conflict Detection & Substitution Flow: Kapag nag-set ng schedule ang Leader o nag-file ng unavailability ang Member, dadaan ito sa Decision Node: 'Any conflicts or substitution needed?'. Kung 'Yes', mag-a-alert ang system at papayagan ang substitution evaluation; kung 'No', diretso itong mag-po-process at mag-u-update sa Firestore.\n"
         "3. Realtime Push & Sync Flow: Pagkatapos ma-save ang changes sa Cloud Firestore, magdi-dispatch ang system ng background updates patungo sa Member screen upang ma-display ang bagong lineups at rehearsal details nang sabay-sabay.\"",
         ["3 Swimlanes: Leader (Manual decisions), Synced N (Algorithmic checks), Member (Response/View)",
          "Decision Diamonds: May conditional branching para sa invalid login, role conflict, at substitution approval",
          "Terminal State: End Session na nag-te-terminate sa token session"]),

        ("Diagram 3: Class Diagram (Section 3.4.3, Page 29)",
         "\"Ang aming Class Diagram ay nagpapatunay ng matibay na Object-Oriented Design (OOD) at Model-View-Controller structure ng application:\n"
         "1. Generalization / Inheritance: Ang base class ay 'User' (naglalaman ng id, name, email, password, role, isMember, createdAt). Mula rito, nag-i-inherit ang 'Ministry Leader' (may leaderId, position, at administrative methods tulad ng manageServices(), assignMembers()) at 'Ministry Member' (may memberId, availabilityStatus, at requestSubstitution()).\n"
         "2. Multiplicity & Associations: Ang isang Team ay may 1-to-many (1..*) na TeamMember. Ang isang Ministry Leader ay pwedeng mag-schedule ng 0..* na Service. Bawat Service ay may 0..* na ServiceAssignment at 1 SongLineup. Ang isang SongLineup ay naglalaman ng 0..* na Song entities.\n"
         "3. Transactional Classes: Ipinapakita rin dito ang 'Availability' (date, status, notes), 'SubstitutionRequest' (requesterUid, substituteUid, status), at 'Notification'/'Message' classes na may kani-kanilang encapsulation at operation methods.\"",
         ["Base Class: User (Id, Name, Email, Role) na ini-extend ng Leader at Member",
          "Multiplicity: 1 Team -> 1..* TeamMember; 1 Service -> 0..* Assignment -> 1 SongLineup",
          "Data Encapsulation: Lahat ng properties ay private/immutable na may explicit getters at business operations"]),

        ("Diagram 4: Entity-Relationship Diagram - ERD (Section 3.4.4, Page 30)",
         "\"Bagamat NoSQL Document Store ang Cloud Firestore, ang logical ERD ang aming mathematical blueprint upang masigurong walang data anomalies at may strict referential integrity:\n"
         "1. Primary Keys (PK) & Foreign Keys (FK): Ang table na USER ay may PK na user_id (matches Firebase Auth UID). Ito ay nag-re-reference sa MEMBER_ROLE gamit ang member_id bilang FK. Ang SERVICE table ay may PK na service_id na nagsisilbing foreign key sa ASSIGNMENT table (FK: service_id, FK: member_id, FK: role_id) at LINEUP_ITEM table (FK: service_id, FK: song_id).\n"
         "2. Normalization & Many-to-Many Resolution: Ang relasyon sa pagitan ng MEMBER at ROLE ay na-normalize gamit ang bridge entity na MEMBER_ROLE. Ganito rin sa SERVICE at SONG na pinag-uugnay ng LINEUP_ITEM na nagtataglay ng specific song_key at song_order para sa service na iyon.\n"
         "3. NoSQL Physical Mapping: Sa aming actual implementation sa Firestore, ang mga relational FK entities na ito ay na-optimize bilang subcollections (e.g. /teams/{teamCode}/services/{serviceId}) para sa high-speed querying at zero-join retrieval.\"",
         ["Bridge Entities: MEMBER_ROLE, ASSIGNMENT, at LINEUP_ITEM upang i-resolve ang M:N relationships",
          "Referential Integrity: Bawat service record ay may explicit binding sa valid member at song ID",
          "Schema Normalization: 3NF logical structure bago i-denormalize para sa NoSQL Firestore reads"])
    ]

    for title, sp, bullets in uml_diagrams:
        flow = []
        flow.append(Paragraph(f"<b>{title}</b>", h2))
        flow.append(Paragraph("<b>Paano Ipapaliwanag sa Panelist (Verbatim Script):</b>", h3))
        flow.append(Paragraph(sp, speech))
        flow.append(Paragraph("<b>Key Architectural Concepts na Dapat I-Highlight:</b>", h3))
        for b in bullets:
            flow.append(Paragraph(f"• {b}", bullet))
        flow.append(Spacer(1, 4))
        story.append(KeepTogether(flow))

    # SECTION 3: DATABASE ADMINISTRATOR DEEP-DIVE
    story.append(Spacer(1, 8))
    story.append(Paragraph("SECTION 3: DATABASE ADMINISTRATOR (PENDRE, JUSTIN KYLE D.)", h1))
    story.append(Paragraph("<i>Mula sa Setup sa Firebase Console hanggang sa 694 lines ng firestore.rules:</i>", body))
    
    dba_steps = [
        ("Step-by-Step Implementation sa Firebase Console:",
         "1. <b>Project Provisioning:</b> Lumikha ng bagong Google Firebase Project na pinangalanang 'Synced N' sa Google Cloud Console.<br/>"
         "2. <b>Authentication Setup:</b> In-enable ang 'Email/Password' Sign-in method at 'Google Sign-In' gamit ang OAuth 2.0 Web Client ID (<code>74207597857-f0c167eg3u06hdvu03h3671ftgt9q8dj.apps.googleusercontent.com</code>).<br/>"
         "3. <b>Firestore Database Creation:</b> In-initialize ang Cloud Firestore sa <b>Production Mode</b> (default deny-all). Pinili ang rehiyon na pinakamalapit sa Pilipinas (asia-southeast1 / Singapore) upang matiyak ang mababang network latency (<80ms).<br/>"
         "4. <b>Android App Registration & Connection:</b> Ni-register ang package name <code>com.example.syncedn</code>. Kinuha ang SHA-1 at SHA-256 digital fingerprint signatures mula sa Gradle Signing Report (<code>./gradlew signingReport</code>) at in-upload sa Firebase Project Settings.<br/>"
         "5. <b>Configuration File Insertion:</b> I-dinownload ang nabuong <code>google-services.json</code> at inilagay sa folder na <code>c:\\Users\\RYAN\\AndroidStudioProjects\\Synced1.0\\app\\google-services.json</code>.<br/>"
         "6. <b>Gradle Dependencies:</b> Sa <code>app/build.gradle.kts</code>, inilagay ang <code>alias(libs.plugins.google.services)</code> at in-import ang official Firebase BoM (Bill of Materials) kasama ang <code>libs.firebase.auth</code> at <code>libs.firebase.firestore</code>."),
        
        ("Database Schema & Document Structure (Eksaktong Collections):",
         "• <b>Root Collection <code>/users/{userId}</code>:</b> Document ID = Firebase Auth UID. May fields na <code>uid</code>, <code>username</code>, <code>email</code>, <code>role</code> ('Leader' o 'Member'), <code>teamCode</code> (6 chars), <code>ministryRole</code>, <code>unavailableRanges</code> (List ng dates), <code>photoUri</code> (Cloudinary HTTPS URL), at <code>removedByLeader</code> (boolean).<br/>"
         "• <b>Root Collection <code>/teams/{teamCode}</code>:</b> Document ID = 6-character uppercase code (e.g. 'W4K9LM'). Naglalaman ng <code>leaderUid</code>, <code>leaderName</code>, <code>roles</code>, at <code>removedMembers</code>.<br/>"
         "• <b>Subcollection <code>/teams/{teamCode}/services/{serviceId}</code>:</b> Naglalaman ng <code>title</code>, <code>date</code>, <code>start</code>, <code>end</code>, <code>venue</code>, <code>status</code> ('Draft'/'Published'), <code>assignments</code> (List ng Maps ng roles at member UIDs), at <code>songs</code>.<br/>"
         "• <b>Subcollection <code>/teams/{teamCode}/songs/{songId}</code>:</b> Naglalaman ng <code>title</code>, <code>artist</code>, <code>key</code>, <code>bpm</code>, <code>sheet</code> (Chordpro formatted text), <code>lyrics</code>, at <code>referenceUrl</code>.<br/>"
         "• <b>Subcollection <code>/teams/{teamCode}/announcements/{announcementId}</code>:</b> Priority ministry broadcasts na may <code>urgent</code> boolean flag.<br/>"
         "• <b>Subcollection <code>/teams/{teamCode}/substitutions/{substitutionId}</code>:</b> Handshake logs: <code>requesterUid</code>, <code>substituteUid</code>, <code>role</code>, <code>reason</code>, at <code>status</code> ('Pending', 'Accepted', 'Approved', 'Declined').<br/>"
         "• <b>Subcollection <code>/teams/{teamCode}/messages/{messageId}</code>:</b> Real-time team chat na may <code>senderUid</code>, <code>text</code>, <code>mediaUrl</code>, <code>timestamp</code>, at <code>isDeleted</code> flags."),

        ("Deep-Dive sa <code>firestore.rules</code> (694 Lines ng Security Validation):",
         "Ang aming Firestore Security Rules ay <b>HINDI</b> naka-test mode kundi production-grade security architecture:<br/>"
         "1. <b>Default Deny Rule (Line 537):</b> <code>match /{document=**} { allow read, write: if false; }</code> — Walang sinuman ang makaka-access sa database nang walang explicit rule.<br/>"
         "2. <b>Authentication & Ownership (Lines 167-188):</b> Lahat ng operation ay kailangang dumaan sa <code>isAuthenticated()</code> (<code>request.auth != null</code>) at <code>isDocOwner()</code> para hindi ma-overwrite ng isang user ang profile ng ibang user.<br/>"
         "3. <b>Multi-Tenant Isolation (Line 282):</b> Function <code>isSameTeam(teamCode)</code> kung saan bina-validate na ang <code>teamCode</code> ng requester ay kapareho ng teamCode ng document. Bawal mabasa ng ibang church ang data ng ibang church.<br/>"
         "4. <b>Role-Based Access Control (RBAC - Line 271):</b> Ang function na <code>isLeaderOfTeam(teamCode)</code> ay nagbabasa sa root <code>/teams/{teamCode}</code> upang patunayan na <code>request.auth.uid == team.leaderUid</code>. Tanging ang Leader lang ang pinapayagang mag-write sa <code>services</code> at <code>announcements</code>.<br/>"
         "5. <b>Field-Level Validation & Sanitization:</b> May functions tulad ng <code>hasOnlyAllowedFields()</code>, <code>validStringLength(min, max)</code>, <code>isValidEmail()</code>, at regex enforcement para sa 6-character team codes (<code>^[A-Z0-9]{6}$</code>).")
    ]

    for title, content, _ in [(x[0], x[1], "") for x in dba_steps]:
        flow = []
        flow.append(Paragraph(f"<b>{title}</b>", h2))
        flow.append(Paragraph(content, body))
        flow.append(Spacer(1, 4))
        story.append(KeepTogether(flow))

    # SECTION 4: DEVOPS & DEPLOYMENT SPECIALIST
    story.append(Spacer(1, 8))
    story.append(Paragraph("SECTION 4: DEVOPS & DEPLOYMENT SPECIALIST (LAGARE, JOSHUA A.)", h1))
    story.append(Paragraph("<i>Eksaktong Proseso ng Pag-Build, Pag-Sign, at Pag-Deploy ng Android APK:</i>", body))

    devops_text = (
        "<b>1. Build Pipeline & Gradle Tasks:</b><br/>"
        "• Ginamit ang <b>Android Gradle Plugin (AGP 8.x+)</b> kasama ang Kotlin Gradle Plugin. Ang buong project compilation ay pinapatakbo sa pamamagitan ng Gradle Wrapper (<code>./gradlew</code>).<br/>"
        "• Para sa development at testing, binuo ang Debug APK gamit ang command na: <code>./gradlew assembleDebug</code> na naglalabas ng artifact sa <code>app/build/outputs/apk/debug/app-debug.apk</code>.<br/>"
        "• Para sa production packaging, ginamit ang command na: <code>./gradlew assembleRelease</code>.<br/><br/>"
        "<b>2. Keystore Signing & Security:</b><br/>"
        "• Gumamit ng Java Keytool upang mag-generate ng 2048-bit RSA cryptographic signing key: <code>keytool -genkey -v -keystore syncedn.jks -keyalg RSA -keysize 2048 -validity 10000 -alias syncedn</code>.<br/>"
        "• Sa <code>app/build.gradle.kts</code> (Lines 52-59), na-configure ang <code>buildTypes { release { signingConfig = signingConfigs.getByName(\"debug\") } }</code> para sa seamless evaluation testing nang hindi nanganganib ma-expose ang release production credentials sa public git.<br/><br/>"
        "<b>3. Environment Secrets Management:</b><br/>"
        "• Mahigpit na ipinagbawal ang pag-hardcode ng API keys sa repository. Sa <code>build.gradle.kts</code> (Lines 9-24), lumikha ng custom resolver na <code>resolveEnvSecret()</code> na nagbabasa mula sa <code>local.properties</code> at <code>.env</code> file.<br/>"
        "• Ang mga secrets na <code>BREVO_API_KEY</code>, <code>BREVO_SENDER_EMAIL</code>, <code>CLOUDINARY_CLOUD_NAME</code>, at <code>CLOUDINARY_UPLOAD_PRESET</code> ay awtomatikong ini-inject bilang dynamic <code>BuildConfig</code> fields sa runtime.<br/><br/>"
        "<b>4. Sideloading, Internal Testing & Deployment Distribution:</b><br/>"
        "• Para sa UAT sa New Life Christian Church, ang distribution ay isinagawa gamit ang <b>Firebase App Distribution</b> at direktang <b>USB Sideloading via ADB</b> (<code>adb install -r SyncedN.apk</code>). Tiniyak na naka-enable ang 'Install Unknown Apps' permission sa mga Android test devices."
    )
    story.append(Paragraph(devops_text, body))

    # SECTION 5: LEAD BACKEND DEVELOPER DEEP-DIVE
    story.append(Spacer(1, 8))
    story.append(Paragraph("SECTION 5: LEAD BACKEND DEVELOPER (VILLAR, JOHN LLOYD F.)", h1))
    story.append(Paragraph("<i>Saan Matatagpuan ang Backend sa Source Code at Paano Tumatakbo ang Business Logic:</i>", body))

    backend_text = (
        "<b>1. Lokasyon ng Backend Engine sa Codebase:</b><br/>"
        "• Ang buong backend business logic ay <b>HINDI</b> nakakalat sa UI activities. Ito ay naka-encapsulate sa dalawang pangunahing klase:<br/>"
        "  a. <code>app/src/main/java/com/example/syncedn/data/database/TeamRepository.kt</code> (4,913 lines) — Core Data Access & State Orchestrator.<br/>"
        "  b. <code>app/src/main/java/com/example/syncedn/data/database/FirestoreManager.kt</code> (634 lines) — Low-level Firestore CRUD & Session Caching.<br/><br/>"
        "<b>2. In-Memory Reactive Caching Engine (Zero Latency on Stage):</b><br/>"
        "• Sa <code>TeamRepository.kt</code> (Lines 38-66), nag-implement ako ng multi-layered in-memory cache gamit ang <code>java.util.concurrent.ConcurrentHashMap</code>:<br/>"
        "  - <code>cachedRosters</code>, <code>cachedServices</code>, <code>cachedSongs</code>, <code>cachedAnnouncements</code>, <code>cachedSubstitutions</code>.<br/>"
        "• <i>Bakit ito ginawa?</i> Sa loob ng simbahan, madalas na mahina ang mobile data o Wi-Fi. Sa pamamagitan ng memory cache at SharedPreferences sync, 0-milliseconds ang response time ng paglipat ng tabs sa screen at hindi mag-ka-crash ang app kung mawalan ng koneksyon habang nagpapatugtog.<br/><br/>"
        "<b>3. Conflict Detection Algorithm:</b><br/>"
        "• Bago i-save ang isang bagong service schedule, tinatawag ang verification engine. Sinusuri nito ang bawat miyembro sa dalawang aspeto:<br/>"
        "  1. <i>Availability Calendar Match:</i> Bina-validate kung ang petsa ng service ay tumatama sa <code>unavailableRanges</code> ng user.<br/>"
        "  2. <i>Double-Booking Check:</i> Bina-validate kung ang miyembro ay naka-assign na sa isa pang service na may overlapping start at end time.<br/>"
        "• Kapag may conflict, nagbabalik ang backend ng structured warning record kaya napipigilan ang leader bago pa ma-publish ang schedule.<br/><br/>"
        "<b>4. Substitution Handshake State Machine:</b><br/>"
        "• Makikita sa <code>TeamRepository.kt</code> (Line 3670: <code>createSubstitution()</code> at Line 4033: <code>acceptSubstitution()</code>).<br/>"
        "• Gumagamit ito ng deterministic status machine: <code>Pending</code> ➔ <code>Accepted</code> ➔ <code>Approved</code>.<br/>"
        "• Sa sandaling i-click ng substitute o ng leader ang 'Accept', awtomatikong binubuksan ng backend ang linked <code>ServiceData</code> document sa Firestore, hinahanap ang lumang assignee sa <code>assignments</code> list, pinapalitan ito ng bagong substitute UID, at sine-save ang update gamit ang atomic transaction."
    )
    story.append(Paragraph(backend_text, body))

    # SECTION 6: BREVO & CLOUDINARY INTEGRATIONS
    story.append(Spacer(1, 8))
    story.append(Paragraph("SECTION 6: CLOUD INTEGRATIONS (BREVO REST API & CLOUDINARY CDN)", h1))
    
    cloud_integrations = (
        "<b>1. Brevo REST API v3 — Email OTP & Account Verification:</b><br/>"
        "• <b>File:</b> <code>app/src/main/java/com/example/syncedn/data/auth/EmailOtpService.kt</code> (271 lines)<br/>"
        "• <b>Bakit hindi Firebase Default Reset Email?</b> Ang generic Firebase email ay madalas pumasok sa Spam folder o ma-block ng email security filters. Sa pamamagitan ng Brevo Transactional SMTP REST API (<code>https://api.brevo.com/v3/smtp/email</code>), nakakapagpadala kami ng customized, professional HTML 6-digit verification code.<br/>"
        "• <b>Cryptographic Security:</b> Gumamit ng <code>java.security.SecureRandom</code> para sa unpredictable 6-digit generation. May strict parameters: <code>OTP_VALIDITY_MS = 10 minutes</code>, <code>RESEND_COOLDOWN_MS = 60 seconds</code>, at <code>MAX_OTP_ATTEMPTS = 5</code> upang maiwasan ang brute-force attacks.<br/><br/>"
        "<b>2. Cloudinary CDN — Client-Side Optimized Image Pipeline:</b><br/>"
        "• <b>File:</b> <code>app/src/main/java/com/example/syncedn/data/storage/CloudinaryUploader.kt</code> (288 lines)<br/>"
        "• <b>Bakit Cloudinary?</b> Ang Cloudinary ay nag-aalok ng high-speed global image delivery network nang libre nang walang strict egress bandwidth caps ng Firebase Storage.<br/>"
        "• <b>Client-Side Image Compression:</b> Bago i-upload ang raw camera photo (na karaniwang 5MB–10MB), binabasa ng device ang EXIF orientation (<code>ExifInterface</code>), dina-downscale ang imahe sa <code>maxDimension = 512px</code>, at kino-compress sa 85% JPEG quality. Dahil dito, ang file size na ini-upload ay nagiging mas mababa sa 150KB—napakabilis kahit mahina ang signal ng phone."
    )
    story.append(Paragraph(cloud_integrations, body))

    # SECTION 7: CRITICAL MIT PANELIST QUESTIONS & HOLES (BUTAS)
    story.append(Spacer(1, 8))
    story.append(Paragraph("SECTION 7: MASTER IN INFORMATION TECHNOLOGY (MIT) PANELIST CRITIQUES & DEFENSE ANSWERS", h1))
    story.append(Paragraph("<i>Kung ako ang MIT Panelist, narito ang mga pinakamabigat na teknikal na tanong, butas, at kahinaan na aking sisitahin, kasama ang inyong bala at opisyal na depensa:</i>", body))
    story.append(Spacer(1, 4))

    mit_qa = [
        ("MIT Question 1: 'Sabi ninyo MVC ang architecture, pero bakit ang AdminActivity.kt ninyo ay umabot ng halos 8,000 lines? Hindi ba ito Anti-Pattern na tinatawag na Massive View Controller (Fat Activity)?'",
         "\"Opo, Panelist, kinikilala po namin na mataas ang line count ng aming AdminActivity.kt. Ngunit kung susuriin po ang aming software architecture, hindi ito monolithic spaghettified code.\n"
         "1. Separation of Concerns: Ang lahat ng persistent state, network calls, at Firestore business logic ay mahigpit na naka-isolate sa TeamRepository.kt at FirestoreManager.kt. Walang direct raw database querying na nakasulat sa loob ng UI buttons.\n"
         "2. Complex Stage Domain: Ang AdminActivity ay nagho-host ng 8 interconnected ministry management modals (Service Creation Wizard, In-App Chord Transposition Engine, YouTube Live Previewer, at Multi-user Roster Manager) na nangangailangan ng masinsinang lifecycle listeners at hardware orientation guards.\n"
         "3. Future Roadmap: Bilang pagpapabuti alinsunod sa Chapter 5 Recommendations, aming plano na i-refactor ang AdminActivity patungo sa modernong MVVM (Model-View-ViewModel) gamit ang Jetpack Compose Navigation upang mahati ito sa maliliit na screen composables.\"",
         "Depensa: I-highlight na nasa TeamRepository ang business logic, at aminin na recommendations ito for MVVM refactoring."),

        ("MIT Question 2: 'Bakit kayo gumamit ng NoSQL Cloud Firestore gayong napakalinaw na Relational ang inyong data (User, Team, Service, Lineup, Song)? Hindi ba magastos sa read/write billing kapag lumaki ang data?'",
         "\"Magandang punto po, Panelist. Sinadya po namin ang pagpili sa Cloud Firestore dahil sa 3 pangunahing bentahe para sa aming specific church domain:\n"
         "1. Native Real-Time Synchronization (WebSockets/Snapshot Listeners): Sa isang church service, kapag nagpalit ng kanta o key ang worship leader habang nagre-rehearse, kailangang mag-reflect agad ito sa screen ng mga gitarista at piyanista nang walang manual screen refresh. Ang Relational DB tulad ng PostgreSQL o MySQL ay mangangailangan ng kumplikadong external WebSocket server o frequent polling.\n"
         "2. Built-in Offline Persistence: Ang Cloud Firestore ay may automatic local SQLite/cache storage sa Android. Kapag nawalan ng internet sa loob ng simbahan, patuloy na mababasa ng musicians ang naunang na-load na chord sheets at schedule.\n"
         "3. Subcollection Scoping: Sa pamamagitan ng pag-organisa ng data sa ilalim ng /teams/{teamCode} subcollections, napanatili naming mababa ang document read costs dahil scoped lamang ang queries sa aktibong team.\"",
         "Depensa: Bigyang-diin ang Real-time Snapshot Listeners at Offline Caching na hindi libre sa traditional Relational SQL."),

        ("MIT Question 3: 'Wala ba kayong Race Condition o Concurrency Issue kapag sabay na nag-accept ang dalawang substitute sa iisang service role?'",
         "\"Naiwasan po namin ang concurrency issue na ito sa pamamagitan ng aming Handshake Logic sa TeamRepository.kt at Firestore Security Rules.\n"
         "1. Atomic Evaluation: Ang bawat substitution document sa /substitutions/{subId} ay may strict initial state na 'Pending'. Kapag ang unang substitute ay nag-click ng 'Accept', ang status ay agad na nag-a-update sa 'Accepted' kasabay ng pag-record ng kanyang specific substituteUid.\n"
         "2. Conditional Rules Validation: Sa aming firestore.rules, may kondisyon na nagbabawal mag-update sa isang substitution document kung ang kasalukuyang status nito ay hindi na 'Pending'. Dahil dito, ang anumang kasunod na request ay ire-reject ng Firestore engine bago pa man ito makapasok sa database.\"",
         "Depensa: I-cite ang Firestore Security Rules status machine check at atomic state transition."),

        ("MIT Question 4: 'Nakalagay sa local.properties ang API keys, pero paano ninyo pinoprotektahan ang Brevo at Cloudinary credentials sa APK decompilation gamit ang reverse-engineering tools tulad ng JADX?'",
         "\"Salamat po sa napakahalagang security question, Panelist.\n"
         "1. Build-Time Injection: Ang mga secrets ay hindi naka-hardcode sa git source code kundi dynamic na binabasa mula sa environment variables at local.properties bago i-compile ng Gradle sa BuildConfig.\n"
         "2. Cloudinary Unsigned Upload Preset: Ang aming Cloudinary configuration ay gumagamit ng 'Unsigned Upload' preset (synced_preset) na may restricted write-only capability (walang permission mag-delete o magbasa ng ibang files sa Cloudinary account).\n"
         "3. Fallback Obfuscation: Sa EmailOtpService.kt, nag-implement kami ng FALLBACK_MASKED_BYTES na ginamitan ng XOR-byte masking (xor 0x5A) upang hindi madaling mabasa sa static string analysis kapag na-decompile ang APK.\"",
         "Depensa: Ipaliwanag ang XOR Byte Masking sa EmailOtpService.kt at Unsigned Write-Only Presets."),

        ("MIT Question 5: 'Bakit Android-Only at walang iOS o Web app? Paano kung iPhone ang gamit ng Pastor o Worship Leader?'",
         "\"Alinsunod po ito sa aming Scope and Limitations sa Chapter 1 (Section 1.4, Limitations #1 at #9).\n"
         "1. Beneficiary Demographic: Sa isinagawang preliminary survey at interview kay Brother Nathaniel Ceralvo ng New Life Christian Church, 100% ng kanilang 15 active music ministry volunteers at leaders ay gumagamit ng Android smartphones.\n"
         "2. Native Hardware Optimization: Ang pag-develop gamit ang Native Android (Kotlin) ay nagbigay-daan upang makabuo kami ng low-latency audio engine para sa Metronome (MetronomePcm.java) at smooth 60fps auto-scrolling sa chord sheets na mahirap maabot sa hybrid web-wrapper apps sa mababang budget.\n"
         "3. Cross-Platform Extensibility: Nakasaad po sa aming Chapter 5 Recommendations na ang modular NoSQL structure sa Cloud Firestore ay 100% handa nang kabitan ng Flutter o React Web dashboard para sa future expansion.\"",
         "Depensa: I-angkla sa actual church interview data at native audio/scroll hardware performance.")
    ]

    for q, a, note in mit_qa:
        flow = []
        flow.append(Paragraph(f"<b>{q}</b>", h2))
        flow.append(Paragraph("<b>Opisyal na Depensa ng Grupo (Verbatim Script):</b>", h3))
        flow.append(Paragraph(a, speech))
        flow.append(Paragraph(f"<b>Core Defense Strategy:</b> {note}", body))
        flow.append(Spacer(1, 4))
        story.append(KeepTogether(flow))

    # SECTION 8: 10 TEAM MEMBERS QUICK REFERENCE CARD
    story.append(Spacer(1, 8))
    story.append(Paragraph("SECTION 8: 10 TEAM MEMBERS RESPONSIBILITY MATRIX (FOR GC COPY-PASTE)", h1))
    
    matrix_data = [
        [Paragraph("<b>Member & Role</b>", body), Paragraph("<b>Primary Technical Scope</b>", body), Paragraph("<b>Defense Focus / Deliverable</b>", body)],
        [Paragraph("<b>Genturo, Ryan Lloyd</b><br/>Project Manager", body), Paragraph("Agile Sprints, Bug Triage, Unit Testing, App Deployment", body), Paragraph("Project Timeline, Bug Resolution metrics, App Packaging", body)],
        [Paragraph("<b>Dizon, Jessa Mae</b><br/>UML Architect / Writer", body), Paragraph("Use Case, Activity, Class, ERD, Academic Cohesion", body), Paragraph("Diagram stereotypes (include/extend), 3NF Normalization", body)],
        [Paragraph("<b>Gregorio, Ralph Lauren</b><br/>UI/UX Lead Designer", body), Paragraph("Figma Design System, Contrast (#0D1B2A), Thumb-zone", body), Paragraph("Stage visibility ergonomics, low-glare dark palette", body)],
        [Paragraph("<b>Lagare, Joshua</b><br/>DevOps / Product Owner", body), Paragraph("Git branching, AGP 8.x, ProGuard/R8, Church Domain", body), Paragraph("Build pipeline, APK signing, Ministry user needs", body)],
        [Paragraph("<b>Lebuna, Angelo Daniel</b><br/>QA Engineer", body), Paragraph("Integration Testing, Edge Case Validation, Bug Log", body), Paragraph("Conflict alert accuracy, offline recovery, stress tests", body)],
        [Paragraph("<b>Pendre, Justin Kyle</b><br/>Database Administrator", body), Paragraph("Cloud Firestore, 694-line firestore.rules, Schema", body), Paragraph("NoSQL subcollections, RBAC security, zero data leaks", body)],
        [Paragraph("<b>Ramirez, John Michael</b><br/>System Analyst", body), Paragraph("SOP 1-5 to Feature Mapping, Process Evaluation", body), Paragraph("Demonstrating how Synced N solved NLCC's problems", body)],
        [Paragraph("<b>Salva, Ma. Paula</b><br/>UAT Coord. / Pitch", body), Paragraph("ISO/IEC 25010 Surveys, NLCC User Feedback, Presentation", body), Paragraph("Usability & Acceptability ratings (>4.5/5.0 Excellent)", body)],
        [Paragraph("<b>Sarmiento, Mark Allen</b><br/>Lead Frontend Developer", body), Paragraph("Material 3 UI, ChordSheetInput, MetronomePcm, Scroll", body), Paragraph("Stage tooling implementation, custom views, smooth UI", body)],
        [Paragraph("<b>Villar, John Lloyd</b><br/>Lead Backend Developer", body), Paragraph("TeamRepository (4.9k lines), Cache, Conflict Engine", body), Paragraph("In-memory caching, Realtime listeners, Substitution logic", body)]
    ]

    mat_table = Table(matrix_data, colWidths=[1.8*inch, 2.7*inch, 2.7*inch])
    mat_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#EEEEEE')),
        ('BOX', (0,0), (-1,-1), 0.5, colors.HexColor('#BBBBBB')),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.HexColor('#DDDDDD')),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('BOTTOMPADDING', (0,0), (-1,-1), 3),
        ('TOPPADDING', (0,0), (-1,-1), 3),
    ]))
    story.append(mat_table)

    doc.build(story, canvasmaker=NumberedCanvas)
    print("Master PDF build successful:", pdf_filename)

if __name__ == "__main__":
    generate_pdf()
