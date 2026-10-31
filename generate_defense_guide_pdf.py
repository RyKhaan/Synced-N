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
        self.setFont("Helvetica", 9)
        self.setFillColor(colors.HexColor("#555555"))
        
        # Header (pages > 1)
        if self._pageNumber > 1:
            self.drawString(54, 750, "PROJECT NEXUS — SYNCED N: Panel Defense & Technical Role Guide")
            self.setStrokeColor(colors.HexColor("#CCCCCC"))
            self.setLineWidth(0.5)
            self.line(54, 742, 558, 742)

        # Footer (all pages)
        self.setStrokeColor(colors.HexColor("#CCCCCC"))
        self.setLineWidth(0.5)
        self.line(54, 45, 558, 45)
        
        page_str = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(558, 32, page_str)
        self.drawString(54, 32, "Confidential — For Internal Project Defense Review")
        self.restoreState()

def build_pdf(filename):
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        leftMargin=54,
        rightMargin=54,
        topMargin=54,
        bottomMargin=54
    )

    styles = getSampleStyleSheet()

    # Custom styles
    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=20,
        leading=24,
        textColor=colors.HexColor('#0D1B2A'),
        alignment=1, # Center
        spaceAfter=6
    )

    subtitle_style = ParagraphStyle(
        'DocSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=11,
        leading=15,
        textColor=colors.HexColor('#333333'),
        alignment=1,
        spaceAfter=15
    )

    h1_style = ParagraphStyle(
        'H1',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=13,
        leading=17,
        textColor=colors.HexColor('#0D1B2A'),
        spaceBefore=12,
        spaceAfter=6,
        keepWithNext=True
    )

    h2_style = ParagraphStyle(
        'H2',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=11,
        leading=15,
        textColor=colors.HexColor('#6C4AB6'),
        spaceBefore=8,
        spaceAfter=4,
        keepWithNext=True
    )

    body_style = ParagraphStyle(
        'BodyPlain',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9.5,
        leading=13.5,
        textColor=colors.HexColor('#222222'),
        spaceAfter=5
    )

    speech_style = ParagraphStyle(
        'SpeechBox',
        parent=styles['Normal'],
        fontName='Helvetica-Oblique',
        fontSize=9.5,
        leading=14,
        textColor=colors.HexColor('#1A237E'),
        leftIndent=15,
        rightIndent=15,
        spaceBefore=4,
        spaceAfter=6
    )

    bullet_style = ParagraphStyle(
        'BulletText',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9,
        leading=13,
        textColor=colors.HexColor('#222222'),
        leftIndent=15,
        firstLineIndent=-10,
        spaceAfter=3
    )

    story = []

    # Title & Header
    story.append(Paragraph("PROJECT NEXUS — SYNCED N", title_style))
    story.append(Paragraph("<b>Church Music Ministry Schedule & Lineup Management System</b><br/>Capstone Panel Defense & Technical Role Guide | A.Y. 2026-2027", subtitle_style))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor("#0D1B2A"), spaceAfter=12))

    # Section 1: Presentation Flow & Strategy
    story.append(Paragraph("PART 1: PRESENTATION STRATEGY & PROGRAM TIMELINE (20-25 MINS)", h1_style))
    story.append(Paragraph("Upang maging maayos at propesyonal ang presentasyon sa panelist, gagamitin ng grupo ang <b>Storytelling Approach</b> na naka-angkla sa totoong suliranin ng New Life Christian Church (Pasig), susundan ng <b>Live Multi-User Interactive App Demonstration</b>.", body_style))
    story.append(Spacer(1, 4))

    table_data = [
        [Paragraph("<b>Time</b>", body_style), Paragraph("<b>Part / Topic</b>", body_style), Paragraph("<b>Lead Speaker</b>", body_style), Paragraph("<b>Key Objectives</b>", body_style)],
        [Paragraph("0:00 - 2:00", body_style), Paragraph("Title, Hook & Background", body_style), Paragraph("Genturo (PM) & Salva (Pitch)", body_style), Paragraph("Panimula, church context (15 members, 3 rotating teams), pain points (Messenger GC gulo, split chord sites).", body_style)],
        [Paragraph("2:00 - 4:00", body_style), Paragraph("Problem & Objectives", body_style), Paragraph("Ramirez (System Analyst)", body_style), Paragraph("5 SOPs: role tracking, chords/keys platform, team communication, last-minute changes, availability conflicts.", body_style)],
        [Paragraph("4:00 - 6:00", body_style), Paragraph("Architecture & Tech Stack", body_style), Paragraph("Lagare (DevOps) & Pendre (DBA)", body_style), Paragraph("Native Kotlin Android, Material 3, Cloud Firestore, Firebase Auth, Brevo REST API, Cloudinary CDN.", body_style)],
        [Paragraph("6:00 - 8:00", body_style), Paragraph("UI/UX Design Philosophy", body_style), Paragraph("Gregorio (UI/UX)", body_style), Paragraph("Dark Navy (#0D1B2A), Stage accessibility, thumb-zone controls, dynamic chord transposition, metronome ticker.", body_style)],
        [Paragraph("8:00 - 17:00", body_style), Paragraph("LIVE APP DEMONSTRATION", body_style), Paragraph("Genturo, Sarmiento, Villar", body_style), Paragraph("Dual-screen live simulation: Member availability -> Admin service & conflict alert -> Lineup sync -> Substitution handshake.", body_style)],
        [Paragraph("17:00 - 19:30", body_style), Paragraph("Testing & UAT Findings", body_style), Paragraph("Lebuna (QA) & Salva (UAT)", body_style), Paragraph("Unit & integration test metrics, ISO/IEC 25010 UAT evaluation results mula sa New Life Christian Church.", body_style)],
        [Paragraph("19:30 - 21:00", body_style), Paragraph("Summary & Recommendations", body_style), Paragraph("Dizon (Writer) & Genturo (PM)", body_style), Paragraph("Objective accomplishment recap, limitations (Android-only, no AI), at future roadmap.", body_style)],
        [Paragraph("21:00+", body_style), Paragraph("Q&A Panel Defense", body_style), Paragraph("All 10 Team Members", body_style), Paragraph("Pagsagot sa technical, architectural, at domain questions ng panel.", body_style)]
    ]

    t = Table(table_data, colWidths=[1.0*inch, 1.8*inch, 1.6*inch, 2.6*inch])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#EEEEEE')),
        ('BOX', (0,0), (-1,-1), 0.5, colors.HexColor('#BBBBBB')),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.HexColor('#DDDDDD')),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('TOPPADDING', (0,0), (-1,-1), 4),
    ]))
    story.append(t)
    story.append(Spacer(1, 14))

    # Section 2: Live App Demo Flow
    story.append(Paragraph("PART 2: LIVE APP DEMO FLOW (DUAL-SCREEN SUNDAY SERVICE SIMULATION)", h1_style))
    story.append(Paragraph("I-project ang dalawang screen magkatabi gamit ang scrcpy o dalawang emulators (Device A = Leader/Admin; Device B = Member/Student).", body_style))
    
    steps = [
        "<b>Step 1 (Member Availability):</b> Mag-login si Member sa Device B. I-mark ang sarili sa Availability Calendar (Available o Unavailable dahil sa school exam). Makikita ng panel na may pre-flight availability data bago magplano.",
        "<b>Step 2 (Service Creation & Conflict Detection):</b> Sa Device A, bubuksan ni Admin ang Service Wizard. Susubukang i-assign ang unavailable member. Agad lalabas ang Conflict Alert dialog! Papalitan ng available instrumentalist at isa-save.",
        "<b>Step 3 (Song Lineup & Chord Sheet Sync):</b> Mag-a-attach si Admin ng 4 na kanta mula sa Song Library. I-transpose ang key (G to D). I-tap ang Publish. Agad mag-a-update nang real-time ang screen ni Member sa Device B nang walang manual refresh! Bubuksan ni Member ang chord sheet, autoscroll controls, at metronome.",
        "<b>Step 4 (Substitution Handshake):</b> Sa Device B, mag-fa-file si Member ng Substitution Request dahil sa emergency. Pipili ng substitute. Sa Device A / feed, lilitaw ang Pending card. I-click ang Accept. Real-time na mapapalitan ang pangalan sa Service Schedule."
    ]
    for s in steps:
        story.append(Paragraph(f"• {s}", bullet_style))
    story.append(Spacer(1, 14))

    # Section 3: Deep-Dive per Role
    story.append(Paragraph("PART 3: INDIVIDUAL ROLE GUIDES & TECHNICAL SCRIPT", h1_style))
    story.append(Paragraph("Ang bawat miyembro ay may nakalaang script at technical deep-dive sa kanilang responsibilidad upang handang sagutin ang anumang katanungan ng panelist.", body_style))
    story.append(Spacer(1, 6))

    roles = [
        {
            "num": "1",
            "name": "Genturo, Ryan Lloyd G.",
            "role": "Project Manager & Scrum Master",
            "speech": "\"Bilang Project Manager at Scrum Master, pinamahalaan ko ang buong lifecycle ng Project Nexus (Synced N) gamit ang Agile Scrum Framework. Sinigurado ko na ang 10 miyembro ay may malinaw na task assignment, transparent sprint progress, at aligned sa requirements ng New Life Christian Church. Ako rin ang personal na nag-oversee ng Unit Testing, Bug Tracking, at Build Deployment upang masigurong production-ready ang application.\"",
            "details": [
                "<b>Agile Sprint Orchestration:</b> Hinati ang proyekto sa 4 na 1-week sprints (Sprint 1: Design/ERD/UML; Sprint 2: Core Auth/Roster; Sprint 3: Advanced Lineup/Conflict/Substitutions; Sprint 4: Testing/UAT/Hardening).",
                "<b>Bug Log Oversight:</b> Sinubaybayan ang critical bugs gaya ng auto-scroll memory spikes sa mahahabang Chord Sheets (nilutas gamit ang StableScrollView at RosterRefreshGate), at concurrent snapshot race conditions.",
                "<b>Q&A Defense Readiness:</b> Kung tanungin kung paano naiwasan ang bottleneck sa 10 members: Ipaliwanag na may clear segregation of duties (Data, Frontend, Backend, QA, at Analysis)."
            ]
        },
        {
            "num": "2",
            "name": "Pendre, Justin Kyle D.",
            "role": "Database Administrator (DBA)",
            "speech": "\"Bilang Database Administrator, ako ang nag-design, nag-implement, at nag-secure ng aming NoSQL Cloud Firestore architecture. Sinigurado ko na normalized at optimized ang aming document-subcollection hierarchy para sa real-time sync at minimal bandwidth cost, habang pinoprotektahan ang data gamit ang mahigpit na Firestore Security Rules.\"",
            "details": [
                "<b>Firebase Connection Setup:</b> Paggawa ng Firebase project, pag-enable ng Email/Password Auth at Cloud Firestore sa Production Mode, pagkuha ng SHA-1 certificate para sa google-services.json, at pag-integrate ng Firebase BoM sa build.gradle.kts.",
                "<b>Firestore Schema:</b> Root collection '/users/{userId}' para sa user profiles, at '/teams/{teamCode}' na may 6-character alphanumeric code. Sa ilalim ng teams ay may subcollections: 'services', 'songs', 'announcements', 'substitutions', at 'messages'.",
                "<b>firestore.rules Security:</b> Mahigpit na Role-Based Access Control (RBAC). Leaders lang ang may write access sa services at songs; ang members ay read-only maliban sa availability, substitution, at chat. May multi-tenant isolation para hindi mabasa ng ibang church ang data."
            ]
        },
        {
            "num": "3",
            "name": "Lagare, Joshua A.",
            "role": "DevOps & Deployment Specialist / Product Owner",
            "speech": "\"Bilang DevOps Specialist, pinamahalaan ko ang version control, build workflows, at deployment packaging ng Synced N. Kasabay nito, bilang Product Owner na may personal na karanasan sa church music ministry, ako ang nag-translate ng tunay na pangangailangan ng worship team sa mga kongkretong technical requirements tulad ng stage readability at rapid communication.\"",
            "details": [
                "<b>Git Workflow & Environment:</b> Feature-branch workflow via GitHub. Isolated feature branches para sa bawat module bago i-merge sa main branch sa pamamagitan ng reviewed Pull Requests.",
                "<b>Build Configurations:</b> Min SDK 24 (sumasaklaw sa >95% ng Android devices) at Target SDK 35. Na-configure ang ProGuard/R8 code shrinking para mapaliit ang APK size sa ilalim ng 25MB.",
                "<b>Secrets Management:</b> Paggamit ng BuildConfig fields sa halip na i-hardcode ang external API keys (Brevo, Cloudinary) sa source control."
            ]
        },
        {
            "num": "4",
            "name": "Villar, John Lloyd F.",
            "role": "Lead Backend Developer",
            "speech": "\"Bilang Lead Backend Developer, ako ang nag-architect ng reactive backend engine sa TeamRepository.kt at FirestoreManager.kt. Nag-implement ako ng real-time snapshot listeners, multi-layered in-memory caching para sa seamless stage performance, at ang automatic scheduling conflict resolution algorithm.\"",
            "details": [
                "<b>TeamRepository Architecture:</b> Single source of truth na may in-memory ConcurrentHashMap caching (cachedRosters, cachedServices, cachedSongs) para sa zero-latency responsiveness kahit mabagal ang network sa sanctuary.",
                "<b>Conflict Detection Algorithm:</b> Bago i-save ang service assignment, bina-validate kung ang member ay may overlapping schedule o nag-marka ng unavailable sa 'unavailableRanges' array.",
                "<b>Substitution State Machine:</b> Automated multi-step status flow (Pending -> Accepted/Declined -> Approved) na awtomatikong nag-a-update sa assignments list ng ServiceData."
            ]
        },
        {
            "num": "5",
            "name": "Brevo & Cloudinary Specialist (Villar & Pendre Joint Focus)",
            "role": "Cloud API Services Implementation",
            "speech": "\"Para sa mga serbisyong hindi sakop ng standard Firestore, nag-integrate kami ng Brevo REST API para sa branded transactional Email OTPs, at Cloudinary para sa client-side optimized media uploads.\"",
            "details": [
                "<b>Brevo (Sendinblue) API (EmailOtpService.kt):</b> Cryptographically secure 6-digit OTP gamit ang java.security.SecureRandom. May 10-minute validity, 60-second cooldown, at 5-attempt brute-force protection para sa reliable account verification nang hindi pumapasok sa spam.",
                "<b>Cloudinary CDN (CloudinaryUploader.kt):</b> Zero-dependency unsigned multipart uploader. Bago i-upload ang picture, dina-downscale ito sa client-side sa 512px at kino-compress sa 85% JPEG upang makatipid sa mobile bandwidth ng mga users."
            ]
        },
        {
            "num": "6",
            "name": "Sarmiento, Mark Allen F.",
            "role": "Lead Frontend Developer",
            "speech": "\"Bilang Lead Frontend Developer, ako ang nag-translate ng layouts sa native Kotlin code gamit ang Material Design 3. Tiniyak ko na ang user interface ay intuitive, highly responsive, at madaling gamitin ng mga musikero kahit nasa stage sila habang nagpapatugtog o nagre-rehearse.\"",
            "details": [
                "<b>Modular Activity Layouts:</b> MainActivity (Auth/OTP), AdminActivity (Service wizard, lineup creation), at MemberActivity (duty calendar, availability setting).",
                "<b>Stage Tooling Components:</b> ChordSheetInput at ChordSheet parser na nagha-highlight ng chords sa purple accent, SheetAutoScrollControls para sa hands-free scrolling habang nag-gigitara, at MetronomeView/MetronomePcm para sa built-in audio click track."
            ]
        },
        {
            "num": "7",
            "name": "Gregorio, Ralph Lauren S.",
            "role": "UI/UX Lead Designer",
            "speech": "\"Bilang UI/UX Lead Designer, ako ang nagdisenyo ng complete design system sa Figma at nag-guide sa layout implementation. Ang aming priority ay Accessibility, Visual Hierarchy, at Minimal Cognitive Load para sa madidilim na santwaryo at stage environments.\"",
            "details": [
                "<b>Color Tokens & Contrast:</b> Dark Navy (#0D1B2A) para sa low-glare visual comfort, Accent Violet (#6C4AB6) para sa primary actions, at Semantic Colors (Green: Available, Amber: Pending, Red: Conflict/Urgent).",
                "<b>Ergonomic Design:</b> Bottom navigation bar na abot ng hinlalaki (thumb-zone navigation), at high-contrast bold fonts sa chord sheets para mabasa kahit nakapatong ang phone sa music stand sa layong 1 metro."
            ]
        },
        {
            "num": "8",
            "name": "Dizon, Jessa Mae G.",
            "role": "Technical Writer & UML Architect",
            "speech": "\"Bilang Technical Writer at UML Architect, ako ang nag-blueprint ng structural at behavioral diagrams ng Synced N alinsunod sa standard software engineering conventions, at nag-maintain ng academic cohesion ng buong capstone manuscript.\"",
            "details": [
                "<b>Use Case Diagram (3.4.1):</b> Actor separation sa pagitan ng Ministry Leader (administrative privileges) at Ministry Member (operational duties gaya ng availability at substitution).",
                "<b>Activity Diagrams (3.4.2):</b> 7 detailed behavioral workflows na nagpapakita ng deterministic logic (hal. Service Creation na may conflict branching at Substitution Handshake).",
                "<b>Class Diagram (3.4.3) & ERD (3.4.4):</b> OOP inheritance ng User entity at relational integrity sa kabila ng pagiging NoSQL ng Cloud Firestore."
            ]
        },
        {
            "num": "9",
            "name": "Ramirez, John Michael B.",
            "role": "System Analyst",
            "speech": "\"Bilang System Analyst, ako ang nag-bridge ng business gap sa pagitan ng manual practices ng New Life Christian Church at ng automated software requirements. Sinuri ko ang information bottlenecks at tiniyak na ang bawat isa sa 5 Statement of the Problem ay may kaukulang functional solution.\"",
            "details": [
                "<b>SOP 1 (Tracking roles):</b> Sinagot ng Centralized Service Assignment Wizard na may visual roster boards.",
                "<b>SOP 2 (No song/chord platform):</b> Sinagot ng In-App Song Library na may unified keys, lyrics, at instant chord transposition.",
                "<b>SOP 3 & 4 (Fragmented communication & last-minute changes):</b> Sinagot ng Dedicated in-app team chat, urgent alerts, at real-time push notifications.",
                "<b>SOP 5 (Availability & scheduling conflicts):</b> Sinagot ng Pre-service Availability Calendar at automated conflict detection."
            ]
        },
        {
            "num": "10",
            "name": "Lebuna, Angelo Daniel A. (QA) & Salva, Ma. Paula A. (UAT)",
            "role": "Quality Assurance & User Acceptance Testing Coordinators",
            "speech": "\"Bilang QA Engineer (Lebuna) at UAT Coordinator (Salva), sinigurado namin na ang Synced N ay dumaan sa mahigpit na functional verification at tunay na human evaluation alinsunod sa ISO/IEC 25010 software quality standards.\"",
            "details": [
                "<b>QA Testing Scenarios (Lebuna):</b> Unit at Integration testing sa login edge cases, offline snapshot recovery, concurrent chat handling, at conflict alert accuracy.",
                "<b>UAT Execution & Feedback (Salva):</b> Sinagutan ng mga music leaders, vocalists, at instrumentalists ng New Life Christian Church. Nakakuha ang system ng weighted mean na mahigit 4.5/5.0 (Excellent) sa Usability, Functional Suitability, at Performance Efficiency."
            ]
        }
    ]

    for r in roles:
        role_flow = []
        role_flow.append(Paragraph(f"<b>{r['num']}. {r['name']} — {r['role']}</b>", h2_style))
        role_flow.append(Paragraph("<b>Opening Speech (Ito ang sasabihin sa Panel):</b>", body_style))
        role_flow.append(Paragraph(r['speech'], speech_style))
        role_flow.append(Paragraph("<b>Technical Deep-Dive & Key Points:</b>", body_style))
        for d in r['details']:
            role_flow.append(Paragraph(f"• {d}", bullet_style))
        role_flow.append(Spacer(1, 8))
        story.append(KeepTogether(role_flow))

    # Golden Rules
    story.append(Spacer(1, 10))
    story.append(Paragraph("PART 4: 5 GOLDEN RULES PARA SA MATAGUMPAY NA DEFENSE", h1_style))
    golden_rules = [
        "<b>1. Maging magalang sa panel:</b> Laging simulan sa <i>'Salamat po sa magandang tanong, Panelist...'</i> bago sumagot.",
        "<b>2. Direct to the Point:</b> Sagutin muna ng <i>'Opo'</i> o <i>'Hindi po'</i> bago ipaliwanag ang technical background.",
        "<b>3. I-cite ang Documentation:</b> Kapag may kinuwestyong proseso o requirement, sabihin: <i>'Nasa Section 3.2 po ng aming Chapter 3...'</i> o <i>'Alinsunod po ito sa Scope and Limitations sa Chapter 1...'</i>",
        "<b>4. Bawal magsalita nang sabay-sabay:</b> Ang Project Manager o Lead Speaker ang mag-a-acknowledge ng tanong at magpapasa sa tamang role specialist.",
        "<b>5. Live Demo Confidence:</b> Kapag nagka-glitch sa network, kalmadong ipaliwanag ang offline caching mechanism ng Firestore na naka-handle sa local persistence."
    ]
    for gr in golden_rules:
        story.append(Paragraph(gr, bullet_style))

    doc.build(story, canvasmaker=NumberedCanvas)
    print("PDF build successful:", filename)

if __name__ == "__main__":
    out_dir = r"C:\Users\RYAN\AndroidStudioProjects\Synced1.0"
    out_file = os.path.join(out_dir, "Synced_N_Panel_Defense_and_Role_Guide.pdf")
    build_pdf(out_file)
