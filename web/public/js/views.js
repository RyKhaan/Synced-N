import {
  escapeHtml,
  isWorshipLeader,
  isSubstituteLeader,
  hasLeaderPrivileges,
  resolveSongCoverUrl,
  formatBpmDisplay,
  formatFriendlyDate,
  formatFriendlyTime,
  formatRelativeTime
} from "./utils.js";
import { DEFAULT_MINISTRY_ROLES } from "./config.js";

export function getAvatarHtml(user, sizeClass = "w-9 h-9", textSize = "text-xs") {
  const name = user?.fullName || user?.name || user?.username || "Member";
  const photo = user?.photoUri || user?.profilePictureUrl || "";
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join("") || "M";

  if (photo && photo.trim()) {
    return `
      <img
        src="${escapeHtml(photo)}"
        alt="${escapeHtml(name)}"
        onerror="this.onerror=null;this.src='/assets/logo.png';"
        class="${sizeClass} rounded-full object-cover border border-slate-200 shrink-0 bg-slate-100"
      />
    `;
  }
  return `
    <div class="${sizeClass} rounded-full bg-[#F5D0FE]/70 border border-[#E879F9] flex items-center justify-center font-bold ${textSize} text-[#500568] shrink-0">
      ${escapeHtml(initials)}
    </div>
  `;
}

export function getRoleBadgeHtml(user) {
  if (!user) return "";
  const ministryRole = user.ministryRole || "Member";
  const authRole = user.role || "Member";

  if (isWorshipLeader(ministryRole, authRole)) {
    return `<span class="badge badge-purple"><i class="fa-solid fa-shield-halved text-[10px]"></i> Worship Leader</span>`;
  }
  if (isSubstituteLeader(ministryRole)) {
    return `<span class="badge badge-amber"><i class="fa-solid fa-user-check text-[10px]"></i> Substitute Leader</span>`;
  }
  return `<span class="badge badge-neutral">${escapeHtml(ministryRole)}</span>`;
}

export function getSongCoverHtml(song, sizeClass = "w-24 h-14", iconSize = "text-xs") {
  const title = song?.title || "Worship Song";
  const coverUrl = resolveSongCoverUrl(song);
  const fallbackSrc = "/assets/worship_hero_bg.png";
  const imgSrc = coverUrl || fallbackSrc;

  return `
    <div class="song-cover-thumb ${sizeClass} relative group/cover">
      <img
        src="${escapeHtml(imgSrc)}"
        alt="${escapeHtml(title)} cover"
        loading="lazy"
        onerror="this.onerror=null;this.src='${fallbackSrc}';"
      />
      <div class="absolute inset-0 bg-gradient-to-t from-[#080720]/70 via-transparent to-transparent flex items-end justify-between p-1.5 pointer-events-none">
        <span class="w-5 h-5 rounded-md bg-[#500568]/90 text-white flex items-center justify-center ${iconSize} shadow-xs">
          <i class="fa-solid fa-music text-[9px]"></i>
        </span>
        ${song?.key ? `
          <span class="px-1.5 py-0.5 rounded bg-black/65 text-white font-mono text-[9px] font-bold leading-none">
            ${escapeHtml(song.key)}
          </span>
        ` : ""}
      </div>
    </div>
  `;
}

export function renderPasswordInputHtml({
  id,
  placeholder = "••••••••",
  required = true,
  value = "",
  autocomplete = "current-password",
  ariaLabel = "Toggle password visibility",
  onInput = "",
  maxlength = 128
}) {
  return `
    <div class="password-input-wrapper">
      <input
        id="${escapeHtml(id)}"
        type="password"
        ${required ? "required" : ""}
        maxlength="${Number(maxlength) || 128}"
        value="${escapeHtml(value)}"
        placeholder="${escapeHtml(placeholder)}"
        autocomplete="${escapeHtml(autocomplete)}"
        ${onInput ? `oninput="${escapeHtml(onInput)}"` : ""}
        class="form-input"
      />
      <button
        type="button"
        onclick="window.appActions.togglePasswordVisibility('${escapeHtml(id)}', this)"
        class="password-toggle-btn"
        aria-label="${escapeHtml(ariaLabel)}"
        aria-pressed="false"
        title="Show password"
      >
        <i class="fa-regular fa-eye text-xs pointer-events-none"></i>
      </button>
    </div>
  `;
}

export function renderPasswordStrengthBoxHtml(inputId) {
  const safeId = escapeHtml(inputId);
  return `
    <div id="${safeId}-strength-box" class="mt-2 p-3 rounded-xl bg-[#F5F5F8] border border-slate-200/90 space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-[11px] font-bold text-[#10113F]">Password Requirements</span>
        <span id="${safeId}-strength-badge" class="px-2 py-0.5 rounded-md text-[10px] font-bold bg-slate-200/80 text-slate-600">
          Min. 8 chars
        </span>
      </div>
      <div class="grid grid-cols-4 gap-1.5">
        <div id="${safeId}-bar-1" class="h-1.5 rounded-full bg-slate-200 transition-colors"></div>
        <div id="${safeId}-bar-2" class="h-1.5 rounded-full bg-slate-200 transition-colors"></div>
        <div id="${safeId}-bar-3" class="h-1.5 rounded-full bg-slate-200 transition-colors"></div>
        <div id="${safeId}-bar-4" class="h-1.5 rounded-full bg-slate-200 transition-colors"></div>
      </div>
      <div class="grid grid-cols-2 gap-x-3 gap-y-1 pt-0.5 text-[11px] font-medium text-slate-500">
        <div id="${safeId}-req-len" class="flex items-center gap-1.5">
          <i class="fa-regular fa-circle text-[9px]"></i>
          <span>8+ characters</span>
        </div>
        <div id="${safeId}-req-case" class="flex items-center gap-1.5">
          <i class="fa-regular fa-circle text-[9px]"></i>
          <span>Upper &amp; lower (Aa)</span>
        </div>
        <div id="${safeId}-req-num" class="flex items-center gap-1.5">
          <i class="fa-regular fa-circle text-[9px]"></i>
          <span>Number (0-9)</span>
        </div>
        <div id="${safeId}-req-spec" class="flex items-center gap-1.5">
          <i class="fa-regular fa-circle text-[9px]"></i>
          <span>Special (!@#$)</span>
        </div>
      </div>
    </div>
  `;
}

export function renderHomeView(state) {
  const { currentUser, currentTeam, teamMembers, services, announcements, songs, substitutions, rejoinRequests } = state;
  const isLeader = hasLeaderPrivileges(currentUser);
  const myAssignedServices = services.filter((s) =>
    (s.assignments || []).some((m) => m.uid === currentUser.uid)
  );
  const pendingSubs = substitutions.filter((s) => s.status === "Pending" || s.status === "Substitute Accepted");
  const pendingRejoins = rejoinRequests.filter((r) => r.status === "Pending");
  const urgentAnnouncements = announcements.filter((a) => a.urgent);
  const teamDisplayName = currentTeam?.teamName || currentUser?.teamName || "Worship Ministry Team";
  const teamCode = currentTeam?.teamCode || currentUser?.teamCode || "------";
  const nextService = services[0] || null;

  return `
    <div class="space-y-6">
      <!-- Synced N Mobile-Matched Worship Hero Header -->
      <div class="synced-hero-banner p-5 sm:p-7 shadow-md">
        <div class="absolute inset-0 opacity-20 pointer-events-none">
          <img src="/assets/worship_hero_bg.png" alt="" class="w-full h-full object-cover" />
        </div>
        <div class="relative z-10 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-6">
          <div class="space-y-2 max-w-2xl">
            <div class="flex flex-wrap items-center gap-2">
              <span class="px-2.5 py-0.5 rounded-full bg-white/15 text-[#F5D0FE] text-[11px] font-semibold tracking-wide uppercase backdrop-blur-xs">
                Welcome to Ministry
              </span>
              ${getRoleBadgeHtml(currentUser)}
            </div>
            <div class="flex items-center gap-2.5">
              <h1 class="text-2xl sm:text-3xl font-bold text-white tracking-tight">
                ${escapeHtml(teamDisplayName)}
              </h1>
              ${isLeader ? `
                <button onclick="window.appActions.openEditTeamNameModal()" class="w-7 h-7 rounded-lg bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition" title="Rename Team">
                  <i class="fa-solid fa-pen text-xs"></i>
                </button>
              ` : ""}
            </div>
            <p class="text-xs sm:text-sm text-[#B7D1FF] font-medium">
              Together, in Worship, in Sync &middot; Signed in as <span class="text-white font-semibold">${escapeHtml(currentUser.fullName || currentUser.name || "Member")}</span>
            </p>
          </div>

          <div class="flex flex-wrap items-center gap-2.5">
            <!-- Team Invite Code Box -->
            <div class="flex items-center gap-3 bg-[#080720]/60 border border-white/20 rounded-xl px-3.5 py-2 backdrop-blur-xs">
              <div>
                <div class="text-[10px] uppercase tracking-wider text-[#B7D1FF] font-semibold leading-none">Invite Code</div>
                <div class="font-mono text-sm font-bold text-white tracking-widest mt-1">${escapeHtml(teamCode)}</div>
              </div>
              <button onclick="window.appActions.copyTeamCode()" class="btn btn-sm bg-white/15 hover:bg-white/25 text-white border border-white/20" title="Copy Team Code">
                <i class="fa-regular fa-copy text-xs"></i> Copy
              </button>
            </div>

            <button onclick="window.appActions.switchTab('calendar')" class="btn bg-white/15 hover:bg-white/25 text-white border border-white/20">
              <i class="fa-regular fa-calendar-days text-xs"></i> Calendar
            </button>

            ${isLeader ? `
              <button onclick="window.appActions.openServiceModal()" class="btn bg-[#A700D5] hover:bg-[#9E069F] text-white border border-[#E879F9]/40 shadow-sm">
                <i class="fa-solid fa-plus text-xs"></i> Schedule Service
              </button>
            ` : ""}
          </div>
        </div>

        ${nextService ? `
          <!-- Next Upcoming Worship Service Spotlight Bar -->
          <div class="relative z-10 mt-5 pt-4 border-t border-white/15 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div class="flex items-center gap-3">
              <div class="synced-date-badge px-3 py-1.5 text-center shrink-0">
                <div class="text-[10px] uppercase font-bold tracking-wider opacity-90">Next Service</div>
                <div class="text-xs font-bold">${escapeHtml(formatFriendlyDate(nextService.date))}</div>
              </div>
              <div class="min-w-0">
                <div class="text-sm font-bold text-white truncate">${escapeHtml(nextService.title || "Sunday Worship Service")}</div>
                <div class="text-xs text-[#B7D1FF] truncate">
                  ${nextService.start ? `${formatFriendlyTime(nextService.start)}${nextService.end ? ` - ${formatFriendlyTime(nextService.end)}` : ""}` : "Time TBD"}
                  &middot; ${escapeHtml(nextService.venue || "Main Sanctuary")}
                </div>
              </div>
            </div>
            <button onclick="window.appActions.switchTab('schedule')" class="btn btn-sm bg-white text-[#10113F] hover:bg-[#F5D0FE] self-start sm:self-auto shrink-0">
              <span>Open Service Lineup</span>
              <i class="fa-solid fa-chevron-right text-[10px]"></i>
            </button>
          </div>
        ` : ""}
      </div>

      <!-- Urgent Announcement Banner (Matching Mobile urgentAlertBanner) -->
      ${urgentAnnouncements.length > 0 ? `
        <div class="surface-card border-rose-300 bg-[#FFFAFA] p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div class="flex items-start gap-3">
            <div class="w-8 h-8 rounded-lg bg-rose-100 text-rose-600 flex items-center justify-center shrink-0 mt-0.5">
              <i class="fa-solid fa-triangle-exclamation text-sm"></i>
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="badge badge-rose">Urgent Notice</span>
                <span class="text-xs font-bold text-[#10113F]">${escapeHtml(urgentAnnouncements[0].title)}</span>
              </div>
              <p class="text-xs text-slate-600 mt-1 line-clamp-2">${escapeHtml(urgentAnnouncements[0].message)}</p>
            </div>
          </div>
          <span class="text-[11px] text-slate-400 shrink-0">${formatRelativeTime(urgentAnnouncements[0].createdAt)}</span>
        </div>
      ` : ""}

      <!-- Ministry Hub Summary Cards -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        <button onclick="window.appActions.switchTab('schedule')" class="surface-card surface-card-interactive p-4 text-left flex items-center justify-between">
          <div>
            <div class="text-xs font-medium text-slate-500">Upcoming Services</div>
            <div class="text-2xl font-bold text-[#10113F] mt-1">${services.length}</div>
            <div class="text-[11px] text-[#500568] font-medium mt-0.5">${myAssignedServices.length} assigned to you</div>
          </div>
          <div class="w-10 h-10 rounded-xl bg-[#F5D0FE]/60 text-[#500568] flex items-center justify-center text-sm shrink-0">
            <i class="fa-regular fa-calendar-check"></i>
          </div>
        </button>

        <button onclick="window.appActions.switchTab('calendar')" class="surface-card surface-card-interactive p-4 text-left flex items-center justify-between">
          <div>
            <div class="text-xs font-medium text-slate-500">Ministry Calendar</div>
            <div class="text-2xl font-bold text-[#500568] mt-1">Month</div>
            <div class="text-[11px] text-slate-500 mt-0.5">Services & availability</div>
          </div>
          <div class="w-10 h-10 rounded-xl bg-[#F5D0FE]/60 text-[#500568] flex items-center justify-center text-sm shrink-0">
            <i class="fa-solid fa-calendar-days"></i>
          </div>
        </button>

        <button onclick="window.appActions.switchTab('songs')" class="surface-card surface-card-interactive p-4 text-left flex items-center justify-between">
          <div>
            <div class="text-xs font-medium text-slate-500">Song Library</div>
            <div class="text-2xl font-bold text-[#10113F] mt-1">${songs.length}</div>
            <div class="text-[11px] text-slate-500 mt-0.5">Chords & transpose</div>
          </div>
          <div class="w-10 h-10 rounded-xl bg-[#E0E7FF] text-[#10113F] flex items-center justify-center text-sm shrink-0">
            <i class="fa-solid fa-music"></i>
          </div>
        </button>

        <button onclick="window.appActions.switchTab('roster')" class="surface-card surface-card-interactive p-4 text-left flex items-center justify-between">
          <div>
            <div class="text-xs font-medium text-slate-500">Worship Team</div>
            <div class="text-2xl font-bold text-[#10113F] mt-1">${teamMembers.length}</div>
            <div class="text-[11px] text-slate-500 mt-0.5">Active roster</div>
          </div>
          <div class="w-10 h-10 rounded-xl bg-[#E0E7FF] text-[#10113F] flex items-center justify-center text-sm shrink-0">
            <i class="fa-solid fa-users"></i>
          </div>
        </button>
      </div>

      <!-- Main Two-Column Content Grid -->
      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <!-- Left Column (7 cols): Upcoming Worship Services & Lineups -->
        <div class="lg:col-span-7 space-y-4">
          <div class="flex items-center justify-between">
            <div>
              <h2 class="text-sm font-bold text-[#10113F]">Upcoming Worship Services & Lineups</h2>
              <p class="text-xs text-slate-500">Scheduled services, rehearsals, assigned positions, and song setlists</p>
            </div>
            <button onclick="window.appActions.switchTab('schedule')" class="btn btn-ghost btn-sm text-[#500568] font-semibold">
              View All (${services.length}) <i class="fa-solid fa-arrow-right text-[10px]"></i>
            </button>
          </div>

          ${services.length === 0 ? `
            <div class="surface-card p-8 text-center">
              <div class="w-11 h-11 rounded-xl bg-[#F5F5F8] border border-slate-200 flex items-center justify-center mx-auto mb-3 text-[#500568]">
                <i class="fa-regular fa-calendar"></i>
              </div>
              <h3 class="text-sm font-bold text-[#10113F]">No services scheduled yet</h3>
              <p class="text-xs text-slate-500 mt-1 max-w-sm mx-auto">Create a worship service schedule to assign team members, set rehearsal times, and organize the song lineup.</p>
              ${isLeader ? `
                <button onclick="window.appActions.openServiceModal()" class="mt-4 btn btn-primary btn-sm">
                  <i class="fa-solid fa-plus text-[10px]"></i> Create First Service
                </button>
              ` : ""}
            </div>
          ` : `
            <div class="space-y-3.5">
              ${services.slice(0, 3).map((srv) => renderServiceCardHtml(srv, state, isLeader)).join("")}
            </div>
          `}
        </div>

        <!-- Right Column (5 cols): Dashboard Calendar Widget, Action Items & Announcements -->
        <div class="lg:col-span-5 space-y-6">
          <!-- Integrated Dashboard Calendar Widget (Accessible directly from Home/Dashboard) -->
          ${renderDashboardCalendarWidget(state)}

          <!-- Pending Requests Alert Card (Leaders Only) -->
          ${isLeader && (pendingSubs.length > 0 || pendingRejoins.length > 0) ? `
            <div class="surface-card overflow-hidden border-amber-300">
              <div class="px-4 py-3 bg-amber-50 border-b border-amber-200 flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <i class="fa-solid fa-circle-exclamation text-amber-600 text-xs"></i>
                  <h3 class="text-xs font-bold text-amber-900 uppercase tracking-wider">Attention Required</h3>
                </div>
                <span class="badge badge-amber">${pendingSubs.length + pendingRejoins.length} Pending</span>
              </div>
              <div class="divide-y divide-slate-100">
                ${pendingSubs.map((sub) => `
                  <div class="p-3.5 flex flex-col gap-2">
                    <div class="flex items-start justify-between gap-2">
                      <div>
                        <span class="text-xs font-bold text-[#10113F]">${escapeHtml(sub.requesterName || "Member")}</span>
                        <span class="text-xs text-slate-500"> requested a substitute (${escapeHtml(sub.role || "Member")}) for </span>
                        <span class="text-xs font-semibold text-[#500568]">${escapeHtml(sub.serviceTitle || "Service")}</span>
                      </div>
                      <span class="badge badge-amber">Sub Request</span>
                    </div>
                    <p class="text-xs text-slate-600 bg-slate-50 px-2.5 py-1.5 rounded-lg border border-slate-200">
                      "${escapeHtml(sub.reason || "Schedule conflict")}"
                      ${sub.substituteName ? ` &middot; Proposed Sub: <strong class="text-[#10113F]">${escapeHtml(sub.substituteName)}</strong>` : ""}
                    </p>
                    <div class="flex items-center gap-2 pt-0.5">
                      <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Approved')" class="btn btn-primary btn-sm">
                        <i class="fa-solid fa-check text-[10px]"></i> Approve
                      </button>
                      <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Declined')" class="btn btn-danger btn-sm">
                        Decline
                      </button>
                    </div>
                  </div>
                `).join("")}

                ${pendingRejoins.map((req) => `
                  <div class="p-3.5 flex items-center justify-between gap-3">
                    <div>
                      <div class="text-xs font-bold text-[#10113F]">${escapeHtml(req.userName || "Former Member")}</div>
                      <div class="text-[11px] text-slate-500">Requested to rejoin the team</div>
                    </div>
                    <div class="flex items-center gap-1.5">
                      <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', true)" class="btn btn-primary btn-sm">Approve</button>
                      <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', false)" class="btn btn-danger btn-sm">Decline</button>
                    </div>
                  </div>
                `).join("")}
              </div>
            </div>
          ` : ""}

          <!-- Announcements Section -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <div>
                <h2 class="text-sm font-bold text-[#10113F]">Ministry Announcements</h2>
                <p class="text-xs text-slate-500">Updates and rehearsal notices for the worship team</p>
              </div>
              ${isLeader ? `
                <button onclick="window.appActions.openAnnouncementModal()" class="btn btn-secondary btn-sm">
                  <i class="fa-solid fa-plus text-[10px]"></i> Post Update
                </button>
              ` : ""}
            </div>

            ${announcements.length === 0 ? `
              <div class="surface-card p-6 text-center">
                <p class="text-xs text-slate-500">No announcements posted yet.</p>
              </div>
            ` : `
              <div class="surface-card divide-y divide-slate-100">
                ${announcements.map((ann) => `
                  <div class="p-4 space-y-1.5">
                    <div class="flex items-start justify-between gap-2">
                      <div class="flex items-center gap-2">
                        ${ann.urgent ? `<span class="badge badge-rose">Urgent</span>` : ""}
                        <h3 class="text-sm font-bold text-[#10113F]">${escapeHtml(ann.title || "Announcement")}</h3>
                      </div>
                      <div class="flex items-center gap-2 shrink-0">
                        <span class="text-[11px] text-slate-400">${formatRelativeTime(ann.createdAt)}</span>
                        ${isLeader ? `
                          <button onclick="window.appActions.deleteAnnouncement('${escapeHtml(ann.id)}')" class="text-slate-400 hover:text-rose-600 text-xs p-1" title="Delete Announcement">
                            <i class="fa-regular fa-trash-can"></i>
                          </button>
                        ` : ""}
                      </div>
                    </div>
                    <p class="text-xs text-slate-600 leading-relaxed whitespace-pre-line">${escapeHtml(ann.message || "")}</p>
                    <div class="pt-1 text-[11px] text-slate-400">
                      Posted by <span class="font-semibold text-[#500568]">${escapeHtml(ann.author || "Leader")}</span>
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

/**
 * Integrated Dashboard Calendar Widget (Accessible directly from Dashboard / Home)
 * Displays monthly calendar with service/rehearsal/unavailability dots, selected date details,
 * upcoming rehearsals overview, and a button to open the full Calendar page.
 */
export function renderDashboardCalendarWidget(state) {
  const { currentUser, services, calendar } = state;
  const isLeader = hasLeaderPrivileges(currentUser);
  const now = new Date();
  const todayIso = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;

  const year = calendar?.year ?? now.getFullYear();
  const month = calendar?.month ?? now.getMonth();
  const selectedDate = calendar?.selectedDate || todayIso;

  const firstOfMonth = new Date(year, month, 1);
  const monthLabel = firstOfMonth.toLocaleDateString("en-US", { month: "long" });
  const startWeekday = firstOfMonth.getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const prevMonthDays = new Date(year, month, 0).getDate();
  const totalCells = Math.ceil((startWeekday + daysInMonth) / 7) * 7;
  const myRanges = currentUser?.unavailableRanges || [];

  function getMiniEvents(isoKey) {
    const dayServices = services.filter((s) => s.date === isoKey);
    const dayRehearsals = services.filter((s) => (s.rehearsal?.date || s.rehearsalDate || "") === isoKey);
    const myUnavail = myRanges.filter((r) => isoKey >= r.startDate && isoKey <= r.endDate);
    return { dayServices, dayRehearsals, myUnavail };
  }

  const miniCells = [];
  for (let i = 0; i < totalCells; i++) {
    const dayNum = i - startWeekday + 1;
    if (dayNum < 1) {
      miniCells.push(`
        <div class="calendar-day-cell compact-cell outside">
          <span class="text-[11px] font-medium text-slate-400">${prevMonthDays + dayNum}</span>
        </div>
      `);
    } else if (dayNum > daysInMonth) {
      miniCells.push(`
        <div class="calendar-day-cell compact-cell outside">
          <span class="text-[11px] font-medium text-slate-400">${dayNum - daysInMonth}</span>
        </div>
      `);
    } else {
      const isoKey = `${year}-${String(month + 1).padStart(2, "0")}-${String(dayNum).padStart(2, "0")}`;
      const isSelected = isoKey === selectedDate;
      const isToday = isoKey === todayIso;
      const { dayServices, dayRehearsals, myUnavail } = getMiniEvents(isoKey);
      const hasService = dayServices.length > 0;
      const hasRehearsal = dayRehearsals.length > 0;
      const hasUnavail = myUnavail.length > 0;

      miniCells.push(`
        <button
          type="button"
          onclick="window.appActions.selectCalendarDate('${isoKey}')"
          class="calendar-day-cell compact-cell ${isSelected ? "selected" : ""} ${isToday ? "today" : ""}"
          aria-label="${escapeHtml(formatFriendlyDate(isoKey))}"
        >
          <span class="text-xs font-bold ${
            isSelected ? "text-white" : hasUnavail ? "text-rose-600" : "text-[#10113F]"
          }">${dayNum}</span>
          <div class="flex items-center justify-center gap-0.5 mt-0.5 min-h-[6px]">
            ${hasService ? `<span class="w-1.5 h-1.5 rounded-full ${isSelected ? "bg-emerald-300" : "bg-[#22C55E]"}"></span>` : ""}
            ${hasRehearsal ? `<span class="w-1.5 h-1.5 rounded-full ${isSelected ? "bg-sky-300" : "bg-[#3B82F6]"}"></span>` : ""}
            ${hasUnavail ? `<span class="w-1.5 h-1.5 rounded-full ${isSelected ? "bg-rose-300" : "bg-[#EF4444]"}"></span>` : ""}
          </div>
        </button>
      `);
    }
  }

  const selEvents = getMiniEvents(selectedDate);
  const upcomingRehearsals = services.filter((s) => (s.rehearsal?.date || "") >= todayIso).slice(0, 2);
  const weekdays = ["S", "M", "T", "W", "T", "F", "S"];

  return `
    <div class="surface-card p-4 sm:p-5 space-y-3.5" id="dashboard-calendar-widget">
      <!-- Widget Header -->
      <div class="flex items-center justify-between gap-2 pb-2.5 border-b border-slate-200">
        <div class="flex items-center gap-2">
          <div class="w-8 h-8 rounded-lg bg-[#F5D0FE]/70 text-[#500568] flex items-center justify-center text-xs shrink-0">
            <i class="fa-solid fa-calendar-days"></i>
          </div>
          <div>
            <h2 class="text-sm font-bold text-[#10113F]">Ministry Calendar</h2>
            <p class="text-[11px] text-slate-500">Services, rehearsals & availability</p>
          </div>
        </div>
        <button onclick="window.appActions.switchTab('calendar')" class="btn btn-secondary btn-sm">
          <span>Open Full Calendar</span>
          <i class="fa-solid fa-expand text-[10px]"></i>
        </button>
      </div>

      <!-- Month Navigation -->
      <div class="flex items-center justify-between gap-2">
        <button
          type="button"
          onclick="window.appActions.changeCalendarMonth(-1)"
          class="w-8 h-8 rounded-lg bg-[#F5F5F8] hover:bg-[#F5D0FE]/60 text-[#10113F] flex items-center justify-center font-bold transition"
          aria-label="Previous month"
        >
          &lsaquo;
        </button>
        <div class="text-center">
          <span class="text-sm font-extrabold text-[#10113F]">${escapeHtml(monthLabel)} ${year}</span>
          ${selectedDate !== todayIso ? `
            <button onclick="window.appActions.jumpCalendarToday()" class="ml-2 text-[11px] font-semibold text-[#500568] hover:underline">
              Today
            </button>
          ` : ""}
        </div>
        <button
          type="button"
          onclick="window.appActions.changeCalendarMonth(1)"
          class="w-8 h-8 rounded-lg bg-[#F5F5F8] hover:bg-[#F5D0FE]/60 text-[#10113F] flex items-center justify-center font-bold transition"
          aria-label="Next month"
        >
          &rsaquo;
        </button>
      </div>

      <!-- Weekday Headers -->
      <div class="grid grid-cols-7 gap-1 text-center">
        ${weekdays.map((d, idx) => `
          <div class="text-[10px] font-bold uppercase ${idx === 0 || idx === 6 ? "text-[#D946EF]" : "text-slate-500"}">${d}</div>
        `).join("")}
      </div>

      <!-- Compact Calendar Grid -->
      <div class="calendar-grid">
        ${miniCells.join("")}
      </div>

      <!-- Legend -->
      <div class="flex flex-wrap items-center justify-between gap-2 pt-1 text-[11px] text-slate-500">
        <div class="flex items-center gap-3">
          <span class="inline-flex items-center gap-1"><span class="w-2 h-2 rounded-full bg-[#22C55E]"></span> Service</span>
          <span class="inline-flex items-center gap-1"><span class="w-2 h-2 rounded-full bg-[#3B82F6]"></span> Rehearsal</span>
          <span class="inline-flex items-center gap-1"><span class="w-2 h-2 rounded-full bg-[#EF4444]"></span> Unavailable</span>
        </div>
      </div>

      <!-- Selected Date Event Summary -->
      <div class="p-3 rounded-xl bg-[#F8FAFC] border border-slate-200 space-y-2">
        <div class="flex items-center justify-between gap-2">
          <span class="text-xs font-bold text-[#10113F]">${escapeHtml(formatFriendlyDate(selectedDate))}</span>
          <div class="flex items-center gap-1.5">
            <button onclick="window.appActions.openAvailabilityModal('${escapeHtml(selectedDate)}')" class="text-[11px] font-semibold text-[#500568] hover:underline">
              Set Unavailability
            </button>
          </div>
        </div>

        ${selEvents.dayServices.length === 0 && selEvents.dayRehearsals.length === 0 && selEvents.myUnavail.length === 0 ? `
          <p class="text-xs text-slate-500">No services or rehearsals on this date.</p>
        ` : `
          <div class="space-y-1.5">
            ${selEvents.dayServices.map((srv) => `
              <div class="flex items-center justify-between gap-2 text-xs bg-emerald-50 border border-emerald-200 rounded-lg px-2.5 py-1.5">
                <div class="truncate">
                  <span class="font-bold text-emerald-800"><i class="fa-solid fa-church text-[10px] mr-1"></i>${escapeHtml(srv.title || "Worship Service")}</span>
                  <span class="text-slate-600 ml-1">${srv.start ? formatFriendlyTime(srv.start) : ""}</span>
                </div>
                <button onclick="window.appActions.switchTab('calendar')" class="text-[11px] font-semibold text-[#500568] shrink-0">Details</button>
              </div>
            `).join("")}
            ${selEvents.dayRehearsals.map((srv) => `
              <div class="flex items-center justify-between gap-2 text-xs bg-blue-50 border border-blue-200 rounded-lg px-2.5 py-1.5">
                <div class="truncate">
                  <span class="font-bold text-blue-800"><i class="fa-solid fa-drum text-[10px] mr-1"></i>Rehearsal: ${escapeHtml(srv.title || "Service")}</span>
                  <span class="text-slate-600 ml-1">${srv.rehearsal?.start ? formatFriendlyTime(srv.rehearsal.start) : ""}</span>
                </div>
                <button onclick="window.appActions.switchTab('calendar')" class="text-[11px] font-semibold text-[#500568] shrink-0">Details</button>
              </div>
            `).join("")}
            ${selEvents.myUnavail.map((r) => `
              <div class="flex items-center justify-between gap-2 text-xs bg-rose-50 border border-rose-200 rounded-lg px-2.5 py-1.5">
                <span class="font-semibold text-rose-800 truncate"><i class="fa-regular fa-calendar-xmark mr-1"></i>Unavailable: ${escapeHtml(r.reason || "Personal")}</span>
                <button onclick="window.appActions.removeUnavailableRange('${escapeHtml(r.id)}', false)" class="text-[11px] font-bold text-rose-700 hover:underline shrink-0">Clear</button>
              </div>
            `).join("")}
          </div>
        `}

        ${upcomingRehearsals.length > 0 ? `
          <div class="pt-2 border-t border-slate-200/80 space-y-1">
            <div class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Upcoming Rehearsals</div>
            ${upcomingRehearsals.map((srv) => `
              <div class="flex items-center justify-between text-xs text-slate-600">
                <span class="truncate font-medium text-[#10113F]"><i class="fa-solid fa-drum text-blue-600 text-[10px] mr-1"></i>${escapeHtml(srv.title)}</span>
                <span class="text-[11px] shrink-0">${escapeHtml(formatFriendlyDate(srv.rehearsal.date))}</span>
              </div>
            `).join("")}
          </div>
        ` : ""}
      </div>
    </div>
  `;
}

export function renderServiceCardHtml(srv, state, isLeader) {
  const { currentUser } = state;
  const assigned = srv.assignments || [];
  const setlist = srv.songs || [];
  const rehearsal = srv.rehearsal || {};
  const mySlot = assigned.find((m) => m.uid === currentUser.uid);
  const status = srv.status || "Published";

  // Check if any assigned member has an unavailability conflict on srv.date
  const conflicts = [];
  if (srv.date) {
    for (const slot of assigned) {
      const memberObj = (state.teamMembers || []).find((u) => u.uid === slot.uid);
      const ranges = memberObj?.unavailableRanges || [];
      if (ranges.some((r) => srv.date >= r.startDate && srv.date <= r.endDate)) {
        conflicts.push(slot.name || memberObj?.fullName || "Member");
      }
    }
  }

  // Format short badge date like Mobile (e.g. "NOV 8")
  let shortMonth = "SRV";
  let shortDay = "";
  if (srv.date && /^\d{4}-\d{2}-\d{2}$/.test(srv.date)) {
    const [y, m, d] = srv.date.split("-").map(Number);
    const dt = new Date(y, m - 1, d);
    shortMonth = dt.toLocaleDateString("en-US", { month: "short" }).toUpperCase();
    shortDay = String(d);
  }

  return `
    <div class="surface-card overflow-hidden">
      <!-- Top Service Header -->
      <div class="p-4 sm:p-5 border-b border-slate-100 flex flex-col sm:flex-row sm:items-start justify-between gap-3">
        <div class="flex items-start gap-3.5">
          ${srv.coverImageUrl ? `
            <img src="${escapeHtml(srv.coverImageUrl)}" alt="${escapeHtml(srv.title)}" onerror="this.onerror=null;this.src='/assets/worship_hero_bg.png';" class="w-14 h-14 rounded-xl object-cover border border-slate-200 shrink-0" />
          ` : `
            <div class="synced-date-badge w-14 h-14 flex flex-col items-center justify-center shrink-0 shadow-xs">
              <span class="text-[10px] font-bold tracking-wider uppercase leading-none opacity-90">${escapeHtml(shortMonth)}</span>
              <span class="text-lg font-extrabold leading-tight mt-0.5">${escapeHtml(shortDay || "★")}</span>
            </div>
          `}
          <div class="space-y-1">
            <div class="flex flex-wrap items-center gap-1.5">
              <span class="badge ${status === "Draft" ? "badge-amber" : "badge-emerald"}">${escapeHtml(status)}</span>
              ${mySlot ? `
                <span class="badge badge-purple">
                  <i class="fa-solid fa-check text-[9px]"></i> Assigned: ${escapeHtml(mySlot.role)}
                </span>
              ` : ""}
              ${conflicts.length > 0 ? `
                <span class="badge badge-rose" title="Unavailable member assigned: ${escapeHtml(conflicts.join(', '))}">
                  <i class="fa-solid fa-triangle-exclamation text-[9px]"></i> ${conflicts.length} Conflict${conflicts.length > 1 ? "s" : ""}
                </span>
              ` : ""}
            </div>
            <h3 class="text-base font-bold text-[#10113F]">${escapeHtml(srv.title || "Worship Service")}</h3>
            <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600">
              <span><i class="fa-regular fa-calendar mr-1 text-[#500568]"></i>${formatFriendlyDate(srv.date)}</span>
              ${srv.start ? `<span><i class="fa-regular fa-clock mr-1 text-[#500568]"></i>${formatFriendlyTime(srv.start)}${srv.end ? ` - ${formatFriendlyTime(srv.end)}` : ""}</span>` : ""}
              ${srv.venue ? `<span class="font-medium text-[#500568]"><i class="fa-solid fa-location-dot mr-1"></i>${escapeHtml(srv.venue)}</span>` : ""}
              ${rehearsal.date ? `
                <span class="text-blue-700 font-medium">
                  <i class="fa-solid fa-drum mr-1"></i>Rehearsal: ${formatFriendlyDate(rehearsal.date)} ${formatFriendlyTime(rehearsal.start || "")}
                </span>
              ` : ""}
            </div>
          </div>
        </div>

        <div class="flex items-center gap-1.5 shrink-0 self-start">
          ${mySlot ? `
            <button onclick="window.appActions.openSubstitutionModal('${escapeHtml(srv.id)}')" class="btn btn-secondary btn-sm" title="Request a substitute for your slot">
              <i class="fa-solid fa-arrows-rotate text-[11px]"></i> Request Sub
            </button>
          ` : ""}
          ${isLeader ? `
            <button onclick="window.appActions.openServiceModal('${escapeHtml(srv.id)}')" class="btn btn-secondary btn-sm" title="Edit Service & Lineup">
              <i class="fa-solid fa-pen text-[11px]"></i> Edit
            </button>
            <button onclick="window.appActions.deleteService('${escapeHtml(srv.id)}')" class="btn btn-danger btn-sm" title="Delete Service">
              <i class="fa-regular fa-trash-can text-[11px]"></i>
            </button>
          ` : ""}
        </div>
      </div>

      ${rehearsal.notes ? `
        <div class="px-4 sm:px-5 py-2.5 bg-[#F5F5F8] border-b border-slate-100 text-xs text-slate-600">
          <span class="font-bold text-[#10113F] mr-1">Notes:</span> ${escapeHtml(rehearsal.notes)}
        </div>
      ` : ""}

      <!-- Two-Column Body: Lineup Assignments & Song Setlist -->
      <div class="p-4 sm:p-5 grid grid-cols-1 md:grid-cols-2 gap-4 bg-white">
        <!-- Assigned Team Lineup -->
        <div class="border border-slate-200 rounded-xl p-3.5 bg-[#F8FAFC]">
          <div class="flex items-center justify-between mb-2.5">
            <span class="text-[11px] font-bold uppercase tracking-wider text-[#10113F]">
              Team Lineup (${assigned.length})
            </span>
          </div>
          ${assigned.length === 0 ? `
            <p class="text-xs text-slate-400 italic py-2">No members assigned yet.</p>
          ` : `
            <div class="divide-y divide-slate-200/70">
              ${assigned.map((slot) => {
                const isMe = slot.uid === currentUser.uid;
                const memberObj = (state.teamMembers || []).find((u) => u.uid === slot.uid);
                const isConflict = srv.date && memberObj && Array.isArray(memberObj.unavailableRanges) &&
                  memberObj.unavailableRanges.some((r) => srv.date >= r.startDate && srv.date <= r.endDate);
                return `
                  <div class="py-1.5 flex items-center justify-between gap-2 text-xs">
                    <span class="text-slate-500 font-medium">${escapeHtml(slot.role || "Member")}</span>
                    <div class="flex items-center gap-1.5">
                      ${isConflict ? `<i class="fa-solid fa-triangle-exclamation text-rose-600 text-[11px]" title="Marked unavailable on this date"></i>` : ""}
                      <span class="font-semibold ${isMe ? "text-[#500568]" : "text-[#10113F]"}">
                        ${escapeHtml(slot.name || "Unassigned")}${isMe ? " (You)" : ""}
                      </span>
                    </div>
                  </div>
                `;
              }).join("")}
            </div>
          `}
        </div>

        <!-- Worship Song Setlist -->
        <div class="border border-slate-200 rounded-xl p-3.5 bg-[#F8FAFC]">
          <div class="flex items-center justify-between mb-2.5">
            <span class="text-[11px] font-bold uppercase tracking-wider text-[#10113F]">
              Song Lineup (${setlist.length})
            </span>
            ${isLeader ? `
              <button onclick="window.appActions.openAddSongToServiceModal('${escapeHtml(srv.id)}')" class="text-[11px] font-bold text-[#500568] hover:underline flex items-center gap-1" title="Add Songs to Setlist">
                <i class="fa-solid fa-plus text-[9px]"></i> Add Song
              </button>
            ` : ""}
          </div>
          ${setlist.length === 0 ? `
            <p class="text-xs text-slate-400 italic py-2">No songs added to setlist yet.</p>
          ` : `
            <div class="divide-y divide-slate-200/70">
              ${setlist.map((songItem, idx) => {
                const songTitle = typeof songItem === "string" ? songItem : (songItem.title || "Untitled");
                const matchedSong = (state.songs || []).find(
                  (s) => (s.title || "").trim().toLowerCase() === String(songTitle).trim().toLowerCase()
                );
                const displayKey = matchedSong?.key || songItem?.key || "";
                const songForThumb = matchedSong || { title: songTitle, key: displayKey };
                return `
                  <button onclick="window.appActions.openSongByTitle('${escapeHtml(songTitle)}')"
                    class="w-full text-left py-2 flex items-center justify-between gap-2.5 text-xs hover:bg-[#FAF5FF] px-1.5 -mx-1.5 rounded-lg transition group">
                    <div class="flex items-center gap-2.5 min-w-0">
                      ${getSongCoverHtml(songForThumb, "w-11 h-7", "hidden")}
                      <span class="truncate font-semibold text-[#10113F] group-hover:text-[#500568]">
                        <span class="text-slate-400 font-mono mr-1">${idx + 1}.</span>${escapeHtml(songTitle)}
                      </span>
                    </div>
                    <div class="flex items-center gap-1.5 shrink-0">
                      ${displayKey ? `<span class="badge badge-purple font-mono text-[10px]">${escapeHtml(displayKey)}</span>` : ""}
                      <i class="fa-solid fa-arrow-up-right-from-square text-[10px] text-slate-400 group-hover:text-[#500568]"></i>
                    </div>
                  </button>
                `;
              }).join("")}
            </div>
          `}
        </div>
      </div>
    </div>
  `;
}

// ============================================================================
// Calendar View (Matching Synced N Mobile calendarView & availabilityView)
// ============================================================================
export function renderCalendarView(state) {
  const { currentUser, services, teamMembers, calendar } = state;
  const isLeader = hasLeaderPrivileges(currentUser);

  const now = new Date();
  const todayIso = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;

  const year = calendar?.year ?? now.getFullYear();
  const month = calendar?.month ?? now.getMonth(); // 0-indexed (0 = Jan)
  const selectedDate = calendar?.selectedDate || todayIso;

  const firstOfMonth = new Date(year, month, 1);
  const monthLabel = firstOfMonth.toLocaleDateString("en-US", { month: "long" });
  const startWeekday = firstOfMonth.getDay(); // 0 (Sun) to 6 (Sat)
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const prevMonthDays = new Date(year, month, 0).getDate();
  const totalCells = Math.ceil((startWeekday + daysInMonth) / 7) * 7;

  const myRanges = currentUser?.unavailableRanges || [];

  // Helper to inspect events on any YYYY-MM-DD key
  function getEventsForDate(isoKey) {
    const dayServices = services.filter((s) => s.date === isoKey);
    const dayRehearsals = services.filter((s) => {
      const rehDate = s.rehearsal?.date || s.rehearsalDate || "";
      return rehDate === isoKey;
    });
    const myUnavail = myRanges.filter((r) => isoKey >= r.startDate && isoKey <= r.endDate);
    const teamUnavail = [];
    for (const m of teamMembers) {
      const hit = (m.unavailableRanges || []).find((r) => isoKey >= r.startDate && isoKey <= r.endDate);
      if (hit) {
        teamUnavail.push({
          uid: m.uid,
          name: m.fullName || m.name || "Member",
          role: m.ministryRole || "Member",
          range: hit
        });
      }
    }
    return { dayServices, dayRehearsals, myUnavail, teamUnavail };
  }

  // Build calendar cells
  const cellsHtml = [];
  for (let i = 0; i < totalCells; i++) {
    const dayNum = i - startWeekday + 1;
    if (dayNum < 1) {
      const priorDay = prevMonthDays + dayNum;
      cellsHtml.push(`
        <div class="calendar-day-cell outside">
          <span class="text-xs font-medium text-slate-400">${priorDay}</span>
        </div>
      `);
    } else if (dayNum > daysInMonth) {
      const nextDay = dayNum - daysInMonth;
      cellsHtml.push(`
        <div class="calendar-day-cell outside">
          <span class="text-xs font-medium text-slate-400">${nextDay}</span>
        </div>
      `);
    } else {
      const isoKey = `${year}-${String(month + 1).padStart(2, "0")}-${String(dayNum).padStart(2, "0")}`;
      const isSelected = isoKey === selectedDate;
      const isToday = isoKey === todayIso;
      const { dayServices, dayRehearsals, myUnavail, teamUnavail } = getEventsForDate(isoKey);
      const hasUnavail = myUnavail.length > 0;
      const hasService = dayServices.length > 0;
      const hasRehearsal = dayRehearsals.length > 0;

      cellsHtml.push(`
        <button
          type="button"
          onclick="window.appActions.selectCalendarDate('${isoKey}')"
          class="calendar-day-cell ${isSelected ? "selected" : ""} ${isToday ? "today" : ""} text-left"
          aria-label="${escapeHtml(formatFriendlyDate(isoKey))}"
        >
          <div class="w-full flex items-center justify-between">
            <span class="text-xs sm:text-sm font-bold ${
              isSelected
                ? "text-white"
                : hasUnavail
                ? "text-rose-600"
                : "text-[#10113F]"
            }">${dayNum}</span>
            ${isToday && !isSelected ? `
              <span class="text-[9px] font-bold uppercase px-1 rounded bg-[#F5D0FE] text-[#500568]">Today</span>
            ` : ""}
          </div>

          <!-- Mobile & Compact Dot Indicators (Matching MemberActivity.kt lines 4223-4229) -->
          <div class="flex items-center justify-center md:justify-start gap-1 mt-1">
            ${hasService ? `<span class="w-2 h-2 rounded-full ${isSelected ? "bg-emerald-300" : "bg-[#22C55E]"}" title="Worship Service"></span>` : ""}
            ${hasRehearsal ? `<span class="w-2 h-2 rounded-full ${isSelected ? "bg-sky-300" : "bg-[#3B82F6]"}" title="Rehearsal"></span>` : ""}
            ${hasUnavail ? `<span class="w-2 h-2 rounded-full ${isSelected ? "bg-rose-300" : "bg-[#EF4444]"}" title="You are Unavailable"></span>` : ""}
            ${!hasUnavail && teamUnavail.length > 0 ? `<span class="w-1.5 h-1.5 rounded-full ${isSelected ? "bg-amber-200" : "bg-amber-500"}" title="${teamUnavail.length} team member(s) unavailable"></span>` : ""}
          </div>

          <!-- Desktop Event Labels -->
          <div class="hidden md:flex flex-col gap-1 mt-1 w-full overflow-hidden">
            ${dayServices.slice(0, 2).map((s) => `
              <div class="truncate text-[10px] font-semibold px-1.5 py-0.5 rounded ${
                isSelected ? "bg-white/20 text-white" : "bg-emerald-50 text-emerald-800 border border-emerald-200"
              }">
                <i class="fa-solid fa-church text-[8px] mr-0.5"></i>${escapeHtml(s.title || "Service")}
              </div>
            `).join("")}
            ${dayRehearsals.slice(0, 1).map((s) => `
              <div class="truncate text-[10px] font-semibold px-1.5 py-0.5 rounded ${
                isSelected ? "bg-white/20 text-white" : "bg-blue-50 text-blue-800 border border-blue-200"
              }">
                <i class="fa-solid fa-drum text-[8px] mr-0.5"></i>Rehearsal
              </div>
            `).join("")}
            ${hasUnavail ? `
              <div class="truncate text-[10px] font-semibold px-1.5 py-0.5 rounded ${
                isSelected ? "bg-rose-400/30 text-white" : "bg-rose-50 text-rose-700 border border-rose-200"
              }">
                Unavailable
              </div>
            ` : ""}
          </div>
        </button>
      `);
    }
  }

  // Selected Date Details
  const selectedEvents = getEventsForDate(selectedDate);
  const isSelectedUnavail = selectedEvents.myUnavail.length > 0;
  const isSelectedService = selectedEvents.dayServices.length > 0;
  const isSelectedRehearsal = selectedEvents.dayRehearsals.length > 0;

  // Match mobile strokeColor & eventTitle hierarchy (MemberActivity.kt lines 4260-4272)
  const statusTitle = isSelectedUnavail
    ? "Unavailable"
    : isSelectedService
    ? "Service Scheduled"
    : isSelectedRehearsal
    ? "Rehearsal Scheduled"
    : "Available";

  const statusBadgeClass = isSelectedUnavail
    ? "badge-rose"
    : isSelectedService
    ? "badge-emerald"
    : isSelectedRehearsal
    ? "badge-blue"
    : "badge-neutral";

  const statusBorderClass = isSelectedUnavail
    ? "border-rose-400"
    : isSelectedService
    ? "border-emerald-400"
    : isSelectedRehearsal
    ? "border-blue-400"
    : "border-slate-200";

  const weekdays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

  return `
    <div class="space-y-6">
      <!-- Page Header -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <div class="flex items-center gap-2">
            <button onclick="window.appActions.switchTab('home')" class="btn btn-ghost btn-sm px-2 text-xs" title="Back to Dashboard">
              <i class="fa-solid fa-arrow-left"></i> Dashboard
            </button>
            <h1 class="text-xl font-bold text-[#10113F]">Ministry Calendar & Availability</h1>
          </div>
          <p class="text-xs text-slate-500 mt-0.5">Monthly schedule of church worship services, band rehearsals, and member availability synced with mobile.</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button onclick="window.appActions.openAvailabilityModal('${escapeHtml(selectedDate)}')" class="btn btn-secondary">
            <i class="fa-regular fa-calendar-xmark text-[#500568]"></i>
            Manage My Unavailability (${myRanges.length})
          </button>
          ${isLeader ? `
            <button onclick="window.appActions.openServiceModal(null, '${escapeHtml(selectedDate)}')" class="btn btn-primary">
              <i class="fa-solid fa-plus text-xs"></i> Schedule Service
            </button>
          ` : ""}
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <!-- Left 7 Cols: Monthly Calendar Grid -->
        <div class="lg:col-span-7 surface-card p-4 sm:p-6 space-y-4">
          <!-- Month Navigation Row (Matching MemberActivity.kt lines 4175-4188) -->
          <div class="flex items-center justify-between gap-2">
            <button
              type="button"
              onclick="window.appActions.changeCalendarMonth(-1)"
              class="w-10 h-10 rounded-xl bg-[#F5F5F8] hover:bg-[#F5D0FE]/60 text-[#10113F] flex items-center justify-center font-bold text-lg transition"
              aria-label="Previous month"
              title="Previous Month"
            >
              &lsaquo;
            </button>

            <div class="text-center">
              <h2 class="text-lg sm:text-xl font-extrabold text-[#10113F] leading-tight">${escapeHtml(monthLabel)}</h2>
              <div class="flex items-center justify-center gap-2 mt-0.5">
                <span class="text-xs font-medium text-slate-500">${year}</span>
                ${(year !== now.getFullYear() || month !== now.getMonth() || selectedDate !== todayIso) ? `
                  <button onclick="window.appActions.jumpCalendarToday()" class="text-[11px] font-semibold text-[#500568] hover:underline">
                    Jump to Today
                  </button>
                ` : ""}
              </div>
            </div>

            <button
              type="button"
              onclick="window.appActions.changeCalendarMonth(1)"
              class="w-10 h-10 rounded-xl bg-[#F5F5F8] hover:bg-[#F5D0FE]/60 text-[#10113F] flex items-center justify-center font-bold text-lg transition"
              aria-label="Next month"
              title="Next Month"
            >
              &rsaquo;
            </button>
          </div>

          <!-- Weekday Headers (Sun/Sat in #D946EF magenta-purple, Mon-Fri in #10113F navy) -->
          <div class="grid grid-cols-7 gap-1 pt-2 border-t border-slate-100 text-center">
            ${weekdays.map((day, idx) => `
              <div class="py-1.5 text-xs font-bold uppercase tracking-wider ${
                idx === 0 || idx === 6 ? "text-[#D946EF]" : "text-[#10113F]"
              }">
                ${day}
              </div>
            `).join("")}
          </div>

          <!-- Calendar Days Grid -->
          <div class="calendar-grid">
            ${cellsHtml.join("")}
          </div>

          <!-- Legend Bar -->
          <div class="pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-600">
            <div class="flex flex-wrap items-center gap-4">
              <span class="inline-flex items-center gap-1.5">
                <span class="w-2.5 h-2.5 rounded-full bg-[#22C55E]"></span> Service
              </span>
              <span class="inline-flex items-center gap-1.5">
                <span class="w-2.5 h-2.5 rounded-full bg-[#3B82F6]"></span> Rehearsal
              </span>
              <span class="inline-flex items-center gap-1.5">
                <span class="w-2.5 h-2.5 rounded-full bg-[#EF4444]"></span> My Unavailability
              </span>
              <span class="inline-flex items-center gap-1.5">
                <span class="w-2.5 h-2.5 rounded-full bg-[#500568]"></span> Selected Date
              </span>
            </div>
          </div>
        </div>

        <!-- Right 5 Cols: Selected Date Details & Quick Actions -->
        <div class="lg:col-span-5 space-y-4">
          <!-- Selected Date Status Card (Matching Mobile eventCard) -->
          <div class="surface-card border-2 ${statusBorderClass} p-5 space-y-4">
            <div class="flex items-start justify-between gap-3">
              <div>
                <div class="text-xs font-semibold uppercase tracking-wider text-slate-400">Selected Date</div>
                <h3 class="text-lg font-bold text-[#10113F] mt-0.5">${escapeHtml(formatFriendlyDate(selectedDate))}</h3>
              </div>
              <span class="badge ${statusBadgeClass} text-xs px-3 py-1">${escapeHtml(statusTitle)}</span>
            </div>

            <!-- My Unavailability on Selected Date -->
            ${selectedEvents.myUnavail.length > 0 ? `
              <div class="bg-rose-50 border border-rose-200 rounded-xl p-3.5 space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-xs font-bold text-rose-900">
                    <i class="fa-regular fa-calendar-xmark mr-1"></i> You Marked Unavailable
                  </span>
                </div>
                ${selectedEvents.myUnavail.map((r) => `
                  <div class="flex items-center justify-between gap-2 text-xs text-rose-800 bg-white/80 px-3 py-2 rounded-lg border border-rose-200">
                    <div>
                      <div class="font-semibold">${escapeHtml(r.startDate)} &rarr; ${escapeHtml(r.endDate)}</div>
                      <div class="text-rose-600">${escapeHtml(r.reason || "Personal / Family")}</div>
                    </div>
                    <button
                      onclick="window.appActions.removeUnavailableRange('${escapeHtml(r.id)}', false)"
                      class="btn btn-danger btn-sm shrink-0"
                      title="Clear This Unavailability"
                    >
                      Clear
                    </button>
                  </div>
                `).join("")}
              </div>
            ` : ""}

            <!-- Services on Selected Date -->
            ${selectedEvents.dayServices.length > 0 ? `
              <div class="space-y-3">
                <div class="text-xs font-bold uppercase tracking-wider text-emerald-800 flex items-center gap-1.5">
                  <span class="w-2 h-2 rounded-full bg-[#22C55E]"></span>
                  Scheduled Worship Service (${selectedEvents.dayServices.length})
                </div>
                ${selectedEvents.dayServices.map((srv) => {
                  const assigned = srv.assignments || [];
                  const songs = srv.songs || [];
                  const mySlot = assigned.find((a) => a.uid === currentUser.uid);
                  return `
                    <div class="border border-emerald-200 bg-emerald-50/40 rounded-xl p-3.5 space-y-2.5">
                      <div class="flex items-start justify-between gap-2">
                        <div>
                          <div class="flex items-center gap-1.5">
                            <span class="badge ${srv.status === "Draft" ? "badge-amber" : "badge-emerald"}">${escapeHtml(srv.status || "Published")}</span>
                            ${mySlot ? `<span class="badge badge-purple">Your Role: ${escapeHtml(mySlot.role)}</span>` : ""}
                          </div>
                          <h4 class="text-sm font-bold text-[#10113F] mt-1">${escapeHtml(srv.title || "Worship Service")}</h4>
                          <p class="text-xs text-slate-600">
                            <i class="fa-regular fa-clock mr-1"></i>${formatFriendlyTime(srv.start || "09:00")}${srv.end ? ` - ${formatFriendlyTime(srv.end)}` : ""}
                            &middot; <i class="fa-solid fa-location-dot mx-1"></i>${escapeHtml(srv.venue || "Main Sanctuary")}
                          </p>
                        </div>
                        ${isLeader ? `
                          <button onclick="window.appActions.openServiceModal('${escapeHtml(srv.id)}')" class="btn btn-secondary btn-sm">
                            <i class="fa-solid fa-pen text-[10px]"></i> Edit
                          </button>
                        ` : ""}
                      </div>

                      ${assigned.length > 0 ? `
                        <div class="text-xs text-slate-600 bg-white p-2.5 rounded-lg border border-slate-200">
                          <div class="font-bold text-[#10113F] mb-1">Lineup (${assigned.length}):</div>
                          <div class="flex flex-wrap gap-1.5">
                            ${assigned.map((a) => `
                              <span class="px-2 py-0.5 rounded bg-slate-100 text-[#10113F] text-[11px]">
                                <strong>${escapeHtml(a.role)}:</strong> ${escapeHtml(a.name)}
                              </span>
                            `).join("")}
                          </div>
                        </div>
                      ` : ""}

                      ${songs.length > 0 ? `
                        <div class="text-xs text-slate-600 bg-white p-2.5 rounded-lg border border-slate-200">
                          <div class="font-bold text-[#10113F] mb-1">Songs (${songs.length}):</div>
                          <div class="flex flex-wrap gap-1.5">
                            ${songs.map((s) => {
                              const sTitle = typeof s === "string" ? s : s.title;
                              return `
                                <button onclick="window.appActions.openSongByTitle('${escapeHtml(sTitle)}')" class="badge badge-purple hover:opacity-85 cursor-pointer">
                                  <i class="fa-solid fa-music text-[9px]"></i> ${escapeHtml(sTitle)}
                                </button>
                              `;
                            }).join("")}
                          </div>
                        </div>
                      ` : ""}
                    </div>
                  `;
                }).join("")}
              </div>
            ` : ""}

            <!-- Rehearsals on Selected Date -->
            ${selectedEvents.dayRehearsals.length > 0 ? `
              <div class="space-y-2.5">
                <div class="text-xs font-bold uppercase tracking-wider text-blue-800 flex items-center gap-1.5">
                  <span class="w-2 h-2 rounded-full bg-[#3B82F6]"></span>
                  Scheduled Rehearsal (${selectedEvents.dayRehearsals.length})
                </div>
                ${selectedEvents.dayRehearsals.map((srv) => {
                  const reh = srv.rehearsal || {};
                  return `
                    <div class="border border-blue-200 bg-blue-50/50 rounded-xl p-3.5 space-y-1">
                      <div class="text-sm font-bold text-[#10113F]">Rehearsal for ${escapeHtml(srv.title || "Worship Service")}</div>
                      <div class="text-xs text-blue-900">
                        <i class="fa-regular fa-clock mr-1"></i>${ reh.start ? formatFriendlyTime(reh.start) : "7:00 PM" }
                        &middot; ${escapeHtml(reh.venue || srv.venue || "Main Sanctuary")}
                      </div>
                      ${reh.notes ? `<div class="text-xs text-slate-600 pt-1 italic">"${escapeHtml(reh.notes)}"</div>` : ""}
                    </div>
                  `;
                }).join("")}
              </div>
            ` : ""}

            <!-- Team Members Unavailable on Selected Date -->
            ${selectedEvents.teamUnavail.length > 0 ? `
              <div class="space-y-2">
                <div class="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Team Members Unavailable (${selectedEvents.teamUnavail.length})
                </div>
                <div class="divide-y divide-slate-100 border border-slate-200 rounded-xl bg-slate-50">
                  ${selectedEvents.teamUnavail.map((u) => `
                    <div class="px-3 py-2 flex items-center justify-between text-xs">
                      <div>
                        <span class="font-semibold text-[#10113F]">${escapeHtml(u.name)}</span>
                        <span class="text-slate-400 ml-1">(${escapeHtml(u.role)})</span>
                      </div>
                      <span class="text-rose-600 font-medium">${escapeHtml(u.range.reason || "Unavailable")}</span>
                    </div>
                  `).join("")}
                </div>
              </div>
            ` : ""}

            ${!isSelectedUnavail && !isSelectedService && !isSelectedRehearsal ? `
              <div class="py-4 text-center text-xs text-slate-500 bg-[#F8FAFC] rounded-xl border border-slate-200/80">
                <i class="fa-regular fa-calendar-check text-slate-400 text-base mb-1 block"></i>
                No services or rehearsals scheduled on this date.
              </div>
            ` : ""}

            <!-- Primary Calendar Action Buttons (Matching MemberActivity.kt Set Unavailable Button) -->
            <div class="pt-2 flex flex-col sm:flex-row gap-2">
              <button
                onclick="window.appActions.openAvailabilityModal('${escapeHtml(selectedDate)}')"
                class="btn btn-primary flex-1"
              >
                <i class="fa-regular fa-calendar-xmark text-xs"></i> Set Unavailable
              </button>
              ${isLeader ? `
                <button
                  onclick="window.appActions.openServiceModal(null, '${escapeHtml(selectedDate)}')"
                  class="btn btn-navy flex-1"
                >
                  <i class="fa-solid fa-plus text-xs"></i> Add Service Here
                </button>
              ` : ""}
            </div>
          </div>

          <!-- Dark Navy Quick Availability Card (Matching Mobile availabilityView Dark Card) -->
          <div class="synced-dark-panel p-5 space-y-3.5 shadow-md">
            <div class="flex items-center justify-between">
              <div>
                <h3 class="text-sm font-bold text-white">Quick Mark Unavailability</h3>
                <p class="text-[11px] text-[#B7D1FF]">Synced directly with leaders' lineup scheduler</p>
              </div>
              <i class="fa-regular fa-calendar-days text-[#F5D0FE]"></i>
            </div>
            <form onsubmit="window.appActions.addUnavailableRange(event)" class="space-y-3">
              <div>
                <label class="block text-xs font-medium text-white/90 mb-1">Reason</label>
                <input
                  id="cal-unavail-reason"
                  type="text"
                  maxlength="80"
                  placeholder="Family bonding / Out of town"
                  class="form-input bg-white/95 text-[#10113F]"
                />
              </div>
              <div class="grid grid-cols-2 gap-2.5">
                <div>
                  <label class="block text-xs font-medium text-white/90 mb-1">Starts</label>
                  <input
                    id="cal-unavail-start"
                    type="date"
                    required
                    value="${escapeHtml(selectedDate)}"
                    class="form-input bg-white/95 text-[#10113F] text-xs"
                  />
                </div>
                <div>
                  <label class="block text-xs font-medium text-white/90 mb-1">Ends</label>
                  <input
                    id="cal-unavail-end"
                    type="date"
                    required
                    value="${escapeHtml(selectedDate)}"
                    class="form-input bg-white/95 text-[#10113F] text-xs"
                  />
                </div>
              </div>
              <button type="submit" class="w-full btn bg-[#10113F] hover:bg-[#080720] text-white border border-white/20">
                Save Unavailability
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  `;
}

export function renderScheduleView(state) {
  const { currentUser, services, substitutions, teamMembers } = state;
  const isLeader = hasLeaderPrivileges(currentUser);
  const myUnavailability = currentUser?.unavailableRanges || [];

  return `
    <div class="space-y-6">
      <!-- Page Header -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 class="text-xl font-bold text-[#10113F]">Schedule, Lineups & Availability</h1>
          <p class="text-xs text-slate-500 mt-0.5">Manage worship service dates, rehearsal schedules, role assignments, and member availability.</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button onclick="window.appActions.switchTab('calendar')" class="btn btn-secondary">
            <i class="fa-solid fa-calendar-days text-[#500568]"></i>
            Monthly Calendar
          </button>
          <button onclick="window.appActions.openAvailabilityModal()" class="btn btn-secondary">
            <i class="fa-regular fa-calendar-xmark text-slate-500"></i>
            My Unavailability (${myUnavailability.length})
          </button>
          ${isLeader ? `
            <button onclick="window.appActions.openServiceModal()" class="btn btn-primary">
              <i class="fa-solid fa-plus text-xs"></i> New Service Schedule
            </button>
          ` : ""}
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        <!-- Left 8 Cols: Service Schedule List -->
        <div class="lg:col-span-8 space-y-4">
          ${services.length === 0 ? `
            <div class="surface-card p-10 text-center">
              <div class="w-11 h-11 rounded-xl bg-[#F5F5F8] border border-slate-200 flex items-center justify-center mx-auto mb-3 text-[#500568]">
                <i class="fa-regular fa-calendar-plus"></i>
              </div>
              <h3 class="text-sm font-bold text-[#10113F]">No services on the schedule</h3>
              <p class="text-xs text-slate-500 mt-1 max-w-md mx-auto">Schedule upcoming Sunday services, youth nights, or special rehearsals to coordinate your worship team lineup.</p>
              ${isLeader ? `
                <button onclick="window.appActions.openServiceModal()" class="mt-4 btn btn-primary btn-sm">
                  <i class="fa-solid fa-plus text-[10px]"></i> Schedule First Service
                </button>
              ` : ""}
            </div>
          ` : services.map((srv) => renderServiceCardHtml(srv, state, isLeader)).join("")}
        </div>

        <!-- Right 4 Cols: Substitution Requests & Member Availability Log -->
        <div class="lg:col-span-4 space-y-5">
          <!-- Substitution Requests Card -->
          <div class="surface-card overflow-hidden">
            <div class="px-4 py-3 bg-[#F8FAFC] border-b border-slate-200 flex items-center justify-between">
              <h3 class="text-xs font-bold uppercase tracking-wider text-[#10113F]">Substitution Requests</h3>
              <span class="badge badge-neutral">${substitutions.length}</span>
            </div>
            <div class="p-4">
              ${substitutions.length === 0 ? `
                <p class="text-xs text-slate-500">No substitution requests submitted.</p>
              ` : `
                <div class="divide-y divide-slate-100 -my-2">
                  ${substitutions.slice(0, 8).map((sub) => {
                    const badgeClass =
                      sub.status === "Approved" || sub.status === "Accepted"
                        ? "badge-emerald"
                        : sub.status === "Declined" || sub.status === "Cancelled"
                        ? "badge-rose"
                        : "badge-amber";
                    return `
                      <div class="py-3 space-y-1.5">
                        <div class="flex items-center justify-between gap-2">
                          <span class="text-xs font-bold text-[#10113F]">${escapeHtml(sub.requesterName || "Member")}</span>
                          <span class="badge ${badgeClass}">${escapeHtml(sub.status || "Pending")}</span>
                        </div>
                        <div class="text-xs text-slate-600">
                          Service: <strong class="text-[#10113F]">${escapeHtml(sub.serviceTitle || "Service")}</strong>
                        </div>
                        <div class="text-xs text-slate-500 italic">"${escapeHtml(sub.reason || "")}"</div>
                        ${sub.substituteName ? `
                          <div class="text-[11px] text-[#500568] font-semibold">Proposed Sub: ${escapeHtml(sub.substituteName)}</div>
                        ` : ""}
                        ${isLeader && (sub.status === "Pending" || sub.status === "Substitute Accepted") ? `
                          <div class="flex items-center gap-2 pt-1">
                            <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Approved')" class="btn btn-primary btn-sm">Approve</button>
                            <button onclick="window.appActions.respondSubstitution('${escapeHtml(sub.id)}', 'Declined')" class="btn btn-danger btn-sm">Decline</button>
                          </div>
                        ` : ""}
                      </div>
                    `;
                  }).join("")}
                </div>
              `}
            </div>
          </div>

          <!-- Team Availability / Unavailability Dates Card -->
          <div class="surface-card overflow-hidden">
            <div class="px-4 py-3 bg-[#F8FAFC] border-b border-slate-200 flex items-center justify-between">
              <h3 class="text-xs font-bold uppercase tracking-wider text-[#10113F]">Team Unavailability</h3>
              <button onclick="window.appActions.openAvailabilityModal()" class="text-xs font-semibold text-[#500568] hover:underline">
                Edit Mine
              </button>
            </div>
            <div class="p-4">
              ${teamMembers.filter((m) => (m.unavailableRanges || []).length > 0).length === 0 ? `
                <div class="flex items-center gap-2 text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2">
                  <i class="fa-solid fa-circle-check"></i>
                  <span>All members are currently marked available.</span>
                </div>
              ` : `
                <div class="divide-y divide-slate-100 -my-2">
                  ${teamMembers
                    .filter((m) => (m.unavailableRanges || []).length > 0)
                    .map((m) => `
                      <div class="py-2.5 space-y-1.5">
                        <div class="flex items-center justify-between">
                          <span class="text-xs font-bold text-[#10113F]">${escapeHtml(m.fullName || m.name || "Member")}</span>
                          <span class="text-[11px] text-slate-400">${escapeHtml(m.ministryRole || "Member")}</span>
                        </div>
                        <div class="flex flex-wrap gap-1">
                          ${(m.unavailableRanges || []).map((r) => `
                            <span class="badge badge-rose text-[10px]">
                              ${escapeHtml(r.startDate)}${r.endDate && r.endDate !== r.startDate ? ` to ${escapeHtml(r.endDate)}` : ""}
                              ${r.reason ? ` (${escapeHtml(r.reason)})` : ""}
                            </span>
                          `).join("")}
                        </div>
                      </div>
                    `).join("")}
                </div>
              `}
            </div>
          </div>
        </div>
      </div>
    </div>
  `;
}

// ============================================================================
// Song Library View (Displaying Song Cover Images on Desktop & Mobile)
// ============================================================================
export function renderSongsView(state) {
  const { currentUser, songs, songSearchQuery, songKeyFilter } = state;
  const isLeader = hasLeaderPrivileges(currentUser);
  const query = (songSearchQuery || "").trim().toLowerCase();
  const activeKeyFilter = (songKeyFilter || "").trim();

  const availableKeys = Array.from(new Set(songs.map((s) => s.key || "C").filter(Boolean))).sort();

  const filteredSongs = songs.filter((s) => {
    if (activeKeyFilter && (s.key || "C") !== activeKeyFilter) return false;
    if (!query) return true;
    return (
      (s.title || "").toLowerCase().includes(query) ||
      (s.artist || "").toLowerCase().includes(query) ||
      (s.key || "").toLowerCase().includes(query) ||
      (s.lyrics || "").toLowerCase().includes(query) ||
      (s.sheet || "").toLowerCase().includes(query)
    );
  });

  return `
    <div class="space-y-6">
      <!-- Page Header -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 class="text-xl font-bold text-[#10113F]">Song Lineup & Chord Library</h1>
          <p class="text-xs text-slate-500 mt-0.5">Browse worship songs by cover artwork, transpose chord charts in real time, and practice with the precision metronome.</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          ${isLeader && songs.length === 0 ? `
            <button onclick="window.appActions.seedDefaultSongs()" class="btn btn-secondary">
              <i class="fa-solid fa-wand-magic-sparkles text-[#500568] text-xs"></i> Load Default Worship Catalog
            </button>
          ` : ""}
          ${isLeader ? `
            <button onclick="window.appActions.openSongEditorModal()" class="btn btn-primary">
              <i class="fa-solid fa-plus text-xs"></i> Add Song to Library
            </button>
          ` : ""}
        </div>
      </div>

      <!-- Search & Filter Bar -->
      <div class="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        <div class="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5 flex-1 max-w-2xl">
          <div class="relative flex-1">
            <i class="fa-solid fa-magnifying-glass absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 text-xs"></i>
            <input
              id="song-search-input"
              type="text"
              value="${escapeHtml(songSearchQuery || "")}"
              oninput="window.appActions.setSongSearch(this.value)"
              placeholder="Search by song title, artist, key, or lyrics..."
              class="form-input pl-9 pr-8"
            />
            ${songSearchQuery ? `
              <button onclick="window.appActions.setSongSearch('')" class="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 text-xs">
                <i class="fa-solid fa-xmark"></i>
              </button>
            ` : ""}
          </div>
          ${availableKeys.length > 1 ? `
            <select
              onchange="window.appActions.setSongKeyFilter(this.value)"
              class="form-select sm:w-40 text-xs font-medium"
              aria-label="Filter by musical key"
            >
              <option value="" ${!activeKeyFilter ? "selected" : ""}>All Keys (${songs.length})</option>
              ${availableKeys.map((k) => `
                <option value="${escapeHtml(k)}" ${activeKeyFilter === k ? "selected" : ""}>Key of ${escapeHtml(k)}</option>
              `).join("")}
            </select>
          ` : ""}
        </div>
        <div class="text-xs text-slate-500">
          Showing <strong class="text-[#10113F]">${filteredSongs.length}</strong> of ${songs.length} songs
        </div>
      </div>

      ${filteredSongs.length === 0 ? `
        <div class="surface-card p-10 text-center">
          <div class="w-12 h-12 rounded-2xl bg-[#F5F5F8] border border-slate-200 flex items-center justify-center mx-auto mb-3 text-[#500568]">
            <i class="fa-solid fa-music text-base"></i>
          </div>
          <h3 class="text-sm font-bold text-[#10113F]">No matching songs found</h3>
          <p class="text-xs text-slate-500 mt-1 max-w-md mx-auto">Add worship songs with chord sheets, original keys, BPM tempo, and YouTube reference links for automatic cover thumbnails.</p>
          ${isLeader ? `
            <div class="mt-4 flex flex-wrap items-center justify-center gap-2">
              <button onclick="window.appActions.openSongEditorModal()" class="btn btn-primary btn-sm">
                <i class="fa-solid fa-plus text-[10px]"></i> Add Song
              </button>
              ${songs.length === 0 ? `
                <button onclick="window.appActions.seedDefaultSongs()" class="btn btn-secondary btn-sm">
                  <i class="fa-solid fa-wand-magic-sparkles text-[10px]"></i> Load Default Worship Catalog
                </button>
              ` : ""}
            </div>
          ` : ""}
        </div>
      ` : `
        <!-- Desktop Table View with Cover Thumbnails -->
        <div class="hidden md:block surface-card overflow-hidden">
          <div class="overflow-x-auto">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Song Cover & Title</th>
                  <th>Original Key</th>
                  <th>Tempo</th>
                  <th>Chart / Lyrics</th>
                  <th>Reference</th>
                  <th class="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                ${filteredSongs.map((song) => {
                  const hasChords = Boolean((song.sheet && song.sheet.trim()) || (song.lyrics && song.lyrics.trim()));
                  const hasYt = Boolean(song.referenceUrl && song.referenceUrl.trim());
                  return `
                    <tr class="cursor-pointer group" onclick="window.appActions.openSongStageModal('${escapeHtml(song.id)}')">
                      <td>
                        <div class="flex items-center gap-3.5">
                          ${getSongCoverHtml(song, "w-24 h-14")}
                          <div class="min-w-0">
                            <div class="font-bold text-sm text-[#10113F] group-hover:text-[#500568] transition truncate">${escapeHtml(song.title || "Untitled Song")}</div>
                            <div class="text-xs text-slate-500 truncate mt-0.5">${escapeHtml(song.artist || "Unknown Artist")}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <span class="badge badge-purple font-mono text-xs">${escapeHtml(song.key || "C")}</span>
                      </td>
                      <td>
                        ${song.bpm ? `<span class="text-xs font-mono font-semibold text-[#10113F]">${escapeHtml(formatBpmDisplay(song.bpm))} BPM</span>` : `<span class="text-xs text-slate-400">&mdash;</span>`}
                      </td>
                      <td>
                        ${hasChords
                          ? `<span class="badge badge-emerald"><i class="fa-solid fa-check text-[9px]"></i> Available</span>`
                          : `<span class="badge badge-neutral">No Chart</span>`}
                      </td>
                      <td>
                        ${hasYt
                          ? `<span class="badge badge-rose"><i class="fa-brands fa-youtube text-[10px]"></i> YouTube</span>`
                          : `<span class="text-xs text-slate-400">&mdash;</span>`}
                      </td>
                      <td class="text-right" onclick="event.stopPropagation()">
                        <div class="inline-flex items-center justify-end gap-1.5">
                          <button onclick="window.appActions.openSongStageModal('${escapeHtml(song.id)}')" class="btn btn-primary btn-sm">
                            <i class="fa-solid fa-sliders text-[10px]"></i> Stage View
                          </button>
                          ${isLeader ? `
                            <button onclick="window.appActions.openSongEditorModal('${escapeHtml(song.id)}')" class="btn btn-secondary btn-sm" title="Edit Song">
                              <i class="fa-solid fa-pen text-xs"></i>
                            </button>
                            <button onclick="window.appActions.deleteSong('${escapeHtml(song.id)}')" class="btn btn-ghost btn-sm text-rose-600 hover:bg-rose-50" title="Delete Song">
                              <i class="fa-regular fa-trash-can text-xs"></i>
                            </button>
                          ` : ""}
                        </div>
                      </td>
                    </tr>
                  `;
                }).join("")}
              </tbody>
            </table>
          </div>
        </div>

        <!-- Mobile Card List View (Matching Synced N Mobile songsView Layout) -->
        <div class="md:hidden space-y-3">
          ${filteredSongs.map((song) => {
            const hasChords = Boolean((song.sheet && song.sheet.trim()) || (song.lyrics && song.lyrics.trim()));
            return `
              <div
                onclick="window.appActions.openSongStageModal('${escapeHtml(song.id)}')"
                class="surface-card surface-card-interactive p-3.5 flex items-center justify-between gap-3 cursor-pointer group"
              >
                <div class="flex items-center gap-3 min-w-0 flex-1">
                  ${getSongCoverHtml(song, "w-24 h-14")}
                  <div class="min-w-0 flex-1">
                    <div class="font-bold text-sm text-[#10113F] truncate group-hover:text-[#500568]">${escapeHtml(song.title || "Untitled Song")}</div>
                    <div class="text-xs text-slate-500 truncate">${escapeHtml(song.artist || "Unknown Artist")}</div>
                    <div class="flex flex-wrap items-center gap-1.5 mt-1.5">
                      <span class="badge badge-purple font-mono text-[10px]">Key: ${escapeHtml(song.key || "C")}</span>
                      ${song.bpm ? `<span class="badge badge-neutral font-mono text-[10px]">${escapeHtml(formatBpmDisplay(song.bpm))} BPM</span>` : ""}
                      ${hasChords ? `<span class="badge badge-emerald text-[10px]">Chords</span>` : ""}
                    </div>
                  </div>
                </div>
                <div class="flex items-center gap-1 shrink-0" onclick="event.stopPropagation()">
                  ${isLeader ? `
                    <button onclick="window.appActions.openSongEditorModal('${escapeHtml(song.id)}')" class="btn btn-ghost btn-sm text-slate-500" title="Edit Song">
                      <i class="fa-solid fa-pen text-xs"></i>
                    </button>
                  ` : ""}
                  <button onclick="window.appActions.openSongStageModal('${escapeHtml(song.id)}')" class="w-8 h-8 rounded-lg bg-[#F5F5F8] text-[#10113F] flex items-center justify-center">
                    <i class="fa-solid fa-chevron-right text-xs"></i>
                  </button>
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
  const { currentUser, currentTeam, teamMembers, rejoinRequests } = state;
  const isPrimaryLeader = isWorshipLeader(currentUser.ministryRole, currentUser.role) || currentTeam?.leaderUid === currentUser.uid;
  const isLeader = hasLeaderPrivileges(currentUser);
  const pendingRejoins = rejoinRequests.filter((r) => r.status === "Pending");
  const teamCode = currentTeam?.teamCode || currentUser?.teamCode || "------";

  return `
    <div class="space-y-6">
      <!-- Mobile Team Hub Switcher (Matches Mobile App Team Chat / Members Tabs) -->
      <div class="md:hidden grid grid-cols-2 p-1 bg-[#F5F5F8] rounded-xl border border-slate-200 text-xs font-bold">
        <button type="button" onclick="window.appActions.switchTab('chat')" class="py-2 rounded-lg text-slate-600 hover:text-[#10113F] transition">
          <i class="fa-regular fa-comments mr-1"></i> Team Chat
        </button>
        <button type="button" onclick="window.appActions.switchTab('roster')" class="py-2 rounded-lg bg-[#500568] text-white shadow-xs transition">
          <i class="fa-solid fa-users mr-1"></i> Team Roster (${teamMembers.length})
        </button>
      </div>

      <!-- Page Header -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 class="text-xl font-bold text-[#10113F]">Team Management & Roster</h1>
          <p class="text-xs text-slate-500 mt-0.5">Manage ministry members, assign musical & leadership roles, and review availability.</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button onclick="window.appActions.copyTeamCode()" class="btn btn-secondary">
            <i class="fa-solid fa-key text-[#500568] text-xs"></i>
            Invite Code: <span class="font-mono font-bold text-[#10113F]">${escapeHtml(teamCode)}</span>
          </button>
          ${isPrimaryLeader ? `
            <button onclick="window.appActions.openTransferLeadershipModal()" class="btn btn-secondary">
              <i class="fa-solid fa-crown text-amber-600 text-xs"></i> Transfer Leadership
            </button>
          ` : ""}
        </div>
      </div>

      <!-- Pending Rejoin Requests (If any) -->
      ${isLeader && pendingRejoins.length > 0 ? `
        <div class="surface-card border-amber-300 overflow-hidden">
          <div class="px-4 py-2.5 bg-amber-50 border-b border-amber-200 flex items-center justify-between">
            <span class="text-xs font-bold text-amber-900">Pending Rejoin Requests (${pendingRejoins.length})</span>
          </div>
          <div class="divide-y divide-slate-100">
            ${pendingRejoins.map((req) => `
              <div class="p-3.5 flex items-center justify-between gap-4">
                <div>
                  <div class="text-sm font-bold text-[#10113F]">${escapeHtml(req.userName || "Former Member")}</div>
                  <div class="text-xs text-slate-500">Requested to rejoin the ministry team</div>
                </div>
                <div class="flex items-center gap-2">
                  <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', true)" class="btn btn-primary btn-sm">Approve</button>
                  <button onclick="window.appActions.handleRejoinRequest('${escapeHtml(req.id)}', false)" class="btn btn-danger btn-sm">Decline</button>
                </div>
              </div>
            `).join("")}
          </div>
        </div>
      ` : ""}

      <!-- Desktop Structured Team Directory Table -->
      <div class="hidden md:block surface-card overflow-hidden">
        <div class="overflow-x-auto">
          <table class="data-table">
            <thead>
              <tr>
                <th>Member</th>
                <th>Authority / Role</th>
                <th>Availability Status</th>
                ${isLeader ? `<th class="text-right">Role Assignment & Actions</th>` : ""}
              </tr>
            </thead>
            <tbody>
              ${teamMembers.map((member) => {
                const isMe = member.uid === currentUser.uid;
                const memberIsPrimaryLeader = isWorshipLeader(member.ministryRole, member.role) || currentTeam?.leaderUid === member.uid;
                const unavailCount = (member.unavailableRanges || []).length;
                const displayName = member.fullName || member.name || member.username || "Member";

                return `
                  <tr>
                    <td>
                      <div class="flex items-center gap-3">
                        ${getAvatarHtml(member, "w-9 h-9", "text-xs")}
                        <div class="min-w-0">
                          <div class="font-bold text-[#10113F] truncate">
                            ${escapeHtml(displayName)}
                            ${isMe ? `<span class="text-xs font-normal text-slate-400 ml-1">(You)</span>` : ""}
                          </div>
                          <div class="text-xs text-slate-500 truncate">${escapeHtml(member.email || "")}</div>
                        </div>
                      </div>
                    </td>
                    <td>
                      ${getRoleBadgeHtml(member)}
                    </td>
                    <td>
                      ${unavailCount === 0
                        ? `<span class="badge badge-emerald"><i class="fa-solid fa-check text-[9px]"></i> Available</span>`
                        : `<span class="badge badge-amber"><i class="fa-regular fa-calendar-xmark text-[9px]"></i> ${unavailCount} Date Range${unavailCount > 1 ? "s" : ""} Unavailable</span>`
                      }
                    </td>
                    ${isLeader ? `
                      <td class="text-right">
                        ${!memberIsPrimaryLeader ? `
                          <div class="inline-flex items-center justify-end gap-2">
                            <select
                              onchange="window.appActions.assignMemberRole('${escapeHtml(member.uid)}', this.value)"
                              class="form-select py-1 px-2.5 text-xs w-44"
                            >
                              ${DEFAULT_MINISTRY_ROLES.filter((r) => r !== "Worship Leader").map((r) => `
                                <option value="${escapeHtml(r)}" ${member.ministryRole === r ? "selected" : ""}>${escapeHtml(r)}</option>
                              `).join("")}
                            </select>
                            ${!isMe ? `
                              <button onclick="window.appActions.removeMemberFromTeam('${escapeHtml(member.uid)}', '${escapeHtml(displayName)}')"
                                class="btn btn-danger btn-sm" title="Remove from Team">
                                <i class="fa-solid fa-user-minus text-[11px]"></i>
                              </button>
                            ` : ""}
                          </div>
                        ` : `
                          <span class="text-xs text-[#500568] font-semibold">Primary Leader</span>
                        `}
                      </td>
                    ` : ""}
                  </tr>
                `;
              }).join("")}
            </tbody>
          </table>
        </div>
      </div>

      <!-- Mobile Team Roster Cards -->
      <div class="md:hidden space-y-3">
        ${teamMembers.map((member) => {
          const isMe = member.uid === currentUser.uid;
          const memberIsPrimaryLeader = isWorshipLeader(member.ministryRole, member.role) || currentTeam?.leaderUid === member.uid;
          const unavailCount = (member.unavailableRanges || []).length;
          const displayName = member.fullName || member.name || member.username || "Member";

          return `
            <div class="surface-card p-4 space-y-3">
              <div class="flex items-start justify-between gap-3">
                <div class="flex items-center gap-3 min-w-0">
                  ${getAvatarHtml(member, "w-10 h-10", "text-xs")}
                  <div class="min-w-0">
                    <div class="font-bold text-sm text-[#10113F] truncate">
                      ${escapeHtml(displayName)}
                      ${isMe ? `<span class="text-xs font-normal text-slate-400">(You)</span>` : ""}
                    </div>
                    <div class="text-xs text-slate-500 truncate">${escapeHtml(member.email || "")}</div>
                  </div>
                </div>
                ${getRoleBadgeHtml(member)}
              </div>

              <div class="flex items-center justify-between pt-2 border-t border-slate-100 text-xs">
                <div>
                  ${unavailCount === 0
                    ? `<span class="badge badge-emerald"><i class="fa-solid fa-check text-[9px]"></i> Available</span>`
                    : `<span class="badge badge-amber">${unavailCount} Unavailable Range${unavailCount > 1 ? "s" : ""}</span>`
                  }
                </div>
                ${isLeader && !memberIsPrimaryLeader ? `
                  <div class="flex items-center gap-1.5">
                    <select
                      onchange="window.appActions.assignMemberRole('${escapeHtml(member.uid)}', this.value)"
                      class="form-select py-1 px-2 text-xs w-36"
                    >
                      ${DEFAULT_MINISTRY_ROLES.filter((r) => r !== "Worship Leader").map((r) => `
                        <option value="${escapeHtml(r)}" ${member.ministryRole === r ? "selected" : ""}>${escapeHtml(r)}</option>
                      `).join("")}
                    </select>
                    ${!isMe ? `
                      <button onclick="window.appActions.removeMemberFromTeam('${escapeHtml(member.uid)}', '${escapeHtml(displayName)}')"
                        class="btn btn-danger btn-sm" title="Remove from Team">
                        <i class="fa-solid fa-user-minus text-[11px]"></i>
                      </button>
                    ` : ""}
                  </div>
                ` : ""}
              </div>
            </div>
          `;
        }).join("")}
      </div>
    </div>
  `;
}

export function renderChatView(state) {
  const { currentUser, messages, chatReplyTarget, chatEditingMessage, teamMembers, chatScroll } = state;
  const isScrolledUp = chatScroll && chatScroll.isPinnedToBottom === false;

  return `
    <div class="space-y-3">
      <!-- Mobile Team Hub Switcher (Matches Mobile App Team Chat / Members Tabs) -->
      <div class="md:hidden grid grid-cols-2 p-1 bg-[#F5F5F8] rounded-xl border border-slate-200 text-xs font-bold">
        <button type="button" onclick="window.appActions.switchTab('chat')" class="py-2 rounded-lg bg-[#500568] text-white shadow-xs transition">
          <i class="fa-regular fa-comments mr-1"></i> Team Chat
        </button>
        <button type="button" onclick="window.appActions.switchTab('roster')" class="py-2 rounded-lg text-slate-600 hover:text-[#10113F] transition">
          <i class="fa-solid fa-users mr-1"></i> Team Roster (${(teamMembers || []).length})
        </button>
      </div>

      <div class="surface-card relative flex flex-col h-[calc(100vh-12.5rem)] md:h-[calc(100vh-7.5rem)] min-h-[460px] overflow-hidden">
        <!-- Chat Header -->
        <div class="px-5 py-3.5 border-b border-slate-200 flex items-center justify-between bg-white">
          <div class="flex items-center gap-2.5">
            <div class="w-9 h-9 rounded-xl bg-[#F5D0FE]/70 border border-[#E879F9] flex items-center justify-center text-[#500568] text-xs">
              <i class="fa-regular fa-comments"></i>
            </div>
            <div>
              <h2 class="text-sm font-bold text-[#10113F]">Ministry Team Chat</h2>
              <p class="text-[11px] text-slate-500">Real-time coordination synced across web and mobile</p>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button onclick="window.appActions.switchTab('roster')" class="hidden sm:inline-flex btn btn-secondary btn-sm text-xs">
              <i class="fa-solid fa-users text-[10px]"></i> Roster (${(teamMembers || []).length})
            </button>
            <span class="badge badge-emerald">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-600"></span> Live Sync
            </span>
          </div>
        </div>

        <!-- Messages Scroll Container -->
        <div
          id="chat-messages-scroll"
          onscroll="window.appActions.handleChatScroll(this)"
          class="flex-1 overflow-y-auto p-4 sm:p-5 space-y-3.5 bg-[#F6F7FB]"
        >
          ${messages.length === 0 ? `
            <div class="h-full flex flex-col items-center justify-center text-center py-12">
              <div class="w-11 h-11 rounded-xl bg-white border border-slate-200 flex items-center justify-center mb-2.5 text-[#500568]">
                <i class="fa-regular fa-comment-dots"></i>
              </div>
              <p class="text-sm font-bold text-[#10113F]">No messages yet</p>
              <p class="text-xs text-slate-500 mt-0.5">Start a conversation with your worship team.</p>
            </div>
          ` : messages.map((msg) => {
            const isMe = msg.senderUid === currentUser.uid;
            const isDeleted = Boolean(msg.isDeleted);

            return `
              <div class="flex items-start gap-2.5 ${isMe ? "flex-row-reverse" : ""}">
                ${getAvatarHtml({ fullName: msg.senderName, photoUri: msg.senderPhotoUri }, "w-7 h-7", "text-[10px]")}
                <div class="max-w-[82%] sm:max-w-[65%] flex flex-col ${isMe ? "items-end" : "items-start"}">
                  <div class="flex items-center gap-1.5 mb-1 px-0.5">
                    <span class="text-[11px] font-bold text-[#10113F]">${escapeHtml(isMe ? "You" : (msg.senderName || "Member"))}</span>
                    ${msg.senderRole ? `<span class="text-[10px] text-[#500568] font-medium">&middot; ${escapeHtml(msg.senderRole)}</span>` : ""}
                    <span class="text-[10px] text-slate-400">${formatRelativeTime(msg.timestamp)}</span>
                  </div>

                  <div class="group relative rounded-2xl px-3.5 py-2.5 text-xs leading-relaxed ${
                    isDeleted
                      ? "bg-slate-100 border border-slate-200 text-slate-400 italic"
                      : isMe
                      ? "bg-[#500568] text-white shadow-xs"
                      : "bg-white border border-slate-200 text-[#10113F] shadow-2xs"
                  }">
                    ${!isDeleted && msg.replyToId ? `
                      <div class="mb-2 pl-2.5 py-1 border-l-2 ${isMe ? "border-[#F5D0FE] bg-white/15 text-white" : "border-[#500568] bg-[#FAF5FF] text-slate-600"} rounded-r text-[11px]">
                        <div class="font-bold">${escapeHtml(msg.replyToSender || "Member")}</div>
                        <div class="truncate opacity-90">${escapeHtml(msg.replyToText || "Photo")}</div>
                      </div>
                    ` : ""}

                    ${!isDeleted && msg.mediaUrl ? `
                      <a href="${escapeHtml(msg.mediaUrl)}" target="_blank" rel="noopener" class="block mb-2">
                        <img
                          src="${escapeHtml(msg.mediaUrl)}"
                          alt="Attachment"
                          onload="window.appActions.handleChatMediaLoad()"
                          class="max-h-60 rounded-lg border border-slate-200 object-cover"
                        />
                      </a>
                    ` : ""}

                    <div class="whitespace-pre-wrap break-words">${isDeleted ? "This message was unsent." : escapeHtml(msg.text || "")}</div>
                    ${!isDeleted && msg.isEdited ? `<div class="text-[10px] opacity-70 mt-1">(edited)</div>` : ""}

                    ${!isDeleted ? `
                      <div class="hidden group-hover:flex items-center gap-1 absolute -top-3 ${isMe ? "left-2" : "right-2"} bg-white border border-slate-200 rounded-lg shadow-xs px-1.5 py-0.5 text-slate-600">
                        <button onclick="window.appActions.setChatReply('${escapeHtml(msg.id)}')" class="hover:text-[#500568] p-1 text-[10px]" title="Reply">
                          <i class="fa-solid fa-reply"></i>
                        </button>
                        ${isMe ? `
                          <button onclick="window.appActions.startEditChatMessage('${escapeHtml(msg.id)}')" class="hover:text-amber-600 p-1 text-[10px]" title="Edit">
                            <i class="fa-solid fa-pen"></i>
                          </button>
                          <button onclick="window.appActions.retractChatMessage('${escapeHtml(msg.id)}')" class="hover:text-rose-600 p-1 text-[10px]" title="Unsend">
                            <i class="fa-regular fa-trash-can"></i>
                          </button>
                        ` : ""}
                      </div>
                    ` : ""}
                  </div>
                </div>
              </div>
            `;
          }).join("")}
          <div id="chat-messages-bottom-anchor" class="h-px w-full"></div>
        </div>

        <!-- Jump to Latest Messages Pill (Visible only when user manually scrolls upward) -->
        <button
          id="chat-scroll-bottom-btn"
          type="button"
          onclick="window.appActions.scrollToLatestChatMessages(true)"
          class="${isScrolledUp ? "inline-flex" : "hidden"} absolute bottom-16 left-1/2 -translate-x-1/2 z-20 items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-[#500568] text-white text-xs font-bold shadow-lg border border-white/20 hover:bg-[#3D0350] transition"
        >
          <i class="fa-solid fa-arrow-down text-[10px]"></i>
          <span>Latest messages</span>
        </button>

        <!-- Reply or Edit Context Banner -->
        ${chatReplyTarget ? `
          <div class="px-4 py-2 bg-[#FAF5FF] border-t border-[#F5D0FE] flex items-center justify-between text-xs">
            <div class="truncate text-[#10113F]">
              <span class="font-bold text-[#500568]"><i class="fa-solid fa-reply mr-1"></i>Replying to ${escapeHtml(chatReplyTarget.senderName)}:</span>
              <span class="text-slate-600 ml-1">${escapeHtml(chatReplyTarget.text || "Photo")}</span>
            </div>
            <button onclick="window.appActions.clearChatReply()" class="text-slate-400 hover:text-slate-700 p-1">
              <i class="fa-solid fa-xmark"></i>
            </button>
          </div>
        ` : ""}

        ${chatEditingMessage ? `
          <div class="px-4 py-2 bg-amber-50 border-t border-amber-200 flex items-center justify-between text-xs">
            <div class="truncate text-amber-900">
              <span class="font-bold"><i class="fa-solid fa-pen mr-1"></i>Editing message:</span>
              <span class="ml-1">${escapeHtml(chatEditingMessage.text)}</span>
            </div>
            <button onclick="window.appActions.clearChatEdit()" class="text-slate-400 hover:text-slate-700 p-1">
              <i class="fa-solid fa-xmark"></i>
            </button>
          </div>
        ` : ""}

        <!-- Message Composer -->
        <form onsubmit="window.appActions.sendChatMessage(event)" class="p-3 bg-white border-t border-slate-200 flex items-center gap-2">
          <label class="btn btn-secondary btn-sm cursor-pointer shrink-0" title="Send Image">
            <i class="fa-regular fa-image text-[#500568]"></i>
            <input id="chat-photo-input" type="file" accept="image/*" class="hidden" onchange="window.appActions.sendChatPhoto(this)" />
          </label>
          <input
            id="chat-message-input"
            type="text"
            value="${escapeHtml(chatEditingMessage ? chatEditingMessage.text : "")}"
            placeholder="Write a message to the team..."
            class="form-input flex-1"
            autocomplete="off"
          />
          <button type="submit" class="btn btn-primary shrink-0">
            <i class="fa-solid fa-paper-plane text-xs"></i> Send
          </button>
        </form>
      </div>
    </div>
  `;
}

export function renderProfileView(state) {
  const { currentUser, currentTeam, theme } = state;
  const isPrimaryLeader = isWorshipLeader(currentUser.ministryRole, currentUser.role) || currentTeam?.leaderUid === currentUser.uid;
  const displayName = currentUser.fullName || currentUser.name || "Member";
  const teamDisplayName = currentTeam?.teamName || currentUser?.teamName || "None";
  const isDark = theme === "dark";

  return `
    <div class="max-w-2xl mx-auto space-y-6">
      <!-- Page Header -->
      <div class="pb-4 border-b border-slate-200">
        <h1 class="text-xl font-bold text-[#10113F]">Account, Theme & Ministry Settings</h1>
        <p class="text-xs text-slate-500 mt-0.5">Manage your appearance preference, personal details, ministry role, and account password.</p>
      </div>

      <!-- Appearance & Dark Mode Card -->
      <div class="surface-card p-6 space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div class="flex items-start gap-3.5">
            <div class="w-10 h-10 rounded-xl bg-[#F5D0FE]/70 border border-[#E879F9] flex items-center justify-center text-[#500568] shrink-0">
              <i class="fa-solid ${isDark ? "fa-moon" : "fa-sun"} text-sm"></i>
            </div>
            <div>
              <h2 class="text-sm font-bold text-[#10113F]">Appearance & Theme</h2>
              <p class="text-xs text-slate-500 mt-0.5">Switch between Light Mode and Dark Mode. Your choice is saved automatically on this device.</p>
            </div>
          </div>

          <div class="grid grid-cols-2 p-1 bg-[#F5F5F8] rounded-xl border border-slate-200 text-xs font-bold shrink-0">
            <button
              id="theme-btn-light"
              type="button"
              onclick="window.appActions.setTheme('light')"
              aria-pressed="${!isDark}"
              class="px-3.5 py-2 rounded-lg flex items-center justify-center gap-1.5 transition ${
                !isDark ? "bg-[#500568] text-white shadow-xs" : "text-slate-600 hover:text-[#10113F]"
              }"
            >
              <i class="fa-regular fa-sun text-xs"></i>
              <span>Light Mode</span>
            </button>
            <button
              id="theme-btn-dark"
              type="button"
              onclick="window.appActions.setTheme('dark')"
              aria-pressed="${isDark}"
              class="px-3.5 py-2 rounded-lg flex items-center justify-center gap-1.5 transition ${
                isDark ? "bg-[#500568] text-white shadow-xs" : "text-slate-600 hover:text-[#10113F]"
              }"
            >
              <i class="fa-solid fa-moon text-xs"></i>
              <span>Dark Mode</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Mobile Quick Access Shortcuts (Calendar & Team Roster) -->
      <div class="md:hidden grid grid-cols-2 gap-3">
        <button onclick="window.appActions.switchTab('calendar')" class="surface-card surface-card-interactive p-3.5 flex items-center gap-3 text-left">
          <div class="w-9 h-9 rounded-lg bg-[#F5D0FE]/70 text-[#500568] flex items-center justify-center text-xs shrink-0">
            <i class="fa-solid fa-calendar-days"></i>
          </div>
          <div>
            <div class="text-xs font-bold text-[#10113F]">Ministry Calendar</div>
            <div class="text-[11px] text-slate-500">Full month view</div>
          </div>
        </button>
        <button onclick="window.appActions.switchTab('roster')" class="surface-card surface-card-interactive p-3.5 flex items-center gap-3 text-left">
          <div class="w-9 h-9 rounded-lg bg-[#E0E7FF] text-[#10113F] flex items-center justify-center text-xs shrink-0">
            <i class="fa-solid fa-users"></i>
          </div>
          <div>
            <div class="text-xs font-bold text-[#10113F]">Team Roster</div>
            <div class="text-[11px] text-slate-500">Members & roles</div>
          </div>
        </button>
      </div>

      <!-- Profile Card -->
      <div class="surface-card p-6 space-y-6">
        <div class="flex flex-col sm:flex-row items-start sm:items-center gap-4 pb-6 border-b border-slate-200">
          <div class="relative">
            ${getAvatarHtml(currentUser, "w-16 h-16", "text-lg")}
            <label class="absolute -bottom-1 -right-1 w-7 h-7 rounded-full bg-[#500568] border-2 border-white shadow-xs flex items-center justify-center text-white hover:bg-[#3D0350] cursor-pointer transition" title="Upload Avatar">
              <i class="fa-solid fa-camera text-[10px]"></i>
              <input id="profile-avatar-input" type="file" accept="image/*" class="hidden" onchange="window.appActions.uploadProfileAvatar(this)" />
            </label>
          </div>
          <div class="space-y-1">
            <h2 class="text-base font-bold text-[#10113F]">${escapeHtml(displayName)}</h2>
            <p class="text-xs text-slate-500">${escapeHtml(currentUser.email || "")}</p>
            <div class="pt-1">${getRoleBadgeHtml(currentUser)}</div>
          </div>
        </div>

        <form onsubmit="window.appActions.saveProfileChanges(event)" class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label class="form-label">Full Name</label>
              <input
                id="profile-name-input"
                type="text"
                required
                maxlength="60"
                value="${escapeHtml(displayName)}"
                class="form-input"
              />
            </div>
            <div>
              <label class="form-label">Phone Number (Optional)</label>
              <input
                id="profile-phone-input"
                type="text"
                maxlength="25"
                value="${escapeHtml(currentUser.phone || "")}"
                placeholder="+63 9XX XXX XXXX"
                class="form-input"
              />
            </div>
          </div>

          <div>
            <label class="form-label">Primary Ministry Role / Instrument</label>
            <select
              id="profile-role-select"
              ${isPrimaryLeader ? "disabled" : ""}
              class="form-select disabled:bg-slate-100 disabled:text-slate-500"
            >
              ${DEFAULT_MINISTRY_ROLES.map((r) => `
                <option value="${escapeHtml(r)}" ${currentUser.ministryRole === r ? "selected" : ""}>${escapeHtml(r)}</option>
              `).join("")}
            </select>
            ${isPrimaryLeader ? `
              <p class="text-[11px] text-slate-500 mt-1">Primary Worship Leaders can transfer leadership from the Team Roster page.</p>
            ` : ""}
          </div>

          <div class="pt-2 flex justify-end">
            <button type="submit" class="btn btn-primary">
              Save Profile Changes
            </button>
          </div>
        </form>
      </div>

      <!-- Change Password Card (with Show/Hide Password Eye Toggles) -->
      <div class="surface-card p-6 space-y-4">
        <div>
          <h3 class="text-sm font-bold text-[#10113F]">Change Account Password</h3>
          <p class="text-xs text-slate-500 mt-0.5">Update your password for both web and mobile sign-in.</p>
        </div>

        <form onsubmit="window.appActions.handleChangePassword(event)" class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label class="form-label" for="profile-new-password">New Password</label>
              ${renderPasswordInputHtml({
                id: "profile-new-password",
                placeholder: "8+ chars, upper, lower, number, symbol",
                required: true,
                autocomplete: "new-password",
                ariaLabel: "Show or hide new password",
                onInput: "window.appActions.updatePasswordStrengthUi('profile-new-password', this.value)"
              })}
            </div>
            <div>
              <label class="form-label" for="profile-confirm-password">Confirm New Password</label>
              ${renderPasswordInputHtml({
                id: "profile-confirm-password",
                placeholder: "Re-enter new password",
                required: true,
                autocomplete: "new-password",
                ariaLabel: "Show or hide confirm password"
              })}
            </div>
          </div>
          ${renderPasswordStrengthBoxHtml("profile-new-password")}
          <div class="flex justify-end">
            <button id="change-password-btn" type="submit" class="btn btn-navy">
              <i class="fa-solid fa-key text-xs"></i> Update Password
            </button>
          </div>
        </form>
      </div>

      <!-- Workspace Membership & Session Controls -->
      <div class="surface-card p-6 space-y-4">
        <div>
          <h3 class="text-sm font-bold text-[#10113F]">Workspace & Session</h3>
          <p class="text-xs text-slate-500 mt-0.5">Current team: <strong class="text-[#10113F]">${escapeHtml(teamDisplayName)}</strong></p>
        </div>
        <div class="flex flex-wrap items-center gap-3 pt-1">
          <button onclick="window.appActions.leaveCurrentTeam()" class="btn btn-danger">
            <i class="fa-solid fa-person-walking-arrow-right text-xs"></i> Leave Current Team
          </button>
          <button onclick="window.appActions.signOutUser()" class="btn btn-secondary">
            <i class="fa-solid fa-right-from-bracket text-xs"></i> Sign Out
          </button>
        </div>
      </div>
    </div>
  `;
}
