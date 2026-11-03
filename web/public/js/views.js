import {
  escapeHtml,
  isWorshipLeader,
  isSubstituteLeader,
  hasLeaderPrivileges,
  extractYoutubeVideoId,
  getYoutubeThumbnailUrl,
  formatFriendlyDate,
  formatFriendlyTime,
  formatRelativeTime
} from "./utils.js";
import { DEFAULT_MINISTRY_ROLES } from "./config.js";

export function getAvatarHtml(name = "Member", photoUri = "", sizeClass = "w-10 h-10 text-sm") {
  const cleanName = String(name || "Member").trim();
  const initials = cleanName
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0] || "")
    .join("")
    .toUpperCase() || "M";

  if (photoUri && (photoUri.startsWith("http://") || photoUri.startsWith("https://"))) {
    return `<img src="${escapeHtml(photoUri)}" alt="${escapeHtml(cleanName)}" class="${sizeClass} rounded-full object-cover border border-purple-500/40 shrink-0 bg-purple-950" onerror="this.style.display='none';this.nextElementSibling.style.display='flex';" /><div style="display:none" class="${sizeClass} rounded-full brand-gradient items-center justify-center font-bold text-white border border-purple-400/30 shrink-0">${escapeHtml(initials)}</div>`;
  }
  return `<div class="${sizeClass} rounded-full brand-gradient flex items-center justify-center font-bold text-white border border-purple-400/30 shrink-0">${escapeHtml(initials)}</div>`;
}

export function getRoleBadgeHtml(ministryRole = "", role = "") {
  if (isWorshipLeader(ministryRole, role)) {
    return `<span class="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-500/20 text-amber-300 border border-amber-500/40"><i class="fas fa-crown text-[10px]"></i> Worship Leader</span>`;
  }
  if (isSubstituteLeader(ministryRole)) {
    return `<span class="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-purple-500/25 text-purple-200 border border-purple-400/40"><i class="fas fa-star text-[10px]"></i> Substitute Leader</span>`;
  }
  const label = ministryRole && ministryRole.trim() ? ministryRole.trim() : "Member";
  return `<span class="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-800 text-slate-200 border border-slate-700">${escapeHtml(label)}</span>`;
}

export function renderHomeView(state) {
  const { profile, team, roster, services, announcements, substitutions, rejoinRequests } = state;
  const isLeader = hasLeaderPrivileges(profile);
  const isPrimaryLeader = isWorshipLeader(profile?.ministryRole, profile?.role);
  const todayIso = new Date().toISOString().slice(0, 10);

  const visibleServices = services
    .filter((s) => isLeader || s.status === "Published")
    .sort((a, b) => String(a.date || "").localeCompare(String(b.date || "")));

  const upcomingServices = visibleServices.filter((s) => !s.date || s.date >= todayIso);
  const pendingSubs = substitutions.filter((s) => s.status === "Pending" || s.status === "Substitute Accepted");
  const pendingRejoins = rejoinRequests.filter((r) => r.status === "Pending");

  return `
    <div class="space-y-6">
      <!-- Hero Team Banner -->
      <div class="brand-card-gradient rounded-2xl p-6 shadow-xl relative overflow-hidden">
        <div class="absolute -right-10 -top-10 w-44 h-44 rounded-full bg-purple-500/15 blur-2xl pointer-events-none"></div>
        <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div class="flex items-center gap-4">
            <img src="/assets/team_icon.png" alt="Team Logo" class="w-16 h-16 rounded-2xl object-cover border border-purple-400/40 shadow-lg bg-purple-950/60" />
            <div>
              <div class="flex flex-wrap items-center gap-2">
                <h1 class="text-2xl font-extrabold text-white tracking-tight">${escapeHtml(team?.teamName || profile?.teamName || "Music Ministry Team")}</h1>
                ${isPrimaryLeader ? `<button onclick="window.appActions.openEditTeamNameModal()" class="text-xs text-purple-300 hover:text-white px-2 py-1 rounded-lg bg-white/5 hover:bg-white/10 transition" title="Edit Team Name"><i class="fas fa-pen"></i></button>` : ""}
              </div>
              <div class="flex flex-wrap items-center gap-2 mt-1.5">
                ${getRoleBadgeHtml(profile?.ministryRole, profile?.role)}
                <span class="text-xs text-slate-300"><i class="fas fa-users mr-1 text-purple-400"></i>${roster.length} Active Member${roster.length === 1 ? "" : "s"}</span>
                <span class="text-xs text-emerald-400 flex items-center gap-1"><span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span> Real-Time Synced</span>
              </div>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-2.5">
            <div class="bg-black/35 border border-purple-400/30 rounded-xl px-3.5 py-2 flex items-center gap-3">
              <div>
                <div class="text-[10px] uppercase tracking-wider text-purple-300 font-semibold">Team Invite Code</div>
                <div class="text-lg font-mono font-bold tracking-widest text-white">${escapeHtml(profile?.teamCode || "------")}</div>
              </div>
              <button onclick="window.appActions.copyTeamCode()" class="p-2 rounded-lg bg-purple-600/30 hover:bg-purple-600/50 text-purple-200 transition" title="Copy Team Code">
                <i class="fas fa-copy"></i>
              </button>
            </div>
            ${isLeader ? `
              <button onclick="window.appActions.openServiceModal()" class="brand-gradient hover:opacity-95 text-white font-semibold px-4 py-3 rounded-xl shadow-lg flex items-center gap-2 text-sm transition">
                <i class="fas fa-calendar-plus"></i> New Service
              </button>
            ` : `
              <button onclick="window.appActions.openAvailabilityModal()" class="bg-purple-600/30 hover:bg-purple-600/45 border border-purple-400/40 text-white font-semibold px-4 py-3 rounded-xl flex items-center gap-2 text-sm transition">
                <i class="fas fa-calendar-xmark"></i> My Availability
              </button>
            `}
          </div>
        </div>
      </div>

      <!-- Quick Stats Grid -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <button onclick="window.appActions.switchTab('schedule')" class="glass-panel rounded-xl p-4 text-left hover:border-purple-500/40 transition">
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium text-slate-400">Upcoming Services</span>
            <span class="w-8 h-8 rounded-lg bg-purple-500/20 text-purple-300 flex items-center justify-center"><i class="fas fa-calendar-days"></i></span>
          </div>
          <div class="text-2xl font-bold text-white mt-2">${upcomingServices.length}</div>
          <div class="text-[11px] text-purple-300 mt-1">View schedule & lineup &rarr;</div>
        </button>

        <button onclick="window.appActions.switchTab('songs')" class="glass-panel rounded-xl p-4 text-left hover:border-purple-500/40 transition">
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium text-slate-400">Song Catalog</span>
            <span class="w-8 h-8 rounded-lg bg-pink-500/20 text-pink-300 flex items-center justify-center"><i class="fas fa-music"></i></span>
          </div>
          <div class="text-2xl font-bold text-white mt-2">${state.songs.length}</div>
          <div class="text-[11px] text-pink-300 mt-1">Chord sheets & transpose &rarr;</div>
        </button>

        <button onclick="window.appActions.switchTab('schedule')" class="glass-panel rounded-xl p-4 text-left hover:border-purple-500/40 transition">
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium text-slate-400">Active Substitutions</span>
            <span class="w-8 h-8 rounded-lg bg-amber-500/20 text-amber-300 flex items-center justify-center"><i class="fas fa-people-arrows"></i></span>
          </div>
          <div class="text-2xl font-bold text-white mt-2">${pendingSubs.length}</div>
          <div class="text-[11px] text-amber-300 mt-1">${pendingSubs.length ? "Action needed" : "All lineups clear"} &rarr;</div>
        </button>

        <button onclick="window.appActions.switchTab('roster')" class="glass-panel rounded-xl p-4 text-left hover:border-purple-500/40 transition">
          <div class="flex items-center justify-between">
            <span class="text-xs font-medium text-slate-400">Ministry Roster</span>
            <span class="w-8 h-8 rounded-lg bg-emerald-500/20 text-emerald-300 flex items-center justify-center"><i class="fas fa-user-check"></i></span>
          </div>
          <div class="text-2xl font-bold text-white mt-2">${roster.length}</div>
          <div class="text-[11px] text-emerald-300 mt-1">${pendingRejoins.length && isLeader ? `${pendingRejoins.length} rejoin request(s)` : "Manage roles & team"} &rarr;</div>
        </button>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Upcoming Worship Services Column -->
        <div class="lg:col-span-2 space-y-4">
          <div class="flex items-center justify-between">
            <h2 class="text-lg font-bold text-white flex items-center gap-2">
              <i class="fas fa-calendar-check text-purple-400"></i> Upcoming Worship Services
            </h2>
            <button onclick="window.appActions.switchTab('schedule')" class="text-xs text-purple-300 hover:text-white font-medium">View All (${visibleServices.length})</button>
          </div>

          ${upcomingServices.length === 0 ? `
            <div class="glass-panel rounded-2xl p-8 text-center">
              <div class="w-12 h-12 rounded-full bg-purple-500/15 text-purple-300 flex items-center justify-center mx-auto mb-3">
                <i class="fas fa-calendar-plus text-xl"></i>
              </div>
              <p class="text-slate-300 font-semibold">No upcoming worship services scheduled yet</p>
              <p class="text-xs text-slate-400 mt-1">Services created on the Android app or Web app appear here in real time.</p>
              ${isLeader ? `<button onclick="window.appActions.openServiceModal()" class="mt-4 brand-gradient text-white text-xs font-semibold px-4 py-2 rounded-xl">Create Worship Service</button>` : ""}
            </div>
          ` : upcomingServices.slice(0, 4).map((svc) => renderServiceCardHtml(svc, state, true)).join("")}
        </div>

        <!-- Ministry Announcements Column -->
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <h2 class="text-lg font-bold text-white flex items-center gap-2">
              <i class="fas fa-bullhorn text-amber-400"></i> Announcements
            </h2>
            ${isLeader ? `
              <button onclick="window.appActions.openAnnouncementModal()" class="text-xs bg-purple-600/30 hover:bg-purple-600/50 text-purple-200 px-3 py-1.5 rounded-lg border border-purple-400/30 font-medium transition">
                <i class="fas fa-plus mr-1"></i> Post
              </button>
            ` : ""}
          </div>

          ${announcements.length === 0 ? `
            <div class="glass-panel rounded-2xl p-6 text-center">
              <i class="fas fa-bell-slash text-slate-500 text-2xl mb-2"></i>
              <p class="text-sm text-slate-300 font-medium">No announcements yet</p>
              <p class="text-xs text-slate-400 mt-1">Team updates and urgent rehearsal reminders will appear here.</p>
            </div>
          ` : `
            <div class="space-y-3">
              ${announcements.slice(0, 6).map((a) => `
                <div class="glass-panel rounded-xl p-4 border-l-4 ${a.urgent ? "border-l-rose-500 bg-rose-950/20" : "border-l-purple-500"}">
                  <div class="flex items-start justify-between gap-2">
                    <div class="flex items-center gap-2">
                      ${a.urgent ? `<span class="px-2 py-0.5 rounded text-[10px] font-bold uppercase bg-rose-500/25 text-rose-300 border border-rose-500/40">Urgent</span>` : ""}
                      <h3 class="font-bold text-sm text-white">${escapeHtml(a.title)}</h3>
                    </div>
                    ${isLeader ? `
                      <button onclick="window.appActions.deleteAnnouncement('${escapeHtml(a.id)}')" class="text-slate-500 hover:text-rose-400 text-xs" title="Delete Announcement">
                        <i class="fas fa-trash"></i>
                      </button>
                    ` : ""}
                  </div>
                  <p class="text-xs text-slate-300 mt-1.5 whitespace-pre-line leading-relaxed">${escapeHtml(a.message)}</p>
                  <div class="flex items-center justify-between text-[11px] text-slate-400 mt-3 pt-2 border-t border-white/5">
                    <span><i class="fas fa-user-pen mr-1 text-purple-400"></i>${escapeHtml(a.author || "Ministry Leader")}</span>
                    <span>${formatRelativeTime(a.createdAt)}</span>
                  </div>
                </div>
              `).join("")}
            </div>
          `}
        </div>
      </div>
    </div>
  `;
}

export function renderServiceCardHtml(svc, state, compact = false) {
  const { profile } = state;
  const isLeader = hasLeaderPrivileges(profile);
  const myUid = profile?.uid || "";
  const myEmail = (profile?.email || "").toLowerCase();
  const assignments = Array.isArray(svc.assignments) ? svc.assignments : [];
  const songs = Array.isArray(svc.songs) ? svc.songs : [];
  const rehearsal = svc.rehearsal || {};

  const myAssignment = assignments.find(
    (a) => (a.uid && a.uid === myUid) || (a.email && a.email.toLowerCase() === myEmail)
  );

  return `
    <div class="glass-panel rounded-2xl overflow-hidden border ${myAssignment ? "border-purple-500/45" : "border-white/10"} transition hover:border-purple-400/50">
      ${svc.coverImageUrl ? `
        <div class="h-36 w-full relative overflow-hidden bg-purple-950">
          <img src="${escapeHtml(svc.coverImageUrl)}" alt="${escapeHtml(svc.title)}" class="w-full h-full object-cover opacity-85" />
          <div class="absolute inset-0 bg-gradient-to-t from-[#181024] via-transparent to-transparent"></div>
        </div>
      ` : ""}
      <div class="p-5">
        <div class="flex flex-wrap items-start justify-between gap-2">
          <div>
            <div class="flex flex-wrap items-center gap-2">
              <span class="px-2.5 py-0.5 rounded-full text-[11px] font-semibold ${svc.status === "Published" ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30" : "bg-amber-500/20 text-amber-300 border border-amber-500/30"}">
                ${escapeHtml(svc.status || "Draft")}
              </span>
              ${myAssignment ? `
                <span class="px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-purple-500/25 text-purple-200 border border-purple-400/40">
                  <i class="fas fa-user-check mr-1"></i>Assigned: ${escapeHtml(myAssignment.role)}
                </span>
              ` : ""}
            </div>
            <h3 class="text-lg font-bold text-white mt-1.5">${escapeHtml(svc.title || "Worship Service")}</h3>
            <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-300 mt-1">
              <span><i class="fas fa-calendar text-purple-400 mr-1.5"></i>${formatFriendlyDate(svc.date)}</span>
              ${svc.start ? `<span><i class="fas fa-clock text-purple-400 mr-1.5"></i>${formatFriendlyTime(svc.start)}${svc.end ? ` - ${formatFriendlyTime(svc.end)}` : ""}</span>` : ""}
              ${svc.venue ? `<span><i class="fas fa-location-dot text-purple-400 mr-1.5"></i>${escapeHtml(svc.venue)}</span>` : ""}
            </div>
          </div>

          <div class="flex items-center gap-1.5">
            ${myAssignment ? `
              <button onclick="window.appActions.openSubstitutionModal('${escapeHtml(svc.id)}', 'Exclusion')" class="text-xs px-3 py-1.5 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-200 border border-amber-500/30 font-medium transition" title="Request Substitute">
                <i class="fas fa-people-arrows mr-1"></i> Sub Out
              </button>
            ` : `
              <button onclick="window.appActions.openSubstitutionModal('${escapeHtml(svc.id)}', 'Inclusion')" class="text-xs px-3 py-1.5 rounded-lg bg-purple-500/20 hover:bg-purple-500/30 text-purple-200 border border-purple-500/30 font-medium transition" title="Volunteer / Step In">
                <i class="fas fa-hand-holding-heart mr-1"></i> Volunteer
              </button>
            `}
            ${isLeader ? `
              <button onclick="window.appActions.openServiceModal('${escapeHtml(svc.id)}')" class="p-2 rounded-lg bg-white/5 hover:bg-white/10 text-slate-200 text-xs transition" title="Edit Service">
                <i class="fas fa-pen"></i>
              </button>
              <button onclick="window.appActions.deleteService('${escapeHtml(svc.id)}')" class="p-2 rounded-lg bg-white/5 hover:bg-rose-500/20 text-slate-400 hover:text-rose-300 text-xs transition" title="Delete Service">
                <i class="fas fa-trash"></i>
              </button>
            ` : ""}
          </div>
        </div>

        ${rehearsal && rehearsal.date ? `
          <div class="mt-3 px-3 py-2 rounded-xl bg-amber-500/10 border border-amber-500/25 text-xs text-amber-200 flex flex-wrap items-center justify-between gap-2">
            <div>
              <span class="font-bold"><i class="fas fa-guitar mr-1.5"></i>Rehearsal:</span>
              ${formatFriendlyDate(rehearsal.date)} ${rehearsal.start ? `at ${formatFriendlyTime(rehearsal.start)}` : ""} ${rehearsal.venue ? `&bull; ${escapeHtml(rehearsal.venue)}` : ""}
            </div>
            ${rehearsal.notes ? `<span class="text-[11px] text-amber-300/90 italic">${escapeHtml(rehearsal.notes)}</span>` : ""}
          </div>
        ` : ""}

        <div class="grid grid-cols-1 ${compact ? "md:grid-cols-2" : "md:grid-cols-2"} gap-4 mt-4 pt-3 border-t border-white/10">
          <!-- Assigned Lineup -->
          <div>
            <div class="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-2">
              <i class="fas fa-users-line mr-1 text-purple-400"></i> Ministry Lineup (${assignments.length})
            </div>
            ${assignments.length === 0 ? `<p class="text-xs text-slate-500 italic">No members assigned yet</p>` : `
              <div class="flex flex-wrap gap-1.5">
                ${assignments.map((a) => `
                  <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-white/5 border border-white/10 text-xs">
                    <span class="font-semibold text-purple-300">${escapeHtml(a.role)}:</span>
                    <span class="text-slate-200">${escapeHtml(a.name)}</span>
                  </span>
                `).join("")}
              </div>
            `}
          </div>

          <!-- Song Setlist -->
          <div>
            <div class="text-[11px] font-bold uppercase tracking-wider text-slate-400 mb-2">
              <i class="fas fa-list-music mr-1 text-pink-400"></i> Worship Setlist (${songs.length})
            </div>
            ${songs.length === 0 ? `<p class="text-xs text-slate-500 italic">No songs in setlist yet</p>` : `
              <div class="space-y-1.5">
                ${songs.map((sg, idx) => `
                  <div onclick="window.appActions.openSongByTitle('${escapeHtml(sg.title)}', '${escapeHtml(sg.key || "C")}')" class="flex items-center justify-between px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-purple-500/20 border border-white/5 cursor-pointer transition">
                    <div class="text-xs truncate">
                      <span class="text-slate-400 mr-1.5">${idx + 1}.</span>
                      <span class="font-semibold text-white">${escapeHtml(sg.title)}</span>
                      ${sg.artist ? `<span class="text-slate-400 text-[11px]"> &bull; ${escapeHtml(sg.artist)}</span>` : ""}
                    </div>
                    <div class="flex items-center gap-1.5 shrink-0">
                      <span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-purple-500/25 text-purple-200">Key: ${escapeHtml(sg.key || "C")}</span>
                      ${sg.bpm ? `<span class="px-1.5 py-0.5 rounded text-[10px] bg-slate-800 text-slate-300">${escapeHtml(sg.bpm)} BPM</span>` : ""}
                    </div>
                  </div>
                `).join("")}
              </div>
            `}
          </div>
        </div>
      </div>
    </div>
  `;
}

export function renderScheduleView(state) {
  const { profile, services, substitutions, roster } = state;
  const isLeader = hasLeaderPrivileges(profile);
  const myRanges = profile?.unavailableRanges || [];
  const visibleServices = services
    .filter((s) => isLeader || s.status === "Published")
    .sort((a, b) => String(a.date || "").localeCompare(String(b.date || "")));

  return `
    <div class="space-y-6">
      <!-- Top Action Bar -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 class="text-2xl font-extrabold text-white">Worship Schedule & Lineups</h1>
          <p class="text-xs text-slate-400">Manage service lineups, rehearsals, member availability, and substitution requests.</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button onclick="window.appActions.openAvailabilityModal()" class="px-3.5 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/15 text-xs font-semibold text-white flex items-center gap-2 transition">
            <i class="fas fa-calendar-xmark text-rose-400"></i> My Unavailability (${myRanges.length})
          </button>
          ${isLeader ? `
            <button onclick="window.appActions.openServiceModal()" class="brand-gradient hover:opacity-95 px-4 py-2.5 rounded-xl text-xs font-semibold text-white shadow-lg flex items-center gap-2 transition">
              <i class="fas fa-plus"></i> Create Service
            </button>
          ` : ""}
        </div>
      </div>

      <!-- My Unavailable Ranges & Team Availability Summary -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div class="lg:col-span-2 space-y-4">
          <h2 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fas fa-calendar-days text-purple-400"></i> Scheduled Worship Services (${visibleServices.length})
          </h2>
          ${visibleServices.length === 0 ? `
            <div class="glass-panel rounded-2xl p-8 text-center">
              <i class="fas fa-calendar-day text-3xl text-purple-400 mb-3"></i>
              <p class="text-sm font-semibold text-white">No services scheduled</p>
              <p class="text-xs text-slate-400 mt-1">When a leader schedules or publishes a service, it appears here immediately.</p>
            </div>
          ` : visibleServices.map((s) => renderServiceCardHtml(s, state, false)).join("")}
        </div>

        <!-- Right Sidebar: Substitutions & Member Availability -->
        <div class="space-y-6">
          <!-- Substitution Requests Card -->
          <div class="glass-panel rounded-2xl p-5 space-y-3">
            <div class="flex items-center justify-between">
              <h3 class="text-sm font-bold text-white flex items-center gap-2">
                <i class="fas fa-people-arrows text-amber-400"></i> Substitution Requests
              </h3>
              <span class="px-2 py-0.5 rounded-full text-[11px] bg-amber-500/20 text-amber-300 font-semibold">${substitutions.length}</span>
            </div>

            ${substitutions.length === 0 ? `
              <p class="text-xs text-slate-400 italic py-3 text-center">No substitution requests submitted.</p>
            ` : `
              <div class="space-y-3 max-h-96 overflow-y-auto pr-1">
                ${substitutions.map((sub) => {
                  const isMySubTarget = sub.substituteUid === profile?.uid || (sub.substituteName && sub.substituteName === (profile?.fullName || profile?.username));
                  const isMyRequest = sub.requesterUid === profile?.uid;
                  const statusColor =
                    sub.status === "Approved" || sub.status === "Accepted"
                      ? "bg-emerald-500/20 text-emerald-300 border-emerald-500/30"
                      : sub.status === "Declined" || sub.status === "Cancelled"
                      ? "bg-rose-500/20 text-rose-300 border-rose-500/30"
                      : "bg-amber-500/20 text-amber-300 border-amber-500/30";

                  return `
                    <div class="p-3.5 rounded-xl bg-white/5 border border-white/10 space-y-2">
                      <div class="flex items-center justify-between gap-2">
                        <span class="text-xs font-bold text-white">${escapeHtml(sub.serviceTitle || "Worship Service")}</span>
                        <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold border ${statusColor}">${escapeHtml(sub.status)}</span>
                      </div>
                      <div class="text-xs text-slate-300 space-y-0.5">
                        <div><span class="text-slate-400">Date:</span> ${formatFriendlyDate(sub.serviceDate)}</div>
                        <div><span class="text-slate-400">Role:</span> <span class="text-purple-300 font-semibold">${escapeHtml(sub.role)}</span> (${escapeHtml(sub.type || "Exclusion")})</div>
                        <div><span class="text-slate-400">Requester:</span> ${escapeHtml(sub.requesterName)}</div>
                        ${sub.substituteName ? `<div><span class="text-slate-400">Substitute:</span> <span class="text-emerald-300 font-medium">${escapeHtml(sub.substituteName)}</span></div>` : ""}
                        <div class="text-[11px] text-slate-400 italic mt-1">"${escapeHtml(sub.reason)}"</div>
                      </div>
                      <div class="flex flex-wrap items-center gap-1.5 pt-2">
                        ${sub.status === "Pending" && isMySubTarget ? `
                          <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Substitute Accepted')" class="px-2.5 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-[11px] font-semibold">Accept Sub</button>
                          <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Declined')" class="px-2.5 py-1 rounded-lg bg-rose-600/40 hover:bg-rose-600 text-white text-[11px] font-semibold">Decline</button>
                        ` : ""}
                        ${(sub.status === "Pending" || sub.status === "Substitute Accepted") && isLeader ? `
                          <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Approved')" class="px-2.5 py-1 rounded-lg bg-purple-600 hover:bg-purple-500 text-white text-[11px] font-semibold">Leader Approve</button>
                          <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Declined')" class="px-2.5 py-1 rounded-lg bg-rose-600/40 hover:bg-rose-600 text-white text-[11px] font-semibold">Reject</button>
                        ` : ""}
                        ${sub.status === "Pending" && isMyRequest ? `
                          <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Cancelled')" class="px-2.5 py-1 rounded-lg bg-slate-700 hover:bg-slate-600 text-slate-200 text-[11px]">Cancel</button>
                        ` : ""}
                      </div>
                    </div>
                  `;
                }).join("")}
              </div>
            `}
          </div>

          <!-- Team Unavailability Feed -->
          <div class="glass-panel rounded-2xl p-5 space-y-3">
            <div class="flex items-center justify-between">
              <h3 class="text-sm font-bold text-white flex items-center gap-2">
                <i class="fas fa-user-clock text-rose-400"></i> Member Unavailability
              </h3>
              <button onclick="window.appActions.openAvailabilityModal()" class="text-xs text-purple-300 hover:text-white">+ Add Mine</button>
            </div>
            ${(() => {
              const allUnavailable = [];
              roster.forEach((m) => {
                (m.unavailableRanges || []).forEach((r) => {
                  allUnavailable.push({ member: m, range: r });
                });
              });
              if (allUnavailable.length === 0) {
                return `<p class="text-xs text-slate-400 italic py-3 text-center">All team members are currently marked available.</p>`;
              }
              return `
                <div class="space-y-2 max-h-72 overflow-y-auto pr-1">
                  ${allUnavailable.map(({ member, range }) => `
                    <div class="p-2.5 rounded-xl bg-rose-950/20 border border-rose-500/25 text-xs">
                      <div class="font-bold text-white">${escapeHtml(member.fullName || member.username)} <span class="text-slate-400 font-normal">(${escapeHtml(member.ministryRole || "Member")})</span></div>
                      <div class="text-rose-300 text-[11px] mt-0.5"><i class="fas fa-calendar-xmark mr-1"></i>${escapeHtml(range.startDate)} to ${escapeHtml(range.endDate)}</div>
                      ${range.reason ? `<div class="text-slate-400 text-[11px] italic mt-0.5">"${escapeHtml(range.reason)}"</div>` : ""}
                    </div>
                  `).join("")}
                </div>
              `;
            })()}
          </div>
        </div>
      </div>
    </div>
  `;
}

export function renderSongsView(state) {
  const { songs, songSearchQuery, profile } = state;
  const isLeader = hasLeaderPrivileges(profile);
  const q = (songSearchQuery || "").trim().toLowerCase();
  const filtered = songs.filter((s) => {
    if (!q) return true;
    return (
      String(s.title || "").toLowerCase().includes(q) ||
      String(s.artist || "").toLowerCase().includes(q) ||
      String(s.key || "").toLowerCase().includes(q) ||
      String(s.lyrics || "").toLowerCase().includes(q) ||
      String(s.sheet || "").toLowerCase().includes(q)
    );
  });

  return `
    <div class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 class="text-2xl font-extrabold text-white">Song Library & Chord Studio</h1>
          <p class="text-xs text-slate-400">Click any song to open Stage View with real-time key transposition, auto-scroll, metronome, and YouTube reference.</p>
        </div>
        <button onclick="window.appActions.openSongEditorModal()" class="brand-gradient hover:opacity-95 px-4 py-2.5 rounded-xl text-xs font-semibold text-white shadow-lg flex items-center gap-2 self-start sm:self-auto transition">
          <i class="fas fa-plus"></i> Add Song / Chord Sheet
        </button>
      </div>

      <!-- Search Bar -->
      <div class="relative">
        <i class="fas fa-search absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"></i>
        <input
          type="text"
          value="${escapeHtml(songSearchQuery || "")}"
          oninput="window.appActions.setSongSearch(this.value)"
          placeholder="Search songs by title, artist, musical key, or lyrics..."
          class="w-full pl-11 pr-4 py-3 rounded-xl bg-white/5 border border-white/10 text-sm text-white placeholder-slate-400 focus:outline-none focus:border-purple-500"
        />
      </div>

      ${filtered.length === 0 ? `
        <div class="glass-panel rounded-2xl p-10 text-center">
          <i class="fas fa-music text-3xl text-purple-400 mb-3"></i>
          <p class="text-base font-bold text-white">No songs found</p>
          <p class="text-xs text-slate-400 mt-1">Add a worship song with chords, lyrics, BPM, and a YouTube reference link.</p>
        </div>
      ` : `
        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          ${filtered.map((song) => {
            const thumb = getYoutubeThumbnailUrl(song.referenceUrl);
            const hasChords = Boolean(song.sheet && song.sheet.trim());
            return `
              <div class="glass-panel rounded-2xl overflow-hidden border border-white/10 hover:border-purple-400/50 transition flex flex-col justify-between">
                <div>
                  ${thumb ? `
                    <div onclick="window.appActions.openSongStageModal('${escapeHtml(song.id)}')" class="h-36 w-full relative overflow-hidden bg-black cursor-pointer group">
                      <img src="${escapeHtml(thumb)}" alt="${escapeHtml(song.title)}" class="w-full h-full object-cover opacity-80 group-hover:scale-105 transition duration-300" />
                      <div class="absolute inset-0 bg-gradient-to-t from-[#181024] via-black/20 to-transparent"></div>
                      <div class="absolute bottom-2.5 left-3 flex items-center gap-1.5 text-[11px] font-semibold text-white bg-rose-600/90 px-2.5 py-0.5 rounded-full">
                        <i class="fab fa-youtube"></i> Video Reference
                      </div>
                    </div>
                  ` : ""}
                  <div class="p-4">
                    <div class="flex items-start justify-between gap-2">
                      <div onclick="window.appActions.openSongStageModal('${escapeHtml(song.id)}')" class="cursor-pointer">
                        <h3 class="font-bold text-base text-white hover:text-purple-300 transition">${escapeHtml(song.title)}</h3>
                        <p class="text-xs text-slate-400">${escapeHtml(song.artist || "Unknown Artist")}</p>
                      </div>
                      <div class="flex items-center gap-1 shrink-0">
                        <span class="px-2.5 py-1 rounded-lg bg-purple-500/25 border border-purple-400/30 text-purple-200 font-mono text-xs font-bold">
                          ${escapeHtml(song.key || "C")}
                        </span>
                        <span class="px-2 py-1 rounded-lg bg-slate-800 text-slate-300 text-xs">
                          ${escapeHtml(song.bpm || 74)} BPM
                        </span>
                      </div>
                    </div>
                    <p class="text-xs text-slate-400 mt-3 line-clamp-2 font-mono">
                      ${escapeHtml((song.sheet || song.lyrics || "No chord sheet text yet.").replace(/\[[^\]]+\]/g, "").slice(0, 120))}
                    </p>
                  </div>
                </div>

                <div class="px-4 py-3 bg-white/[0.03] border-t border-white/5 flex items-center justify-between">
                  <button onclick="window.appActions.openSongStageModal('${escapeHtml(song.id)}')" class="text-xs font-semibold text-purple-300 hover:text-white flex items-center gap-1.5">
                    <i class="fas fa-sliders"></i> Open Stage View ${hasChords ? "(Chords)" : "(Lyrics)"}
                  </button>
                  <div class="flex items-center gap-1.5">
                    <button onclick="window.appActions.openSongEditorModal('${escapeHtml(song.id)}')" class="p-1.5 rounded-lg hover:bg-white/10 text-slate-300 text-xs" title="Edit Song">
                      <i class="fas fa-pen"></i>
                    </button>
                    ${(isLeader || song.createdBy === profile?.uid) ? `
                      <button onclick="window.appActions.deleteSong('${escapeHtml(song.id)}')" class="p-1.5 rounded-lg hover:bg-rose-500/20 text-slate-400 hover:text-rose-300 text-xs" title="Delete Song">
                        <i class="fas fa-trash"></i>
                      </button>
                    ` : ""}
                  </div>
                </div>
              </div>
            `;
          }).join("")}
        </div>
      `}
    </div>
  `;
}

export function renderRosterView(state) {
  const { profile, team, roster, rejoinRequests } = state;
  const isPrimaryLeader = isWorshipLeader(profile?.ministryRole, profile?.role);
  const isSubLeader = isSubstituteLeader(profile?.ministryRole);
  const isLeader = isPrimaryLeader || isSubLeader || hasLeaderPrivileges(profile);

  const teamRoles = (team?.roles && team.roles.length ? team.roles : DEFAULT_MINISTRY_ROLES);
  const pendingRejoins = rejoinRequests.filter((r) => r.status === "Pending");

  return `
    <div class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 class="text-2xl font-extrabold text-white">Team Roster & Ministry Roles</h1>
          <p class="text-xs text-slate-400">Real-time directory of active members in ${escapeHtml(team?.teamName || profile?.teamName || "your ministry")}.</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button onclick="window.appActions.copyTeamCode()" class="px-3.5 py-2 rounded-xl bg-purple-600/25 border border-purple-400/40 text-xs font-semibold text-purple-200 hover:bg-purple-600/40 transition">
            <i class="fas fa-key mr-1.5"></i>Invite Code: <span class="font-mono font-bold text-white">${escapeHtml(profile?.teamCode || "")}</span>
          </button>
          ${isPrimaryLeader ? `
            <button onclick="window.appActions.openTransferLeadershipModal()" class="px-3.5 py-2 rounded-xl bg-amber-500/20 border border-amber-500/40 text-xs font-semibold text-amber-200 hover:bg-amber-500/30 transition">
              <i class="fas fa-crown mr-1.5"></i>Transfer Leadership
            </button>
          ` : ""}
        </div>
      </div>

      ${isLeader && pendingRejoins.length > 0 ? `
        <div class="glass-panel rounded-2xl p-5 border border-amber-500/40 space-y-3">
          <h2 class="text-sm font-bold text-amber-300 flex items-center gap-2">
            <i class="fas fa-user-plus"></i> Pending Rejoin Requests (${pendingRejoins.length})
          </h2>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            ${pendingRejoins.map((req) => `
              <div class="p-3.5 rounded-xl bg-white/5 border border-white/10 flex items-center justify-between gap-3">
                <div>
                  <div class="font-bold text-sm text-white">${escapeHtml(req.userName)}</div>
                  <div class="text-xs text-slate-400">${escapeHtml(req.userEmail || "")}</div>
                  ${req.reason ? `<div class="text-xs text-amber-200 italic mt-1">"${escapeHtml(req.reason)}"</div>` : ""}
                </div>
                <div class="flex items-center gap-1.5 shrink-0">
                  <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', 'Approved')" class="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold">Approve</button>
                  <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', 'Declined')" class="px-3 py-1.5 rounded-lg bg-rose-600/40 hover:bg-rose-600 text-white text-xs font-semibold">Decline</button>
                </div>
              </div>
            `).join("")}
          </div>
        </div>
      ` : ""}

      <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        ${roster.map((member) => {
          const memberIsWorshipLeader = isWorshipLeader(member.ministryRole, member.role);
          const memberIsSubLeader = isSubstituteLeader(member.ministryRole);
          const isSelf = member.uid === profile?.uid || (member.email && profile?.email && member.email.toLowerCase() === profile.email.toLowerCase());
          const canEditRole =
            (isPrimaryLeader && !memberIsWorshipLeader) ||
            (isSubLeader && !memberIsWorshipLeader && !memberIsSubLeader);

          const assignableRoles = teamRoles.filter((r) => {
            const clean = r.trim().toLowerCase();
            if (clean === "worship leader" || clean === "leader") return false;
            if (isSubLeader && clean === "substitute leader") return false;
            return true;
          });
          if (isPrimaryLeader && !assignableRoles.some((r) => r.toLowerCase() === "substitute leader")) {
            assignableRoles.unshift("Substitute Leader");
          }

          return `
            <div class="glass-panel rounded-2xl p-5 border ${memberIsWorshipLeader ? "border-amber-500/40" : memberIsSubLeader ? "border-purple-400/40" : "border-white/10"} flex flex-col justify-between gap-4">
              <div class="flex items-start gap-3.5">
                ${getAvatarHtml(member.fullName || member.username, member.photoUri, "w-12 h-12 text-base")}
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2">
                    <h3 class="font-bold text-base text-white truncate">${escapeHtml(member.fullName || member.username || "Member")}</h3>
                    ${isSelf ? `<span class="px-1.5 py-0.5 rounded text-[10px] bg-purple-500/30 text-purple-200 font-semibold">You</span>` : ""}
                  </div>
                  <p class="text-xs text-slate-400 truncate">${escapeHtml(member.email || `@${member.username}`)}</p>
                  <div class="mt-2 flex flex-wrap items-center gap-1.5">
                    ${getRoleBadgeHtml(member.ministryRole, member.role)}
                    ${(member.unavailableRanges && member.unavailableRanges.length > 0) ? `
                      <span class="px-2 py-0.5 rounded-full text-[10px] bg-rose-500/20 text-rose-300 border border-rose-500/30">
                        ${member.unavailableRanges.length} Unavailable Date(s)
                      </span>
                    ` : `
                      <span class="px-2 py-0.5 rounded-full text-[10px] bg-emerald-500/15 text-emerald-300">Available</span>
                    `}
                  </div>
                </div>
              </div>

              ${canEditRole ? `
                <div class="pt-3 border-t border-white/10 flex items-center justify-between gap-2">
                  <div class="flex-1">
                    <label class="block text-[10px] uppercase tracking-wider text-slate-400 mb-1">Assign Ministry Role</label>
                    <select
                      onchange="window.appActions.assignMemberRole('${escapeHtml(member.uid)}', this.value, '${escapeHtml(member.email)}', '${escapeHtml(member.fullName || member.username)}')"
                      class="w-full px-2.5 py-1.5 rounded-lg bg-slate-900 border border-white/15 text-xs text-white focus:outline-none focus:border-purple-500"
                    >
                      <option value="Member" ${(!member.ministryRole || member.ministryRole === "Member") ? "selected" : ""}>Member (Unassigned)</option>
                      ${assignableRoles.map((r) => `
                        <option value="${escapeHtml(r)}" ${String(member.ministryRole || "").toLowerCase() === r.toLowerCase() ? "selected" : ""}>
                          ${escapeHtml(r)}
                        </option>
                      `).join("")}
                    </select>
                  </div>
                  ${isPrimaryLeader && !isSelf ? `
                    <button
                      onclick="window.appActions.removeMemberFromTeam('${escapeHtml(member.uid)}', '${escapeHtml(member.fullName || member.username)}')"
                      class="mt-4 p-2 rounded-lg bg-rose-500/15 hover:bg-rose-500/30 text-rose-300 text-xs transition"
                      title="Remove Member from Team"
                    >
                      <i class="fas fa-user-minus"></i>
                    </button>
                  ` : ""}
                </div>
              ` : ""}
            </div>
          `;
        }).join("")}
      </div>
    </div>
  `;
}

export function renderChatView(state) {
  const { messages, profile, chatReplyTo, chatEditingMessage } = state;
  const isLeader = hasLeaderPrivileges(profile);

  return `
    <div class="glass-panel rounded-2xl flex flex-col h-[calc(100vh-11rem)] md:h-[calc(100vh-7rem)] overflow-hidden">
      <!-- Chat Header -->
      <div class="px-5 py-3.5 border-b border-white/10 flex items-center justify-between bg-white/[0.02]">
        <div class="flex items-center gap-3">
          <div class="w-9 h-9 rounded-xl brand-gradient flex items-center justify-center text-white shadow">
            <i class="fas fa-comments"></i>
          </div>
          <div>
            <h2 class="font-bold text-sm text-white">${escapeHtml(state.team?.teamName || profile?.teamName || "Ministry")} Team Chat</h2>
            <p class="text-[11px] text-slate-400">Real-time messages & photo sharing synced with the Android app</p>
          </div>
        </div>
        <span class="text-xs text-emerald-400 flex items-center gap-1.5">
          <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span> Live
        </span>
      </div>

      <!-- Messages Scroll Container -->
      <div id="chatMessagesContainer" class="flex-1 overflow-y-auto p-4 space-y-3">
        ${messages.length === 0 ? `
          <div class="h-full flex flex-col items-center justify-center text-center text-slate-400">
            <i class="fas fa-message text-3xl text-purple-400/60 mb-2"></i>
            <p class="text-sm font-semibold text-slate-300">No messages yet</p>
            <p class="text-xs">Start the conversation with your music ministry team!</p>
          </div>
        ` : messages.map((msg) => {
          const isMe = msg.senderUid === profile?.uid;
          const isRetracted = msg.isDeleted === true;
          return `
            <div class="flex ${isMe ? "justify-end" : "justify-start"} gap-2.5 group">
              ${!isMe ? getAvatarHtml(msg.senderName, msg.senderPhotoUri, "w-8 h-8 text-xs mt-1") : ""}
              <div class="max-w-[78%] sm:max-w-[65%] ${isMe ? "items-end" : "items-start"} flex flex-col">
                <div class="flex items-center gap-2 mb-1 px-1">
                  <span class="text-[11px] font-bold ${isMe ? "text-purple-300" : "text-slate-300"}">${escapeHtml(msg.senderName || "Member")}</span>
                  ${msg.senderRole ? `<span class="text-[10px] text-slate-400">${escapeHtml(msg.senderRole)}</span>` : ""}
                  <span class="text-[10px] text-slate-500">${formatRelativeTime(msg.timestamp)}</span>
                </div>

                <div class="rounded-2xl px-4 py-2.5 ${isRetracted ? "bg-white/5 border border-white/10 italic text-slate-400" : isMe ? "brand-gradient text-white shadow-md" : "bg-slate-800/90 border border-white/10 text-slate-100"}">
                  ${!isRetracted && msg.replyToId && msg.replyToText ? `
                    <div class="mb-2 px-2.5 py-1.5 rounded-lg bg-black/25 border-l-2 border-purple-300 text-xs">
                      <div class="font-bold text-[10px] text-purple-200">${escapeHtml(msg.replyToSender || "Teammate")}</div>
                      <div class="truncate text-white/80 text-[11px]">${escapeHtml(msg.replyToText)}</div>
                    </div>
                  ` : ""}

                  ${isRetracted ? `
                    <span class="text-xs"><i class="fas fa-ban mr-1"></i>Message unsent</span>
                  ` : `
                    ${msg.mediaUrl ? `
                      <a href="${escapeHtml(msg.mediaUrl)}" target="_blank" rel="noopener" class="block mb-2">
                        <img src="${escapeHtml(msg.mediaUrl)}" alt="Attachment" class="rounded-xl max-h-60 object-cover border border-white/15" />
                      </a>
                    ` : ""}
                    ${msg.text ? `<p class="text-sm whitespace-pre-wrap break-words leading-relaxed">${escapeHtml(msg.text)}</p>` : ""}
                    ${msg.isEdited ? `<div class="text-[10px] opacity-70 mt-1 text-right">(edited)</div>` : ""}
                  `}
                </div>

                ${!isRetracted ? `
                  <div class="flex items-center gap-2 mt-1 px-1 opacity-80 sm:opacity-0 group-hover:opacity-100 transition">
                    <button onclick="window.appActions.setChatReply('${escapeHtml(msg.id)}')" class="text-[11px] text-slate-400 hover:text-purple-300">
                      <i class="fas fa-reply mr-0.5"></i> Reply
                    </button>
                    ${isMe && msg.text ? `
                      <button onclick="window.appActions.startEditChatMessage('${escapeHtml(msg.id)}')" class="text-[11px] text-slate-400 hover:text-amber-300">
                        <i class="fas fa-pen mr-0.5"></i> Edit
                      </button>
                    ` : ""}
                    ${(isMe || isLeader) ? `
                      <button onclick="window.appActions.retractChatMessage('${escapeHtml(msg.id)}')" class="text-[11px] text-slate-400 hover:text-rose-400">
                        <i class="fas fa-trash mr-0.5"></i> Unsend
                      </button>
                    ` : ""}
                  </div>
                ` : ""}
              </div>
            </div>
          `;
        }).join("")}
      </div>

      <!-- Reply or Edit Banner -->
      ${chatReplyTo ? `
        <div class="px-4 py-2 bg-purple-950/70 border-t border-purple-500/30 flex items-center justify-between text-xs">
          <div class="truncate">
            <span class="font-bold text-purple-300"><i class="fas fa-reply mr-1"></i>Replying to ${escapeHtml(chatReplyTo.senderName)}:</span>
            <span class="text-slate-300 ml-1">${escapeHtml(chatReplyTo.text || "[Photo]")}</span>
          </div>
          <button onclick="window.appActions.clearChatReply()" class="text-slate-400 hover:text-white ml-2"><i class="fas fa-xmark"></i></button>
        </div>
      ` : ""}

      ${chatEditingMessage ? `
        <div class="px-4 py-2 bg-amber-950/70 border-t border-amber-500/30 flex items-center justify-between text-xs">
          <div class="truncate">
            <span class="font-bold text-amber-300"><i class="fas fa-pen mr-1"></i>Editing message:</span>
            <span class="text-slate-300 ml-1">${escapeHtml(chatEditingMessage.text)}</span>
          </div>
          <button onclick="window.appActions.clearChatEdit()" class="text-slate-400 hover:text-white ml-2"><i class="fas fa-xmark"></i></button>
        </div>
      ` : ""}

      <!-- Chat Input Bar -->
      <form onsubmit="window.appActions.sendChatMessage(event)" class="p-3 border-t border-white/10 bg-white/[0.02] flex items-center gap-2">
        <label class="p-2.5 rounded-xl bg-white/5 hover:bg-white/10 text-purple-300 cursor-pointer transition shrink-0" title="Send Photo">
          <i class="fas fa-image"></i>
          <input type="file" accept="image/*" class="hidden" onchange="window.appActions.sendChatPhoto(this.files[0]); this.value='';" />
        </label>
        <input
          id="chatInputBox"
          type="text"
          value="${escapeHtml(chatEditingMessage ? chatEditingMessage.text : "")}"
          placeholder="Type a message to your team..."
          class="flex-1 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-sm text-white placeholder-slate-400 focus:outline-none focus:border-purple-500"
        />
        <button type="submit" class="brand-gradient hover:opacity-95 px-4 py-2.5 rounded-xl text-white font-semibold text-sm shadow-lg shrink-0 transition">
          <i class="fas fa-paper-plane"></i>
        </button>
      </form>
    </div>
  `;
}

export function renderProfileView(state) {
  const { profile, team } = state;
  const isPrimaryLeader = isWorshipLeader(profile?.ministryRole, profile?.role);

  return `
    <div class="max-w-2xl mx-auto space-y-6">
      <div class="glass-panel rounded-2xl p-6 space-y-6">
        <div class="flex flex-col sm:flex-row items-center gap-5 pb-6 border-b border-white/10">
          <div class="relative">
            ${getAvatarHtml(profile?.fullName || profile?.username, profile?.photoUri, "w-20 h-20 text-2xl")}
            <label class="absolute -bottom-1 -right-1 w-8 h-8 rounded-full brand-gradient text-white flex items-center justify-center cursor-pointer shadow-lg border border-white/20" title="Change Profile Photo">
              <i class="fas fa-camera text-xs"></i>
              <input type="file" accept="image/*" class="hidden" onchange="window.appActions.uploadProfileAvatar(this.files[0])" />
            </label>
          </div>
          <div class="text-center sm:text-left">
            <h1 class="text-xl font-extrabold text-white">${escapeHtml(profile?.fullName || profile?.username || "Ministry Member")}</h1>
            <p class="text-xs text-slate-400">${escapeHtml(profile?.email || "")}</p>
            <div class="mt-2 flex flex-wrap justify-center sm:justify-start items-center gap-2">
              ${getRoleBadgeHtml(profile?.ministryRole, profile?.role)}
              ${profile?.teamCode ? `<span class="px-2.5 py-0.5 rounded-full text-xs bg-white/5 border border-white/10 text-slate-300">Team: ${escapeHtml(team?.teamName || profile?.teamName)} (${escapeHtml(profile.teamCode)})</span>` : ""}
            </div>
          </div>
        </div>

        <form onsubmit="window.appActions.saveProfileChanges(event)" class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Full Name</label>
              <input type="text" name="fullName" value="${escapeHtml(profile?.fullName || "")}" maxlength="50" required class="w-full px-3.5 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white focus:outline-none focus:border-purple-500" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Display Username</label>
              <input type="text" name="username" value="${escapeHtml(profile?.username || "")}" maxlength="30" required class="w-full px-3.5 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white focus:outline-none focus:border-purple-500" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Phone Number (PH Format)</label>
              <input type="text" name="phone" value="${escapeHtml(profile?.phone || "")}" placeholder="09XXXXXXXXX or +639XXXXXXXXX" maxlength="13" class="w-full px-3.5 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white focus:outline-none focus:border-purple-500" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Ministry Role</label>
              <input type="text" value="${escapeHtml(profile?.ministryRole || profile?.role || "Member")}" disabled class="w-full px-3.5 py-2.5 rounded-xl bg-white/[0.02] border border-white/10 text-sm text-slate-400 cursor-not-allowed" />
            </div>
          </div>

          <div class="pt-2 flex flex-wrap items-center justify-between gap-3">
            <button type="submit" class="brand-gradient hover:opacity-95 px-5 py-2.5 rounded-xl text-xs font-semibold text-white shadow-lg transition">
              <i class="fas fa-floppy-disk mr-1.5"></i> Save Profile Changes
            </button>

            <div class="flex items-center gap-2">
              <button type="button" onclick="window.appActions.leaveCurrentTeam()" class="px-4 py-2.5 rounded-xl bg-rose-500/15 hover:bg-rose-500/25 border border-rose-500/30 text-xs font-semibold text-rose-300 transition">
                <i class="fas fa-person-walking-arrow-right mr-1"></i> Leave Team
              </button>
              <button type="button" onclick="window.appActions.signOutUser()" class="px-4 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/15 text-xs font-semibold text-slate-200 transition">
                <i class="fas fa-right-from-bracket mr-1"></i> Sign Out
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  `;
}
