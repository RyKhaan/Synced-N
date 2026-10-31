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
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#444444"))
        
        # Header (pages > 1)
        if self._pageNumber > 1:
            self.drawString(54, 752, "SYNCED N — 2-PHONE LIVE APP DEMONSTRATION & SCRIPT GUIDE")
            self.drawRightString(558, 752, "CapStone Defense Guide")
            self.setStrokeColor(colors.HexColor("#B0BEC5"))
            self.setLineWidth(0.5)
            self.line(54, 745, 558, 745)

        # Footer (all pages)
        self.setStrokeColor(colors.HexColor("#B0BEC5"))
        self.setLineWidth(0.5)
        self.line(54, 42, 558, 42)
        
        self.drawString(54, 30, "Project Nexus: Synced N — New Life Christian Church Music Ministry")
        page_str = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(558, 30, page_str)
        self.restoreState()

def create_demo_pdf():
    pdf_filename = r"C:\Users\RYAN\AndroidStudioProjects\Synced1.0\Synced_N_Live_Demo_Guide_2Phones.pdf"
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
        fontSize=17,
        leading=21,
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
        fontSize=11.5,
        leading=15,
        textColor=colors.HexColor('#0D1B2A'),
        spaceBefore=12,
        spaceAfter=5,
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
        'SpeechText',
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

    note_box = ParagraphStyle(
        'NoteBox',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8,
        leading=11.5,
        textColor=colors.HexColor('#37474F'),
        leftIndent=10,
        spaceBefore=2,
        spaceAfter=4
    )

    story = []

    # Title Block
    story.append(Paragraph("SYNCED N: 2-PHONE LIVE DEMONSTRATION GUIDE", doc_title))
    story.append(Paragraph("<b>Step-by-Step App Flow & Taglish Explanations para sa Panel Defense</b><br/>Setup: Phone A (Worship Leader / Admin) & Phone B (Music Ministry Member / Volunteer)", doc_sub))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor("#0D1B2A"), spaceAfter=8))

    # Introduction / Setup
    story.append(Paragraph("PAANO ANG DISKARTE SA LIVE DEMO:", h1))
    intro_p = (
        "Sa halip na ipakita nang magkahiwalay ang features ng Leader at Member, gagawin natin itong <b>'Interactive Sunday Service Simulation'</b>. "
        "Ibig sabihin, makikita ng panel kung paano nagbabatuhan ng data ang <b>Phone A (Leader)</b> at <b>Phone B (Member)</b> gamit ang real-time cloud sync. "
        "I-project ang dalawang phone magkatabi sa screen (gamit ang <i>scrcpy</i> o emulator) para habang may pinipindot sa kaliwa, nakikita agad ang epekto sa kanan."
    )
    story.append(Paragraph(intro_p, body))
    story.append(Spacer(1, 4))

    # Setup Table
    setup_data = [
        [Paragraph("<b>Device</b>", body), Paragraph("<b>Gaganap / Role</b>", body), Paragraph("<b>Hawak Ni</b>", body), Paragraph("<b>Screen Position</b>", body)],
        [Paragraph("<b>Phone A</b>", body), Paragraph("Worship Leader / Admin (Nag-i-iskedyul, namumuno)", body), Paragraph("Genturo (PM) o Sarmiento (Frontend)", body), Paragraph("Kaliwang Screen (Left Display)", body)],
        [Paragraph("<b>Phone B</b>", body), Paragraph("Ministry Member (Keyboardist / Student Volunteer)", body), Paragraph("Salva (UAT) o Villar (Backend)", body), Paragraph("Kanang Screen (Right Display)", body)]
    ]
    st = Table(setup_data, colWidths=[1.1*inch, 2.6*inch, 2.1*inch, 1.4*inch])
    st.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#EEEEEE')),
        ('BOX', (0,0), (-1,-1), 0.5, colors.HexColor('#B0BEC5')),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.HexColor('#CFD8DC')),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('BOTTOMPADDING', (0,0), (-1,-1), 3),
        ('TOPPADDING', (0,0), (-1,-1), 3),
    ]))
    story.append(st)
    story.append(Spacer(1, 8))

    # ACTS BREAKDOWN
    acts = [
        {
            "act": "ACT 1: SPLASH SCREEN, AUTHENTICATION AT TEAM ONBOARDING",
            "desc": "Layunin: Ipakita ang fresh entry, 6-digit OTP email verification via Brevo, at multi-tenant team code join.",
            "steps": [
                {
                    "title": "Step 1.1: Splash Screen at Get Started (Phone A - Leader)",
                    "actions": [
                        "I-launch ang Synced N sa Phone A mula sa background.",
                        "Ipakita ang Splash Screen na may Synced N logo.",
                        "Lalabas ang 'startView' (Get Started screen). I-tap ang button na 'Get Started' patungo sa Auth screen."
                    ],
                    "speech": "\"Good day po mga panelist. Sa simula ng aming demo, makikita sa Kaliwang Screen (Phone A) ang malinis na Splash Screen ng Synced N. Pag-tap ng Get Started, mapupunta tayo sa aming Authentication module kung saan may options para sa Login at Sign Up.\"",
                    "note": "Technical Note: Ang MainActivity ay nagche-check ng installation marker at active session token para automatic ang route."
                },
                {
                    "title": "Step 1.2: Sign Up at 6-Digit Email OTP via Brevo (Phone A - Leader)",
                    "actions": [
                        "Piliin ang 'Sign Up' tab sa Phone A.",
                        "Mag-type ng Username, Email, at Password.",
                        "I-tap ang Register button. Lalabas ang 6-Digit OTP Verification Dialog.",
                        "I-input ang natanggap na OTP code at i-confirm."
                    ],
                    "speech": "\"Hindi po generic na sign up ito. Upang masigurong lehitimo ang account ng church volunteer, nag-integrate po kami ng Brevo REST API v3. Nagpapadala ang app ng cryptographically secure na 6-digit OTP code sa email. May 10-minute validity at brute-force attempt limits ito para sa proteksyon ng account.\"",
                    "note": "Technical Note: Pinatatakbo ito ng EmailOtpService.kt gamit ang SecureRandom at direct SMTP API."
                },
                {
                    "title": "Step 1.3: Paggawa ng Ministry Team (Phone A - Leader)",
                    "actions": [
                        "Pagkatapos ng OTP, mapupunta sa 'teamChoice' screen ('Create Team' o 'Join Team').",
                        "Pindutin ang 'Create Team'. I-type ang name: 'NLCC Worship Ministry'.",
                        "I-tap ang Create. Lalabas ang dialog na may 6-Character Code (halimbawa: 'NLCC01')."
                    ],
                    "speech": "\"Dahil si Phone A ang tatayong Worship Leader, pipiliin niya ang 'Create Team'. Awtomatikong bumubuo ang aming system ng unique 6-character alphanumeric code sa Cloud Firestore. Ang code na ito ang magsisilbing team invite key para sa buong music ministry.\"",
                    "note": "Technical Note: Naka-store ang team sa root collection na /teams/{teamCode} na may leaderUid binding."
                },
                {
                    "title": "Step 1.4: Pagsali ng Miyembro gamit ang Team Code (Phone B - Member)",
                    "actions": [
                        "Sa Kanang Screen (Phone B), buksan din ang app.",
                        "Mag-sign in o mag-register bilang member (halimbawa si 'Paula').",
                        "Sa 'teamChoice' screen, pindutin ang 'Join Team'.",
                        "I-type ang 6-character code na binigay ni Leader ('NLCC01') at i-tap ang 'Join'."
                    ],
                    "speech": "\"Ngayon naman po, tingnan natin ang Kanang Screen (Phone B) kung saan gaganap si Sister Paula bilang student volunteer. Pinili niya ang 'Join Team' at inilagay ang 6-character code mula kay Leader. Sa isang click, na-validate ito ng firestore.rules at kusa na siyang napasama sa roster ng ministry nang walang manual admin encoding.\"",
                    "note": "Technical Note: Sina-sanitize ang input gamit ang regex ^[A-Z0-9]{6}$ sa firestore.rules line 353."
                }
            ]
        },
        {
            "act": "ACT 2: ROSTER MANAGEMENT AT ROLE ASSIGNMENT",
            "desc": "Layunin: Ipakita kung paano pinapamahalaan ng Leader ang mga musikero at mang-aawit.",
            "steps": [
                {
                    "title": "Step 2.1: Real-time Member Discovery at Pag-assign ng Role (Phone A ➔ Phone B)",
                    "actions": [
                        "Sa Phone A (Leader), buksan ang 'Teams / Roster' tab.",
                        "Makikita na agad na nakalista si 'Paula' sa roster list.",
                        "I-click ang profile ni Paula, i-edit ang Ministry Role, at piliin ang 'Keyboardist'. I-save.",
                        "Sa Phone B (Member), ipakita na sa kanyang Profile/Dashboard, automatic nang nag-update ang role niya bilang 'Keyboardist'."
                    ],
                    "speech": "\"Dahil sa aming Firestore real-time snapshot listeners sa TeamRepository.kt, nakita agad ni Leader si Paula sa listahan nang hindi nagre-refresh. In-assign ni Leader ang role ni Paula bilang 'Keyboardist'. Mapapansin sa Phone B na instant ding nagbago ang role sa screen ni member.\"",
                    "note": "Technical Note: Walang manual polling. Ang addSnapshotListener ang nagti-trigger ng notifyDataSetChanged."
                }
            ]
        },
        {
            "act": "ACT 3: AVAILABILITY CALENDAR AT AUTOMATIC CONFLICT DETECTION",
            "desc": "Layunin: Patunayan ang solusyon sa SOP #1 at #5 — Pagpigil sa scheduling conflicts bago pa man mag-rehearsal.",
            "steps": [
                {
                    "title": "Step 3.1: Pag-set ng Unavailability ng Miyembro (Phone B - Member)",
                    "actions": [
                        "Sa Phone B (Member), buksan ang 'Calendar / Availability' tab.",
                        "Piliin ang petsa ng paparating na Sunday Service (e.g. November 2).",
                        "I-mark ang sarili bilang 'Unavailable' at maglagay ng reason: 'Midterm Exam Week'.",
                        "I-tap ang Submit."
                    ],
                    "speech": "\"Sa tradisyonal na church, madalas last minute bago malaman na absent pala ang piyanista dahil may exams. Sa Synced N, binuksan ni Paula ang Availability Calendar sa Phone B at minarkahan ang Sunday bilang Unavailable kalakip ang dahilan. Na-save po agad ito sa cloud sa ilalim ng kanyang user profile.\"",
                    "note": "Technical Note: Naka-store ito sa unavailableRanges array field sa /users/{uid} document."
                },
                {
                    "title": "Step 3.2: Service Wizard at ang Conflict Detection Alert (Phone A - Leader)",
                    "actions": [
                        "Sa Phone A (Leader), pumunta sa 'Services' tab at pindutin ang 'Create Service'.",
                        "I-fill up ang Title: 'Sunday Worship Service', Date: (kaparehong Sunday), Time: '8:00 AM - 10:00 AM', Venue: 'Main Sanctuary'.",
                        "Sa Role Assignments, subukang piliin at i-assign si 'Paula' bilang Keyboardist.",
                        "BOOM! Lilitaw ang Warning Dialog: 'Scheduling Conflict Detected: Paula is marked UNAVAILABLE on this date (Reason: Midterm Exam Week)'.",
                        "Palitan ang assignment sa available na member o i-leave as Vacant, at i-save ang draft service."
                    ],
                    "speech": "\"Ito po ang aming Automatic Conflict Detection Algorithm. Noong sinubukan ni Leader na i-assign si Paula sa Sunday, binalaan agad siya ng system na may conflict at ipinakita pa ang dahilan ni Paula. Hindi po hahayaan ng app na magkaroon ng double-booking o bulag na line-up. Naiwasan po agad ang abala sa mismong araw ng tugtugan!\"",
                    "note": "Technical Note: Ang detectConflict() logic sa TeamRepository.kt ang nagko-cross reference sa unavailableRanges."
                }
            ]
        },
        {
            "act": "ACT 4: SONG LIBRARY, CHORD SHEET VIEWER AT STAGE PERFORMANCE TOOLS",
            "desc": "Layunin: Sagutin ang SOP #2 — Centralized chords, lyrics, stage transposition, autoscroll, at metronome.",
            "steps": [
                {
                    "title": "Step 4.1: Pag-attach ng Lineup at Key Override (Phone A - Leader)",
                    "actions": [
                        "Sa Phone A (Leader), buksan ang ginawang Service at pumunta sa 'Song Lineup'.",
                        "Pumili ng kanta mula sa Song Library (halimbawa: 'Way Maker' o 'Praise').",
                        "Ipakita na ang default key ay 'Key of G', ngunit pinalitan ito ni Leader ng 'Key of D' para bumagay sa boses ng lead singer.",
                        "I-tap ang 'Publish Service'."
                    ],
                    "speech": "\"Sa lumang practice sa Messenger, nagkakagulo sa GC kung anong key kakantahin o iba-ibang website ang tinitingnan ng banda. Dito po, pumili si Leader ng kanta mula sa Song Library at in-override ang performance key mula G papuntang D. Pag-tap po ng Publish Service, opisyal na itong bukas sa buong team.\"",
                    "note": "Technical Note: Naka-save ang custom key sa subcollection /teams/{code}/services/{id} lineup map."
                },
                {
                    "title": "Step 4.2: Real-time Lineup Access at Stage Tools (Phone B - Member)",
                    "actions": [
                        "Sa Phone B (Member), ipakita na lumabas agad ang bagong published service sa kanyang Home/Duty list nang walang reload.",
                        "Buksan ang Service at i-click ang kanta para lumabas ang Chord Sheet viewer.",
                        "I-demonstrate ang 3 Stage Tools:",
                        "  1. Transpose Buttons (+1 / -1): Pindutin para makitang dynamic na nagbabago ang chords sa screen.",
                        "  2. Built-in Metronome: I-on ang Metronome ticker para marinig ang tamang tempo (BPM).",
                        "  3. Hands-free Auto-Scroll: I-play ang auto-scroll at i-adjust ang speed para kusang umaakyat ang chords habang nagpapatugtog."
                    ],
                    "speech": "\"Tingnan po natin ang Phone B ni Member. Automatic na nag-sync ang service at pagbukas ng kanta, naka-Key of D na agad ito! Bukod dito, nilagyan po namin ito ng tatlong live stage tools:\nUna, may Chord Transposition kung saan dynamic na nagpapalit ang nota.\nPangalawa, may built-in PCM Audio Metronome para sa tamang tempo.\nAt pangatlo, may Auto-Scroll controls para hindi na kailangang mag-swipe ng kamay ang piyanista habang nagpapatugtog sa stage.\"",
                    "note": "Technical Note: Ginamit ang ChordSheet.java, StableScrollView.kt, at low-latency MetronomePcm.java."
                }
            ]
        },
        {
            "act": "ACT 5: REAL-TIME COMMUNICATION AT SUBSTITUTION HANDSHAKE",
            "desc": "Layunin: Sagutin ang SOP #3 at #4 — In-app team communication, urgent alerts, at automated substitution state machine.",
            "steps": [
                {
                    "title": "Step 5.1: Urgent Announcements at Team Chat with Image Upload (Phone A ➔ Phone B)",
                    "actions": [
                        "Sa Phone A (Leader), pumunta sa 'Announcements' tab.",
                        "Mag-post ng announcement: Title: 'Call Time Reminder', Message: 'Rehearsal strictly at 4 PM!', at lagyan ng check ang 'Urgent' checkbox.",
                        "Sa Phone B (Member), ipakita na may lumabas na card na may red 'Urgent Alert' badge.",
                        "Pumunta sa 'Team Chat' tab sa dalawang phone. Mag-send ng message at mag-attach ng picture mula sa gallery (ina-upload sa Cloudinary CDN)."
                    ],
                    "speech": "\"Para hindi matabunan ang importanteng updates sa casual GC, may hiwalay tayong Urgent Announcements module na may visual priority badge. Mayroon din tayong dedicated Team Chat na kayang mag-send ng pictures gamit ang aming Cloudinary CDN pipeline na nagko-compress ng image bago i-upload para tipid sa data ng volunteers.\"",
                    "note": "Technical Note: Ang CloudinaryUploader.kt ay nagda-downscale sa 512px bago mag-upload via unsigned preset."
                },
                {
                    "title": "Step 5.2: The Substitution Handshake (Ang Pinakatampok na Feature)",
                    "actions": [
                        "Sa Phone B (Member): Kunwari ay may biglaang emergency si Member sa kanyang duty. Pindutin ang 'Request Substitution'.",
                        "Pumili ng kapalit na teammate mula sa active roster at maglagay ng reason: 'Family Emergency'. I-submit.",
                        "Sa Phone A (Leader / Screen): Sa notification/announcement feed, lilitaw ang Substitution Request Card na may status na 'Pending'.",
                        "Sa Phone A, i-click ang 'Accept' o 'Approve'.",
                        "Ipakita sa screen: Awtomatikong napalitan ang pangalan ng assignee sa Service Schedule document sa Firestore nang walang manual re-encoding!"
                    ],
                    "speech": "\"Ito po ang solusyon sa problema ng mga biglaang absent sa worship service. Nag-submit si Member ng substitution request. Agad itong lumabas sa screen ni Leader bilang 'Pending'.\n\nNang i-click ni Leader ang 'Accept', pinagana ng aming backend ang atomic state update: pinalitan ang assignment record sa Firestore at in-update ang duty list. May kumpletong audit trail at walang kaguluhang naganap bago ang rehearsal.\"",
                    "note": "Technical Note: Pinatatakbo ito ng acceptSubstitution() sa TeamRepository.kt na may atomic update sa ServiceData."
                }
            ]
        },
        {
            "act": "ACT 6: PROFILE, CLOUDINARY AVATAR AT LOGOUT",
            "desc": "Layunin: Ipakita ang profile customization, compressed cloud storage, at secure session termination.",
            "steps": [
                {
                    "title": "Step 6.1: Avatar Upload at Clean Logout (Phone B - Member)",
                    "actions": [
                        "Sa Phone B, pumunta sa 'Profile' tab.",
                        "I-tap ang profile avatar icon. Pumili ng picture mula sa device para ma-update ang avatar via Cloudinary.",
                        "I-tap ang 'Logout' button. Babalik ang application sa Start/Login screen."
                    ],
                    "speech": "\"Panghuli po, sa Profile screen, maaaring mag-upload ng profile photo ang bawat miyembro. At alinsunod sa aming Non-Functional Security Requirement (NFR-12), sa pag-tap ng Logout, awtomatikong nina-nullify ng SessionManager ang credentials upang maiwasan ang unauthorized session hijacking.\"",
                    "note": "Technical Note: Sinisira ng AuthManager.signOut() at SessionManager ang persistent cached auth state."
                }
            ]
        }
    ]

    for act_data in acts:
        act_flow = []
        act_flow.append(Paragraph(f"<b>{act_data['act']}</b>", h1))
        act_flow.append(Paragraph(f"<i>{act_data['desc']}</i>", body))
        act_flow.append(Spacer(1, 4))
        
        for step in act_data['steps']:
            step_flow = []
            step_flow.append(Paragraph(f"<b>{step['title']}</b>", h2))
            
            step_flow.append(Paragraph("<b>Aksyon sa Screen (What to click):</b>", body))
            for a in step['actions']:
                step_flow.append(Paragraph(f"• {a}", bullet))
                
            step_flow.append(Paragraph("<b>Sasabihin sa Panel (Taglish Script):</b>", body))
            step_flow.append(Paragraph(step['speech'], speech))
            
            step_flow.append(Paragraph(f"<b>Under the Hood:</b> {step['note']}", note_box))
            step_flow.append(Spacer(1, 4))
            act_flow.append(KeepTogether(step_flow))
            
        story.append(KeepTogether(act_flow))
        story.append(Spacer(1, 6))

    # SECTION: EMERGENCY CHEAT SHEET DURING LIVE DEMO
    story.append(Spacer(1, 8))
    story.append(Paragraph("EMERGENCY CHEAT SHEET (KUNG MAY MAG-LAG O MAG-DELAY HABANG NAGDE-DEMO)", h1))
    
    faq_data = [
        "<b>Kung nag-delay ng 2-3 seconds ang sync dahil mabagal ang venue Wi-Fi:</b><br/>"
        "<i>Sasabihin:</i> 'Mapapansin po ninyo na may kaunting latency dahil sa live cloud transmission sa Singapore region ng Firebase. Ngunit dahil naka-enable po ang aming In-Memory ConcurrentHashMap caching sa TeamRepository.kt, seamless pa rin ang switching sa local device nang walang crash.'",
        
        "<b>Kung may magtanong na panelist: 'Bakit hindi ninyo ginawang Web App ito?':</b><br/>"
        "<i>Sasabihin:</i> 'Alinsunod po ito sa Scope and Limitations sa Chapter 1. Ang 15 worship volunteers ng New Life Christian Church ay 100% naka-Android phones. Kailangan din po namin ng direct hardware access para sa low-latency audio engine ng Metronome at 60fps auto-scroll sa music stand.'",

        "<b>Kung itanong kung paano kapag offline sa loob ng sanctuary:</b><br/>"
        "<i>Sasabihin:</i> 'May built-in offline persistence ang Cloud Firestore. Lahat ng naunang na-load na chord sheets at schedules ay naka-cache sa local SQLite storage ng phone, kaya mababasa pa rin ito ng banda kahit mawalan ng internet signal sa stage.'"
    ]

    for f in faq_data:
        story.append(Paragraph(f, body))
        story.append(Spacer(1, 3))

    doc.build(story, canvasmaker=NumberedCanvas)
    print("Demo PDF build successful:", pdf_filename)

if __name__ == "__main__":
    create_demo_pdf()
