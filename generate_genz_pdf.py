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
            self.drawString(54, 752, "SYNCED N")
            self.setFont("Helvetica", 8)
            self.setFillColor(colors.HexColor("#555555"))
            self.drawString(108, 752, "|  Intelligent Gen Z Capstone Defense & 2-Phone Live Demo Guide")
            self.drawRightString(558, 752, "Taglish Presentation Script")
            self.setStrokeColor(colors.HexColor("#B0BEC5"))
            self.setLineWidth(0.5)
            self.line(54, 745, 558, 745)

        # Footer (all pages)
        self.setStrokeColor(colors.HexColor("#B0BEC5"))
        self.setLineWidth(0.5)
        self.line(54, 42, 558, 42)
        
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#666666"))
        self.drawString(54, 30, "Project Nexus: Synced N — ICCT Colleges Foundation Inc.")
        page_str = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(558, 30, page_str)
        self.restoreState()

def generate_genz_pdf():
    out_dir = r"C:\Users\RYAN\AndroidStudioProjects\Synced1.0"
    pdf_filename = os.path.join(out_dir, "Synced_N_GenZ_Panel_Demo_Guide.pdf")
    download_copy = r"C:\Users\RYAN\Downloads\Synced_N_GenZ_Panel_Demo_Guide.pdf"

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
        fontSize=16,
        leading=20,
        textColor=colors.HexColor('#0D1B2A'),
        alignment=1,
        spaceAfter=3
    )

    doc_sub = ParagraphStyle(
        'DocSub',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9,
        leading=13,
        textColor=colors.HexColor('#333333'),
        alignment=1,
        spaceAfter=10
    )

    h1 = ParagraphStyle(
        'Heading1',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=11,
        leading=15,
        textColor=colors.HexColor('#0D1B2A'),
        spaceBefore=11,
        spaceAfter=4,
        keepWithNext=True
    )

    h2 = ParagraphStyle(
        'Heading2',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9.5,
        leading=13.5,
        textColor=colors.HexColor('#6C4AB6'),
        spaceBefore=8,
        spaceAfter=3,
        keepWithNext=True
    )

    body = ParagraphStyle(
        'BodyText',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=12.5,
        textColor=colors.HexColor('#212121'),
        spaceAfter=4
    )

    speech = ParagraphStyle(
        'GenZSpeech',
        parent=styles['Normal'],
        fontName='Helvetica-Oblique',
        fontSize=8.5,
        leading=13,
        textColor=colors.HexColor('#1A237E'),
        leftIndent=12,
        rightIndent=12,
        spaceBefore=3,
        spaceAfter=4
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
        spaceAfter=2
    )

    tech_note = ParagraphStyle(
        'TechNote',
        parent=styles['Normal'],
        fontName='Courier',
        fontSize=7.5,
        leading=10.5,
        textColor=colors.HexColor('#37474F'),
        leftIndent=10,
        spaceBefore=2,
        spaceAfter=4
    )

    story = []

    # Title Block
    story.append(Paragraph("SYNCED N: 2-PHONE LIVE DEMO & PANEL DEFENSE SCRIPT", doc_title))
    story.append(Paragraph("<b>The Intelligent Gen Z IT Student Guide</b> — Natural, Confident Taglish Walkthrough<br/>Setup: Dual-Screen Live Interaction (Phone A: Leader vs. Phone B: Member)", doc_sub))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor("#0D1B2A"), spaceAfter=8))

    # Gen Z Pitch Strategy Intro
    story.append(Paragraph("GAME PLAN & MINDSET SA HARAP NG PANELIST:", h1))
    intro_txt = (
        "Hindi natin ibebenta ang project na parang binabasa lang mula sa libro. Magiging <b>confident, sharp, at articulate na Gen Z IT students</b> tayo. "
        "I-highlight natin yung <i>real-world impact</i> sa New Life Christian Church (Pasig) habang sabay na ipinapakita ang <i>under-the-hood technical flex</i> "
        "tulad ng real-time snapshot sync, atomic state transitions, at zero-latency caching. "
        "Ang buong presentation ay tatakbo bilang isang <b>'Sunday Service Simulation'</b> kung saan sabay na nag-i-interact ang dalawang phone nang live."
    )
    story.append(Paragraph(intro_txt, body))
    story.append(Spacer(1, 4))

    # Setup Table
    st_data = [
        [Paragraph("<b>Hardware</b>", body), Paragraph("<b>User Persona</b>", body), Paragraph("<b>Presenter & Role</b>", body), Paragraph("<b>Display Location</b>", body)],
        [Paragraph("<b>Phone A</b>", body), Paragraph("Worship Leader / Admin<br/>(Planner, Organizer)", body), Paragraph("Genturo (PM) / Sarmiento (Frontend)", body), Paragraph("Left Screen (Projector/TV)", body)],
        [Paragraph("<b>Phone B</b>", body), Paragraph("Student Musician / Member<br/>(Volunteer, Keyboardist)", body), Paragraph("Salva (UAT) / Villar (Backend)", body), Paragraph("Right Screen (Projector/TV)", body)]
    ]
    st = Table(st_data, colWidths=[1.0*inch, 2.5*inch, 2.3*inch, 1.4*inch])
    st.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#EEEEEE')),
        ('BOX', (0,0), (-1,-1), 0.5, colors.HexColor('#B0BEC5')),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.HexColor('#CFD8DC')),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('BOTTOMPADDING', (0,0), (-1,-1), 3),
        ('TOPPADDING', (0,0), (-1,-1), 3),
    ]))
    story.append(st)
    story.append(Spacer(1, 6))

    # THE 6 ACTS
    acts = [
        {
            "act": "ACT 1: SPLASH, EMAIL OTP SECURITY & MULTI-TENANT ONBOARDING",
            "desc": "Goal: Ipakita ang fresh launch, secure verification gamit ang Brevo REST API, at dynamic 6-char team code creation.",
            "steps": [
                {
                    "title": "Step 1.1: Splash Screen & First Impression (Phone A - Leader)",
                    "actions": [
                        "I-open ang app sa Phone A.",
                        "Ipakita ang clean minimalist Splash Screen na may Synced N branding.",
                        "Lalabas ang 'startView' (Get Started screen). I-tap ang 'Get Started' para makapunta sa Auth tab."
                    ],
                    "speech": "\"Good day po to our respected panel! To start off our live demonstration, makikita niyo po sa left screen (Phone A) yung smooth launch ng Synced N from our native Splash Screen. Modern and distraction-free po yung branding para bagay sa sanctuary environment. Once we tap 'Get Started', diretso na tayo sa ating Authentication gateway.\"",
                    "note": "Under the Hood: Check sa installation marker sa noBackupFilesDir para i-guarantee ang fresh onboarding state."
                },
                {
                    "title": "Step 1.2: Sign Up & Brevo REST API Email OTP (Phone A - Leader)",
                    "actions": [
                        "Piliin ang 'Sign Up' tab sa Phone A.",
                        "Mag-enter ng Username, Email, at Password.",
                        "I-tap ang Register button. Lalabas ang 6-digit OTP verification modal.",
                        "I-input ang 6-digit code na dumating sa email at i-verify."
                    ],
                    "speech": "\"Instead of using basic, generic sign-ups na prone sa ghost accounts o spam, we leveled up our auth security. Gumamit po kami ng Brevo REST API v3 para mag-fire ng real-time 6-digit Email OTP. Gamit po ito ng cryptographically secure na SecureRandom generator, may built-in 10-minute expiry, at 5-attempt brute-force protection. Super reliable po siya at diretsong pumapasok sa inbox, hindi sa spam folder.\"",
                    "note": "Under the Hood: EmailOtpService.kt ang nag-o-orchestrate via direct HTTPS POST sa api.brevo.com/v3/smtp/email."
                },
                {
                    "title": "Step 1.3: Team Creation & Generating the 6-Char Code (Phone A - Leader)",
                    "actions": [
                        "Pagka-verify ng OTP, mapupunta sa 'teamChoice' screen.",
                        "Piliin ang 'Create Team'. I-type ang name: 'NLCC Worship Ministry'.",
                        "I-tap ang Create. Instant na lilitaw ang custom modal na may 6-Character Code (e.g. 'NLCC01') na may copy button."
                    ],
                    "speech": "\"Since si Phone A po ang tatayong Worship Leader, pipiliin niya yung 'Create Team'. Pagka-type niya ng ministry name, our cloud engine automatically generates a unique 6-character uppercase code sa Cloud Firestore. Ito po yung magsisilbing digital key para ma-isolate yung church records nila at walang makikialam na taga-ibang ministry.\"",
                    "note": "Under the Hood: FirestoreManager.createTeamWithCode() creates document at /teams/{teamCode} with leaderUid binding."
                },
                {
                    "title": "Step 1.4: Seamless Member Joining via Team Code (Phone B - Member)",
                    "actions": [
                        "Lumipat sa Right Screen (Phone B).",
                        "Mag-login o sign up bilang Member (si 'Paula', student volunteer).",
                        "Sa 'teamChoice' screen, pindutin ang 'Join Team'.",
                        "I-input ang 6-character code mula kay Leader ('NLCC01') at i-tap ang 'Join'."
                    ],
                    "speech": "\"Now, let's switch our focus to Phone B on the right side. Si Sister Paula naman po ito, one of our student volunteers. She simply selects 'Join Team', inputs the 6-character code na binigay ng leader niya, and hits Join. Instantly, our firestore.rules validates the code, associates her profile, and adds her to the ministry roster. Zero administrative headache po, napaka-straightforward!\"",
                    "note": "Under the Hood: Regex validation ^[A-Z0-9]{6}$ sa firestore.rules bago i-link ang user sa team subcollection."
                }
            ]
        },
        {
            "act": "ACT 2: ROSTER DISCOVERY & INSTANT ROLE ALLOCATION",
            "desc": "Goal: Ipakita ang centralized membership list at pag-assign ng specific instrument/vocal duties.",
            "steps": [
                {
                    "title": "Step 2.1: Real-time Member Discovery & Role Assignment (Phone A ➔ Phone B)",
                    "actions": [
                        "Sa Phone A (Leader), i-open ang 'Teams / Roster' tab.",
                        "Makikita na agad na nag-appear si 'Paula' sa roster list nang hindi nag-ma-manual refresh.",
                        "I-tap ang profile ni Paula, i-select ang 'Edit Role', at i-set as 'Keyboardist'. I-save.",
                        "Ipakita sa Phone B (Member): Real-time na nag-update ang role badge niya sa 'Keyboardist'."
                    ],
                    "speech": "\"Notice po sa left screen, dahil naka-hook up tayo sa Cloud Firestore real-time snapshot listeners via TeamRepository.kt, nag-reflect agad si Paula sa roster ni Leader without pulling to refresh! In-assign po siya ni Leader as 'Keyboardist'. And if you look at Phone B right now, instant din pong nag-update yung role sa dashboard niya. This directly addresses SOP #1—wala nang confusion kung sino ang tutugtog sa stage.\"",
                    "note": "Under the Hood: TeamRepository.subscribeToRoster() manages concurrent state at nag-a-update agad ng memory cache."
                }
            ]
        },
        {
            "act": "ACT 3: AVAILABILITY CALENDAR & CONFLICT DETECTION ENGINE",
            "desc": "Goal: Solusyonan ang SOP #1 at SOP #5 — Pagpigil sa scheduling conflicts at pag-respeto sa schedule ng student volunteers.",
            "steps": [
                {
                    "title": "Step 3.1: Member Setting Unavailability (Phone B - Member)",
                    "actions": [
                        "Sa Phone B (Member), buksan ang 'Calendar / Availability' tab.",
                        "I-tap ang paparating na Sunday (e.g. November 2).",
                        "I-toggle as 'Unavailable' at mag-input ng reason: 'Midterm Exam Week'.",
                        "I-hit ang Submit button."
                    ],
                    "speech": "\"In reality po, student volunteers often struggle balancing church duties and academic loads. Sa lumang setup, malalaman na lang ni Leader na absent si Paula kung kailan rehearsal na. But with Synced N, Paula opens her Availability Calendar, marks upcoming Sunday as Unavailable, and enters her reason: 'Midterm Exam Week'. That data is instantly written sa cloud.\"",
                    "note": "Under the Hood: Updates the unavailableRanges array sa /users/{uid} document with ISO date-string bounds."
                },
                {
                    "title": "Step 3.2: Service Wizard & Automatic Conflict Detection Alert (Phone A - Leader)",
                    "actions": [
                        "Sa Phone A (Leader), pumunta sa 'Services' tab at i-tap ang 'Create Service'.",
                        "I-input: Title: 'Sunday Worship Service', Date: (same Sunday), Time: '8:00 AM - 10:00 AM', Venue: 'Main Sanctuary'.",
                        "Sa Role Assignments, subukang i-assign si 'Paula' as Keyboardist.",
                        "BOOM! Lilitaw ang red/amber Warning Dialog: 'Scheduling Conflict Detected: Paula is marked UNAVAILABLE on this date (Reason: Midterm Exam Week)'.",
                        "Palitan ang assignment sa available member at i-save ang draft service."
                    ],
                    "speech": "\"Eto po yung isa sa pinaka-proud naming feature—ang aming Automatic Conflict Detection Algorithm. Nung sinubukan po ni Leader na i-assign si Paula sa Sunday, our backend immediately cross-referenced Paula's availability records. Hindi hinayaan ng system na magka-double booking o bulag na line-up. It prompted a clear conflict alert with Paula's exact reason! We just prevented a massive Sunday morning catastrophe right on screen!\"",
                    "note": "Under the Hood: detectConflict() evaluates unavailableRanges and overlapping service hours before committing."
                }
            ]
        },
        {
            "act": "ACT 4: SONG LIBRARY, CHORD TRANSPOSITION & STAGE TOOLS",
            "desc": "Goal: Solusyonan ang SOP #2 — Centralized chords, lyrics, stage transposition, autoscroll, at metronome.",
            "steps": [
                {
                    "title": "Step 4.1: Song Lineup Attachment & Key Transposition (Phone A - Leader)",
                    "actions": [
                        "Sa Phone A (Leader), buksan ang ginawang Service at pumunta sa 'Song Lineup'.",
                        "Pumili ng kanta mula sa Song Library (halimbawa: 'Way Maker' o 'Praise').",
                        "Ipakita na ang default key ng kanta ay 'Key of G', pero pinalitan ito ni Leader into 'Key of D' para mag-fit sa vocal range ng lead singer.",
                        "I-tap ang 'Publish Service'."
                    ],
                    "speech": "\"Before Synced N, the band members had to browse multiple websites like Ultimate Guitar or chord blogs, which resulted in mismatched chords and confusion sa stage. Dito po, pumili si Leader ng kanta from our unified Song Library and overrode the key from G to D. Once he taps 'Publish Service', it officially syncs to every assigned band member.\"",
                    "note": "Under the Hood: Custom key override is encapsulated within the lineup array in /teams/{code}/services/{id}."
                },
                {
                    "title": "Step 4.2: Real-time Lineup Reflection & Live Stage Performance Tools (Phone B - Member)",
                    "actions": [
                        "Sa Phone B (Member), ipakita na lumitaw agad ang newly published service sa kanyang Home duty feed.",
                        "I-tap ang Service at buksan ang assigned song para lumabas ang interactive Chord Sheet viewer.",
                        "I-showcase ang 3 Stage Tools:",
                        "  1. Transpose Buttons (+1 / -1): Pindutin para makitang dynamic na nagta-transpose ang chords (e.g. C -> C# -> D).",
                        "  2. Built-in Low-Latency Metronome: I-toggle on ang click track para marinig ang tamang BPM tempo.",
                        "  3. Hands-free Auto-Scroll: I-play ang auto-scroll at i-adjust ang speed para kusang umaakyat ang lyrics/chords."
                    ],
                    "speech": "\"Now look at Phone B! Without any manual pull-to-refresh, the service showed up instantly. And when Paula opens the chord sheet, it's already set to Key of D!\n\nPlus, we engineered three crucial stage tools:\nFirst, dynamic Chord Transposition—no more mental math while playing!\nSecond, a built-in PCM Audio Metronome for perfect tempo locking.\nAnd third, hands-free Auto-Scroll with adjustable speed so musicians don't have to swipe their screens while their hands are busy on their instruments.\"",
                    "note": "Under the Hood: Powered by ChordSheet.java parser, StableScrollView.kt, at low-latency MetronomePcm.java."
                }
            ]
        },
        {
            "act": "ACT 5: PRIORITY COMMUNICATIONS & THE SUBSTITUTION HANDSHAKE",
            "desc": "Goal: Solusyonan ang SOP #3 at #4 — In-app alerts, encrypted chat with image uploads, at automated substitution state machine.",
            "steps": [
                {
                    "title": "Step 5.1: Urgent Alerts & In-App Team Chat with Cloudinary (Phone A ➔ Phone B)",
                    "actions": [
                        "Sa Phone A (Leader), buksan ang 'Announcements' tab.",
                        "Mag-post ng announcement: Title: 'Call Time Reminder', Message: 'Rehearsal strictly at 4 PM!', at i-check ang 'Urgent' toggle.",
                        "Sa Phone B (Member), ipakita na may highlighted card na may red 'Urgent Alert' badge.",
                        "Pumunta sa 'Team Chat' tab sa dalawang phone. Magpalitan ng chat at mag-attach ng picture gamit ang Cloudinary CDN uploader."
                    ],
                    "speech": "\"Para hindi matabunan yung mga mahahalagang paalala sa mga casual chitchat sa Messenger, we created an Urgent Announcements channel with high-visibility badges. We also implemented an in-app Team Chat equipped with our Cloudinary CDN pipeline. Bago po i-upload ang photo, the app automatically downscales it to 512px client-side to save mobile data and prevent lag.\"",
                    "note": "Under the Hood: CloudinaryUploader.kt handles client-side compression (85% JPEG) before unsigned multipart upload."
                },
                {
                    "title": "Step 5.2: The Substitution Handshake (The Climax of the Demo)",
                    "actions": [
                        "Sa Phone B (Member): Kunwari nagkaroon ng emergency si Member. Pindutin ang 'Request Substitution'.",
                        "Pumili ng teammate mula sa roster, maglagay ng reason: 'Family Emergency', at i-submit.",
                        "Sa Phone A (Leader): Sa announcements/feed, instant na lilitaw ang Substitution Request Card with 'Pending' status.",
                        "Sa Phone A, i-tap ang 'Accept' o 'Approve'.",
                        "Ipakita sa screen: Kusa at awtomatikong napalitan ang assignee sa Service Schedule document nang walang manual editing!"
                    ],
                    "speech": "\"Now, here is the climax of our demo—The Substitution Handshake. Kapag may emergency po ang isang miyembro, she taps 'Request Substitution', picks a teammate, and enters her reason. Right away, on Leader's screen, a Pending Card pops up in real-time.\n\nWhen Leader taps 'Accept', our backend executes an atomic update in Cloud Firestore: it replaces the service assignment, notifies the team, and preserves a complete audit trail. Walang tampuhan, walang nawawalang instrumentalist, perfectly solved!\"",
                    "note": "Under the Hood: acceptSubstitution() in TeamRepository.kt executes an atomic transaction on the ServiceData document."
                }
            ]
        },
        {
            "act": "ACT 6: PROFILE CUSTOMIZATION & SECURE LOGOUT",
            "desc": "Goal: Ipakita ang profile avatar updating via CDN at session nullification (NFR-12).",
            "steps": [
                {
                    "title": "Step 6.1: Avatar Upload & Session Termination (Phone B - Member)",
                    "actions": [
                        "Sa Phone B, pumunta sa 'Profile' tab.",
                        "I-tap ang avatar icon para mag-upload ng bagong picture gamit ang Cloudinary.",
                        "I-tap ang 'Logout' button. Babalik ang application sa Start screen."
                    ],
                    "speech": "\"To wrap up our live demo, members can personalize their profiles by uploading avatars directly to Cloudinary CDN. And following our Non-Functional Security Requirement NFR-12, once a user taps 'Logout', our SessionManager completely invalidates active tokens to prevent any session hijacking vulnerabilities. That concludes our 2-phone live demonstration!\"",
                    "note": "Under the Hood: AuthManager.signOut() clears SharedPreferences auth cache and resets root navigation."
                }
            ]
        }
    ]

    for act_data in acts:
        act_flow = []
        act_flow.append(Paragraph(f"<b>{act_data['act']}</b>", h1))
        act_flow.append(Paragraph(f"<i>{act_data['desc']}</i>", body))
        act_flow.append(Spacer(1, 3))
        
        for step in act_data['steps']:
            step_flow = []
            step_flow.append(Paragraph(f"<b>{step['title']}</b>", h2))
            
            step_flow.append(Paragraph("<b>Physical Screen Actions (Ano ang iki-click):</b>", body))
            for a in step['actions']:
                step_flow.append(Paragraph(f"• {a}", bullet))
                
            step_flow.append(Paragraph("<b>Intelligent Gen Z Verbal Script (Sasabihin sa Panel):</b>", body))
            step_flow.append(Paragraph(step['speech'], speech))
            
            step_flow.append(Paragraph(f"<b>Code Underneath:</b> {step['note']}", tech_note))
            step_flow.append(Spacer(1, 3))
            act_flow.append(KeepTogether(step_flow))
            
        story.append(KeepTogether(act_flow))
        story.append(Spacer(1, 5))

    # GEN Z DEFENSE CHEAT SHEET / CLAP-BACKS
    story.append(Spacer(1, 6))
    story.append(Paragraph("GEN Z DEFENSE CHEAT SHEET: HOW TO CONFIDENTLY ANSWER TOUGH PANEL QUESTIONS", h1))
    
    genz_qa = [
        ("Panelist asks: 'Bakit may 2-second delay minsan bago mag-update yung kabilang screen?'",
         "\"Very sharp observation po, Panelist! That minor latency is due to the live round-trip cloud socket handshake with our Firebase Singapore cluster. But notice po: because we engineered an In-Memory ConcurrentHashMap caching layer in TeamRepository.kt, the local UI never freezes or stutters. The user experience remains completely responsive at 60fps kahit nag-a-await tayo ng network response.\""),

        ("Panelist asks: 'Bakit hindi na lang Google Forms or Messenger bots ang ginamit ninyo?'",
         "\"With all due respect po, Google Forms and Messenger bots are passive and fragmented. They can't do live chord transposition, they don't have a built-in PCM metronome, they won't automatically block scheduling conflicts, and they can't execute an atomic substitution handshake on stage. Synced N is an end-to-end, purpose-built ecosystem designed specifically for church musicians.\""),

        ("Panelist asks: 'Paano kapag nawalan ng internet connection sa loob ng simbahan?'",
         "\"We anticipated that real-world stage scenario po! Cloud Firestore has native offline persistence enabled by default. Every single chord sheet, lineup, and schedule na na-load prior to the service is cached in the phone's local SQLite storage. Even if the church Wi-Fi totally dies mid-worship, our musicians can continue reading their chords without interruption.\"")
    ]

    for q, a in genz_qa:
        q_flow = []
        q_flow.append(Paragraph(f"<b>{q}</b>", h2))
        q_flow.append(Paragraph(f"<b>Gen Z High-Impact Answer:</b> {a}", speech))
        q_flow.append(Spacer(1, 3))
        story.append(KeepTogether(q_flow))

    doc.build(story, canvasmaker=NumberedCanvas)
    print("Gen Z PDF build successful:", pdf_filename)

    # Copy to Downloads
    import shutil
    shutil.copyfile(pdf_filename, download_copy)
    print("Copied to Downloads:", download_copy)

if __name__ == "__main__":
    generate_genz_pdf()
